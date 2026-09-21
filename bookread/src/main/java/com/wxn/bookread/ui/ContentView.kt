package com.wxn.bookread.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.TransitionDrawable
import android.os.Looper
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.ImageView
import com.wxn.base.ext.getCompatColor
import com.wxn.base.ext.statusBarHeight
import com.wxn.base.util.Coroutines
import com.wxn.base.util.Logger
import com.wxn.base.util.launchMain
import com.wxn.bookread.R
import com.wxn.bookread.data.model.TextPage
import com.wxn.bookread.databinding.ViewBookPageBinding
import kotlinx.coroutines.launch

class ContentView(context: Context) : FrameLayout(context) {

    companion object {
        /**
         * 背景切换的交叉淡入时长（ms）。
         *   封面淡出期间背景同步淡入，无明显跳变。
         */
        private const val BG_FADE_MS = 300
    }
    /**
     * 当前背景过渡的还原 Runnable 句柄。
     * 连续快速切换时通过 removeCallbacks 取消上一次未执行的还原，
     * 避免新动画中途被旧 Runnable 替换为旧 newDrawable。
     */
    private var bgTransitionRunnable: Runnable? = null

    private val binding = ViewBookPageBinding.inflate(
        LayoutInflater.from(context), this, true
    )

    init {
        //设置背景颜色防止切换背景时文字重叠
        setBackgroundColor(context.getCompatColor(R.color.background))
        upTipStyle()
        upStyle()
    }

    var callback: SelectTextCallback? = null

    fun setSelectTextCallback(callback: SelectTextCallback) {
        this.callback = callback
        binding.contentTextView.callback = callback
    }

    /**
     * v6：透传尺寸变化重排请求到内部 ContentTextView（非连续翻页首帧裁剪修复）。
     *
     * 用自定义 setter：PageView 在添加 ContentView 之后赋值（时机晚于 ContentView 构造），
     * setter 确保赋值立即透传到 binding.contentTextView.onRequestRepaginate。
     */
    var onRequestRepaginate: (() -> Unit)? = null
        set(value) {
            field = value
            binding.contentTextView.onRequestRepaginate = value
        }

    /****
     * 更新显示的样式
     */
    fun upStyle() {
        binding.apply {
            // 信息条已迁移至 app 模块 Compose 覆盖层（ReaderInfoBar），
            // View 层仅保留状态栏占位与可视区刷新
            upStatusBar()
            binding.contentTextView.refreshVisibleRect()
        }
    }

    /**
     * 显示状态栏时隐藏header
     */
    fun upStatusBar() {
        with(binding.vwStatusBar) {
            setPadding(paddingLeft, context.statusBarHeight, paddingRight, paddingBottom)

            Coroutines.mainScope().launch {
//                ChapterProvider.tryCreatePreference(context)
//                val tipPreference =
//                    ChapterProvider.readTipPreferencesUtil?.readTIpPreferencesFlow?.firstOrNull()
//                        ?: return@launch

//                isGone = tipPreference.hideStatusBar || (activity as? BaseActivity)?.isInMultiWindow == true
            }
        }
    }

    /***
     * 更新提示信息显示的控件（历史接口：信息条已迁移至 app 模块 Compose 覆盖层 ReaderInfoBar，
     * View 层槽位渲染已移除；保留空实现以兼容 PageViewController.updatePageViews 调用链）
     */
    fun upTipStyle() = Unit

    /****
     * 更新背景显示
     */
    fun setBg(bg: Drawable?) {
        binding.ivPageBg.setScaleType(ImageView.ScaleType.CENTER_CROP)
        crossfadeToBg(bg)
//        binding.ivPageBg.setImageDrawable(bg)
    }

    fun setBg(bgColor: Int, cleanImage:Boolean = false) {
        val ivBg = binding.ivPageBg
        val oldBmp : Bitmap? = (ivBg.drawable as? BitmapDrawable)?.bitmap
        crossfadeToBg(ColorDrawable(bgColor)) {
            // Q-09 OOM 防护：延后 recycle，确保动画期间 TransitionDrawable 已不再引用 oldBmp
            if (cleanImage && oldBmp != null && !oldBmp.isRecycled) {
                oldBmp.recycle()
            }
        }
    }

