package com.wxn.reader.presentation.mainReader.autoread

import com.wxn.base.bean.TtsPlaybackStatus

/**
 * 自动阅读呈现方式仲裁（docs/plans/2026-09-08-plan-auto-read-mode-arbitration.md v2 §3.1）。
 *
 * v2 裁决：自动阅读模式是派生值，不是存储值——每次进入由（手动翻页模式 + 双列开关）现算，
 * 不落盘（沿 FabDockPolicy 公开纯函数先例，便于单测）。
 */
object AutoReadViewPolicy {
    const val SCROLL_NO_ANIM = 0          //无动画翻页视图
    const val SCROLL_CONTINUOUS = 6       //连续滚动视图

    /**
     * 自动阅读呈现方式派生（R-A/R-B/R-C）：唯一依据=手动翻页模式+双列开关，不落盘。
     * 连续滚动→自动滚动；其余→覆盖翻页。双列为防御性输入：互斥不变量下双列恒非 6，
     * 若不变量未来被破坏，双列也强制覆盖翻页（连续滚动视图双列非法）。
     */
    fun deriveAutoReadMode(manualScrollType: Int, isDualColumn: Boolean): Int =
        if (!isDualColumn && manualScrollType == SCROLL_CONTINUOUS) 0 else 1

    /** 会话内切换目标视图：自动滚动→恒 6；覆盖翻页→保持非 6 视图（当前若为 6 则切无动画 0） */
    fun targetScrollForAutoMode(autoMode: Int, currentScroll: Int): Int =
        if (autoMode == 0) SCROLL_CONTINUOUS
        else if (currentScroll == SCROLL_CONTINUOUS) SCROLL_NO_ANIM
        else currentScroll

    /**
     * TTS 互斥门控（docs/plans/2026-09-11-plan-auto-read-tts-entry-gating.md §3.1）：
     * TTS 会话未完全停止（非 IDLE，含启动/暂停过渡态）时置灰开启入口——防止开启自动阅读
     * 按"后启生效"停掉 TTS 的回归缺陷；自动阅读运行中恒可用，置灰只应阻止"开启"、
     * 不应阻止"关闭"（媒体键拉起 TTS 的过渡态可与运行中的自动阅读短暂共存）。
     */
    fun isEntryEnabled(autoReadActive: Boolean, ttsStatus: TtsPlaybackStatus): Boolean =
        autoReadActive || ttsStatus == TtsPlaybackStatus.IDLE
}
