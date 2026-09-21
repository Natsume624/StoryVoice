package com.wxn.bookread.data.model

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * 信息条槽位配置（顶/底各一组左中右槽位）。
 * 取值为内容码，与 ReadTipPreferencesUtil 既有常量同值：0 无 / 1 章节名 / 2 时间 /
 * 3 电量 / 4 本章页码 / 5 总进度 / 6 页码+进度 / 7 书名。
 */
data class InfoBarSlots(val left: Int, val middle: Int, val right: Int) {
    fun hasContent(): Boolean = left != InfoBarSpec.SLOT_NONE ||
        middle != InfoBarSpec.SLOT_NONE || right != InfoBarSpec.SLOT_NONE

    /** 是否包含指定内容码（宿主用于判断是否启动时间 tick / 电量广播） */
    fun hasCode(code: Int): Boolean = left == code || middle == code || right == code
}

/**
 * 阅读信息条纯逻辑规格：槽位解析、滚动模式降级、排版预留、格式化。
 * 不依赖 Android 框架类型（时间格式化仅用 java.text），保证 JVM 单测可直接覆盖。
 */
object InfoBarSpec {
    const val SLOT_NONE = 0
    const val SLOT_CHAPTER_TITLE = 1
    const val SLOT_TIME = 2
    const val SLOT_BATTERY = 3
    const val SLOT_PAGE = 4
    const val SLOT_TOTAL_PROGRESS = 5
    const val SLOT_PAGE_AND_TOTAL = 6
    const val SLOT_BOOK_NAME = 7

    /** 条高与字号（门禁决议：12sp / 约 24dp，不开放配置） */
    const val BAR_HEIGHT_DP = 24
    const val BAR_TEXT_SP = 12
    const val BAR_INNER_PADDING_DP = 4
    /** 预留时信息条与正文之间的呼吸间距 */
    const val RESERVE_GAP_DP = 8

    /** 电量槽电池图形总尺寸与内部文字字号（绘制细节常量在 ReaderInfoBar.kt） */
    const val BATTERY_TOTAL_WIDTH_DP = 27
    const val BATTERY_HEIGHT_DP = 14
    const val BATTERY_TEXT_SP = 8

    /** 任一槽位有内容才渲染（空配置等价关闭） */
    fun isEnabled(hide: Boolean, slots: InfoBarSlots): Boolean = !hide && slots.hasContent()

    /**
     * 计算排版预留（px）。语义为"对称抬高的最小 paddingVertical 下限"：
     * - 顶部条利用状态栏占位区，仅超出部分需要预留；
     * - 底部条需 barPx + RESERVE_GAP_DP；
     * - 取已启用条所需值的最大值（paddingVertical 对称，见实施计划 §1.2）；
     * - 滚动模式恒为 0（paddingVertical 强制 0，信息条为 scrim 悬浮）。
     */
    fun computeReservePx(
        density: Float,
        statusBarHeightPx: Int,
        headerEnabled: Boolean,
        footerEnabled: Boolean,
        isScrollMode: Boolean,
    ): Int {
        if (isScrollMode || (!headerEnabled && !footerEnabled)) return 0
        val barPx = (BAR_HEIGHT_DP * density).toInt()
        val gapPx = (RESERVE_GAP_DP * density).toInt()
        val topNeed = if (headerEnabled) (barPx - statusBarHeightPx).coerceAtLeast(0) else 0
        val bottomNeed = if (footerEnabled) barPx + gapPx else 0
        return maxOf(topNeed, bottomNeed)
    }

    /** 页码槽文本："12/345"（index 为 0 基，展示时 +1） */
    fun formatPage(pageIndex0Based: Int, pageSize: Int): String =
        "${pageIndex0Based + 1}/$pageSize"

    /** 总进度文本："45.28%"（越界收敛；显式 Locale.US——进度与页码统一西文数字，避免 ar/de 等 locale 的本地化数字/逗号小数点与页码混排，并保证单测确定性） */
    fun formatProgress(progression: Double): String =
        String.format(Locale.US, "%.2f%%", progression.coerceIn(0.0, 1.0) * 100)

    /** 页码+进度组合："12/345 · 45.28%" */
    fun formatPageAndTotal(pageIndex0Based: Int, pageSize: Int, progression: Double): String =
        "${formatPage(pageIndex0Based, pageSize)} · ${formatProgress(progression)}"

    /** 时间文本，跟随系统 12/24 小时制（is24Hour 由调用方读取，便于纯函数测试） */
    fun formatTime(is24Hour: Boolean, calendar: Calendar, locale: Locale): String {
        val pattern = if (is24Hour) "HH:mm" else "h:mm"
        val sdf = SimpleDateFormat(pattern, locale)
        return sdf.format(calendar.time)
    }

    /** 电量百分比（0..100），输入非法时返回 null（调用方隐藏该槽位） */
    fun parseBattery(level: Int, scale: Int): Int? {
        if (scale <= 0 || level < 0) return null
        return (level * 100 / scale).coerceIn(0, 100)
    }

    /** 电量填充比例（0f..1f），入参越界收敛，供电池图形 Canvas 填充宽度与单测使用 */
    fun batteryFillFraction(percent: Int): Float = percent.coerceIn(0, 100) / 100f
}
