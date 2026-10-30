package com.baogemusic.tv.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.baogemusic.tv.ui.theme.AppColors

/**
 * A poster-style focusable card: square cover + title + optional subtitle.
 *
 * [compact] 用于列数多（6–8 列）的场景：缩小内边距与字号，避免卡片挤在一起互相遮挡。
 */
@Composable
fun MediaCard(
    title: String,
    picURL: String?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    compact: Boolean = false,
    onClick: () -> Unit,
) {
    val pad = if (compact) 5.dp else 8.dp
    val coverShape = RoundedCornerShape(if (compact) 6.dp else 8.dp)
    val titleSp = if (compact) 11.sp else 13.sp
    val subSp = if (compact) 9.sp else 11.sp
    val gap = if (compact) 3.dp else 6.dp
    TvFocusable(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.padding(pad)) {
            Artwork(
                picURL,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                shape = coverShape,
            )
            Box(Modifier.padding(top = gap)) {
                Text(
                    title,
                    color = AppColors.TextPrimary,
                    fontSize = titleSp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (subtitle != null) {
                Text(
                    subtitle,
                    color = AppColors.TextMuted,
                    fontSize = subSp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
