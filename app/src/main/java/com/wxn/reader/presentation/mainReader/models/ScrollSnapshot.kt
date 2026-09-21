package com.wxn.reader.presentation.mainReader.models

/**
 * 滚动状态快照
 *
 * 用于 snapshotFlow 统一观察 LazyListState 和 mergedPages 的变化。
 * 当任一字段变化时触发重发射。
 *
 * @param pageCount dirty flag — 仅用于触发 snapshotFlow 重发射，
 *   collect 内部通过 mergedPages (Compose state) 读取最新数据
 * @param positionResolved dirty flag（F3）— 初始定位置位/超时放弃时强制重发射，
 *   使观察者从写回门控态无迟滞恢复（snapshotFlow 具 distinctUntilChanged 语义，
 *   不入快照键则可见项未变时不重发射，观察者将滞留门控态）
 */
data class ScrollSnapshot(
    val firstVisibleIndex: Int,
    val canScrollForward: Boolean,
    val canScrollBackward: Boolean,
    val pageCount: Int,
    val positionResolved: Boolean = true
)