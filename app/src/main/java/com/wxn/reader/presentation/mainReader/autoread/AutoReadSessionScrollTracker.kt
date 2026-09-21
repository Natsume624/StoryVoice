package com.wxn.reader.presentation.mainReader.autoread

/**
 * 自动阅读会话视图权威值状态机（F2 修复，纯 Kotlin 可单测）。
 *
 * 不变量：
 *  - [sessionScroll] 在会话内的每次底层视图切换都必须被 [trackViewChange] 推进，
 *    不论触发路径是弹窗二选一还是 ReaderSettings 手动切换（F2）；
 *  - [exit] 仅当会话存在且 sessionScroll != originalScroll 时返回恢复目标，
 *    幂等：复位后再次调用返回 null；
 *  - [epoch] 在真实会话边界（[enter]；[exit]/[reset] 结束一个活跃会话）时单调递增，
 *    无会话时的空操作不递增——updateScrollType 的 S6 钩子跨 DataStore 写入挂起执行，
 *    捕获-比对 epoch 可识别"协程挂起期间会话已更替"的陈旧执行并放弃同步
 *    （方案 §4.4 R2-1，同时覆盖手动切换与退出恢复两条陈旧路径）。
 */
class AutoReadSessionScrollTracker {

    /** 进入会话时的手动翻页模式（恢复基准）；-1 = 无活跃会话 */
    var originalScroll: Int = -1
        private set

    /** 会话内底层视图权威值；-1 = 无活跃会话 */
    var sessionScroll: Int = -1
        private set

    /** 会话代际号：每次会话边界跨越（enter/exit/reset）递增；陈旧协程的防护凭据 */
    var epoch: Int = 0
        private set

    val hasSession: Boolean get() = originalScroll >= 0

    /** 进入会话（startAutoReadInternal 调用；重复 enter 以最新值为准重置） */
    fun enter(scroll: Int) {
        originalScroll = scroll
        sessionScroll = scroll
        epoch++
    }

    /** 会话内底层视图发生切换（updateScrollType 活跃钩子 / updateAutoReadMode 发起切换时调用） */
    fun trackViewChange(scroll: Int) {
        if (hasSession) sessionScroll = scroll
    }

    /**
     * 退出会话，返回应恢复的手动翻页模式；视图未被会话改动或无会话时返回 null。
     * 调用后状态复位（幂等），与既有 N15 语义一致。
     */
    fun exit(): Int? {
        val restore = if (hasSession && sessionScroll != originalScroll) originalScroll else null
        reset()
        return restore
    }

    /** 无条件复位（onCleared 兜底路径亦复用）；仅当复位的是活跃会话（真实边界）时递增 epoch */
    fun reset() {
        if (hasSession) epoch++
        originalScroll = -1
        sessionScroll = -1
    }
}
