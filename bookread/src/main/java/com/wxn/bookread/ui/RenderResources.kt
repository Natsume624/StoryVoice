package com.wxn.bookread.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Build
import android.text.TextPaint
import androidx.core.graphics.toColorInt
import com.wxn.base.bean.CssFontStyle
import com.wxn.base.bean.CssFontWeight
import com.wxn.base.bean.TextCssInfo
import com.wxn.base.ext.DpExt
import com.wxn.base.ext.getCompatColor
import com.wxn.base.ext.isReadableOn
import com.wxn.base.ext.toColor
import com.wxn.base.unit.toFontScale
import com.wxn.bookread.R
import com.wxn.bookread.data.model.TextChar
import com.wxn.bookread.data.model.preference.BASE_FONT_SIZE
import com.wxn.bookread.data.model.preference.BookColorMode
import com.wxn.bookread.ext.BitmapExt
import com.wxn.bookread.provider.ChapterProvider
import com.wxn.bookread.provider.ShapedRunBuffer

/**
 * 阅读器渲染资源单例。
 *
 * 在 `MainActivity.onCreate()` 中紧跟 `ChapterProvider.init()` 之后调用 [init]。
 * init() 之前访问属性不会崩溃（有 fallback 默认值），但颜色/尺寸可能不准确。
 * 不持有任何 Context 引用，无内存泄漏风险。
 */
object RenderResources {

    private var initialized = false

    // ==================== 颜色常量 ====================

    /** 笔记默认颜色（十六进制字符串） */
    const val NOTE_DEFAULT_COLOR_HEX = "#FFFF00"

    /** 笔记背景半透明 alpha 值（≈ 0.4f * 255 = 102 = 0x66） */
    const val NOTE_BG_ALPHA = 0x66

    // ==================== 尺寸（init 中精确解析，fallback 为近似 dp 值） ====================

    var dp4: Float = 4f; private set
    var dp6: Float = 6f; private set
    var dp12: Float = 12f; private set
    var dp21: Float = 21f; private set
    var handleRadiusPx: Float = 10f; private set
    var handleLineHeightPx: Float = 24f; private set

    // ==================== 笔记图标 Bitmap（init 中加载） ====================

    var noteIconBmp: Bitmap? = null; private set

    // ==================== 画笔 — context 依赖颜色（init 中设置） ====================

    val highlightPaint = Paint().apply { style = Paint.Style.FILL }

    val linePaint = Paint().apply { style = Paint.Style.FILL }

    val selectedPaint = Paint().apply { style = Paint.Style.FILL }

    // ==================== 画笔 — 固定颜色（不需要 context） ====================

    val bookmarkPaint = Paint().apply {
        style = Paint.Style.FILL
        color = AccentPalette.LIGHT.bookmark
    }

    val noteBgPaint = Paint().apply { style = Paint.Style.FILL }

    val noteCirclePaint = Paint().apply { style = Paint.Style.FILL }

    val readAloudBgPaint = Paint().apply { style = Paint.Style.FILL }

    val searchHighlightPaint = Paint().apply {
        color = AccentPalette.LIGHT.searchHighlight
        style = Paint.Style.FILL
    }

    val imagePlaceholderPaint = Paint().apply {
        style = Paint.Style.FILL
        color = AccentPalette.LIGHT.imagePlaceholder
    }

    val drawingPaint = TextPaint().apply { isAntiAlias = true }

    // ==================== AS-1 作者样式三态（智能对比判定基准） ====================

    /** 当前阅读背景色（ARGB）。分页模式由 PageView.upBg 同步（图背景用 foldColor 主色近似）；
     *  连续滚动模式由 ContinuousScrollReaderView SideEffect 同步。智能对比模式的判定基准。 */
    var pageBgColor: Int = Color.WHITE

    /** 「书籍字体颜色」三态模式（[BookColorMode]）。由 ChapterProvider.applyStyleInternal 同步
     *  （prefs → 单例，isLayoutChange → updatePageViews → upStyle 链保证绘制前已同步），applyCharPaint 绘制期读取。 */
    var bookColorMode: BookColorMode = BookColorMode.SMART

