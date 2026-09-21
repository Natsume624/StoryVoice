package com.wxn.reader.presentation.mainReader.autoread

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.wxn.reader.R

/**
 * 自动阅读 FAB 首次引导 Tip（真机验收第二轮 P3；第三轮 P7 解耦）：视觉与交互照抄 SearchFabGuideTooltip——
 * Popup 锚定右下角（FAB 默认位）-100dp、focusable（点击外部/返回即收起并消费该次点击）。
 * 落盘/显隐不在此处：MainReadScreen 首帧调 markAutoReadFabGuideShown（只落盘）；
 * 用户处置经 onDismiss → dismissAutoReadFabGuide（置否+落盘）。
 */
@Composable
fun AutoReadFabGuideTooltip(
    onDismiss: () -> Unit,
) {
    Popup(
        alignment = Alignment.BottomEnd,
        offset = IntOffset(0, with(LocalDensity.current) { (-100).dp.roundToPx() }),
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
        ),
        onDismissRequest = onDismiss
    ) {
        Surface(
            modifier = Modifier
                .width(260.dp)
                .wrapContentHeight()
                .padding(end = 16.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.inverseSurface,
            shadowElevation = 8.dp,
            tonalElevation = 2.dp,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = stringResource(R.string.auto_read_fab_guide_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.auto_read_fab_guide_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.7f),
                )
            }
        }
    }
}
