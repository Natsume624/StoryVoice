package com.wxn.reader.presentation.mainReader.autoread

/**
 * 自动阅读 FAB 位置/吸附 UI 状态（真机验收第二轮 P2，方案 §3.2，审查 R6/R7）。
 *
 * 存放于 MainReadViewModel（审查 R6）：会话级生命周期——活过旋转/分屏/切后台，
 * 随阅读返回栈条目销毁（onCleared），进程死亡不恢复，与既定"不跨进程记忆"决策一致。
 *
 * R7 派生式吸附：只存自由态偏移与吸附侧两样；吸附态 x 不存储，渲染时由 dockSide + 当前父宽
 * 派生，旋转/分屏后不残留越界坐标快照；自由态偏移亦在渲染期统一 clamp（本类不感知父尺寸）。
 *
 * @param offsetX  自由态 FAB 左上角 x（父容器 px；[Float.NaN]=未初始化 → 渲染落到默认物理右下角 24dp）
 * @param offsetY  自由态 FAB 左上角 y（父容器 px；[Float.NaN]=未初始化 → 同上）
 * @param dockSide 吸附侧：[FabDockPolicy.SIDE_NONE] / [FabDockPolicy.SIDE_LEFT] / [FabDockPolicy.SIDE_RIGHT]
 */
data class FabDockUiState(
    val offsetX: Float = Float.NaN,
    val offsetY: Float = Float.NaN,
    val dockSide: Int = FabDockPolicy.SIDE_NONE,
)

/**
 * FAB 位置/吸附纯函数集（可单测）：释放吸附判定、点按脱附、自由态坐标解析。
 */
object FabDockPolicy {
    const val SIDE_NONE = 0
    const val SIDE_LEFT = -1
    const val SIDE_RIGHT = 1

    /**
     * 释放时按中心 x 判定吸附：距左缘 < [thresholdPx] 吸左，距右缘 < [thresholdPx] 吸右，否则自由。
     * 父宽非法（<=0）一律自由；中心恰好落在阈值上视为自由。
     */
    fun decideDock(centerXpx: Float, parentWidthPx: Float, thresholdPx: Float): Int {
        if (parentWidthPx <= 0f) return SIDE_NONE
        if (centerXpx < thresholdPx) return SIDE_LEFT
        if (centerXpx > parentWidthPx - thresholdPx) return SIDE_RIGHT
        return SIDE_NONE
    }

    /**
     * 点按脱附的目标 x（真机验收第三轮 P6）：从吸附缘向屏幕内侧收进 [inwardMarginPx]。
     * 仅 docked 态调用；非吸附侧入参走防御分支（返回 inwardMargin，审查 S2）。
     */
    fun undockInwardX(dockSide: Int, parentWidthPx: Float, freeSizePx: Float, inwardMarginPx: Float): Float =
        when (dockSide) {
            SIDE_RIGHT -> (parentWidthPx - freeSizePx - inwardMarginPx).coerceAtLeast(0f)
            else -> inwardMarginPx.coerceAtMost((parentWidthPx - freeSizePx).coerceAtLeast(0f))
        }

    /**
     * 自由态 x 解析（审查 G1：渲染 targetX/自由拖拽基准/点按脱附终位三处统一）：
     * [Float.NaN] → 默认物理右下角（右缘收进 defaultMarginPx），否则 clamp 在 [0, 父宽-尺寸]。
     * 注意：拖拽脱附的弹出起点 originX 为 dockSide 派生（贴边弹出），语义不同，不经此函数（审查 N1）。
     */
    fun resolveClampedX(offsetX: Float, parentWidthPx: Float, freeSizePx: Float, defaultMarginPx: Float): Float =
        if (offsetX.isNaN()) {
            (parentWidthPx - freeSizePx - defaultMarginPx).coerceAtLeast(0f)
        } else {
            offsetX.coerceIn(0f, (parentWidthPx - freeSizePx).coerceAtLeast(0f))
        }

    /**
     * 自由态 y 解析（审查 G1：渲染 targetY/拖拽脱附 originY/点按脱附 y 三处统一）：
     * [Float.NaN] → 默认物理右下角，否则 clamp 在上下各 edgeMarginPx 内。
     */
    fun resolveClampedY(
        offsetY: Float,
        parentHeightPx: Float,
        freeSizePx: Float,
        edgeMarginPx: Float,
        defaultMarginPx: Float,
    ): Float =
        if (offsetY.isNaN()) {
            parentHeightPx - freeSizePx - defaultMarginPx
        } else {
            offsetY.coerceIn(edgeMarginPx, (parentHeightPx - freeSizePx - edgeMarginPx).coerceAtLeast(edgeMarginPx))
        }

    /**
     * 吸附态点按脱附的完整目标状态（真机验收第三轮 P6）：
     * x=从吸附缘内收 inwardMarginPx、y=保持当前 y（经 clamp）、脱离吸附。
     */
    fun undockTapTarget(
        dockState: FabDockUiState,
        parentWidthPx: Float,
        parentHeightPx: Float,
        freeSizePx: Float,
        inwardMarginPx: Float,
        edgeMarginPx: Float,
        defaultMarginPx: Float,
    ): FabDockUiState = FabDockUiState(
        offsetX = undockInwardX(dockState.dockSide, parentWidthPx, freeSizePx, inwardMarginPx),
        offsetY = resolveClampedY(dockState.offsetY, parentHeightPx, freeSizePx, edgeMarginPx, defaultMarginPx),
        dockSide = SIDE_NONE,
    )
}
