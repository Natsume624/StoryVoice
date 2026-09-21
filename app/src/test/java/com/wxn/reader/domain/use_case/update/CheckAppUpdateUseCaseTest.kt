package com.wxn.reader.domain.use_case.update

import com.wxn.reader.data.remote.api.ApiBaseException
import com.wxn.reader.data.remote.api.ApiCode
import com.wxn.reader.data.remote.api.AppUpdateApi
import com.wxn.reader.data.remote.dto.AppUpdateInfo
import com.wxn.reader.data.remote.dto.BaseResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CheckAppUpdateUseCaseTest {

    private class FakeAppUpdateApi : AppUpdateApi {
        var callCount = 0
        var lastSource: String? = null
        var lastArch: String? = null
        var lastVersionCode: Int? = null
        var outcome: Result<BaseResponse<AppUpdateInfo>>? = null
        var thrown: Throwable? = null

        override suspend fun checkUpdate(
            source: String,
            arch: String,
            versionCode: Int
        ): Result<BaseResponse<AppUpdateInfo>> {
            callCount++
            lastSource = source
            lastArch = arch
            lastVersionCode = versionCode
            thrown?.let { throw it }
            return outcome ?: throw IllegalStateException("outcome not configured")
        }
    }

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
        encodeDefaults = true
    }

    private val arm64First = arrayOf("arm64-v8a", "armeabi-v7a")
    private val x86Only = arrayOf("x86", "x86_64")

    private fun envelopeJson(data: String?): String =
        """{"success":true,"code":"0","message":"success","data":$data}"""

    private fun infoJson(
        versionCode: Int = 28,
        notes: String = """{"default":"New features"}""",
        urls: String? = """{"arm64-v8a":"https://example.com/arm64.apk","armeabi-v7a":"https://example.com/v7a.apk"}""",
        forceUpdate: Boolean = false
    ): String {
        return if (urls != null) {
            """{"latestVersionCode":$versionCode,"latestVersionName":"1.24.0","releaseNotes":$notes,"forceUpdate":$forceUpdate,"downloadUrls":$urls}"""
        } else {
            """{"latestVersionCode":$versionCode,"latestVersionName":"1.24.0","releaseNotes":$notes,"forceUpdate":$forceUpdate}"""
        }
    }

    private fun FakeAppUpdateApi.ok(data: String?) {
        outcome = Result.success(json.decodeFromString(envelopeJson(data)))
    }

    // ===== 版本比较 =====

    @Test
    fun `newer version returns available with abi url`() = runTest {
        val fake = FakeAppUpdateApi().apply { ok(infoJson(versionCode = 28)) }
        val useCase = CheckAppUpdateUseCase(fake, isPlayChannel = false)

        val r = useCase(currentVersionCode = 27, appLanguage = "en", supportedAbis = arm64First)

        assertTrue(r is CheckAppUpdateUseCase.UpdateResult.Available)
        r as CheckAppUpdateUseCase.UpdateResult.Available
        assertEquals(28, r.latestVersionCode)
        assertEquals("https://example.com/arm64.apk", r.downloadUrl)
        assertEquals("New features", r.resolvedNotes)
        assertEquals("android-general", fake.lastSource)
        assertEquals("arm64-v8a", fake.lastArch)
        assertEquals(27, fake.lastVersionCode)
    }

    @Test
    fun `equal version returns up to date`() = runTest {
        val fake = FakeAppUpdateApi().apply { ok(infoJson(versionCode = 27)) }
        val r = CheckAppUpdateUseCase(fake, isPlayChannel = false)(
            currentVersionCode = 27, appLanguage = "en", supportedAbis = arm64First
        )
        assertTrue(r is CheckAppUpdateUseCase.UpdateResult.UpToDate)
    }

    @Test
    fun `local higher version returns up to date`() = runTest {
        val fake = FakeAppUpdateApi().apply { ok(infoJson(versionCode = 26)) }
        val r = CheckAppUpdateUseCase(fake, isPlayChannel = false)(
            currentVersionCode = 27, appLanguage = "en", supportedAbis = arm64First
        )
        assertTrue(r is CheckAppUpdateUseCase.UpdateResult.UpToDate)
    }

    // ===== data:null 与错误分支 =====

    @Test
    fun `null data means unconfigured source and up to date`() = runTest {
        val fake = FakeAppUpdateApi().apply { ok("null") }
        val r = CheckAppUpdateUseCase(fake, isPlayChannel = false)(
            currentVersionCode = 27, appLanguage = "en", supportedAbis = arm64First
        )
        assertTrue(r is CheckAppUpdateUseCase.UpdateResult.UpToDate)
    }

    @Test
    fun `timeout exception maps to network error`() = runTest {
        val fake = FakeAppUpdateApi().apply {
            outcome = Result.failure(ApiBaseException(ApiCode.CODE_TIME_OUT, "timeout"))
        }
        val r = CheckAppUpdateUseCase(fake, isPlayChannel = false)(
            currentVersionCode = 27, appLanguage = "en", supportedAbis = arm64First
        )
        assertTrue(r is CheckAppUpdateUseCase.UpdateResult.Error)
        assertTrue((r as CheckAppUpdateUseCase.UpdateResult.Error).isNetwork)
    }

    @Test
    fun `unknown host exception maps to network error`() = runTest {
        val fake = FakeAppUpdateApi().apply {
            outcome = Result.failure(ApiBaseException(ApiCode.CODE_SERV_UNKOWN, "offline"))
        }
        val r = CheckAppUpdateUseCase(fake, isPlayChannel = false)(
            currentVersionCode = 27, appLanguage = "en", supportedAbis = arm64First
        )
        assertTrue((r as CheckAppUpdateUseCase.UpdateResult.Error).isNetwork)
    }

    @Test
    fun `server error maps to non-network error`() = runTest {
        val fake = FakeAppUpdateApi().apply {
            outcome = Result.failure(ApiBaseException(ApiCode.CODE_SERV_ERROR, "http 500"))
        }
        val r = CheckAppUpdateUseCase(fake, isPlayChannel = false)(
            currentVersionCode = 27, appLanguage = "en", supportedAbis = arm64First
        )
        assertTrue(r is CheckAppUpdateUseCase.UpdateResult.Error)
        assertFalse((r as CheckAppUpdateUseCase.UpdateResult.Error).isNetwork)
    }

    @Test
    fun `cancellation exception is rethrown`() = runTest {
        val fake = FakeAppUpdateApi().apply { thrown = CancellationException("scope cancelled") }
        try {
            CheckAppUpdateUseCase(fake, isPlayChannel = false)(
                currentVersionCode = 27, appLanguage = "en", supportedAbis = arm64First
            )
            fail("expected CancellationException to propagate")
        } catch (expected: CancellationException) {
            assertEquals("scope cancelled", expected.message)
        }
    }

    // ===== 渠道分流 =====

    @Test
    fun `play channel ignores download urls`() = runTest {
        val fake = FakeAppUpdateApi().apply { ok(infoJson(versionCode = 28)) }
        val r = CheckAppUpdateUseCase(fake, isPlayChannel = true)(
            currentVersionCode = 27, appLanguage = "en", supportedAbis = arm64First
        )
        assertTrue(r is CheckAppUpdateUseCase.UpdateResult.Available)
        assertNull((r as CheckAppUpdateUseCase.UpdateResult.Available).downloadUrl)
        assertEquals("android-play", fake.lastSource)
    }

    @Test
    fun `general channel missing abi url returns error`() = runTest {
        val fake = FakeAppUpdateApi().apply {
            ok(infoJson(versionCode = 28, urls = """{"armeabi-v7a":"https://example.com/v7a.apk"}"""))
        }
        val r = CheckAppUpdateUseCase(fake, isPlayChannel = false)(
            currentVersionCode = 27, appLanguage = "en", supportedAbis = arm64First
        )
        assertTrue(r is CheckAppUpdateUseCase.UpdateResult.Error)
        assertFalse((r as CheckAppUpdateUseCase.UpdateResult.Error).isNetwork)
    }

    @Test
    fun `force update is passed through`() = runTest {
        val fake = FakeAppUpdateApi().apply { ok(infoJson(versionCode = 28, forceUpdate = true)) }
        val r = CheckAppUpdateUseCase(fake, isPlayChannel = false)(
            currentVersionCode = 27, appLanguage = "en", supportedAbis = arm64First
        )
        assertTrue((r as CheckAppUpdateUseCase.UpdateResult.Available).forceUpdate)
    }

    // ===== ABI 守卫 =====

    @Test
    fun `x86 only device short circuits without request`() = runTest {
        val fake = FakeAppUpdateApi().apply { ok(infoJson(versionCode = 28)) }
        val r = CheckAppUpdateUseCase(fake, isPlayChannel = false)(
            currentVersionCode = 27, appLanguage = "en", supportedAbis = x86Only
        )
        assertTrue(r is CheckAppUpdateUseCase.UpdateResult.UnsupportedAbi)
        assertEquals(0, fake.callCount)
    }

    // ===== D12 releaseNotes 选择链 =====

    @Test
    fun `zh-TW exact match wins`() = runTest {
        val fake = FakeAppUpdateApi().apply {
            ok(infoJson(notes = """{"default":"Default text","zh-TW":"繁中介紹","zh":"简体介绍"}"""))
        }
        val r = CheckAppUpdateUseCase(fake, isPlayChannel = false)(
            currentVersionCode = 27, appLanguage = "zh-TW", supportedAbis = arm64First
        )
        assertEquals("繁中介紹", (r as CheckAppUpdateUseCase.UpdateResult.Available).resolvedNotes)
    }

    @Test
    fun `zh-TW never falls back to simplified zh`() = runTest {
        val fake = FakeAppUpdateApi().apply {
            ok(infoJson(notes = """{"default":"Default text","zh":"简体介绍"}"""))
        }
        val r = CheckAppUpdateUseCase(fake, isPlayChannel = false)(
            currentVersionCode = 27, appLanguage = "zh-TW", supportedAbis = arm64First
        )
        assertEquals("Default text", (r as CheckAppUpdateUseCase.UpdateResult.Available).resolvedNotes)
    }

    @Test
    fun `unknown language falls back to default`() = runTest {
        val fake = FakeAppUpdateApi().apply {
            ok(infoJson(notes = """{"default":"Default text","zh":"简体介绍"}"""))
        }
        val r = CheckAppUpdateUseCase(fake, isPlayChannel = false)(
            currentVersionCode = 27, appLanguage = "fr", supportedAbis = arm64First
        )
        assertEquals("Default text", (r as CheckAppUpdateUseCase.UpdateResult.Available).resolvedNotes)
    }

    @Test
    fun `blank notes resolve to null and ui hides the block`() = runTest {
        val fake = FakeAppUpdateApi().apply {
            ok(infoJson(notes = "{}"))
        }
        val r = CheckAppUpdateUseCase(fake, isPlayChannel = false)(
            currentVersionCode = 27, appLanguage = "en", supportedAbis = arm64First
        )
        assertNull((r as CheckAppUpdateUseCase.UpdateResult.Available).resolvedNotes)
    }
}
