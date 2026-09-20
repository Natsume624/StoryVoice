package com.storyvoice.app.audio

import android.content.Context
import android.media.MediaPlayer
import android.media.PlaybackParams
import com.storyvoice.app.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
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
    val serverUrl: String = "",
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
    private val preferences = appContext.getSharedPreferences("narration_settings", Context.MODE_PRIVATE)
    private var serverUrl = preferences.getString("server_url", BuildConfig.NARRATION_API_URL)
        ?.normalizeServerUrl() ?: BuildConfig.NARRATION_API_URL
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val cacheDir = File(appContext.cacheDir, "narration").apply { mkdirs() }
    private val _state = MutableStateFlow(NarrationState(serverUrl = serverUrl))
    val state: StateFlow<NarrationState> = _state.asStateFlow()
    private var player: MediaPlayer? = null
    private var segments = emptyList<SpeechSegment>()
    private var current = 0
    private var speed = 1f
    private var expressiveness = .96f
    private var selectedVoice = "Cindy"

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
        _state.value = NarrationState(voiceId = voice.id, voiceLabel = voice.label, serverUrl = serverUrl)
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

    fun setServerUrl(value: String): Boolean {
        val normalized = value.normalizeServerUrl()
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            _state.value = _state.value.copy(error = "服务地址必须以 http:// 或 https:// 开头")
            return false
        }
        serverUrl = normalized
        preferences.edit().putString("server_url", serverUrl).apply()
        stop()
        _state.value = _state.value.copy(serverUrl = serverUrl, error = null)
        return true
    }

    fun shutdown() {
        player?.release(); player = null; scope.cancel()
    }

    private fun prepareStory(text: String) {
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
        val payload = JSONObject().put("text", text)
        val response = postJson("/api/plan", payload)
        val array = response.getJSONArray("segments")
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
        val payload = JSONObject()
            .put("text", segment.text)
            .put("speaker", segment.speaker)
            .put("emotion", segment.emotion)
            .put("voice", selectedVoice)
            .put("expressiveness", expressiveness)
        val connection = openConnection("/api/speech").apply {
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }
        connection.outputStream.use { it.write(payload.toString().toByteArray()) }
        if (connection.responseCode !in 200..299) error(readError(connection))
        connection.inputStream.use { input -> file.outputStream().use(input::copyTo) }
        connection.disconnect()
        return file
    }

    private fun postJson(path: String, body: JSONObject): JSONObject {
        val connection = openConnection(path).apply {
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }
        connection.outputStream.use { it.write(body.toString().toByteArray()) }
        if (connection.responseCode !in 200..299) error(readError(connection))
        val result = connection.inputStream.bufferedReader().use { it.readText() }
        connection.disconnect()
        return JSONObject(result)
    }

    private fun openConnection(path: String) = (URL(serverUrl + path).openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        connectTimeout = 20_000
        readTimeout = 90_000
    }

    private fun readError(connection: HttpURLConnection): String =
        connection.errorStream?.bufferedReader()?.use { it.readText() }?.let {
            runCatching { JSONObject(it).optString("error") }.getOrNull()
        }?.takeIf { it.isNotBlank() } ?: "云端朗读服务不可用（HTTP ${connection.responseCode}）"

    private fun showError(error: Throwable) {
        val detail = error.message ?: "云端朗读失败"
        val message = if (detail.contains("failed to connect", ignoreCase = true) || detail.contains("connect", ignoreCase = true)) {
            "无法连接朗读服务 $serverUrl。请确认电脑端服务已启动、手机与电脑在同一网络，或在设置中更改服务地址。"
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

    private fun String.normalizeServerUrl(): String = trim().trimEnd('/')
}
