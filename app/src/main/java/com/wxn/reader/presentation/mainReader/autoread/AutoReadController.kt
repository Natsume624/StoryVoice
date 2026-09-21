package com.wxn.reader.presentation.mainReader.autoread

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch

/** 自动阅读状态 */
enum class AutoReadStatus { IDLE, RUNNING, PAUSED }

data class AutoReadState(
    val status: AutoReadStatus = AutoReadStatus.IDLE,
    /** 翻页模式=当前页揭页进度；滚动模式=当前页滚动进度（进度环用） */
    val progressFraction: Float = 0f,
)

/** 滚动模式由 ContinuousScrollReaderView 组合时挂载、离开时置空 */
interface AutoReadScrollAdapter {
    /** 匀速前进 px；返回 false 表示已到书末（无更多内容） */
    suspend fun scrollByPx(px: Float): Boolean

    /** 视口高度 px */
    fun viewportHeightPx(): Float

    /**
     * 阅读序每字对应的垂直像素数（= 视口高 / 当前屏字数）。
     * 用它折算 px/s（速度/60 × 该值）后，整屏耗时 = 屏字数/速度×60s，与覆盖翻页模式严格一致
     * （真机验收修订：原"平均字宽"语义把每字当一行宽的垂直步进，滚动明显快于覆盖，已废弃）。
     * <=0 表示暂不可用（跳过本拍）。
     */
    fun verticalPxPerChar(): Float

    /** 当前页滚动进度 0..1 */
    fun pageProgressFraction(): Float
}

// 注意：AutoReadTouchListener 定义在 bookread 模块 PageViewCallback.kt（审查 S5），
// 此处不得声明——bookread 仅依赖 :base，反向引用 app 模块类型无法编译。

