package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.util.Locale

@Composable
fun LiveRecordingWaveform(
    amplitudes: List<Float>,
    isRecording: Boolean,
    modifier: Modifier = Modifier,
    barHeight: Dp = 52.dp,
    activeColor: Color = MaterialTheme.colorScheme.secondary,
    idleColor: Color = MaterialTheme.colorScheme.outline
) {
    val barCount = amplitudes.size.coerceAtLeast(1)
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(barHeight)
    ) {
        val spacing = 4.dp.toPx()
        val totalSpacing = spacing * (barCount - 1)
        val barWidth = ((size.width - totalSpacing) / barCount).coerceAtLeast(2.dp.toPx())
        val centerY = size.height / 2f

        amplitudes.forEachIndexed { index, rawAmp ->
            val amp = if (isRecording) rawAmp.coerceIn(0.08f, 1f) else 0.10f
            val h = (size.height * amp).coerceAtLeast(4.dp.toPx())
            val x = index * (barWidth + spacing)
            val color = if (isRecording) {
                activeColor.copy(alpha = (0.45f + amp * 0.55f).coerceIn(0.45f, 1f))
            } else {
                idleColor.copy(alpha = 0.4f)
            }
            drawRoundRect(
                color = color,
                topLeft = Offset(x, centerY - h / 2f),
                size = Size(barWidth, h),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}

@Composable
fun SeekablePlaybackWaveform(
    amplitudes: List<Float>,
    progress: Float,
    isPlaying: Boolean,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    playedColor: Color = MaterialTheme.colorScheme.primary,
    unplayedColor: Color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = spring(stiffness = 500f),
        label = "waveformProgress"
    )
    val bars = amplitudes.ifEmpty { List(36) { 0.25f } }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(38.dp)
            .pointerInput(bars.size) {
                detectTapGestures { offset ->
                    val fraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                    onSeek(fraction)
                }
            }
    ) {
        val count = bars.size
        val spacing = 2.5.dp.toPx()
        val totalSpacing = spacing * (count - 1)
        val barWidth = ((size.width - totalSpacing) / count).coerceAtLeast(2.dp.toPx())
        val centerY = size.height / 2f
        val playHeadX = size.width * animatedProgress

        bars.forEachIndexed { index, amp ->
            val h = (size.height * amp.coerceIn(0.12f, 1f)).coerceAtLeast(4.dp.toPx())
            val x = index * (barWidth + spacing)
            val isPlayed = (x + barWidth / 2f) <= playHeadX && animatedProgress > 0f
            drawRoundRect(
                color = if (isPlayed) playedColor else unplayedColor,
                topLeft = Offset(x, centerY - h / 2f),
                size = Size(barWidth, h),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }

        if (isPlaying || animatedProgress > 0f) {
            drawCircle(
                color = playedColor,
                radius = 3.5.dp.toPx(),
                center = Offset(playHeadX.coerceIn(3.5.dp.toPx(), size.width - 3.5.dp.toPx()), centerY)
            )
        }
    }
}

fun formatDurationMs(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}

fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024L -> "$bytes B"
        bytes < 1024L * 1024L -> String.format(Locale.US, "%.1f KB", bytes / 1024f)
        else -> String.format(Locale.US, "%.2f MB", bytes / (1024f * 1024f))
    }
}
