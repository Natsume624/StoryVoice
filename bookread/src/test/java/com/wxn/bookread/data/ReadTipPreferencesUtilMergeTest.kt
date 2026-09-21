package com.wxn.bookread.data

import com.wxn.bookread.data.source.local.ReadTipPreferencesUtil
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 红线契约：updateInfoBarConfig/resetInfoBarConfig 只允许影响 8 个信息条相关键，
 * 同 DataStore 文件中的活跃字段（clickTurnPage/clickAllNext/textFullJustify/textBottomJustify，
 * PageView init 实时读取）必须原样保留。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReadTipPreferencesUtilMergeTest {

    private lateinit var util: ReadTipPreferencesUtil

    @Before
    fun setUp() {
        util = ReadTipPreferencesUtil(RuntimeEnvironment.getApplication())
    }

    @Test
    fun `updateInfoBarConfig only writes the 8 info-bar keys`() = runBlocking {
        // 先制造非默认活跃字段状态
        util.updatePreferences(
            ReadTipPreferencesUtil.defaultReadTipPreference.copy(clickTurnPage = false, textFullJustify = false)
        )

        util.updateInfoBarConfig(
            hideHeader = false, hideFooter = false,
            tipHeaderLeft = 2, tipHeaderMiddle = 1, tipHeaderRight = 3,
            tipFooterLeft = 5, tipFooterMiddle = 0, tipFooterRight = 6,
        )

        val after = util.readTIpPreferencesFlow.firstOrNull()!!
        assertFalse(after.hideHeader)
        assertFalse(after.hideFooter)
        assertEquals(1, after.tipHeaderMiddle)
        assertEquals(5, after.tipFooterLeft)
        // 活跃字段未被重置（红线）
        assertFalse(after.clickTurnPage)
        assertFalse(after.textFullJustify)
    }

    @Test
    fun `resetInfoBarConfig resets only info-bar keys`() = runBlocking {
        util.updatePreferences(
            ReadTipPreferencesUtil.defaultReadTipPreference.copy(hideFooter = false, clickTurnPage = false)
        )

        util.resetInfoBarConfig()

        val after = util.readTIpPreferencesFlow.firstOrNull()!!
        assertTrue(after.hideHeader)
        assertTrue(after.hideFooter)
        assertEquals(2, after.tipHeaderLeft)
        assertEquals(6, after.tipFooterRight)
        assertFalse(after.clickTurnPage) // 活跃字段保持用户值
    }
}