    /**
     * 用 [TransitionDrawable] 把 [binding.ivPageBg] 当前 drawable 交叉淡入到 [newDrawable]。
     */
    fun crossfadeToBg(newbg : Drawable?, onEnd: (()-> Unit)? = null) {
        val iv = binding.ivPageBg
        bgTransitionRunnable?.let { iv.removeCallbacks(it) }
        bgTransitionRunnable = null
        if (newbg == null) {
            iv.setImageDrawable(null)
            onEnd?.invoke()
            return
        }
        val old = iv.drawable ?: ColorDrawable(Color.TRANSPARENT)
        val td = TransitionDrawable(arrayOf(old, newbg))
        iv.setImageDrawable(td)
        td.startTransition(BG_FADE_MS)
        val r = Runnable {
            // 先替换 td 为单一 newDrawable，再回调 onEnd（确保 recycle 时 td 已不被引用）
            if (iv.drawable === td) {
                iv.setImageDrawable(newbg)
            }
            bgTransitionRunnable = null
            onEnd?.invoke()
        }
        bgTransitionRunnable = r
        iv.postDelayed(r, BG_FADE_MS + 50L)
    }

    /****
     * 设置需要显示的TextPage内容
     *
     * 主线程调用时同步内联执行（自动提交翻页路径）：揭页遮罩撤除与内容切换同拍完成，
     * 避免"旧页先绘制一帧再切新页"的竞态闪屏（方案 2026-09-07-plan-auto-read-acceptance-round2 §3.4）；
     * IO 线程调用（章节异步解析后的 loadContent 回调）保持 post 到主线程，行为不变。
     */
    fun setContent(textPage: TextPage, resetPageOffset: Boolean = true) {
        Logger.i("ContentView::setContent::textPage.pageSize=${textPage.pageSize}")
        val apply: () -> Unit = {
            if (resetPageOffset) {
                resetPageOffset()
            }
            binding.contentTextView.setContent(textPage)
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            apply()
        } else {
            Coroutines.mainScope().launchMain { apply() }
        }
    }

    /***
     * 重置 界面移动时的偏移值
     */
    fun resetPageOffset() {
        binding.contentTextView.resetPageOffset()
    }

    /***
     * 移动时，设置显示界面的偏移
     */
    fun onScroll(offset: Float) {
        binding.contentTextView.onScroll(offset)
    }

    /***
     * 是否运行选中文本
     */
    fun upSelectAble(selectAble: Boolean) {
        binding.contentTextView.selectAble = selectAble
    }

    /****
     * 选中文本
     */
    fun selectText(
        x: Float,
        y: Float,
        select: (relativePage: Int, lineIndex: Int, charIndex: Int) -> Unit
    ) {
        Coroutines.mainScope().launch {
            val headerHeight = context.statusBarHeight
            binding.contentTextView.selectText(x, y - headerHeight, select)
        }
    }

    /****
     * 移动 选中结束符
     */
    fun selectStartMove(x: Float, y: Float) {
        val headerHeight = context.statusBarHeight
        binding.contentTextView.selectStartMove(x, y - headerHeight)
    }

    /****
     * 选中开始符 的位置设置
     */
    fun selectStartMoveIndex(relativePage: Int, lineIndex: Int, charIndex: Int) {
        binding.contentTextView.selectStartMoveIndex(relativePage, lineIndex, charIndex)
    }

    /****
     * 移动 选中结束符
     */
    fun selectEndMove(x: Float, y: Float) {
        val headerHeight = context.statusBarHeight
        binding.contentTextView.selectEndMove(x, y - headerHeight)
    }

    /***
     * 选中结束符 的位置设置
     */
    fun selectEndMoveIndex(relativePage: Int, lineIndex: Int, charIndex: Int) {
        binding.contentTextView.selectEndMoveIndex(relativePage, lineIndex, charIndex)
    }

    fun selectSentenceAtChar(relativePage: Int, lineIndex: Int, charIndex: Int) {
        binding.contentTextView.selectWordAtChar(relativePage, lineIndex, charIndex)
    }

    fun getSelectionHandlePositions(): Pair<Pair<Float, Float>, Pair<Float, Float>>? {
        val positions = binding.contentTextView.getSelectionHandlePositions() ?: return null
        val headerHeight = context.statusBarHeight
        return Pair(
            Pair(positions.first.first, positions.first.second + headerHeight),
            Pair(positions.second.first, positions.second.second + headerHeight)
        )
    }

    /****
     * 取消选中文字
     */
    fun cancelSelect() {
        Logger.i("ContentView::cancelSelect")
        binding.contentTextView.cancelSelect()
    }

    /***
     * 获取选中的文本内容
     */
    val selectedText: String get() = binding.contentTextView.selectText

    val textPage: TextPage get() = binding.contentTextView.textPage

    override fun onDetachedFromWindow() {
        Logger.i("ContentView::onDetachedFromWindow")
        callback = null
        super.onDetachedFromWindow()
    }
}