package com.wxn.reader.presentation.bookReader.components

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wxn.base.ext.toComposeColor
import com.wxn.bookread.data.model.InfoBarSlots
import com.wxn.bookread.data.model.InfoBarSpec
import com.wxn.reader.R
import com.wxn.reader.presentation.mainReader.MainReadViewModel
import kotlinx.coroutines.delay
import java.util.Calendar
import java.util.Locale

/** 滚动模式非内容区渐变带高度（scrim 总高 = 条高 + 16dp，产品方案 §5.2） */
private val SCRIM_EXTRA = 16.dp

// ---- 电量槽电池图形绘制细节（跨层共享的规格常量在 InfoBarSpec，见方案审查建议 2）----
private val BATTERY_CAP_WIDTH = 2.dp
private val BATTERY_CAP_GAP = 1.dp

/** 电池身宽 = 总宽 − 极帽 − 间隙 */
private val BATTERY_BODY_WIDTH =
    InfoBarSpec.BATTERY_TOTAL_WIDTH_DP.dp - BATTERY_CAP_WIDTH - BATTERY_CAP_GAP
private val BATTERY_CAP_HEIGHT = 6.dp
private val BATTERY_STROKE_WIDTH = 1.2.dp
private val BATTERY_CORNER_RADIUS = 2.5.dp

/** 电量填充相对轮廓色的透明度系数（填充更淡，与轮廓形成层次） */
private const val BATTERY_FILL_ALPHA_FACTOR = 0.5f

/** 信息条槽位单元：内容码 + 文本对齐 + 容器对齐 */
private data class InfoBarCell(val code: Int, val textAlign: TextAlign, val contentAlign: Alignment)

/**
 * 阅读页信息条覆盖层宿主：统一挂载在 ReaderView 内容 Box 中，
 * 翻页/滚动两种模式共用；无任何 pointer 消费，点击穿透到阅读画布。
 */
@Composable
fun ReaderInfoBarHost(viewModel: MainReadViewModel) {
    val tip = viewModel.readTipPreferences.collectAsStateWithLifecycle().value ?: return
    val readerPrefs by viewModel.readerPreferences.collectAsStateWithLifecycle()
    val isScrollMode = readerPrefs.scroll == 6

    // 菜单/设置面板打开期间 SetFullScreen 强制显示系统状态栏，会遮挡贴顶的顶部条：
    // 此时下移避让保持可实时预览；阅读态（状态栏隐藏）零边距贴顶（翻页/滚动两模式一致）
    val showMenu by viewModel.showMenu.collectAsStateWithLifecycle()
    val showReaderUISettings by viewModel.showReaderUISettings.collectAsStateWithLifecycle()
    val showReaderSettings by viewModel.showReaderSettings.collectAsStateWithLifecycle()
    val systemBarsVisible = showMenu || showReaderUISettings || showReaderSettings
    val density = LocalDensity.current
    val statusBarTopDp = if (systemBarsVisible) {
        with(density) { WindowInsets.statusBars.getTop(density).toDp() }
    } else {
        0.dp
    }

    val headerSlots = InfoBarSlots(tip.tipHeaderLeft, tip.tipHeaderMiddle, tip.tipHeaderRight)
    val footerSlots = InfoBarSlots(tip.tipFooterLeft, tip.tipFooterMiddle, tip.tipFooterRight)
    val headerEnabled = InfoBarSpec.isEnabled(tip.hideHeader, headerSlots)
    val footerEnabled = InfoBarSpec.isEnabled(tip.hideFooter, footerSlots)
    if (!headerEnabled && !footerEnabled) return

    val textColor = readerPrefs.textColor.toComposeColor().copy(alpha = 0.6f)
    val scrimColor = readerPrefs.backgroundColor.toComposeColor()
    // 任一条含时间槽才启动分钟 tick
    val needsTime =
        headerSlots.hasCode(InfoBarSpec.SLOT_TIME) || footerSlots.hasCode(InfoBarSpec.SLOT_TIME)

    Box(modifier = Modifier.fillMaxSize()) {
        if (headerEnabled) {
            ReaderInfoBar(
                slots = headerSlots,
                isScrollMode = isScrollMode,
                topScrim = true,
                needsTime = needsTime,
                textColor = textColor,
                scrimColor = scrimColor,
                viewModel = viewModel,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(top = statusBarTopDp)
            )
        }
        if (footerEnabled) {
            ReaderInfoBar(
                slots = footerSlots,
                isScrollMode = isScrollMode,
                topScrim = false,
                needsTime = needsTime,
                textColor = textColor,
                scrimColor = scrimColor,
                viewModel = viewModel,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            )
        }
    }
}

/**
 * 单条信息条。
 * [topScrim]：滚动模式 scrim 渐变方向（true=顶部条，自上向下淡出；false=底部条）。
 * 槽位顺序固定为 左/中/右；阅读方向 RTL（readingProgression）时反转顺序，
 * 并将 Row 的 LayoutDirection 锁定为 LTR——镜像完全由顺序反转控制，
 * 避免 locale 级 RTL（如阿语系统）与手动反转叠加造成双重镜像。
 */