    /**
     * 作者颜色统一应用点（段级 fontColor 与 span 级 inlineColor 同规则，AS-1 §3.6.1/§3.6.3）：
     * - [BookColorMode.THEME]：不覆盖——保持进入时画笔色（用户主题文字色），即"用户优先"旧语义；
     * - [BookColorMode.BOOK]  ：无条件覆盖（跳过对比判定与低 alpha 守卫，逃生门）；
     * - [BookColorMode.SMART] ：对比度判定（含低 alpha 守卫），可读才覆盖；不可读则继承当前画笔色
     *                           （用户色，或已保留的段级作者色——与 CSS 继承一致）。
     * colorStr 空/null → 直接返回（无作者声明）。
     */
    fun applyAuthorColor(colorStr: String?) {
        if (colorStr.isNullOrEmpty()) return
        when (bookColorMode) {
            BookColorMode.THEME -> Unit
            BookColorMode.BOOK -> colorStr.toColor()?.let { drawingPaint.color = it }
            BookColorMode.SMART -> colorStr.toColor()
                ?.takeIf { it.isReadableOn(pageBgColor) }
                ?.let { drawingPaint.color = it }
        }
    }

    // ==================== AD-1 背景自适应默认色（AccentPalette 消费端） ====================

    /** 最近一次背景解析产物。绘制处 fallback（ContentTextView / ContinuousScrollReaderView）
     *  只读本值；画笔烧入仅发生在 [resolveAdaptiveColors]。 */
    var resolved: AccentPalette.Resolved = AccentPalette.LIGHT
        private set

    /** 记忆化键。null 哨兵保证首次调用必解析（若初值取 Color.WHITE 会与默认 pageBgColor
     *  相等而短路，aPaint 停留在 TextPaint 默认黑——r4-R1） */
    private var resolvedBg: Int? = null

