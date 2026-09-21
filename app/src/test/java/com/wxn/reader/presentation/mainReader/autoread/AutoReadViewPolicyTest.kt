package com.wxn.reader.presentation.mainReader.autoread

import com.wxn.base.bean.TtsPlaybackStatus
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 自动阅读呈现方式仲裁纯函数用例（docs/plans/2026-09-08-plan-auto-read-mode-arbitration.md v2 §5.1）：
 * 入口派生（deriveAutoReadMode，R-A/R-B/R-C）+ 会话内切换目标视图（targetScrollForAutoMode）
 * + TTS 互斥门控（isEntryEnabled，docs/plans/2026-09-11-plan-auto-read-tts-entry-gating.md §4）。
 *
 * 纯 JUnit：[AutoReadViewPolicy] 为纯函数对象，无 Android 依赖（沿 FabDockPolicyTest 先例）。
 */
class AutoReadViewPolicyTest {

    // ===== deriveAutoReadMode：入口派生 =====

    @Test
    fun `派生_非双列手动0到5派生覆盖翻页`() {
        // R-A：无动画/水平覆盖/水平滑动/模拟/垂直覆盖/垂直滑动 → 覆盖翻页
        for (scroll in 0..5) {
            assertEquals("scroll=$scroll 应派生覆盖翻页", 1, AutoReadViewPolicy.deriveAutoReadMode(scroll, isDualColumn = false))
        }
    }

    @Test
    fun `派生_非双列手动6派生自动滚动`() {
        // R-C：连续滚动 → 自动滚动
        assertEquals(0, AutoReadViewPolicy.deriveAutoReadMode(6, isDualColumn = false))
    }

    @Test
    fun `派生_双列恒派生覆盖翻页`() {
        // R-B：双列开启恒覆盖翻页。互斥不变量下双列不可能 scroll=6，
        // 此处验证防御分支：即便不变量被破坏（scroll=6+双列）也强制覆盖翻页
        assertEquals(1, AutoReadViewPolicy.deriveAutoReadMode(6, isDualColumn = true))
        assertEquals(1, AutoReadViewPolicy.deriveAutoReadMode(2, isDualColumn = true))
    }

    // ===== targetScrollForAutoMode：会话内切换目标视图 =====

    @Test
    fun `会话内目标_自动滚动恒切连续滚动6`() {
        assertEquals(6, AutoReadViewPolicy.targetScrollForAutoMode(0, 0))
        assertEquals(6, AutoReadViewPolicy.targetScrollForAutoMode(0, 3))
        assertEquals(6, AutoReadViewPolicy.targetScrollForAutoMode(0, 6))
    }

    @Test
    fun `会话内目标_覆盖翻页非6透传`() {
        // 覆盖翻页保持当前非 6 视图不动（入口派生后零切换的前提）
        assertEquals(3, AutoReadViewPolicy.targetScrollForAutoMode(1, 3))
    }

    @Test
    fun `会话内目标_覆盖翻页从6切无动画0`() {
        // 手动 6 进入（自动滚动呈现）后会话内切覆盖翻页 → 底层滚动视图切无动画
        assertEquals(0, AutoReadViewPolicy.targetScrollForAutoMode(1, 6))
    }

    @Test
    fun `幂等_同模式重复选择目标等于当前值`() {
        // 调用方按"目标 != 当前则切"判等跳过：重复点同一模式不应产生视图抖动
        assertEquals(6, AutoReadViewPolicy.targetScrollForAutoMode(0, 6))
        assertEquals(3, AutoReadViewPolicy.targetScrollForAutoMode(1, 3))
    }

    // ===== isEntryEnabled：TTS 互斥门控（2026-09-11-plan-auto-read-tts-entry-gating §4）=====

    @Test
    fun `门控_TTS未启动IDLE入口可用`() {
        assertEquals(
            true,
            AutoReadViewPolicy.isEntryEnabled(autoReadActive = false, ttsStatus = TtsPlaybackStatus.IDLE)
        )
    }

    @Test
    fun `门控_TTS非IDLE四态入口全部置灰`() {
        // 未完全停止（启动过渡/播放/暂停过渡/已暂停）均视为"TTS 已启动"，禁止开启自动阅读：
        // 防止开启自动阅读按"后启生效"停掉 TTS 的回归缺陷
        val nonIdleStates = listOf(
            TtsPlaybackStatus.PENDING_PLAYING,
            TtsPlaybackStatus.PLAYING,
            TtsPlaybackStatus.PENDING_PAUSE,
            TtsPlaybackStatus.PAUSED
        )
        for (status in nonIdleStates) {
            assertEquals(
                "tts=$status 应置灰",
                false,
                AutoReadViewPolicy.isEntryEnabled(autoReadActive = false, ttsStatus = status)
            )
        }
    }

    @Test
    fun `门控_自动阅读运行中任意TTS状态恒可用防锁死`() {
        // 媒体键拉起 TTS 的过渡态可与运行中的自动阅读短暂共存：
        // 置灰只应阻止"开启"，不应阻止"关闭"
        for (status in TtsPlaybackStatus.values()) {
            assertEquals(
                "tts=$status 应可用",
                true,
                AutoReadViewPolicy.isEntryEnabled(autoReadActive = true, ttsStatus = status)
            )
        }
    }
}
