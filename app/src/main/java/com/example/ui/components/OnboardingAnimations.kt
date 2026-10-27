// InstaGallery v1.0
package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.HeartTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val StorySweepColors = listOf(
    Color(0xFFFEDA77),
    Color(0xFFF58529),
    Color(0xFFDD2A7B),
    Color(0xFF8134AF),
    Color(0xFF515BD4),
    Color(0xFFFEDA77)
)

data class HeartBurstInstance(
    val id: Long,
    val tapOffset: Offset,
    val rotationDeg: Float
)

private fun buildCenteredSmoothHeartPath(baseSize: Float): Path {
    val u = baseSize / 24f
    fun x(v: Float) = (v - 12f) * u
    fun y(v: Float) = (v - 12.175f) * u

    return Path().apply {
        moveTo(x(12f), y(20.6f))
        cubicTo(x(12f), y(20.6f), x(2f), y(14.5f), x(2f), y(8.4f))
        cubicTo(x(2f), y(5.2f), x(4.5f), y(3.0f), x(7.5f), y(3.0f))
        cubicTo(x(9.4f), y(3.0f), x(11.1f), y(4.0f), x(12f), y(5.5f))
        cubicTo(x(12.9f), y(4.0f), x(14.6f), y(3.0f), x(16.5f), y(3.0f))
        cubicTo(x(19.5f), y(3.0f), x(22f), y(5.2f), x(22f), y(8.4f))
        cubicTo(x(22f), y(14.5f), x(12f), y(20.6f), x(12f), y(20.6f))
        close()
    }
}

