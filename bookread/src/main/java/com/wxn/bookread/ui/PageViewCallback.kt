package com.wxn.bookread.ui

import com.wxn.base.bean.TextTag

interface PageViewCallback  : TextPageFactoryCallback {

    /***
     *
     */
    var isInitFinish: Boolean

    /***
     *
     */
    var isAutoPage: Boolean

    /** 自动阅读揭页进度（0..1 归一化，随页高换算绘制；旋转/双列宽度变化无损） */
    var autoPageProgressFraction: Float

    /***
     *
     */
    fun clickCenter()

    fun hideMenu()

    /***
     *
     */
    fun screenOffTimerStart()

    /***
     *
     */
    fun showTextActionMenu()


    fun showToolbarMenu()

    /***
     * 当前章节中正在显示的页面的索引
     */
    fun durChapterPos(): Int

    /***
     * click href link
     */
    fun clickLink(tag: TextTag, clickX: Float, clickY: Float)

    /****
     * click annotation like underline/highlight/note..
     */
    fun clickedAnnotation(annotationIds: List<String>)

    fun clickedNote(noteId: String)
}

/** 翻页模式 PageView 手势冻结钩子（按下冻结、抬起恢复；单击语义由 PageView 接管后经 VM 分发） */
fun interface AutoReadTouchListener {
    fun onTouch(down: Boolean)
}