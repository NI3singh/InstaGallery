// InstaGallery v2.0
package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlin.math.roundToInt

enum class RefreshableTab {
    HOME,
    REELS,
    SAVED,
    PROFILE
}

object TabRefreshManager {
    private val _refreshEvents = MutableSharedFlow<RefreshableTab>(extraBufferCapacity = 1)
    val refreshEvents: SharedFlow<RefreshableTab> = _refreshEvents.asSharedFlow()

    fun triggerRefresh(tab: RefreshableTab) {
        _refreshEvents.tryEmit(tab)
    }
}

private val RefreshRingGradient = Brush.sweepGradient(
    colors = listOf(
        Color(0xFFFEDA77),
        Color(0xFFF58529),
        Color(0xFFDD2A7B),
        Color(0xFF8134AF),
        Color(0xFFFEDA77)
    )
)

@Composable
fun InstaPullToRefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    canStartPull: () -> Boolean = { true },
    content: @Composable BoxScope.() -> Unit
) {
    val density = LocalDensity.current
    val thresholdPx = remember(density) { with(density) { 76.dp.toPx() } }
    val hiddenOffsetPx = remember(density) { with(density) { (-44).dp.toPx() } }
    val activeTopOffsetPx = remember(density) { with(density) { 14.dp.toPx() } }

    var pullDistancePx by remember { mutableFloatStateOf(0f) }

    val currentEnabled by rememberUpdatedState(enabled)
    val currentCanStartPull by rememberUpdatedState(canStartPull)
    val currentIsRefreshing by rememberUpdatedState(isRefreshing)
    val currentOnRefresh by rememberUpdatedState(onRefresh)

    LaunchedEffect(isRefreshing) {
        if (!isRefreshing) {
            pullDistancePx = 0f
        }
    }

    val nestedScrollConnection = remember(thresholdPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (pullDistancePx > 0f && available.y < 0f) {
                    val consumedY = maxOf(available.y, -pullDistancePx)
                    pullDistancePx = (pullDistancePx + consumedY).coerceAtLeast(0f)
                    return Offset(0f, consumedY)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (currentIsRefreshing || !currentEnabled || !currentCanStartPull()) return Offset.Zero
                // Only start pulling down when the child did not consume any vertical scroll
                // and the finger is dragging strictly downward (available.y > 0f)
                if (source == NestedScrollSource.Drag && available.y > 0f && consumed.y == 0f) {
                    pullDistancePx = (pullDistancePx + available.y * 0.45f).coerceAtMost(thresholdPx * 1.4f)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (pullDistancePx > 0f) {
                    val triggered = pullDistancePx >= thresholdPx && !currentIsRefreshing
                    pullDistancePx = 0f
                    if (triggered) {
                        currentOnRefresh()
                    }
                    // Do NOT consume upward fling velocity so VerticalPager always snaps cleanly!
                }
                return Velocity.Zero
            }
        }
    }

    val pullProgress = (pullDistancePx / thresholdPx).coerceIn(0f, 1f)
    val targetProgress = if (isRefreshing) 1f else pullProgress

    val animatedProgress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = spring(stiffness = 420f),
        label = "pull_refresh_progress"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "refresh_spin")
    val spinDegrees by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 720, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "refresh_spin_degrees"
    )

    Box(
        modifier = modifier.nestedScroll(nestedScrollConnection)
    ) {
        content()

        if (animatedProgress > 0.02f || isRefreshing) {
            val currentOffsetY = hiddenOffsetPx + (activeTopOffsetPx - hiddenOffsetPx) * animatedProgress
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .zIndex(20f)
                    .offset { IntOffset(0, currentOffsetY.roundToInt()) }
                    .scale(animatedProgress.coerceIn(0.65f, 1f))
                    .alpha(animatedProgress.coerceIn(0f, 1f))
                    .size(36.dp)
                    .shadow(6.dp, CircleShape)
                    .clip(CircleShape)
                    .background(Color(0xFF18181E))
                    .border(1.dp, Color(0xFF2E2E36), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(20.dp)) {
                    val rotation = if (isRefreshing) spinDegrees else (pullProgress * 280f)
                    val sweep = if (isRefreshing) 275f else (60f + pullProgress * 220f)
                    rotate(degrees = rotation) {
                        drawArc(
                            brush = RefreshRingGradient,
                            startAngle = -90f,
                            sweepAngle = sweep,
                            useCenter = false,
                            style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                }
            }
        }
    }
}
