package com.wxn.reader.presentation.mainReader.autoread

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wxn.bookread.data.model.preference.ReaderPreferences
import com.wxn.reader.R
import com.wxn.reader.presentation.mainReader.MainReadViewModel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 自动阅读设置弹窗（方案 §7.4）：速度滑杆（字/分钟，实时生效）+ 翻页方式切换（双列开启时滚动模式
 * 禁用置灰）+ 关闭自动翻页。打开期间自动阅读保持暂停（由 onAutoReadFabTap 的 pauseByOverlay 保证）。
 *
 * v2（仲裁方案 §3.2/T4）：呈现方式为 VM 会话内存态（[MainReadViewModel.autoReadMode]），不落盘——
 * 进入时由（手动翻页模式+双列）派生初始化，会话内二选一仅改内存，退出随会话消失。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoReadSettingsSheet(
    viewModel: MainReadViewModel,
    readerPreferences: ReaderPreferences,
    pageChars: Int,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val autoReadMode by viewModel.autoReadMode.collectAsStateWithLifecycle()   //会话内存态（v2，不落盘）
    var speed by remember(readerPreferences.autoReadSpeed) {
        mutableFloatStateOf(readerPreferences.autoReadSpeed.toFloat())
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(stringResource(R.string.auto_read_speed), style = MaterialTheme.typography.titleMedium)

            Slider(
                value = speed,
                onValueChange = {
                    speed = it
                    scope.launch { viewModel.updateAutoReadSpeed(it.roundToInt()) }   //suspend：协程包装实时落盘
                },
                valueRange = ReaderPreferences.AUTO_READ_SPEED_MIN.toFloat()..ReaderPreferences.AUTO_READ_SPEED_MAX.toFloat()  //2026-09-08 方案A：50~3000 字/分（上限对标竞品天花板）
            )
            val charsPerMin = speed.roundToInt()
            val estSeconds = if (pageChars > 0) (pageChars * 60 / charsPerMin).coerceAtLeast(1) else 0  //钳 ≥1：极端小页×高速不出 "~0 s per page"
            Text(
                text = "$charsPerMin " + stringResource(R.string.auto_read_chars_per_min) +
                        " · " + stringResource(R.string.auto_read_est_page_seconds, estSeconds),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 自动阅读呈现方式二选一：自动滚动=连续滚动视图；覆盖翻页=翻页视图+下一页自顶部向下覆盖。
            // 双列开启时"自动滚动"置灰（与连续滚动互斥，R-B）。选中态读 VM 会话内存态
            val isDualCol = readerPreferences.columns == 2
            Text(stringResource(R.string.auto_read_mode), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    0 to stringResource(R.string.auto_read_mode_scroll),
                    1 to stringResource(R.string.auto_read_mode_cover),
                ).forEach { (id, label) ->
                    val isSelected = autoReadMode == id
                    val isDisabled = (id == 0 && isDualCol)
                    FilledTonalButton(
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            contentColor = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        ),
                        enabled = !isDisabled,
                        onClick = { viewModel.updateAutoReadMode(id) }   //会话内存切换，即时生效；不落盘（v2）
                    ) {
                        Text(text = label, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Button(
                onClick = { viewModel.stopAutoReadFromSheet() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Text(stringResource(R.string.close_auto_reading), color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
    }
}
