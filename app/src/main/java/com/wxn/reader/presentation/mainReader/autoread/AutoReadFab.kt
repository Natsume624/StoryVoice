package com.wxn.reader.presentation.mainReader.autoread

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.hypot
import kotlin.math.roundToInt

private val FabSize = 56.dp
private val FabDockedWidth = 28.dp
/** 56dp 圆的圆角即 28dp（=CircleShape），吸附态朝屏侧同为 28dp（半圆） */
private val FabCornerRadius = 28.dp
/** 释放时距左/右缘小于该值即吸附 */
private val DockSnapThreshold = 64.dp
/** 吸附态下拖动超过该距离才弹出，防误触 */
private val UndockDragDistance = 24.dp
/** 拖拽时 FAB 上下留出的最小边距 */
private val DragEdgeMargin = 16.dp
/** 默认位边距（未拖动过时与旧 align(BottomEnd)+24dp 视觉一致） */
private val FabDefaultMargin = 24.dp
/** 点按脱附后距屏缘的内收边距（与默认位边距同值，视觉习惯一致；真机验收第三轮 P6） */
private val FabInwardMargin = 24.dp
/** 吸附态整体透明度：明显可发现且显著降低视觉权重（F-6 实测后可单点调，审查 S3） */
private const val FabDockedAlpha = 0.45f
private const val MorphAnimMs = 150

