package com.wxn.reader.data.remote.dto

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateDtoTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
        encodeDefaults = true
    }

    @Test
    fun `parses full general payload`() {
        val payload = """
            {"success":true,"code":"0","message":"success","data":{
                "latestVersionCode":28,
                "latestVersionName":"1.24.0",
                "releaseNotes":{"default":"Bug fixes","zh":"修复问题"},
                "forceUpdate":false,
                "downloadUrls":{"arm64-v8a":"https://a/64.apk","armeabi-v7a":"https://a/32.apk"}
            }}
        """.trimIndent()

        val envelope = json.decodeFromString<BaseResponse<AppUpdateInfo>>(payload)
        assertTrue(envelope.success == true)
        val info = envelope.data
        if (info == null) {
            throw AssertionError("expected non-null data")
        } else {
            assertEquals(28, info.latestVersionCode)
            assertEquals("1.24.0", info.latestVersionName)
            assertEquals("修复问题", info.releaseNotes["zh"])
            assertEquals("https://a/64.apk", info.downloadUrls["arm64-v8a"])
            assertTrue(!info.forceUpdate)
        }
    }

    @Test
    fun `parses null data as unconfigured`() {
        val payload = """{"success":true,"code":"0","message":"no update configured","data":null}"""
        val envelope = json.decodeFromString<BaseResponse<AppUpdateInfo>>(payload)
        assertNull(envelope.data)
    }

    @Test
    fun `play payload without downloadUrls uses empty map default`() {
        val payload = """
            {"success":true,"code":"0","message":"success","data":{
                "latestVersionCode":28,
                "latestVersionName":"1.24.0",
                "releaseNotes":{"default":"New"},
                "forceUpdate":true
            }}
        """.trimIndent()

        val envelope = json.decodeFromString<BaseResponse<AppUpdateInfo>>(payload)
        val info = envelope.data
        if (info == null) {
            throw AssertionError("expected non-null data")
        } else {
            assertTrue(info.downloadUrls.isEmpty())
            assertTrue(info.forceUpdate)
        }
    }

    @Test
    fun `unknown server keys are ignored`() {
        val payload = """
            {"success":true,"code":"0","message":"success","data":{
                "latestVersionCode":28,
                "latestVersionName":"1.24.0",
                "releaseNotes":{"default":"x"},
                "futureField":"whatever"
            },"extraTopLevel":1}
        """.trimIndent()

        val envelope = json.decodeFromString<BaseResponse<AppUpdateInfo>>(payload)
        assertEquals(28, envelope.data?.latestVersionCode)
    }
}