    /**
     * 背景自适应统一解析入口（AD-1 §3.3）：按 [pageBgColor] 重算并烧入全部自适应画笔。
     * [onPageBgChanged]（背景变化）与 ChapterProvider.applyStyleInternal（样式刷新兜底）调用；
     * bg 未变则短路（零开销）。调用方均在主线程（upBg 主 Scope launch / SideEffect / upStyle）。
     */
    fun resolveAdaptiveColors() {
        val bg = pageBgColor
        if (resolvedBg == bg) return
        resolvedBg = bg
        resolved = AccentPalette.resolve(bg)
        searchHighlightPaint.color = resolved.searchHighlight
        handlePaint.color = resolved.selectionHandle
        handleStrokePaint.color = resolved.selectionHandle
        imagePlaceholderPaint.color = resolved.imagePlaceholder
        bookmarkPaint.color = resolved.bookmark
        underlinePaint.color = resolved.underlineFallback
        // aPaint 颜色唯一写入点（r1-F3）：文字与下划线同色
        ChapterProvider.aPaint.color = resolved.link
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
            ChapterProvider.aPaint.underlineColor = resolved.link
        }
    }

    /** 背景变化统一入口：原 pageBgColor 直写点（PageView.upBg、
     *  ContinuousScrollReaderView SideEffect）改调此处 */
    fun onPageBgChanged(bg: Int) {
        pageBgColor = bg
        resolveAdaptiveColors()
    }

    val listDotPaint = Paint().apply {
        color = "#FF333333".toColorInt()
        isAntiAlias = true
    }

    val listDotStrokePaint = Paint().apply {
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    val listMarkerPaint = TextPaint().apply {
        isAntiAlias = true
        textAlign = Paint.Align.LEFT   // drawX 由渲染器自算，固定 LEFT 防外部污染
    }

    val underlinePaint = Paint().apply {
        color = AccentPalette.LIGHT.underlineFallback
        style = Paint.Style.FILL
    }

    // ==================== 选区手柄画笔 ====================

    val handlePaint = Paint().apply {
        color = AccentPalette.LIGHT.selectionHandle
        style = Paint.Style.FILL
        strokeWidth = 8f
    }

    val handleStrokePaint = Paint().apply {
        color = AccentPalette.LIGHT.selectionHandle
        style = Paint.Style.STROKE
        strokeWidth = 3f
        isAntiAlias = true
    }

    // ==================== 几何暂存对象 ====================

    val bookmarkPath = Path()
    val noteIconRect = RectF()
    val noteBgRect = RectF()
    val readAloudBgRect = RectF()

    // ==================== 初始化 ====================
    val shapedRunBuffer = ShapedRunBuffer()

    // ==================== 初始化 ====================

    fun init(context: Context) {
        if (initialized) return
        val ctx = context.applicationContext

        highlightPaint.color = ctx.getCompatColor(R.color.highlight)
        linePaint.color = ctx.getCompatColor(R.color.divider)
        selectedPaint.color = ctx.getCompatColor(R.color.btn_bg_press_2)

        noteIconBmp = BitmapExt.bitmapFromResource(ctx, R.drawable.ic_note)

        dp4 = DpExt.dp2px(ctx, 4f)
        dp6 = DpExt.dp2px(ctx, 6f)
        dp12 = DpExt.dp2px(ctx, 12f)
        dp21 = DpExt.dp2px(ctx, 21f)
        handleRadiusPx = DpExt.dp2px(ctx, 10f)
        handleLineHeightPx = DpExt.dp2px(ctx, 24f)

        initialized = true
    }

    /***
     * apply one single character paint to drawingPaint
     * @param ch
     * @param isTitle    current Character is Title
     * @param isBold
     * @param isSmall    current Character font is small size
     * @param textCssInfo  paragraph css info
     * @param inlineScale current Character font size scale
     */
    fun applyCharPaint(
        ch: TextChar,
        isTitle: Boolean,
        isBold: Boolean,
        isSmall: Boolean,
        textCssInfo: TextCssInfo?,
        inlineScale: Float,
        inlineColor : String? = null
    ) {
        // ① 段级作者样式（AS-1 §3.1/§3.6.3，display:block 门控退役）：字号归一化（倍率作用于
        //    用户基准，em/rem 直乘、%/100、px 锚定 BASE_FONT_SIZE）+ 颜色按三态模式应用
        if (!isTitle && textCssInfo != null) {
            textCssInfo.fontSize.toFontScale(BASE_FONT_SIZE)?.let { scale ->
                drawingPaint.textSize *= scale
            }
            applyAuthorColor(textCssInfo.fontColor)
        }

        // ② inline scale
        if (!isTitle && !ch.isImage && inlineScale != 1f) {
            drawingPaint.textSize *= inlineScale
        }

        // ③ inline color：与段级同一三态规则（THEME 不覆盖=继承①之后色；SMART 可读才覆盖；
        //    BOOK 无条件覆盖）。表格单元格内不进入本入口（ContentTextView isTableCell 排除）。
        if (!isTitle && !ch.isImage) {
            applyAuthorColor(inlineColor)
        }

        // ④ fontWeight / fontStyle
        val effectiveFontWeight = when {
            isBold -> CssFontWeight.FontWeightBold
            textCssInfo != null -> textCssInfo.fontWeight
            else -> CssFontWeight.FontWeightNormal
        }
        val effectiveFontStyle = textCssInfo?.fontStyle ?: CssFontStyle.CssFontStyleNormal

        drawingPaint.typeface = ChapterProvider.getTypeface(effectiveFontWeight, effectiveFontStyle)

        // ④ hasGlyph fallback(字形缺失时换 fallback 字体;hasGlyph 不依赖 textSize,时序无关)
        if (ch.charData.isNotEmpty() && !drawingPaint.hasGlyph(ch.charData)) {
            drawingPaint.typeface = ChapterProvider.fallbackTypeface
        }
        // ⑤ <small> 缩小
        if (isSmall) {
            drawingPaint.textSize *= 0.8f
        }
    }
}
