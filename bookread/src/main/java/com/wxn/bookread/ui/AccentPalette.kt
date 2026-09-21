package com.wxn.bookread.ui

import com.wxn.base.ext.relativeLuminance

/**
 * 应用默认强调色板（AD-1）：A 类"应用侧默认色"按阅读背景亮度自适应的唯一事实源。
 *
 * 背景：a 标签链接、书签指示、选区手柄、搜索/朗读高亮、图片占位、标注 fallback 等颜色
 * 此前写死（BLUE/GREEN/YELLOW/灰系），深色主题下不可读或刺眼。本色板按 [isDarkBackground]
 * 提供深浅两档：浅色档 = 2026-09 前线上观感（零回归），深色档为 AD-1 新增。
 *
 * 纯 Int→Int 计算，不依赖 android.graphics——可纯 JVM 单测（消费方 RenderResources 负责把
 * 解析结果烧入画笔，绘制处 fallback 只读 [RenderResources.resolved]）。
 *
 * 边界：本对象只覆盖"书籍未声明颜色时"的应用默认值（L2）；书内作者色由
 * [RenderResources.applyAuthorColor] 三态门控处理（L1，AS-1），两层互不感知。
 * 半透明 wash（searchHighlight/readAloudBg/highlightFallback）不做对比度门控——alpha 由
 * 绘制处既有逻辑控制，可读性以人工验收 ⑥⑦ 目视为准（AD-1 §6.6）。
 */
object AccentPalette {

    /** 判暗阈值：背景相对亮度 < 0.25 视为深色背景（[AccentPaletteTest] 钉边界） */
    const val LUMINANCE_DARK_THRESHOLD = 0.25f

    /** 浅色背景档（= 线上现值） */
    val LIGHT: Resolved = Resolved(
        link = 0xFF0B57D0.toInt(),
        searchHighlight = 0x4000BFFF,
        readAloudBg = 0xFFFFFF00.toInt(),
        selectionHandle = 0xFF1A73E8.toInt(),
        imagePlaceholder = 0xFFCCCCCC.toInt(),   // = Color.LTGRAY
        bookmark = 0xFF575757.toInt(),
        underlineFallback = 0xFF575757.toInt(),
        highlightFallback = 0xFFFFFF00.toInt(),
        noteFallback = 0xFFFFFF00.toInt(),       // = NOTE_DEFAULT_COLOR_HEX("#FFFF00") 解析值
    )

    /** 深色背景档（AD-1 新增） */
    val DARK: Resolved = Resolved(
        link = 0xFF8AB4F8.toInt(),
        searchHighlight = 0x668AB4F8.toInt(),
        readAloudBg = 0xFFFFC107.toInt(),        // amber 500
        selectionHandle = 0xFF90CAF9.toInt(),
        imagePlaceholder = 0xFF424242.toInt(),
        bookmark = 0xFFBDBDBD.toInt(),
        underlineFallback = 0xFFBDBDBD.toInt(),
        highlightFallback = 0x80FFC107.toInt(),  // 半透明 amber
        noteFallback = 0xFFFFFF00.toInt(),       // 深浅同值：0x66 半透明已有缓冲
    )

    fun isDarkBackground(bg: Int): Boolean = bg.relativeLuminance() < LUMINANCE_DARK_THRESHOLD

    /** 按背景选择色档（唯一入口） */
    fun resolve(bg: Int): Resolved = if (isDarkBackground(bg)) DARK else LIGHT

    /**
     * 一次背景解析的产物，字段与消费画笔一一对应。
     *
     * @param link a 标签文字与下划线（ChapterProvider.aPaint.color/underlineColor 唯一来源）
     * @param searchHighlight 搜索结果高亮背景（searchHighlightPaint）
     * @param readAloudBg 朗读行高亮背景（readAloudBgPaint，绘制处赋值）
     * @param selectionHandle 选区手柄填充与描边（handlePaint/handleStrokePaint）
     * @param imagePlaceholder 图片占位块（imagePlaceholderPaint）
     * @param bookmark 书签页边指示（bookmarkPaint）
     * @param underlineFallback 下划线标注色解析失败 fallback（linePaint 绘制处 + underlinePaint 初值）
     * @param highlightFallback 高亮标注色解析失败 fallback（highlightPaint 绘制处）
     * @param noteFallback 笔记色解析失败 fallback（noteBg/noteCirclePaint 绘制处；深浅同值）
     */
    data class Resolved(
        val link: Int,
        val searchHighlight: Int,
        val readAloudBg: Int,
        val selectionHandle: Int,
        val imagePlaceholder: Int,
        val bookmark: Int,
        val underlineFallback: Int,
        val highlightFallback: Int,
        val noteFallback: Int,
    )
}
