package com.wxn.reader.presentation.mainReader.autoread

import com.wxn.bookread.data.model.preference.ReaderPreferences
import com.wxn.bookread.data.source.local.ReaderPreferencesUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 自动阅读速度范围单测（2026-09-08 方案 A，docs/plans/2026-09-08-plan-auto-read-speed-range.md §5.1）。
 *
 * 覆盖：范围常量自洽、存量 100~900 兼容、[ReaderPreferences.coerceAutoReadSpeed] 钳制语义
 * （ReaderPreferencesUtil 写入 :381 / 读取 :247 双路共用）、默认值接线。
 *
 * 使用 Robolectric：[ReaderPreferences] 依赖 `android.graphics.Color`（同 PerBookOverrideMapperTest）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])  // Robolectric 4.14.1 最高支持 SDK 34；compileSdk=36 需显式锁定（同 bookread 既有做法）
class AutoReadSpeedRangeTest {

    @Test
    fun `范围自洽 min小于default小于max`() {
        assertTrue(
            "MIN(${ReaderPreferences.AUTO_READ_SPEED_MIN}) < DEFAULT(${ReaderPreferences.AUTO_READ_SPEED_DEFAULT}) " +
                    "< MAX(${ReaderPreferences.AUTO_READ_SPEED_MAX}) 不成立",
            ReaderPreferences.AUTO_READ_SPEED_MIN < ReaderPreferences.AUTO_READ_SPEED_DEFAULT &&
                    ReaderPreferences.AUTO_READ_SPEED_DEFAULT < ReaderPreferences.AUTO_READ_SPEED_MAX
        )
    }

    @Test
    fun `存量兼容 旧范围100到900包含于新范围`() {
        assertTrue("旧下限 100 不得低于新下限", ReaderPreferences.AUTO_READ_SPEED_MIN <= 100)
        assertTrue("旧上限 900 不得高于新上限", ReaderPreferences.AUTO_READ_SPEED_MAX >= 900)
    }

    @Test
    fun `钳制下界 0与49收敛到min_min透传`() {
        assertEquals(ReaderPreferences.AUTO_READ_SPEED_MIN, ReaderPreferences.coerceAutoReadSpeed(0))
        assertEquals(ReaderPreferences.AUTO_READ_SPEED_MIN, ReaderPreferences.coerceAutoReadSpeed(49))
        assertEquals(ReaderPreferences.AUTO_READ_SPEED_MIN, ReaderPreferences.coerceAutoReadSpeed(ReaderPreferences.AUTO_READ_SPEED_MIN))
    }

    @Test
    fun `钳制上界 9999收敛到max_max透传`() {
        assertEquals(ReaderPreferences.AUTO_READ_SPEED_MAX, ReaderPreferences.coerceAutoReadSpeed(9999))
        assertEquals(ReaderPreferences.AUTO_READ_SPEED_MAX, ReaderPreferences.coerceAutoReadSpeed(ReaderPreferences.AUTO_READ_SPEED_MAX))
    }

    @Test
    fun `界内透传 300与1500原样返回`() {
        assertEquals(300, ReaderPreferences.coerceAutoReadSpeed(300))
        assertEquals(1500, ReaderPreferences.coerceAutoReadSpeed(1500))
    }

    @Test
    fun `默认值接线 defaultPreferences速度等于常量`() {
        assertEquals(
            ReaderPreferences.AUTO_READ_SPEED_DEFAULT,
            ReaderPreferencesUtil.defaultPreferences.autoReadSpeed
        )
    }
}
