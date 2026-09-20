package com.storyvoice.app.audio

import android.content.Context
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.util.Base64
import com.storyvoice.app.data.SecureSettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

data class NarrationState(
    val isReady: Boolean = true,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val currentChunk: Int = 0,
    val totalChunks: Int = 0,
    val speaker: String = "旁白",
    val emotion: String = "自然",
    val currentText: String = "",
    val currentOffset: Int = 0,
    val voiceId: String = "Cindy",
    val voiceLabel: String = "台湾故事姐姐",
    val apiConfigured: Boolean = false,
    val endpoint: String = SecureSettingsStore.DEFAULT_ENDPOINT,
    val sleepTimerSeconds: Int? = null,
    val error: String? = null
)

data class NarrationVoice(val id: String, val label: String, val description: String)

private data class SpeechSegment(
    val text: String,
    val speaker: String,
    val emotion: String
)

/**
 * Cloud AI narration player. The Alibaba Cloud Model Studio key lives only in the companion server;
 * this Android client never receives or persists it.
 */
class CloudNarrator(context: Context) {
    val availableVoices = listOf(
        NarrationVoice("Cindy", "台湾故事姐姐", "温柔台湾腔，幼儿园老师般亲切"),
        NarrationVoice("Tina", "暖心姐姐", "甜美温暖、轻柔陪伴"),
        NarrationVoice("Serena", "温柔女声", "柔和自然的普通话女声"),
        NarrationVoice("Ethan", "阳光男声", "清朗温暖的普通话男声"),
        NarrationVoice("auto", "自动多角色", "旁白与角色自动使用不同音色")
    )
    private val appContext = context.applicationContext
    private val settings = SecureSettingsStore(appContext)
    private var endpoint = settings.endpoint
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val cacheDir = File(appContext.cacheDir, "narration").apply { mkdirs() }
    private val _state = MutableStateFlow(NarrationState(apiConfigured = settings.hasApiKey(), endpoint = endpoint))
    val state: StateFlow<NarrationState> = _state.asStateFlow()
    private var player: MediaPlayer? = null
    private var segments = emptyList<SpeechSegment>()
    private var current = 0
    private var speed = 1f
    private var expressiveness = .96f
    private var selectedVoice = "Cindy"
    private var sleepTimerJob: Job? = null

    fun play(text: String) {
        if (segments.isNotEmpty() && current >= segments.size) {
            player?.release(); player = null; current = 0
            playCurrent()
            return
        }
        player?.let {
            if (!it.isPlaying && segments.isNotEmpty()) {
                it.start()
                _state.value = _state.value.copy(isPlaying = true)
                return
            }
        }
        if (segments.isEmpty()) prepareStory(text) else playCurrent()
    }

    fun pause() {
        player?.takeIf { it.isPlaying }?.pause()
        _state.value = _state.value.copy(isPlaying = false)
    }

    fun stop() {
        player?.release(); player = null
        segments = emptyList(); current = 0
        val voice = availableVoices.first { it.id == selectedVoice }
        _state.value = _state.value.copy(
            isPlaying = false, isLoading = false, currentChunk = 0, totalChunks = 0,
            speaker = "旁白", emotion = "自然", currentText = "", currentOffset = 0,
            voiceId = voice.id, voiceLabel = voice.label, error = null
        )
    }

    fun seekTo(fraction: Float) {
        if (segments.isEmpty()) return
        current = (segments.lastIndex * fraction.coerceIn(0f, 1f)).toInt()
        player?.release(); player = null
        if (_state.value.isPlaying) playCurrent()
        else _state.value = _state.value.copy(
            currentChunk = current,
            currentText = segments[current].text,
            currentOffset = segments.take(current).sumOf { it.text.length }
        )
    }

    fun setRate(value: Float) {
        speed = value
        runCatching { player?.playbackParams = PlaybackParams().setSpeed(value) }
    }

    fun setExpressiveness(value: Float) { expressiveness = value }

    fun setVoice(voiceId: String) {
        val voice = availableVoices.firstOrNull { it.id == voiceId } ?: return
        selectedVoice = voice.id
        player?.release(); player = null
        _state.value = _state.value.copy(
            isPlaying = false,
            isLoading = false,
            voiceId = voice.id,
            voiceLabel = voice.label
        )
    }

