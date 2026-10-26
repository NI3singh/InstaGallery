// InstaGallery v1.0
package com.example.ui.screens.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.ui.components.LivingAuroraBackground
import com.example.ui.theme.GrandHotelFont
import com.example.ui.theme.IgBlack
import com.example.ui.theme.IgStoryGradient
import com.example.ui.theme.InterFont
import kotlinx.coroutines.delay
import java.io.File
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun FaceScanLoadingScreen(
    viewModel: OnboardingViewModel,
    onScanCompleted: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.startInitialScanIfNeeded()
    }

    // Automatically transition to ProfileSetupScreen when scan completes
    LaunchedEffect(uiState.isScanFinished) {
        if (uiState.isScanFinished) {
            delay(650)
            onScanCompleted()
        }
    }

    val processed = uiState.processedCount
    val total = uiState.totalMediaCount
    val peopleCount = uiState.profilesCreated
    val rawProgress = if (uiState.isScanFinished) {
        1f
    } else if (total > 0) {
        (processed.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    val progress by animateFloatAsState(
        targetValue = rawProgress,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "scan_progress"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "stitch_loading_transition")

    // Infinite subtle breathing glow (4s ease-in-out infinite)
    val glowScale by infiniteTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_scale"
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.16f,
        targetValue = 0.28f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    // Rotating shimmer scan effect (continuous 360 deg rotation)
    val shimmerRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_rotation"
    )

    // Scanning light beam across the front glass card (-56dp to +64dp)
    val scanBeamOffset by infiniteTransition.animateFloat(
        initialValue = -56f,
        targetValue = 64f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scan_beam_offset"
    )
    val scanBeamAlpha by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scan_beam_alpha"
    )

    // Subtle floating motion for center glassmorphic cards
    val cardsFloatOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cards_float_offset"
    )

    // Active micro-bar pulse
    val activeBarAlpha by infiniteTransition.animateFloat(
        initialValue = 0.75f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "active_bar_alpha"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(IgBlack)
            .testTag("face_scan_loading_screen")
    ) {
        LivingAuroraBackground(
            primaryOrbColor = Color(0xFF8134AF),
            secondaryOrbColor = Color(0xFFDD2A7B),
            tertiaryOrbColor = Color(0xFFF58529),
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. Top Subtle Wordmark
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "InstaGallery",
                    fontSize = 30.sp,
                    fontFamily = GrandHotelFont,
                    color = Color.White.copy(alpha = 0.85f),
                    letterSpacing = 0.5.sp
                )
            }

            // 2. Centerpiece Hero Animation & Radial Progress Halo
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    contentAlignment = Alignment.Center
                ) {
                    // Ambient Breathing Glow Behind Ring
                    Canvas(
                        modifier = Modifier
                            .size(240.dp)
                            .scale(glowScale)
                    ) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFFDD2A7B).copy(alpha = glowAlpha),
                                    Color(0xFF8134AF).copy(alpha = glowAlpha * 0.85f),
                                    Color(0xFFFEDA77).copy(alpha = glowAlpha * 0.35f),
                                    Color.Transparent
                                ),
                                center = center,
                                radius = size.minDimension / 2f
                            ),
                            radius = size.minDimension / 2f,
                            center = center
                        )
                    }

                    // 188.dp Circular Container
                    Box(
                        modifier = Modifier.size(188.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Radial Progress Halo + Rotating Shimmer Scan Effect
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("radial_progress_canvas")
                        ) {
                            val strokeWidthTrack = 2.dp.toPx()
                            val strokeWidthArc = 3.5.dp.toPx()
                            val radius = 88.dp.toPx()
                            val arcTopLeft = Offset(center.x - radius, center.y - radius)
                            val arcSize = Size(radius * 2f, radius * 2f)

                            // Background Track: 2dp subtle dark gray dashed ring (#222222)
                            drawCircle(
                                color = Color(0xFF222222),
                                radius = radius,
                                center = center,
                                style = Stroke(
                                    width = strokeWidthTrack,
                                    cap = StrokeCap.Round,
                                    pathEffect = PathEffect.dashPathEffect(
                                        intervals = floatArrayOf(4.dp.toPx(), 5.dp.toPx()),
                                        phase = 0f
                                    )
                                )
                            )

                            // Subtle Rotating Shimmer Scan Halo along the track
                            rotate(degrees = shimmerRotation, pivot = center) {
                                drawArc(
                                    brush = Brush.sweepGradient(
                                        0.0f to Color.Transparent,
                                        0.75f to Color.Transparent,
                                        0.92f to Color(0xFFDD2A7B).copy(alpha = 0.22f),
                                        1.0f to Color(0xFFFEDA77).copy(alpha = 0.38f),
                                        center = center
                                    ),
                                    startAngle = 0f,
                                    sweepAngle = 360f,
                                    useCenter = false,
                                    topLeft = arcTopLeft,
                                    size = arcSize,
                                    style = Stroke(width = strokeWidthTrack, cap = StrokeCap.Round)
                                )
                            }

                            // Active Progress Arc: 3.5dp smooth sweeping arc with Brush.sweepGradient & StrokeCap.Round
                            val sweepAngle = (progress * 360f).coerceIn(0f, 360f)
                            if (sweepAngle > 0.5f) {
                                rotate(degrees = -90f, pivot = center) {
                                    drawArc(
                                        brush = Brush.sweepGradient(
                                            0.0f to Color(0xFF8134AF),
                                            0.32f to Color(0xFFDD2A7B),
                                            0.72f to Color(0xFFF58529),
                                            1.0f to Color(0xFFFEDA77),
                                            center = center
                                        ),
                                        startAngle = 0f,
                                        sweepAngle = sweepAngle,
                                        useCenter = false,
                                        topLeft = arcTopLeft,
                                        size = arcSize,
                                        style = Stroke(
                                            width = strokeWidthArc,
                                            cap = StrokeCap.Round
                                        )
                                    )
                                }

                                // Glowing White-Pink Leading Comet Dot at tip of arc
                                val endAngleRad = Math.toRadians((-90.0 + sweepAngle.toDouble()))
                                val tipX = center.x + radius * cos(endAngleRad).toFloat()
                                val tipY = center.y + radius * sin(endAngleRad).toFloat()
                                val tipOffset = Offset(tipX, tipY)

                                drawCircle(
                                    color = Color(0xFFDD2A7B).copy(alpha = 0.42f),
                                    radius = 9.dp.toPx(),
                                    center = tipOffset
                                )
                                drawCircle(
                                    color = Color.White.copy(alpha = 0.5f),
                                    radius = 6.dp.toPx(),
                                    center = tipOffset
                                )
                                drawCircle(
                                    color = Color.White,
                                    radius = 4.5.dp.toPx(),
                                    center = tipOffset
                                )
                                drawCircle(
                                    color = Color(0xFFFFF5FB),
                                    radius = 3.dp.toPx(),
                                    center = tipOffset
                                )
                            }
                        }

                        // Inside the Radial Ring: Minimalist Overlapping Glassmorphic Photo Cards
                        Box(
                            modifier = Modifier
                                .size(116.dp)
                                .offset(y = cardsFloatOffset.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            // Back Card (Tilted +6 deg)
                            Box(
                                modifier = Modifier
                                    .size(width = 82.dp, height = 100.dp)
                                    .offset(x = 12.dp, y = (-4).dp)
                                    .rotate(6f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xFF141414).copy(alpha = 0.90f))
                                    .border(
                                        width = 1.dp,
                                        color = Color.White.copy(alpha = 0.12f),
                                        shape = RoundedCornerShape(14.dp)
                                    )
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(62.dp)
                                        .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                                        .background(Color(0xFF1A1A1A).copy(alpha = 0.6f))
                                )
                            }

                            // Front Card (Tilted -6 deg)
                            Box(
                                modifier = Modifier
                                    .size(width = 86.dp, height = 104.dp)
                                    .offset(x = (-4).dp)
                                    .rotate(-6f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xFF161616).copy(alpha = 0.95f))
                                    .border(
                                        width = 1.dp,
                                        color = Color.White.copy(alpha = 0.18f),
                                        shape = RoundedCornerShape(14.dp)
                                    )
                            ) {
                                // Diagonal Glass Reflection
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(
                                                    Color.White.copy(alpha = 0.08f),
                                                    Color.Transparent
                                                )
                                            )
                                        )
                                )

                                // Soft Horizontal Gradient Light-Beam Scanning Across Card
                                Box(
                                    modifier = Modifier
                                        .width(130.dp)
                                        .height(28.dp)
                                        .align(Alignment.Center)
                                        .offset(y = scanBeamOffset.dp)
                                        .rotate(15f)
                                        .alpha(scanBeamAlpha)
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(
                                                    Color.Transparent,
                                                    Color(0xFFDD2A7B).copy(alpha = 0.35f),
                                                    Color.White.copy(alpha = 0.80f),
                                                    Color(0xFFDD2A7B).copy(alpha = 0.35f),
                                                    Color.Transparent
                                                )
                                            )
                                        )
                                )

                                // Front Card Inner Content
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(10.dp),
                                    verticalArrangement = Arrangement.SpaceBetween,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    // Top Camera Status Indicator Dot & Pill
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 2.dp, vertical = 2.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFFDD2A7B).copy(alpha = 0.85f))
                                        )
                                        Box(
                                            modifier = Modifier
                                                .width(16.dp)
                                                .height(4.dp)
                                                .clip(RoundedCornerShape(50))
                                                .background(Color.White.copy(alpha = 0.20f))
                                        )
                                    }

                                    // Centered Minimalist White Instagram Gallery-Camera Vector Outline
                                    Canvas(
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        val sx = size.width / 24f
                                        val sy = size.height / 24f
                                        val strokePx = 1.8f * sx

                                        // Outer rounded rectangle: x=2.5, y=4, w=19, h=16, rx=4.5
                                        drawRoundRect(
                                            color = Color.White,
                                            topLeft = Offset(2.5f * sx, 4f * sy),
                                            size = Size(19f * sx, 16f * sy),
                                            cornerRadius = CornerRadius(4.5f * sx, 4.5f * sy),
                                            style = Stroke(width = strokePx, cap = StrokeCap.Round)
                                        )

                                        // Center lens circle: cx=12, cy=12, r=4.2
                                        drawCircle(
                                            color = Color.White,
                                            radius = 4.2f * sx,
                                            center = Offset(12f * sx, 12f * sy),
                                            style = Stroke(width = strokePx)
                                        )

                                        // Inner mountain path: M10 13.5 l1.5 -1.5 2.5 2.5
                                        val mountainPath = Path().apply {
                                            moveTo(10f * sx, 13.5f * sy)
                                            lineTo(11.5f * sx, 12f * sy)
                                            lineTo(14f * sx, 14.5f * sy)
                                        }
                                        drawPath(
                                            path = mountainPath,
                                            color = Color.White,
                                            style = Stroke(
                                                width = 1.4f * sx,
                                                cap = StrokeCap.Round,
                                                join = StrokeJoin.Round
                                            )
                                        )

                                        // Top-right indicator dot: cx=17.2, cy=7.2, r=0.9
                                        drawCircle(
                                            color = Color.White,
                                            radius = 0.9f * sx,
                                            center = Offset(17.2f * sx, 7.2f * sy)
                                        )
                                    }

                                    // Bottom Miniature Photo Card Label Lines
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 2.dp, vertical = 2.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                        horizontalAlignment = Alignment.Start
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .width(36.dp)
                                                .height(6.dp)
                                                .clip(RoundedCornerShape(50))
                                                .background(Color.White.copy(alpha = 0.30f))
                                        )
                                        Box(
                                            modifier = Modifier
                                                .width(20.dp)
                                                .height(4.dp)
                                                .clip(RoundedCornerShape(50))
                                                .background(Color.White.copy(alpha = 0.15f))
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 3. Live Discovered People Pill (Directly below radial ring, 24dp gap)
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFF121212))
                        .border(1.dp, Color(0xFF262626), RoundedCornerShape(50))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .testTag("discovered_people_pill"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 3 Tiny Overlapping 23dp Circular Face Avatars Wrapped in 1dp Sunset Gradient Rings
                    val previewPeople = uiState.allDetectedPeople.take(3)
                    val avatarBgColors = listOf(
                        Color(0xFF202024),
                        Color(0xFF2A1E28),
                        Color(0xFF1E2430)
                    )
                    val avatarTintColors = listOf(
                        Color(0xFFD4D4D8),
                        Color(0xFFFBCFE8),
                        Color(0xFFC7D2FE)
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy((-6).dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (index in 0 until 3) {
                            val personCard = previewPeople.getOrNull(index)
                            val cropPath = personCard?.person?.avatarCropPath.orEmpty()
                            Box(
                                modifier = Modifier
                                    .size(23.dp)
                                    .clip(CircleShape)
                                    .background(IgStoryGradient)
                                    .padding(1.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                        .background(avatarBgColors[index]),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (cropPath.isNotBlank() && File(cropPath).exists()) {
                                        AsyncImage(
                                            model = File(cropPath),
                                            contentDescription = personCard?.person?.name,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(CircleShape)
                                        )
                                    } else {
                                        // Clean silhouette vector matching Stitch SVG
                                        Canvas(modifier = Modifier.size(14.dp)) {
                                            val w = size.width
                                            val h = size.height
                                            drawCircle(
                                                color = avatarTintColors[index],
                                                radius = w * 0.20f,
                                                center = Offset(w * 0.5f, h * 0.34f)
                                            )
                                            drawArc(
                                                color = avatarTintColors[index],
                                                startAngle = 180f,
                                                sweepAngle = 180f,
                                                useCenter = true,
                                                topLeft = Offset(w * 0.16f, h * 0.58f),
                                                size = Size(w * 0.68f, h * 0.52f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    AnimatedContent(
                        targetState = peopleCount,
                        transitionSpec = {
                            (slideInVertically { it } + fadeIn(tween(220))) togetherWith
                                (slideOutVertically { -it } + fadeOut(tween(180)))
                        },
                        label = "profiles_created_odometer"
                    ) { count ->
                        Text(
                            text = "$count profiles created",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = InterFont,
                            color = Color(0xFFE0E0E0),
                            modifier = Modifier.padding(end = 2.dp)
                        )
                    }
                }
            }

            // 4. Minimalist Typography & 28-Segment Vertical Capsule Progress Bar (Bottom section)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Large Clean Percentage Readout
                Text(
                    text = "${(progress * 100).toInt()}%",
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Light,
                    fontFamily = InterFont,
                    color = Color.White,
                    letterSpacing = (-1).sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("progress_percentage_text")
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Primary Status Text
                Text(
                    text = "Organizing your memories...",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = InterFont,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Secondary Photo Counter Text
                Text(
                    text = "$processed of $total photos",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    fontFamily = InterFont,
                    color = Color(0xFF737373),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("photo_counter_text")
                )

                Spacer(modifier = Modifier.height(20.dp))

                // 28-Segment Vertical Capsule Progress Bar
                val litSegments = (progress * 28).toInt().coerceIn(0, 28)
                Row(
                    modifier = Modifier
                        .width(220.dp)
                        .testTag("capsule_progress_bar"),
                    horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (index in 0 until 28) {
                        val isLit = index < litSegments
                        val isLeadingLit = index == litSegments - 1
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(10.dp)
                                .alpha(if (isLeadingLit) activeBarAlpha else 1f)
                                .clip(RoundedCornerShape(50))
                                .then(
                                    if (isLit) {
                                        Modifier.background(IgStoryGradient)
                                    } else {
                                        Modifier.background(Color(0xFF262626))
                                    }
                                )
                        )
                    }
                }
            }
        }
    }
}
