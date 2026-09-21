package com.wxn.base.unit

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable

@Immutable
class CssUnit(
    val value: Float,
    val type: UnitType = UnitType.Undifined
) {

    fun isEm() = (type == UnitType.Em || type == UnitType.Rem)
    fun isPx() = (type == UnitType.Px)
    fun isPercent() = (type == UnitType.Percent)
    fun isAuto() = (type == UnitType.Auto)

    override fun toString(): String {
        return if (type == UnitType.Undifined) "undefined" else if (isAuto()) "auto" else "$value$type"
    }

    enum class UnitType {
        Undifined,
        Auto,
        Em,
        Rem,
        Px,
        Percent;

        companion object {
            fun format(cell: String): UnitType =
                when (cell) {
                    "em" -> Em
                    "rem" -> Rem
                    "px" -> Px
                    "%" -> Percent
                    else -> Undifined
                }
        }

        override fun toString(): String {
            return when (this) {
                Em -> "em"
                Rem -> "rem"
                Px -> "px"
                Percent -> "%"
                else -> "undifined"
            }
        }
    }

    companion object {
        /**
         * 段级/span 级作者字号倍率 clamp 范围（防损坏 EPUB：0.5x ~ 5.0x）。
         * 原 ReaderText.MIN/MAX_INLINE_SCALE 提为公开共用（值不变）。
         */
        const val MIN_FONT_SCALE = 0.5f
        const val MAX_FONT_SCALE = 5.0f

        /**
         * A dimension used to represent a hairline drawing element. Hairline elements take up no
         * space, but will draw a single pixel, independent of the device's resolution and density.
         */
        @Stable
        val Hairline = CssUnit(0f)

        /**
         * Infinite dp dimension.
         */
        @Stable
        val Auto = CssUnit(0f, UnitType.Auto)

        /**
         * Constant that means unspecified Dp
         */
        @Stable
        val Unspecified = CssUnit(Float.NaN)

        fun format(value: String): CssUnit =
            if (value == "auto") {
                Auto
            } else {
                var ret = Unspecified
                for (unit in arrayOf("em", "rem", "px", "pt", "%")) {
                    if (value.endsWith(unit)) {
                        val size = value.substring(0, value.length - unit.length).toFloatOrNull()
                        ret = if (size == null) {
                            Unspecified
                        } else {
                            CssUnit(
                                if (unit == "pt") {
                                    (size * 1.333f).coerceIn(48f, 84f)
                                } else if (unit == "px") {
                                    size.coerceIn(48f, 84f)
                                } else {
                                    size
                                }, when (unit) {
                                    "em" -> UnitType.Em
                                    "rem" -> UnitType.Rem
                                    "px" -> UnitType.Px
                                    "pt" -> UnitType.Px
                                    "%" -> UnitType.Percent
                                    else -> UnitType.Undifined
                                }
                            )
                        }
                        break
                    }
                }

                if (ret == Unspecified) {
                    value.toIntOrNull()?.let { size ->  //size="1" 对应的是 12px（默认字体大小）
                        if (size in 1..10) {
                            ret = Px(size.coerceIn(3, 7) * 12f)
                        }
                    }
                }

                ret
            }

        /***
         *   //相对于父控件的字体比例值
         */
        fun Em(value: Float) = CssUnit(value, UnitType.Em)

        /**
         * //相对于顶级容器控件的字体比例值
         */
        fun Rem(value: Float) = CssUnit(value, UnitType.Rem)

        /**
         *  //px像素
         */
        fun Px(value: Float) = CssUnit(value, UnitType.Px)

        /***
         * //百分比
         */
        fun Percent(value: Float) = CssUnit(value, UnitType.Percent)
    }
}

/**
 * AS-1 §3.1：作者字号 → 作用于用户基准的倍率（单一真相源，渲染侧/排版侧共用）。
 * - em/rem：值即倍率；percent：值/100；px：值/basePx（锚定应用默认正文基准，非 CSS 16px——
 *   [CssUnit.format] 解析期已把 px/pt clamp 到 [48,84]，px/16 会把 Calibre 12pt 书放大 3 倍）；
 * - Undifined/Auto → null（不缩放）；结果 clamp 到 [MIN_FONT_SCALE, MAX_FONT_SCALE]。
 */
fun CssUnit.toFontScale(basePx: Float): Float? = when {
    isEm() -> value
    isPercent() -> value / 100f
    isPx() -> value / basePx
    else -> null
}?.coerceIn(CssUnit.MIN_FONT_SCALE, CssUnit.MAX_FONT_SCALE)