@Composable
fun TapHeartBurstOverlay(
    bursts: List<HeartBurstInstance>,
    heartTheme: HeartTheme,
    onBurstFinished: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    if (bursts.isEmpty()) return

    val progressMap = remember { mutableStateMapOf<Long, Float>() }

    bursts.forEach { burst ->
        key(burst.id) {
            LaunchedEffect(burst.id) {
                val anim = Animatable(0f)
                anim.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 750, easing = LinearEasing)
                ) {
                    progressMap[burst.id] = value
                }
                progressMap.remove(burst.id)
                onBurstFinished(burst.id)
            }
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val baseSize = 86.dp.toPx()
        val half = baseSize / 2f
        val maxDriftPx = 52.dp.toPx()
        val shadowOffsetY = 4.dp.toPx()
        val maxSparkleRadius = 4.dp.toPx()
        val heartPath = buildCenteredSmoothHeartPath(baseSize)

        bursts.forEach { burst ->
            val t = (progressMap[burst.id] ?: 0f).coerceIn(0f, 1f)
            if (t <= 0f || t >= 1f) return@forEach

            val scale = when {
                t <= 0.24f -> (t / 0.24f) * 1.30f
                t <= 0.42f -> 1.30f + ((t - 0.24f) / 0.18f) * (0.96f - 1.30f)
                t <= 0.60f -> 0.96f + ((t - 0.42f) / 0.18f) * (1.06f - 0.96f)
                else -> 1.06f + ((t - 0.60f) / 0.40f) * (1.16f - 1.06f)
            }

            val driftY = if (t < 0.45f) {
                0f
            } else {
                -((t - 0.45f) / 0.55f) * maxDriftPx
            }

            val alpha = if (t <= 0.70f) {
                1f
            } else {
                (1f - ((t - 0.70f) / 0.30f)).coerceIn(0f, 1f)
            }

            val centerX = burst.tapOffset.x
            val centerY = burst.tapOffset.y + driftY

            // Shockwave Micro-Sparkles (t in 0.08f..0.55f): 6 small circles at 60° angles
            if (t in 0.08f..0.55f) {
                val sparkleProgress = ((t - 0.08f) / 0.47f).coerceIn(0f, 1f)
                val sparkleAlpha = (1f - sparkleProgress) * alpha
                val sparkleRadius = (maxSparkleRadius * (1f - 0.55f * sparkleProgress)).coerceAtLeast(1f)
                val ringDist = baseSize * (0.44f + 0.46f * sparkleProgress)
                for (i in 0 until 6) {
                    val angleRad = (i * 60.0) * PI / 180.0
                    val sx = centerX + (cos(angleRad) * ringDist).toFloat()
                    val sy = burst.tapOffset.y + (sin(angleRad) * ringDist).toFloat()
                    drawCircle(
                        color = heartTheme.primaryColor.copy(alpha = sparkleAlpha),
                        radius = sparkleRadius,
                        center = Offset(sx, sy)
                    )
                }
            }

            // Smooth Bezier Heart Path with contour-hugging drop shadow (never a square box)
            withTransform({
                translate(left = centerX, top = centerY)
                rotate(degrees = burst.rotationDeg, pivot = Offset.Zero)
                scale(scaleX = scale, scaleY = scale, pivot = Offset.Zero)
            }) {
                // Contour-hugging drop shadow (scaled 1.06f, shifted down by 4.dp)
                withTransform({
                    translate(left = 0f, top = shadowOffsetY)
                    scale(scaleX = 1.06f, scaleY = 1.06f, pivot = Offset.Zero)
                }) {
                    drawPath(
                        path = heartPath,
                        color = Color.Black.copy(alpha = 0.28f * alpha)
                    )
                }

                // Main gradient-filled smooth bezier heart
                drawPath(
                    path = heartPath,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            heartTheme.primaryColor.copy(alpha = alpha),
                            heartTheme.secondaryColor.copy(alpha = alpha)
                        ),
                        start = Offset(-half, -half),
                        end = Offset(half, half)
                    )
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeartThemePickerSheet(
    currentTheme: HeartTheme,
    onSelectTheme: (HeartTheme) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val themes = HeartTheme.entries

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF121216)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp)
        ) {
            Text(
                text = "Heart Reaction Color",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Tip: Long-press any heart icon to change this anytime",
                fontSize = 12.sp,
                color = Color(0xFFA0A0A8)
            )
            Spacer(modifier = Modifier.height(18.dp))

            // 3-column grid (2 rows of 3) of the 6 HeartTheme options
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                themes.chunked(3).forEach { rowThemes ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        rowThemes.forEach { theme ->
                            val isSelected = theme == currentTheme
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        if (isSelected) Color(0xFF1C222E) else Color(0xFF1B1B21)
                                    )
                                    .border(
                                        width = if (isSelected) 1.8.dp else 1.dp,
                                        color = if (isSelected) Color(0xFF0095F6) else Color(0xFF2A2A32),
                                        shape = RoundedCornerShape(14.dp)
                                    )
                                    .bounceClick { onSelectTheme(theme) }
                                    .padding(vertical = 14.dp, horizontal = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .size(18.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF0095F6)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = Color.White,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Canvas(modifier = Modifier.size(34.dp)) {
                                        val s = size.minDimension
                                        val half = s / 2f
                                        val path = buildCenteredSmoothHeartPath(s)
                                        withTransform({
                                            translate(left = center.x, top = center.y)
                                        }) {
                                            drawPath(
                                                path = path,
                                                brush = Brush.linearGradient(
                                                    colors = listOf(theme.primaryColor, theme.secondaryColor),
                                                    start = Offset(-half, -half),
                                                    end = Offset(half, half)
                                                )
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = theme.label,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                        color = if (isSelected) Color.White else Color(0xFFD4D4DC),
                                        textAlign = TextAlign.Center,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LivingAuroraBackground(
    primaryOrbColor: Color = Color(0xFF8134AF),
    secondaryOrbColor: Color = Color(0xFFDD2A7B),
    tertiaryOrbColor: Color = Color(0xFFF58529),
    modifier: Modifier = Modifier
) {
    val animatedPrimary by animateColorAsState(
        targetValue = primaryOrbColor,
        animationSpec = tween(durationMillis = 650),
        label = "aurora_primary_color"
    )
    val animatedSecondary by animateColorAsState(
        targetValue = secondaryOrbColor,
        animationSpec = tween(durationMillis = 650),
        label = "aurora_secondary_color"
    )
    val animatedTertiary by animateColorAsState(
        targetValue = tertiaryOrbColor,
        animationSpec = tween(durationMillis = 650),
        label = "aurora_tertiary_color"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "living_aurora_transition")
    val phase1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 11000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "aurora_phase_1"
    )
    val phase2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 15000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "aurora_phase_2"
    )

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF000000))
    ) {
        val w = size.width
        val h = size.height
        val minDim = size.minDimension.coerceAtLeast(1f)

        // Orb 1: Top-Left / Center figure-eight drift
        val orb1Radius = minDim * 0.72f
        val orb1Center = Offset(
            x = w * (0.34f + 0.16f * sin(phase1)),
            y = h * (0.28f + 0.10f * sin(2f * phase1))
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    animatedPrimary.copy(alpha = 0.18f),
                    animatedPrimary.copy(alpha = 0.08f),
                    Color.Transparent
                ),
                center = orb1Center,
                radius = orb1Radius
            ),
            radius = orb1Radius,
            center = orb1Center
        )

        // Orb 2: Center-Right figure-eight drift
        val orb2Radius = minDim * 0.65f
        val orb2Center = Offset(
            x = w * (0.68f + 0.15f * cos(phase2)),
            y = h * (0.48f + 0.12f * sin(phase1 + phase2 * 0.5f))
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    animatedSecondary.copy(alpha = 0.15f),
                    animatedSecondary.copy(alpha = 0.06f),
                    Color.Transparent
                ),
                center = orb2Center,
                radius = orb2Radius
            ),
            radius = orb2Radius,
            center = orb2Center
        )

        // Orb 3: Bottom-Center figure-eight drift
        val orb3Radius = minDim * 0.78f
        val orb3Center = Offset(
            x = w * (0.48f + 0.18f * sin(phase2)),
            y = h * (0.74f + 0.09f * cos(2f * phase2))
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    animatedTertiary.copy(alpha = 0.12f),
                    animatedTertiary.copy(alpha = 0.05f),
                    Color.Transparent
                ),
                center = orb3Center,
                radius = orb3Radius
            ),
            radius = orb3Radius,
            center = orb3Center
        )
    }
}

