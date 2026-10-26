// InstaGallery v1.0
package com.example.ui.components

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