class AutoReadController(
    private val scope: CoroutineScope,
    private val speedCharsPerMin: () -> Int,
    private val isScrollMode: () -> Boolean,
    private val currentPageChars: () -> Int,
    private val commitNextPage: () -> Boolean,
    private val isChapterLoading: () -> Boolean,
    private val onBookEnd: () -> Unit,
    private val onFrame: (Float) -> Unit = {},
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val frameDelayMillis: Long = 16L,
) {
    @Volatile
    var scrollAdapter: AutoReadScrollAdapter? = null

    private val _state = MutableStateFlow(AutoReadState())
    val state: StateFlow<AutoReadState> = _state.asStateFlow()

    private var loopJob: Job? = null
    private var lastFrameMillis = 0L
    private var fraction = 0f

    @Volatile private var touchFrozen = false
    @Volatile private var overlayPaused = false
    @Volatile private var selfCommitPending = false
    private var selfCommitAtMillis = 0L

    val isActive: Boolean get() = _state.value.status != AutoReadStatus.IDLE

    fun start() {
        if (isActive) return
        resetForStart()
        loopJob = scope.launch { runLoop() }
    }

    fun stop() {
        loopJob?.cancel()
        loopJob = null
        fraction = 0f
        touchFrozen = false
        overlayPaused = false
        selfCommitPending = false
        _state.value = AutoReadState()
    }

    /** 手势按下（PageView 触摸 / 滚动模式拖动开始） */
    fun pauseByTouch() { touchFrozen = true; publishPaused() }

    /** 手势抬起且未引发弹层类交互 */
    fun resumeByTouch() { touchFrozen = false; publishResumed() }

    /** 菜单/弹窗/文本选择/切后台等接管型暂停（恢复须显式 resumeByOverlay） */
    fun pauseByOverlay() { overlayPaused = true; publishPaused() }

    fun resumeByOverlay() { overlayPaused = false; touchFrozen = false; publishResumed() }

    /** 翻页提交后的统一 reset 钩子（音量键/滑动/跳转共用，见方案 §7.5） */
    fun onPageCommitted() {
        if (!isActive) return
        if (selfCommitPending) {
            // 揭满自提交：moveToNext → provider.setPageIndex → PageViewController:587 同步触发本钩子
            //（审查 S7）。余量已在 tickReveal 的 fraction -= 1f 保留，不得归零。
            selfCommitPending = false
            return
        }
        fraction = 0f
        publish()
    }

    /** start() 的无协程部分，单元测试直接驱动 tick 用 */
    internal fun resetForStart() {
        loopJob?.cancel()
        loopJob = null
        fraction = 0f
        touchFrozen = false
        overlayPaused = false
        selfCommitPending = false
        _state.value = AutoReadState(AutoReadStatus.RUNNING, 0f)
    }

    /** 单帧推进（runLoop 每拍调用；单元测试直接调用） */
    internal suspend fun tick(elapsedMs: Long) {
        if (!isActive || touchFrozen || overlayPaused || elapsedMs <= 0) return
        if (selfCommitPending && nowMillis() - selfCommitAtMillis > SELF_COMMIT_HOOK_TIMEOUT_MS) {
            // 缓存章节直跨（moveToNextChapter 命中缓存）不经过 onPageChange，守卫无人消费；
            // 超时作废，防止吞掉后续外部提交（音量键/滑动）的归零（代码审查 FIX-B）
            selfCommitPending = false
        }
        if (isScrollMode()) tickScroll(elapsedMs) else tickReveal(elapsedMs)
    }

    private suspend fun runLoop() {
        lastFrameMillis = nowMillis()
        while (currentCoroutineContext().isActive) {
            delay(frameDelayMillis)
            val now = nowMillis()
            val elapsed = now - lastFrameMillis
            lastFrameMillis = now
            tick(elapsed)
        }
    }

    /** 揭页模式：每页时长 = 页字数 / 速度 × 60_000ms（字数多的页揭得慢，体感速度恒定） */
    private suspend fun tickReveal(elapsedMs: Long) {
        if (isChapterLoading()) return
        val chars = currentPageChars().coerceAtLeast(1)
        val pageDurationMs = chars * 60_000f / speedCharsPerMin().coerceAtLeast(1)
        fraction += elapsedMs / pageDurationMs
        while (fraction >= 1f) {
            fraction -= 1f                    //余量带入新页，长程平均速度精确
            selfCommitPending = true          //自提交会经同步 onPageChange 打进 onPageCommitted 钩子（审查 S7）
            selfCommitAtMillis = nowMillis()
            if (!commitNextPage()) {
                selfCommitPending = false
                stop()
                onBookEnd()
                return
            }
        }
        publish()
    }

    /** 滚动模式：px/s = 速度/60 × 每字垂直像素（真机验收修订：与覆盖模式整页耗时公式严格一致） */
    private suspend fun tickScroll(elapsedMs: Long) {
        val adapter = scrollAdapter ?: return
        val pxPerChar = adapter.verticalPxPerChar()
        if (pxPerChar <= 0f) return
        val px = (speedCharsPerMin().coerceAtLeast(1) / 60f) * pxPerChar * (elapsedMs / 1000f)
        val canContinue = adapter.scrollByPx(px)
        _state.update { it.copy(progressFraction = adapter.pageProgressFraction().coerceIn(0f, 1f)) }
        if (!canContinue) {
            stop()
            onBookEnd()
        }
    }

    private fun publish() {
        _state.update {
            it.copy(
                progressFraction = if (isScrollMode()) {
                    scrollAdapter?.pageProgressFraction()?.coerceIn(0f, 1f) ?: it.progressFraction
                } else {
                    fraction
                }
            )
        }
        onFrame(_state.value.progressFraction)
    }

    private fun publishPaused() {
        _state.update { if (it.status == AutoReadStatus.RUNNING) it.copy(status = AutoReadStatus.PAUSED) else it }
    }

    private fun publishResumed() {
        _state.update { if (it.status == AutoReadStatus.PAUSED) it.copy(status = AutoReadStatus.RUNNING) else it }
    }

    private companion object {
        /** 自提交守卫有效期：覆盖异步 loadContent 的 onPageChange 回达；缓存直跨无钩子时按此作废（FIX-B） */
        private const val SELF_COMMIT_HOOK_TIMEOUT_MS = 2_000L
    }
}