    fun configureCloud(apiKey: String, endpointValue: String): Boolean {
        val normalized = endpointValue.trim().trimEnd('/')
        if (!normalized.startsWith("https://")) {
            _state.value = _state.value.copy(error = "阿里云地址必须以 https:// 开头")
            return false
        }
        if (apiKey.isNotBlank()) settings.saveApiKey(apiKey)
        if (!settings.hasApiKey()) {
            _state.value = _state.value.copy(error = "请填写阿里云百炼 API Key")
            return false
        }
        endpoint = normalized
        settings.endpoint = normalized
        stop()
        _state.value = _state.value.copy(apiConfigured = true, endpoint = endpoint, error = null)
        return true
    }

    fun setSleepTimer(minutes: Int?) {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        if (minutes == null) {
            _state.value = _state.value.copy(sleepTimerSeconds = null)
            return
        }
        sleepTimerJob = scope.launch {
            var remaining = minutes * 60
            _state.value = _state.value.copy(sleepTimerSeconds = remaining)
            while (remaining > 0) {
                delay(1_000)
                remaining--
                _state.value = _state.value.copy(sleepTimerSeconds = remaining)
            }
            pause()
            _state.value = _state.value.copy(sleepTimerSeconds = null)
        }
    }

    fun shutdown() {
        sleepTimerJob?.cancel(); player?.release(); player = null; scope.cancel()
    }