fun Modifier.bounceClick(
    enabled: Boolean = true,
    hapticType: HapticFeedbackType = HapticFeedbackType.TextHandleMove,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current

    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.94f else 1f,
        animationSpec = spring(
            dampingRatio = 0.55f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "bounce_click_scale"
    )

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            onClick = {
                haptic.performHapticFeedback(hapticType)
                onClick()
            }
        )
}

@Composable
fun RotatingStoryRingAvatar(
    modifier: Modifier = Modifier,
    avatarSize: Dp = 92.dp,
    ringWidth: Dp = 3.dp,
    innerCutoutWidth: Dp = 3.dp,
    innerCutoutColor: Color = Color(0xFF141418),
    isMuted: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "rotating_story_ring")
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "story_ring_angle"
    )

    Box(
        modifier = modifier.size(avatarSize),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
        ) {
            if (isMuted) {
                drawCircle(color = Color(0xFF452328))
            } else {
                withTransform({
                    rotate(degrees = angle, pivot = center)
                }) {
                    drawCircle(
                        brush = Brush.sweepGradient(
                            colors = StorySweepColors,
                            center = center
                        )
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(ringWidth)
                .clip(CircleShape)
                .background(innerCutoutColor)
                .padding(innerCutoutWidth)
                .clip(CircleShape),
            contentAlignment = Alignment.Center,
            content = content
        )
    }
}