@Composable
fun ReaderInfoBar(
    slots: InfoBarSlots,
    isScrollMode: Boolean,
    topScrim: Boolean,
    needsTime: Boolean,
    textColor: Color,
    scrimColor: Color,
    viewModel: MainReadViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val isRtlReading = viewModel.isRtlReadingProgression()

    // ---- 时间：分钟 tick，跟随系统 12/24 小时制（格式每次 tick 重算，系统设置变化自动生效）----
    var timeText by remember { mutableStateOf("") }
    if (needsTime) {
        LaunchedEffect(Unit) {
            while (true) {
                val is24 = DateFormat.is24HourFormat(context)
                timeText = InfoBarSpec.formatTime(is24, Calendar.getInstance(), Locale.getDefault())
                val now = Calendar.getInstance()
                val delayMs = (60 - now.get(Calendar.SECOND)) * 1000L - now.get(Calendar.MILLISECOND)
                delay(delayMs.coerceIn(200L, 60_000L))
            }
        }
    }

    // ---- 电量：粘性广播注册即得当前值；parse 后整数赋值天然去重（充电高频广播不反复触发）----
    // 顶/底两条都配电量槽时会各注册一个 receiver，行为正确仅冗余，接受。
    var batteryPercent by remember { mutableIntStateOf(-1) }
    if (slots.hasCode(InfoBarSpec.SLOT_BATTERY)) {
        DisposableEffect(context) {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    intent ?: return
                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    InfoBarSpec.parseBattery(level, scale)?.let { batteryPercent = it }
                }
            }
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(receiver, filter)
            }
            onDispose { context.unregisterReceiver(receiver) }
        }
    }

    val chapterName by viewModel.curChapterName.collectAsStateWithLifecycle()
    val progression by viewModel.readProgression.collectAsStateWithLifecycle()
    val bookTitle = viewModel.currentBookTitle()
    val page = viewModel.infoBarPage.collectAsStateWithLifecycle().value
    // 电量无障碍描述复用信息条既有词条（如"电量 85%"），零新增翻译
    val batteryLabel = stringResource(R.string.reader_info_bar_battery)

    // 电量槽不走文本渲染（BatteryIndicator 图形化），其余槽位仍为纯文本
    fun slotText(code: Int): String? = when (code) {
        InfoBarSpec.SLOT_CHAPTER_TITLE -> chapterName.ifEmpty { "—" }
        InfoBarSpec.SLOT_TIME -> timeText.ifEmpty { null }
        InfoBarSpec.SLOT_PAGE -> page?.let { InfoBarSpec.formatPage(it.index0Based, it.pageSize) }
        InfoBarSpec.SLOT_TOTAL_PROGRESS -> InfoBarSpec.formatProgress(progression)
        InfoBarSpec.SLOT_PAGE_AND_TOTAL -> page?.let {
            InfoBarSpec.formatPageAndTotal(it.index0Based, it.pageSize, progression)
        }
        InfoBarSpec.SLOT_BOOK_NAME -> bookTitle?.ifEmpty { null }
        else -> null
    }

    // 三等分槽位（每格 ≤33% 屏宽，满足宽度上限且三槽互不挤压）；
    // RTL 阅读方向时顺序反转（Row 已锁定 LTR，见 barContent）
    val cells = listOf(
        InfoBarCell(slots.left, TextAlign.Left, Alignment.CenterStart),
        InfoBarCell(slots.middle, TextAlign.Center, Alignment.Center),
        InfoBarCell(slots.right, TextAlign.Right, Alignment.CenterEnd)
    ).let { if (isRtlReading) it.reversed() else it }

    val textSp = InfoBarSpec.BAR_TEXT_SP.sp

    val barContent: @Composable () -> Unit = {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(InfoBarSpec.BAR_HEIGHT_DP.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                cells.forEach { cell ->
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = cell.contentAlign
                    ) {
                        if (cell.code == InfoBarSpec.SLOT_BATTERY) {
                            // 电量槽：电池图形（轮廓+填充+内部百分比），未取得电量时隐藏
                            if (batteryPercent >= 0) {
                                BatteryIndicator(
                                    percent = batteryPercent,
                                    color = textColor,
                                    contentDesc = "$batteryLabel $batteryPercent%"
                                )
                            }
                        } else {
                            val text = slotText(cell.code)
                            if (text != null) {
                                Text(
                                    text = text,
                                    fontSize = textSp,
                                    color = textColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = cell.textAlign,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .semantics { contentDescription = text }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (isScrollMode) {
        // 滚动模式：内容区实心底遮断穿过的正文，非内容区渐变带保留淡出层次。
        // 原单一 40dp 整条渐变在 24dp 文字带内不透明度仅 100%→40%，正文透出与信息条文字重叠
        Box(modifier = modifier.height(InfoBarSpec.BAR_HEIGHT_DP.dp + SCRIM_EXTRA)) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (topScrim) {
                    SolidScrimBand(scrimColor)
                    GradientScrimBand(scrimColor, fadeOut = true)
                } else {
                    GradientScrimBand(scrimColor, fadeOut = false)
                    SolidScrimBand(scrimColor)
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(if (topScrim) Alignment.TopCenter else Alignment.BottomCenter)
            ) {
                barContent()
            }
        }
    } else {
        // 翻页模式：透明覆盖层（顶部条落于状态栏占位区、底部条落于下边距区），点击穿透
        Box(modifier = modifier) {
            barContent()
        }
    }
}

/**
 * 内容区实心底：与阅读背景同色（backgroundColor 全部写入路径均不透明，见方案 §2.3），
 * 遮断从信息条下穿过的正文。
 */
@Composable
private fun SolidScrimBand(color: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(InfoBarSpec.BAR_HEIGHT_DP.dp)
            .background(color)
    )
}

/**
 * 非内容区渐变带：贴条带侧为 color，向外淡出到透明（fadeOut=true 为顶部条方向）。
 * 起始色与实心底同色，衔接处无缝。
 */
@Composable
private fun GradientScrimBand(color: Color, fadeOut: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(SCRIM_EXTRA)
            .background(
                brush = if (fadeOut) {
                    Brush.verticalGradient(listOf(color, Color.Transparent))
                } else {
                    Brush.verticalGradient(listOf(Color.Transparent, color))
                }
            )
    )
}

/**
 * 电量槽电池图形：电池身轮廓 + 右侧正极帽 + 按电量水平的低透明度填充，
 * 百分比文字居中显示在电池身内（方案 §3.2）。纯静态绘制，percent 变化时重组重绘；
 * 图形为符号不随 RTL 镜像（极帽恒在右侧）。
 */
@Composable
private fun BatteryIndicator(
    percent: Int,
    color: Color,
    contentDesc: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(InfoBarSpec.BATTERY_TOTAL_WIDTH_DP.dp, InfoBarSpec.BATTERY_HEIGHT_DP.dp)
            .semantics { contentDescription = contentDesc }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokePx = BATTERY_STROKE_WIDTH.toPx()
            val bodyWidth = BATTERY_BODY_WIDTH.toPx()
            val bodyHeight = size.height
            // 1. 电量填充：身内缩后按比例自左向右（低透明度，与轮廓形成层次）
            val fillInset = strokePx / 2 + 0.5.dp.toPx()
            val fillWidth = (bodyWidth - fillInset * 2) * InfoBarSpec.batteryFillFraction(percent)
            if (fillWidth > 0f) {
                drawRect(
                    color = color.copy(alpha = color.alpha * BATTERY_FILL_ALPHA_FACTOR),
                    topLeft = Offset(fillInset, fillInset),
                    size = Size(fillWidth, bodyHeight - fillInset * 2)
                )
            }
            // 2. 电池身轮廓
            drawRoundRect(
                color = color,
                size = Size(bodyWidth, bodyHeight),
                cornerRadius = CornerRadius(BATTERY_CORNER_RADIUS.toPx()),
                style = Stroke(width = strokePx)
            )
            // 3. 正极帽（垂直居中于身侧）
            drawRoundRect(
                color = color,
                topLeft = Offset(
                    bodyWidth + BATTERY_CAP_GAP.toPx(),
                    (bodyHeight - BATTERY_CAP_HEIGHT.toPx()) / 2f
                ),
                size = Size(BATTERY_CAP_WIDTH.toPx(), BATTERY_CAP_HEIGHT.toPx()),
                cornerRadius = CornerRadius(1.dp.toPx())
            )
        }
        // 百分比文字限定在电池身宽度内精确居中（右侧极帽不参与，避免视觉偏移）；
        // 大字体缩放下 Clip 防止溢出撑破图形（审查必改项 1）。
        // 垂直居中约束：默认字体填充（includeFontPadding）会为重音符号预留顶部空间，
        // 使数字字形在行盒内偏下——必须关闭字体填充、行盒收敛为字号并按度量居中，
        // 字形才能在电池身内视觉居中，而非仅行盒居中
        Text(
            text = "$percent%",
            fontSize = InfoBarSpec.BATTERY_TEXT_SP.sp,
            lineHeight = InfoBarSpec.BATTERY_TEXT_SP.sp,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            textAlign = TextAlign.Center,
            style = LocalTextStyle.current.merge(
                TextStyle(
                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                    lineHeightStyle = LineHeightStyle(
                        alignment = LineHeightStyle.Alignment.Center,
                        trim = LineHeightStyle.Trim.None
                    )
                )
            ),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(BATTERY_BODY_WIDTH)
        )
    }
}