    private fun prepareStory(text: String) {
        if (!settings.hasApiKey()) {
            _state.value = _state.value.copy(error = "请先点击设置，填写阿里云百炼 API Key")
            return
        }
        scope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            runCatching { withContext(Dispatchers.IO) { requestPlan(text) } }
                .onSuccess { plan ->
                    segments = plan
                    current = 0
                    _state.value = _state.value.copy(isLoading = false, totalChunks = plan.size)
                    playCurrent()
                }
                .onFailure(::showError)
        }
    }

    private fun playCurrent() {
        if (current !in segments.indices) {
            _state.value = _state.value.copy(isPlaying = false)
            return
        }
        val segment = segments[current]
        scope.launch {
            _state.value = _state.value.copy(
                isLoading = true,
                currentChunk = current,
                speaker = speakerLabel(segment.speaker),
                emotion = emotionLabel(segment.emotion),
                currentText = segment.text,
                currentOffset = segments.take(current).sumOf { it.text.length },
                error = null
            )
            runCatching { withContext(Dispatchers.IO) { audioFor(segment) } }
                .onSuccess(::startPlayer)
                .onFailure(::showError)
        }
    }

    private fun startPlayer(audio: File) {
        player?.release()
        player = MediaPlayer().apply {
            setDataSource(audio.absolutePath)
            setOnPreparedListener {
                runCatching { playbackParams = PlaybackParams().setSpeed(speed) }
                start()
                _state.value = _state.value.copy(isPlaying = true, isLoading = false)
            }
            setOnCompletionListener {
                current++
                if (current < segments.size) playCurrent()
                else _state.value = _state.value.copy(isPlaying = false, isLoading = false)
            }
            setOnErrorListener { _, _, _ ->
                showError(IllegalStateException("音频播放失败")); true
            }
            prepareAsync()
        }
    }

    private fun requestPlan(text: String): List<SpeechSegment> {
        return chunkText(text).flatMap(::requestPlanChunk)
    }

    private fun requestPlanChunk(text: String): List<SpeechSegment> {
        val instruction = """
            你是中文有声书导演。把输入原文切成适合逐段配音的片段。
            必须逐字保留全部原文，不得改写、删减或新增内容。
            叙述使用 narrator；人物台词稳定映射到 character_1 至 character_3。
            每段为完整句子且不超过350个汉字，emotion 只能是 neutral、warm、joy、sad、tense、angry、whisper、solemn。
            只返回 JSON：{"segments":[{"text":"原文","speaker":"narrator","emotion":"warm"}]}。
        """.trimIndent()
        val messages = org.json.JSONArray()
            .put(JSONObject().put("role", "system").put("content", instruction))
            .put(JSONObject().put("role", "user").put("content", text))
        val payload = JSONObject()
            .put("model", "qwen3.8-flash")
            .put("messages", messages)
            .put("enable_thinking", false)
            .put("response_format", JSONObject().put("type", "json_object"))
        val response = postJsonAbsolute("${compatibleEndpoint()}/chat/completions", payload)
        val content = response.getJSONArray("choices").getJSONObject(0)
            .getJSONObject("message").getString("content")
            .trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val array = JSONObject(content).getJSONArray("segments")
        return (0 until array.length()).map { index ->
            array.getJSONObject(index).let {
                SpeechSegment(it.getString("text"), it.getString("speaker"), it.getString("emotion"))
            }
        }.filter { it.text.isNotBlank() }
    }

    private fun chunkText(text: String, maxLength: Int = 11_000): List<String> {
        if (text.length <= maxLength) return listOf(text)
        val result = mutableListOf<String>()
        var remaining = text
        while (remaining.isNotEmpty()) {
            if (remaining.length <= maxLength) {
                result += remaining
                break
            }
            val boundary = listOf(
                remaining.lastIndexOf("\n\n", maxLength),
                remaining.lastIndexOf('。', maxLength),
                remaining.lastIndexOf('！', maxLength),
                remaining.lastIndexOf('？', maxLength)
            ).maxOrNull()?.takeIf { it > maxLength / 2 } ?: maxLength
            result += remaining.substring(0, boundary + 1)
            remaining = remaining.substring(boundary + 1)
        }
        return result
    }

    private fun audioFor(segment: SpeechSegment): File {
        val cacheKey = sha256("$selectedVoice|${segment.speaker}|${segment.emotion}|$expressiveness|${segment.text}")
        val file = File(cacheDir, "$cacheKey.audio")
        if (file.exists() && file.length() > 0) return file
        val voice = if (selectedVoice == "auto") when (segment.speaker) {
            "character_1" -> "Serena"; "character_2" -> "Cherry"; "character_3" -> "Chelsie"; else -> "Ethan"
        } else selectedVoice
        val audio = if (voice == "Cindy" || voice == "Tina") {
            createOmniSpeech(segment, voice)
        } else {
            createTtsSpeech(segment, voice)
        }
        file.outputStream().use { it.write(audio) }
        return file
    }

    private fun createTtsSpeech(segment: SpeechSegment, voice: String): ByteArray {
        val payload = JSONObject().put("model", "qwen3-tts-instruct-flash").put("input", JSONObject()
            .put("text", segment.text).put("voice", voice).put("language_type", "Chinese")
            .put("instructions", storyDirection(segment)).put("optimize_instructions", true))
        val response = postJsonAbsolute("$endpoint/services/aigc/multimodal-generation/generation", payload)
        val audioUrl = response.optJSONObject("output")?.optJSONObject("audio")?.optString("url")
            ?.takeIf { it.isNotBlank() } ?: error("阿里云未返回音频地址：${response.optString("message", "未知错误")}")
        val connection = URL(audioUrl).openConnection() as HttpURLConnection
        connection.connectTimeout = 20_000; connection.readTimeout = 90_000
        if (connection.responseCode !in 200..299) error("下载阿里云音频失败（HTTP ${connection.responseCode}）")
        return connection.inputStream.use { it.readBytes() }.also { connection.disconnect() }
    }

    private fun createOmniSpeech(segment: SpeechSegment, voice: String): ByteArray {
        val messages = org.json.JSONArray()
            .put(JSONObject().put("role", "system").put("content", storyDirection(segment) + if (voice == "Cindy") "\n使用明显的台湾国语口音，咬字柔软圆润、语调轻扬，像温柔的台湾幼稚园老师讲睡前故事。" else "\n声音甜美温暖。"))
            .put(JSONObject().put("role", "user").put("content", segment.text))
        val payload = JSONObject().put("model", "qwen3.5-omni-plus").put("messages", messages)
            .put("modalities", org.json.JSONArray().put("text").put("audio"))
            .put("audio", JSONObject().put("voice", voice).put("format", "wav"))
            .put("stream", true)
        val connection = authorizedConnection("${compatibleEndpoint()}/chat/completions").apply { doOutput = true }
        connection.outputStream.use { it.write(payload.toString().toByteArray()) }
        if (connection.responseCode !in 200..299) error(readError(connection))
        val bytes = ByteArrayOutputStream()
        connection.inputStream.bufferedReader().useLines { lines ->
            lines.filter { it.startsWith("data:") }.forEach { line ->
                val data = line.removePrefix("data:").trim()
                if (data.isNotBlank() && data != "[DONE]") {
                    val encoded = runCatching { JSONObject(data).getJSONArray("choices").getJSONObject(0)
                        .getJSONObject("delta").optJSONObject("audio")?.optString("data") }.getOrNull()
                    if (!encoded.isNullOrBlank()) bytes.write(Base64.decode(encoded, Base64.DEFAULT))
                }
            }
        }
        connection.disconnect()
        val raw = bytes.toByteArray()
        if (raw.isEmpty()) error("阿里云没有返回音频数据")
        return if (raw.size >= 4 && String(raw, 0, 4) == "RIFF") raw else pcmToWav(raw)
    }

    private fun storyDirection(segment: SpeechSegment): String {
        val emotion = mapOf(
            "neutral" to "自然克制", "warm" to "温暖亲切", "joy" to "愉悦明亮", "sad" to "低沉悲伤",
            "tense" to "紧张克制", "angry" to "压抑而有力量", "whisper" to "轻声低语", "solemn" to "庄重沉稳"
        )[segment.emotion] ?: "自然温柔"
        return "像有耐心的幼儿园老师给小朋友讲睡前故事。声音温柔、甜美、柔软，语速稍慢，停顿自然。本段情绪：$emotion，强度 ${(expressiveness * 100).toInt()}%。逐字朗读原文，不增删内容。"
    }

    private fun pcmToWav(pcm: ByteArray): ByteArray {
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray()).putInt(36 + pcm.size).put("WAVE".toByteArray())
        header.put("fmt ".toByteArray()).putInt(16).putShort(1.toShort()).putShort(1.toShort())
        header.putInt(24_000).putInt(48_000).putShort(2.toShort()).putShort(16.toShort())
        header.put("data".toByteArray()).putInt(pcm.size)
        return header.array() + pcm
    }

    private fun postJsonAbsolute(url: String, body: JSONObject): JSONObject {
        val connection = authorizedConnection(url).apply {
            doOutput = true
        }
        connection.outputStream.use { it.write(body.toString().toByteArray()) }
        if (connection.responseCode !in 200..299) error(readError(connection))
        val result = connection.inputStream.bufferedReader().use { it.readText() }
        connection.disconnect()
        return JSONObject(result)
    }

    private fun compatibleEndpoint(): String = endpoint.replace(Regex("/api/v1$"), "/compatible-mode/v1")

    private fun authorizedConnection(url: String) = (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        connectTimeout = 20_000
        readTimeout = 90_000
        setRequestProperty("Authorization", "Bearer ${settings.loadApiKey()}")
        setRequestProperty("Content-Type", "application/json")
    }

    private fun readError(connection: HttpURLConnection): String =
        connection.errorStream?.bufferedReader()?.use { it.readText() }?.let {
            runCatching { JSONObject(it).optString("error") }.getOrNull()
        }?.takeIf { it.isNotBlank() } ?: "云端朗读服务不可用（HTTP ${connection.responseCode}）"

    private fun showError(error: Throwable) {
        val detail = error.message ?: "云端朗读失败"
        val message = if (detail.contains("failed to connect", ignoreCase = true) || detail.contains("connect", ignoreCase = true)) {
            "无法连接阿里云百炼。请检查手机网络、API 地址和 Key。"
        } else detail
        _state.value = _state.value.copy(isPlaying = false, isLoading = false, error = message)
    }

    private fun speakerLabel(value: String) = when (value) {
        "narrator" -> "旁白"
        "character_1" -> "角色一"
        "character_2" -> "角色二"
        "character_3" -> "角色三"
        else -> "角色"
    }

    private fun emotionLabel(value: String) = mapOf(
        "neutral" to "自然", "warm" to "温柔", "joy" to "喜悦", "sad" to "悲伤",
        "tense" to "紧张", "angry" to "愤怒", "whisper" to "低语", "solemn" to "庄重"
    )[value] ?: "自然"

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

}
