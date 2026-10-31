package com.baogemusic.tv.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.baogemusic.tv.ui.theme.AppColors

/**
 * iOS 风格的图形化开关：一行文字 + 右侧胶囊开关，不再让整行文字自己当按钮。
 * 整行仍可聚焦 / 点击（遥控器习惯），但视觉上「开关状态」由右侧控件表达。
 */
@Composable
fun TvSwitchRow(
    label: String,
    checked: Boolean,
    modifier: Modifier = Modifier,
    onToggle: () -> Unit,
) {
    TvFocusable(onClick = onToggle, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                color = AppColors.TextPrimary,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            SwitchGraphic(checked = checked)
        }
    }
}

@Composable
private fun SwitchGraphic(checked: Boolean) {
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 24.dp else 3.dp,
        animationSpec = tween(durationMillis = 160),
        label = "switchThumb",
    )
    Box(
        modifier = Modifier
            .width(52.dp)
            .height(30.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(if (checked) AppColors.AccentGreen else Color(0xFF3A3F4B)),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(24.dp)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}

/**
 * 步进控件：− / 数值 / ＋。用于歌词字号（sp）与行距倍率（0.5–2.0，步进 0.1）。
 * [valueText] 由调用方格式化（如 "1.4×"）。
 */
@Composable
fun Stepper(
    label: String,
    valueText: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(label, color = AppColors.TextSecondary, fontSize = 13.sp)
        TvPill(
            onClick = onMinus,
            shape = CircleShape,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        ) { Text("−", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
        Text(
            valueText,
            color = AppColors.TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(52.dp),
        )
        TvPill(
            onClick = onPlus,
            shape = CircleShape,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        ) { Text("＋", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
    }
}
