package com.wxn.reader.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceAbiTest {

    @Test
    fun `arm64 first returns arm64`() {
        assertEquals(
            DeviceAbi.ARM64,
            DeviceAbi.preferredAbi(arrayOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64"))
        )
    }

    @Test
    fun `v7a first still prefers arm64 by hardware priority`() {
        assertEquals(
            DeviceAbi.ARM64,
            DeviceAbi.preferredAbi(arrayOf("armeabi-v7a", "arm64-v8a"))
        )
    }

    @Test
    fun `arm32 only device returns v7a`() {
        assertEquals(
            DeviceAbi.ARM32,
            DeviceAbi.preferredAbi(arrayOf("armeabi-v7a", "x86"))
        )
        assertTrue(DeviceAbi.isArmSupported(arrayOf("armeabi-v7a", "x86")))
    }

    @Test
    fun `x86 only emulator falls back to v7a and is not arm supported`() {
        val abis = arrayOf("x86", "x86_64")
        assertEquals(DeviceAbi.ARM32, DeviceAbi.preferredAbi(abis))
        assertFalse(DeviceAbi.isArmSupported(abis))
    }

    @Test
    fun `arm64 device is arm supported`() {
        val abis = arrayOf("arm64-v8a", "armeabi-v7a")
        assertTrue(DeviceAbi.isArmSupported(abis))
    }
}