/**
 * 自动阅读 FAB（方案 §7.3 + 真机验收第二轮 P2 / 第三轮 P5/P6）：
 * 56dp 圆形；点按=暂停+设置弹窗，长按=退出自动阅读；支持自由拖动，
 * 拖到左/右缘附近释放吸附为 28×56dp 贴边半圆（贴边侧直角、朝屏侧圆角），
 * 吸附态整体半透明、无图标（P6：目标"不干扰阅读"）；吸附态点按=脱附（恢复正常圆形并
 * 向屏内侧收进，不弹面板），拖出 >24dp 同样脱附。形变/位移动画 tween(150)。
 * 进度环已移除（第三轮 P5：进度信息对操作无决策价值，视觉噪音）。
 *
 * 位置状态存 MainReadViewModel（审查 R6，会话级）；吸附态 x 渲染期由 dockSide+父宽派生（审查 R7）；
 * 自由态坐标解析统一走 FabDockPolicy 纯函数（审查 G1，可单测）。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AutoReadFab(
    visible: Boolean,
    paused: Boolean,
    dockState: FabDockUiState,
    onDockStateChange: (FabDockUiState) -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(visible = visible, modifier = modifier) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val density = LocalDensity.current
            val parentW = constraints.maxWidth.toFloat()
            val parentH = constraints.maxHeight.toFloat()
            val freeSizePx = with(density) { FabSize.toPx() }
            val dockedWidthPx = with(density) { FabDockedWidth.toPx() }
            val edgeMarginPx = with(density) { DragEdgeMargin.toPx() }
            val defaultMarginPx = with(density) { FabDefaultMargin.toPx() }
            val inwardMarginPx = with(density) { FabInwardMargin.toPx() }
            val snapThresholdPx = with(density) { DockSnapThreshold.toPx() }
            val undockDistancePx = with(density) { UndockDragDistance.toPx() }

            val docked = dockState.dockSide != FabDockPolicy.SIDE_NONE

            // R7 派生式定位：吸附态 x 由 dockSide+父宽派生，自由态经 FabDockPolicy 统一 clamp（NaN 落默认右下角）
            val targetX = when {
                dockState.dockSide == FabDockPolicy.SIDE_LEFT -> 0f
                dockState.dockSide == FabDockPolicy.SIDE_RIGHT -> (parentW - dockedWidthPx).coerceAtLeast(0f)
                else -> FabDockPolicy.resolveClampedX(dockState.offsetX, parentW, freeSizePx, defaultMarginPx)
            }
            val targetY = FabDockPolicy.resolveClampedY(
                dockState.offsetY, parentH, freeSizePx, edgeMarginPx, defaultMarginPx
            )

            // 拖拽期间跟手（snap），弹出/吸附落位动画（tween 150ms）
            var isDragging by remember { mutableStateOf(false) }
            val positionSpec = if (isDragging) snap() else tween<Float>(MorphAnimMs)
            val fabX by animateFloatAsState(targetValue = targetX, animationSpec = positionSpec, label = "fabX")
            val fabY by animateFloatAsState(targetValue = targetY, animationSpec = positionSpec, label = "fabY")

            // 形变动画：贴边侧圆角 56↔0，朝屏侧恒 28dp；宽度 56↔28
            val leftRadius by animateDpAsState(
                targetValue = if (dockState.dockSide == FabDockPolicy.SIDE_LEFT) 0.dp else FabCornerRadius,
                animationSpec = tween(MorphAnimMs),
                label = "fabLeftRadius"
            )
            val rightRadius by animateDpAsState(
                targetValue = if (dockState.dockSide == FabDockPolicy.SIDE_RIGHT) 0.dp else FabCornerRadius,
                animationSpec = tween(MorphAnimMs),
                label = "fabRightRadius"
            )
            val fabWidth by animateDpAsState(
                targetValue = if (docked) FabDockedWidth else FabSize,
                animationSpec = tween(MorphAnimMs),
                label = "fabWidth"
            )
            val fabShape = RoundedCornerShape(
                topStart = leftRadius, bottomStart = leftRadius,
                topEnd = rightRadius, bottomEnd = rightRadius,
            )

            // 吸附态半透明（第三轮 P6）：必须置于 .shadow 之前——alpha 建 graphicsLayer 包裹后续
            // shadow/clip/background/内容统一合成，阴影同步变淡；置于 shadow 之后则阴影仍不透明
            val fabAlpha by animateFloatAsState(
                targetValue = if (docked) FabDockedAlpha else 1f,
                animationSpec = tween(MorphAnimMs),
                label = "fabAlpha"
            )

            // pointerInput(Unit) 跨重组持有同一 lambda，闭包内经 rememberUpdatedState 读最新值
            val latestDockState by rememberUpdatedState(dockState)
            val latestOnDockStateChange by rememberUpdatedState(onDockStateChange)
            val latestParentW by rememberUpdatedState(parentW)
            val latestParentH by rememberUpdatedState(parentH)
            var undockDistance by remember { mutableFloatStateOf(0f) }

            val containerColor = MaterialTheme.colorScheme.primaryContainer

            Box(
                modifier = Modifier
                    .offset { IntOffset(fabX.roundToInt(), fabY.roundToInt()) }
                    .size(width = fabWidth, height = FabSize)
                    .alpha(fabAlpha)
                    .shadow(6.dp, fabShape)
                    .clip(fabShape)
                    .background(containerColor)
                    // 拖拽检测置于 combinedClickable 之前（审查 S4）：未过 touch slop=点击/长按；
                    // 过 slop 后移动事件被消费，clickable 自动取消按压，二者语义不重叠
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = {
                                isDragging = true
                                undockDistance = 0f
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val state = latestDockState
                                val pw = latestParentW
                                val ph = latestParentH
                                if (state.dockSide != FabDockPolicy.SIDE_NONE) {
                                    // 吸附态：累计位移 >24dp 才弹出（弹出后开始跟手）
                                    undockDistance += hypot(dragAmount.x, dragAmount.y)
                                    if (undockDistance > undockDistancePx) {
                                        // originX 为 dockSide 派生的贴边弹出起点（审查 N1：语义不同，不走 resolveClampedX）
                                        val originX = when (state.dockSide) {
                                            FabDockPolicy.SIDE_LEFT -> 0f
                                            else -> (pw - freeSizePx).coerceAtLeast(0f)
                                        }
                                        val originY = FabDockPolicy.resolveClampedY(
                                            state.offsetY, ph, freeSizePx, edgeMarginPx, defaultMarginPx
                                        )
                                        latestOnDockStateChange(
                                            FabDockUiState(originX, originY, FabDockPolicy.SIDE_NONE)
                                        )
                                    }
                                } else {
                                    // 自由态：跟手移动，上下留 16dp 边距、左右不越界
                                    val baseX = FabDockPolicy.resolveClampedX(state.offsetX, pw, freeSizePx, defaultMarginPx)
                                    val baseY = FabDockPolicy.resolveClampedY(
                                        state.offsetY, ph, freeSizePx, edgeMarginPx, defaultMarginPx
                                    )
                                    val nx = (baseX + dragAmount.x).coerceIn(0f, (pw - freeSizePx).coerceAtLeast(0f))
                                    val ny = (baseY + dragAmount.y).coerceIn(
                                        edgeMarginPx,
                                        (ph - freeSizePx - edgeMarginPx).coerceAtLeast(edgeMarginPx)
                                    )
                                    latestOnDockStateChange(FabDockUiState(nx, ny, FabDockPolicy.SIDE_NONE))
                                }
                            },
                            onDragEnd = {
                                isDragging = false
                                // 释放落位：按中心 x 判定吸附侧（纯函数，可单测）
                                val state = latestDockState
                                if (state.dockSide == FabDockPolicy.SIDE_NONE) {
                                    val baseX = FabDockPolicy.resolveClampedX(
                                        state.offsetX, latestParentW, freeSizePx, defaultMarginPx
                                    )
                                    val centerX = baseX + freeSizePx / 2f
                                    val side = FabDockPolicy.decideDock(centerX, latestParentW, snapThresholdPx)
                                    if (side != FabDockPolicy.SIDE_NONE) {
                                        latestOnDockStateChange(state.copy(dockSide = side))
                                    }
                                }
                            },
                            onDragCancel = { isDragging = false }
                        )
                    }
                    .combinedClickable(
                        onClick = {
                            if (docked) {
                                // 吸附态点按=脱附（第三轮 P6）：恢复正常圆形并向屏内侧收进至标准自由位边距（y 不变），
                                // 不弹暂停面板；长按仍=退出（onLongClick 不分态）；连点幂等（审查 G2）
                                onDockStateChange(
                                    FabDockPolicy.undockTapTarget(
                                        dockState, parentW, parentH,
                                        freeSizePx, inwardMarginPx, edgeMarginPx, defaultMarginPx
                                    )
                                )
                            } else {
                                onClick()
                            }
                        },
                        onLongClick = onLongClick
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (!docked) {
                    Icon(
                        imageVector = if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}
