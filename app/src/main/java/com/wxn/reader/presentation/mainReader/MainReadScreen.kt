package com.wxn.reader.presentation.mainReader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.activity.compose.BackHandler
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.wxn.reader.R
import com.wxn.reader.navigation.LocalNavController
import com.wxn.reader.navigation.navigateToHome
import com.wxn.reader.presentation.bookReader.BookReaderUiState
import com.wxn.reader.presentation.sharedComponents.BookCover
import com.wxn.reader.ui.theme.stringResource
import com.wxn.reader.presentation.mainReader.autoread.AutoReadFab
import com.wxn.reader.presentation.mainReader.autoread.AutoReadFabGuideTooltip
import com.wxn.reader.presentation.mainReader.autoread.AutoReadSettingsSheet
import com.wxn.reader.presentation.mainReader.autoread.AutoReadStatus
import com.wxn.reader.util.FullScreenManager
import com.wxn.reader.util.KeepScreenOn
import com.wxn.reader.util.SetFullScreen
import com.wxn.reader.util.consumeClick
import kotlinx.coroutines.launch

@Composable
fun MainReadScreen(viewModel: MainReadViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val navController = LocalNavController.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val readerPreferences by viewModel.readerPreferences.collectAsStateWithLifecycle()
    val displayCover by viewModel.displayCover.collectAsStateWithLifecycle()
    val displayTitle by viewModel.displayTitle.collectAsStateWithLifecycle()
    val displayAuthor by viewModel.displayAuthor.collectAsStateWithLifecycle()
    // 系统栏跟随工具栏/设置面板可见性同步：任一为 true 即显示。
    // 替换原先永远为 false 的失效局部变量（Bug：工具栏显示时系统栏仍隐藏）。
    val showMenu by viewModel.showMenu.collectAsStateWithLifecycle()
    val showReaderUISettings by viewModel.showReaderUISettings.collectAsStateWithLifecycle()
    val showReaderSettings by viewModel.showReaderSettings.collectAsStateWithLifecycle()
    val autoReadState by viewModel.autoReadState.collectAsStateWithLifecycle()
    val showAutoReadSheet by viewModel.showAutoReadSheet.collectAsStateWithLifecycle()
    val autoReadPageChars by viewModel.autoReadPageChars.collectAsStateWithLifecycle()
    val fabDock by viewModel.fabDock.collectAsStateWithLifecycle()
    val showAutoReadFabGuide by viewModel.showAutoReadFabGuide.collectAsStateWithLifecycle()
    val showTextToolbar by viewModel.showTextToolbar.collectAsStateWithLifecycle()
    val autoReadActive = autoReadState.status != AutoReadStatus.IDLE
    val showSystemBars by remember {
        derivedStateOf { showMenu || showReaderUISettings || showReaderSettings }
    }

    // 自动阅读：弹层（菜单/设置面板/选择工具栏）任一打开即暂停，全部关闭恢复（方案 §7.5，审查 S2/G11）
    val autoReadOverlayBlocking = showMenu || showReaderSettings || showReaderUISettings || showTextToolbar
    LaunchedEffect(autoReadOverlayBlocking, autoReadActive) {
        if (autoReadActive) viewModel.onAutoReadOverlay(pause = autoReadOverlayBlocking)
    }

    // 自动阅读运行中强制亮屏（方案 §9）
    KeepScreenOn(readerPreferences.keepScreenOn || autoReadActive)

    // 自动阅读切后台暂停；回前台保持暂停待用户恢复（方案 §7.5）
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) viewModel.onAutoReadBackground()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 自动阅读 Back 优先级（方案 §7.5）：弹窗/菜单/工具栏打开时由各自组件处理；裸 Running 拦截 = 退出自动阅读
    BackHandler(
        enabled = autoReadActive && !showAutoReadSheet && !showMenu &&
                !showReaderSettings && !showReaderUISettings && !showTextToolbar
    ) {
        viewModel.stopAutoReadByBack()
    }

    DisposableEffect(Unit) {
        FullScreenManager.registerReadPage()
        viewModel.viewModelScope.launch {
            viewModel.updateReadingTime()
        }
        onDispose {
            viewModel.viewModelScope.launch {
                viewModel.updateReadingTime(true)
                viewModel.resetReadingSession()
            }
            FullScreenManager.unregisterReadPage()
            // 离开阅读器返回书架等非全屏页面：确保系统栏恢复显示。
            // MainActivity 为 singleInstance，popBackStack 不会触发 onResume，故在 onDispose 兜底。
            (context as? android.app.Activity)?.window?.let { window ->
                WindowCompat.getInsetsController(window, window.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    val isLoading = uiState is BookReaderUiState.Loading
    val isError = uiState is BookReaderUiState.Error

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {

        ReaderView(viewModel = viewModel)

        // 自动阅读 FAB 层（真机验收第二轮 P2）：全屏容器不消费点击、不拦截触控，
        // 仅 FAB 本体可交互；位置/吸附由 VM 的 fabDock 状态驱动（拖拽/贴边吸附/形变动画）
        val autoReadFabVisible = autoReadActive && !showAutoReadSheet && !showMenu &&
                !showReaderSettings && !showReaderUISettings && !showTextToolbar
        Box(modifier = Modifier.fillMaxSize()) {
            AutoReadFab(
                visible = autoReadFabVisible,
                paused = autoReadState.status == AutoReadStatus.PAUSED,
                dockState = fabDock,
                onDockStateChange = { viewModel.updateFabDock(it) },
                onClick = { viewModel.onAutoReadFabTap() },
                onLongClick = { viewModel.stopAutoReadByBack() },
                modifier = Modifier.fillMaxSize()
            )
        }

        // 首次引导 Tip（第二轮 P3；第三轮 P7 解耦修复）：显隐仅由 showAutoReadFabGuide 门控；
        // 首帧只落盘（mark，不动 UI 状态）——修复原实现"落盘即置否"导致 Tip ≤1 帧闪没的缺陷。
        // 落盘先行保证跨会话严格一次（R3 意图）；session 内未处置重显属预期（审查 N2，与搜索 Tip 一致）
        val autoReadFabGuideVisible = autoReadFabVisible && showAutoReadFabGuide
        LaunchedEffect(autoReadFabGuideVisible) {
            if (autoReadFabGuideVisible) viewModel.markAutoReadFabGuideShown()
        }
        if (autoReadFabGuideVisible) {
            AutoReadFabGuideTooltip(onDismiss = { viewModel.dismissAutoReadFabGuide() })
        }

        if (showAutoReadSheet) {
            AutoReadSettingsSheet(
                viewModel = viewModel,
                readerPreferences = readerPreferences,
                pageChars = autoReadPageChars,
                onDismiss = { viewModel.dismissAutoReadSheet() }
            )
        }

        AnimatedVisibility(
            visible = isLoading,
            enter = fadeIn(animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(300)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .consumeClick(),
                contentAlignment = Alignment.Center
            ) {
                BookCover(
                    coverImage = displayCover,
                    title = displayTitle.orEmpty(),
                    author = displayAuthor.orEmpty(),
                    isAudiobook = false,
                    modifier = Modifier
                        .fillMaxWidth(0.81f)
                        .fillMaxHeight(0.75f)
                        .padding(8.dp),
                    shape = RectangleShape,
                    contentScale = ContentScale.FillWidth,
                )
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 42.dp)
                )
            }
        }

        if (isError) {
            val errorState = uiState as? BookReaderUiState.Error
            val isDeleting by viewModel.isDeleting.collectAsStateWithLifecycle()
            val coroutineScope = rememberCoroutineScope()
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Outlined.ErrorOutline,
                    contentDescription = errorState?.message,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = errorState?.message ?: stringResource(R.string.book_file_not_found),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
                )
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                viewModel.removeCurrentBook()
                                navigateToHome(navController)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        enabled = !isDeleting
                    ) {
                        if (isDeleting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onError
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(stringResource(R.string.remove_from_library))
                    }
                    FilledTonalButton(
                        onClick = { navigateToHome(navController) }
                    ) {
                        Text(stringResource(R.string.ignore))
                    }
                }
            }
        }
    }

    SetFullScreen(context, showSystemBars = showSystemBars)
}
