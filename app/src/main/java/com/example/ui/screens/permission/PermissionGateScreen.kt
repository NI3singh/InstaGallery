// InstaGallery v1.0
package com.example.ui.screens.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.local.OnboardingPreferences
import com.example.ui.screens.onboarding.FaceScanLoadingScreen
import com.example.ui.screens.onboarding.OnboardingViewModel
import com.example.ui.theme.GrandHotelFont
import com.example.ui.theme.IgBlack
import com.example.ui.theme.PlusJakartaSansFont
import kotlinx.coroutines.delay

@Composable
fun PermissionGateScreen(
    onPermissionsGranted: () -> Unit,
    onboardingViewModel: OnboardingViewModel = viewModel(),
    onScanCompleted: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(hasAnyMediaPermission(context)) }

    val requiredPermissions = remember {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            )
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO
            )
            else -> arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
        }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results.values.any { it } || hasAnyMediaPermission(context)
        if (granted) {
            if (OnboardingPreferences.isInitialScanDone(context)) {
                onPermissionsGranted()
            } else {
                hasPermission = true
            }
        }
    }

    LaunchedEffect(Unit) {
        if (hasAnyMediaPermission(context)) {
            if (OnboardingPreferences.isInitialScanDone(context)) {
                onPermissionsGranted()
            } else {
                hasPermission = true
            }
        }
    }

    AnimatedContent(
        targetState = hasPermission,
        transitionSpec = {
            (fadeIn(animationSpec = tween(400, easing = FastOutSlowInEasing)) +
                scaleIn(
                    initialScale = 0.96f,
                    animationSpec = tween(400, easing = FastOutSlowInEasing)
                )) togetherWith fadeOut(animationSpec = tween(300))
        },
        label = "PermissionToRadialHaloTransition",
        modifier = Modifier
            .fillMaxSize()
            .background(IgBlack)
    ) { granted ->
        if (granted) {
            FaceScanLoadingScreen(
                viewModel = onboardingViewModel,
                onScanCompleted = {
                    if (onScanCompleted != null) {
                        onScanCompleted()
                    } else {
                        onPermissionsGranted()
                    }
                }
            )
        } else {
            StitchWelcomeOnboardingContent(
                onContinueClick = {
                    launcher.launch(requiredPermissions)
                }
            )
        }
    }
}

@Composable
private fun StitchWelcomeOnboardingContent(
    onContinueClick: () -> Unit
) {
    // 2-Stage + Text Entrance Choreography States
    // Stage 1 (0ms..650ms): Fade and scale in central ambient sunset glow and center card
    var stage1Started by remember { mutableStateOf(false) }
    // Stage 2 (350ms..950ms): Fan out left/right tilted cards and pop in floating Story-ring avatar bubbles
    var stage2Started by remember { mutableStateOf(false) }
    // Stage 3 (500ms..1100ms): Slide and fade in "InstaGallery" cursive title, "Your gallery, reimagined." headline, and "Continue with Gallery" pill button
    var stage3Started by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        stage1Started = true
        delay(350L)
        stage2Started = true
        delay(150L)
        stage3Started = true
    }

    // Stage 1 Animations (0ms..650ms)
    val centerCardScale by animateFloatAsState(
        targetValue = if (stage1Started) 1f else 0.78f,
        animationSpec = spring(
            dampingRatio = 0.75f,
            stiffness = Spring.StiffnessLow
        ),
        label = "centerCardScale"
    )
    val centerCardAlpha by animateFloatAsState(
        targetValue = if (stage1Started) 1f else 0f,
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "centerCardAlpha"
    )

    // Stage 2 Animations (350ms..950ms)
    val leftWingRotation by animateFloatAsState(
        targetValue = if (stage2Started) -12f else 0f,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "leftWingRotation"
    )
    val rightWingRotation by animateFloatAsState(
        targetValue = if (stage2Started) 12f else 0f,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "rightWingRotation"
    )
    val leftWingOffsetX by animateDpAsState(
        targetValue = if (stage2Started) (-54).dp else 0.dp,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "leftWingOffsetX"
    )
    val rightWingOffsetX by animateDpAsState(
        targetValue = if (stage2Started) 54.dp else 0.dp,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "rightWingOffsetX"
    )
    val wingAlpha by animateFloatAsState(
        targetValue = if (stage2Started) 1f else 0f,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "wingAlpha"
    )
    val bubbleScale by animateFloatAsState(
        targetValue = if (stage2Started) 1f else 0f,
        animationSpec = spring(
            dampingRatio = 0.68f,
            stiffness = Spring.StiffnessLow
        ),
        label = "bubbleScale"
    )

    // Stage 3 Animations (500ms..1100ms)
    val textAndCtaAlpha by animateFloatAsState(
        targetValue = if (stage3Started) 1f else 0f,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "textAndCtaAlpha"
    )
    val titleSlideY by animateDpAsState(
        targetValue = if (stage3Started) 0.dp else (-14).dp,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "titleSlideY"
    )
    val bottomSlideY by animateDpAsState(
        targetValue = if (stage3Started) 0.dp else 18.dp,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "bottomSlideY"
    )

    // Continuous subtle floating animation (-4.dp to +4.dp over 3000ms)
    val infiniteTransition = rememberInfiniteTransition(label = "cardFanFloat")
    val floatOffsetDp by infiniteTransition.animateFloat(
        initialValue = -4f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floatOffsetDp"
    )
    val density = LocalDensity.current
    val floatTranslationYPx = with(density) { floatOffsetDp.dp.toPx() }

    val sunsetBrush = remember {
        Brush.linearGradient(
            colors = listOf(
                Color(0xFFF58529),
                Color(0xFFDD2A7B),
                Color(0xFF8134AF)
            )
        )
    }

    val storyRingBrush = remember {
        Brush.linearGradient(
            colors = listOf(
                Color(0xFFFEDA77),
                Color(0xFFF58529),
                Color(0xFFDD2A7B),
                Color(0xFF8134AF)
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(IgBlack)
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 420.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. Top Wordmark with soft sunset ambient glow behind it
            Box(
                modifier = Modifier
                    .padding(top = 20.dp)
                    .offset(y = titleSlideY)
                    .alpha(textAndCtaAlpha),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(width = 200.dp, height = 80.dp)) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFDD2A7B).copy(alpha = 0.24f),
                                Color(0xFF8134AF).copy(alpha = 0.14f),
                                Color.Transparent
                            ),
                            radius = size.maxDimension * 0.55f
                        ),
                        radius = size.maxDimension * 0.55f,
                        center = center
                    )
                }

                Text(
                    text = "InstaGallery",
                    fontFamily = GrandHotelFont,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color.White,
                    letterSpacing = 0.5.sp
                )
            }

            // 2. Centerpiece Hero Fan + Headline & Subtitle Group
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Hero Card Fan Container (with continuous floating animation)
                Box(
                    modifier = Modifier
                        .size(width = 320.dp, height = 276.dp)
                        .graphicsLayer {
                            translationY = floatTranslationYPx
                        },
                    contentAlignment = Alignment.Center
                ) {
                    // Central Ambient Sunset Glow behind the cards
                    Canvas(
                        modifier = Modifier
                            .size(280.dp)
                            .graphicsLayer {
                                scaleX = centerCardScale
                                scaleY = centerCardScale
                                alpha = centerCardAlpha
                            }
                    ) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFFDD2A7B).copy(alpha = 0.25f),
                                    Color(0xFF8134AF).copy(alpha = 0.17f),
                                    Color(0xFFF58529).copy(alpha = 0.10f),
                                    Color.Transparent
                                ),
                                radius = size.minDimension * 0.52f
                            ),
                            radius = size.minDimension * 0.52f,
                            center = center
                        )
                    }

                    // Left Wing Card ("Goa Trip" Sunset Landscape)
                    LeftWingGoaCard(
                        modifier = Modifier
                            .offset(x = leftWingOffsetX, y = 8.dp)
                            .graphicsLayer {
                                rotationZ = leftWingRotation
                                alpha = wingAlpha
                                scaleX = 0.92f + (0.08f * wingAlpha)
                                scaleY = 0.92f + (0.08f * wingAlpha)
                            }
                    )

                    // Right Wing Card (Night Bokeh Video Reel Card)
                    RightWingReelCard(
                        modifier = Modifier
                            .offset(x = rightWingOffsetX, y = 8.dp)
                            .graphicsLayer {
                                rotationZ = rightWingRotation
                                alpha = wingAlpha
                                scaleX = 0.92f + (0.08f * wingAlpha)
                                scaleY = 0.92f + (0.08f * wingAlpha)
                            }
                    )

                    // Center Hero Card ("Oct 2026" / "Family Dinner")
                    CenterFamilyDinnerCard(
                        modifier = Modifier
                            .graphicsLayer {
                                scaleX = centerCardScale
                                scaleY = centerCardScale
                                alpha = centerCardAlpha
                            }
                    )

                    // Floating Story-Ring Avatar Bubble 1: @Papa (Top-Left)
                    FloatingStoryBubble(
                        handle = "@Papa",
                        isPapa = true,
                        storyRingBrush = storyRingBrush,
                        modifier = Modifier
                            .offset(x = (-74).dp, y = (-84).dp)
                            .graphicsLayer {
                                scaleX = bubbleScale
                                scaleY = bubbleScale
                                alpha = bubbleScale.coerceIn(0f, 1f)
                            }
                    )

                    // Floating Story-Ring Avatar Bubble 2: @Maa (Bottom-Right)
                    FloatingStoryBubble(
                        handle = "@Maa",
                        isPapa = false,
                        storyRingBrush = storyRingBrush,
                        modifier = Modifier
                            .offset(x = 76.dp, y = 82.dp)
                            .graphicsLayer {
                                scaleX = bubbleScale
                                scaleY = bubbleScale
                                alpha = bubbleScale.coerceIn(0f, 1f)
                            }
                    )
                }

                Spacer(modifier = Modifier.height(22.dp))

                // Headline & Subtitle
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .offset(y = bottomSlideY)
                        .alpha(textAndCtaAlpha)
                        .padding(horizontal = 8.dp)
                ) {
                    Text(
                        text = "Your gallery, reimagined.",
                        fontFamily = PlusJakartaSansFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp,
                        lineHeight = 30.sp,
                        letterSpacing = (-0.4).sp,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Photos, videos, and the people you love—\nautomatically organized into your personal\nfeed.",
                        fontFamily = PlusJakartaSansFont,
                        fontWeight = FontWeight.Normal,
                        fontSize = 13.5.sp,
                        lineHeight = 20.sp,
                        color = Color(0xFFA1A1AA),
                        textAlign = TextAlign.Center
                    )
                }
            }

            // 3. Bottom CTA Pill Button + Subtle Caption
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
                    .offset(y = bottomSlideY)
                    .alpha(textAndCtaAlpha)
            ) {
                // Pill Button (52.dp Solid Pure White background, Pure Black 15.sp Bold text)
                val interactionSource = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = onContinueClick
                        )
                        .testTag("grant_permission_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Continue with Gallery",
                            fontFamily = PlusJakartaSansFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.Black,
                            letterSpacing = (-0.1).sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Continue",
                            tint = Color.Black,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Requires access to your local photos and videos",
                    fontFamily = PlusJakartaSansFont,
                    fontWeight = FontWeight.Normal,
                    fontSize = 11.5.sp,
                    color = Color(0xFF686870),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun LeftWingGoaCard(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(width = 128.dp, height = 172.dp)
            .shadow(16.dp, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(18.dp))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Warm sunset sky gradient
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF4B1D3F),
                        Color(0xFF8B2654),
                        Color(0xFFC85A48),
                        Color(0xFFD99B52)
                    )
                )
            )

            // Setting sun orb
            drawCircle(
                color = Color(0xFFF6D389).copy(alpha = 0.78f),
                radius = size.width * 0.20f,
                center = Offset(size.width * 0.66f, size.height * 0.52f)
            )

            // Back mountain silhouette
            val backHills = Path().apply {
                moveTo(0f, size.height * 0.72f)
                lineTo(size.width * 0.42f, size.height * 0.56f)
                lineTo(size.width, size.height * 0.68f)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(path = backHills, color = Color(0xFF231426))

            // Foreground hill silhouette
            val frontHills = Path().apply {
                moveTo(0f, size.height * 0.78f)
                lineTo(size.width * 0.65f, size.height * 0.65f)
                lineTo(size.width, size.height * 0.74f)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(path = frontHills, color = Color(0xFF150D1A))
        }

        // Bottom location pill ("Goa Trip")
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 8.dp, bottom = 8.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.Black.copy(alpha = 0.55f))
                .border(0.5.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(50))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.LocationOn,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(10.dp)
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = "Goa Trip",
                fontFamily = PlusJakartaSansFont,
                fontWeight = FontWeight.Medium,
                fontSize = 9.5.sp,
                color = Color.White.copy(alpha = 0.9f)
            )
        }
    }
}

@Composable
private fun RightWingReelCard(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(width = 128.dp, height = 172.dp)
            .shadow(16.dp, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(18.dp))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Deep indigo-violet night gradient
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF171332),
                        Color(0xFF281948),
                        Color(0xFF391944),
                        Color(0xFF161124)
                    )
                )
            )

            // Soft magenta/violet bokeh glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF9D2A7B).copy(alpha = 0.55f),
                        Color.Transparent
                    ),
                    radius = size.width * 0.45f,
                    center = Offset(size.width * 0.62f, size.height * 0.45f)
                ),
                radius = size.width * 0.45f,
                center = Offset(size.width * 0.62f, size.height * 0.45f)
            )

            // Soft subtle bokeh circles
            drawCircle(
                color = Color.White.copy(alpha = 0.08f),
                radius = size.width * 0.09f,
                center = Offset(size.width * 0.44f, size.height * 0.60f)
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.06f),
                radius = size.width * 0.13f,
                center = Offset(size.width * 0.42f, size.height * 0.78f)
            )
        }

        // Top-right play badge
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 10.dp, end = 10.dp)
                .size(22.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.55f))
                .border(0.5.dp, Color.White.copy(alpha = 0.18f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(13.dp)
            )
        }
    }
}

@Composable
private fun CenterFamilyDinnerCard(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(width = 160.dp, height = 206.dp)
            .shadow(24.dp, RoundedCornerShape(22.dp))
            .clip(RoundedCornerShape(22.dp))
            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(22.dp))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Warm portrait background gradient
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF4A2B1D),
                        Color(0xFF6B3D28),
                        Color(0xFF4E2A1B),
                        Color(0xFF1A100C)
                    )
                )
            )

            // Subtle warm radial glow in center
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFD98A4E).copy(alpha = 0.35f),
                        Color.Transparent
                    ),
                    radius = size.width * 0.55f,
                    center = Offset(size.width * 0.45f, size.height * 0.38f)
                ),
                radius = size.width * 0.55f,
                center = Offset(size.width * 0.45f, size.height * 0.38f)
            )

            // Warm bokeh dots in upper right
            drawCircle(
                color = Color(0xFFD99B26).copy(alpha = 0.65f),
                radius = 3.dp.toPx(),
                center = Offset(size.width * 0.84f, size.height * 0.21f)
            )
            drawCircle(
                color = Color(0xFFC84B31).copy(alpha = 0.65f),
                radius = 4.5.dp.toPx(),
                center = Offset(size.width * 0.88f, size.height * 0.45f)
            )

            // Left Person (Father)
            // Torso
            drawRoundRect(
                color = Color(0xFF162636),
                topLeft = Offset(size.width * 0.16f, size.height * 0.48f),
                size = Size(size.width * 0.38f, size.height * 0.38f),
                cornerRadius = CornerRadius(24.dp.toPx(), 24.dp.toPx())
            )
            // Head
            val fatherCenter = Offset(size.width * 0.35f, size.height * 0.36f)
            val fatherRadius = size.width * 0.115f
            drawCircle(
                color = Color(0xFFE0B187),
                radius = fatherRadius,
                center = fatherCenter
            )
            // Hair arc
            drawArc(
                color = Color(0xFF2B1911),
                startAngle = 190f,
                sweepAngle = 160f,
                useCenter = false,
                topLeft = Offset(fatherCenter.x - fatherRadius * 0.75f, fatherCenter.y - fatherRadius * 0.65f),
                size = Size(fatherRadius * 1.5f, fatherRadius * 0.9f),
                style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
            )

            // Right Person (Mother)
            // Torso
            drawRoundRect(
                color = Color(0xFF8C3B2B),
                topLeft = Offset(size.width * 0.48f, size.height * 0.51f),
                size = Size(size.width * 0.35f, size.height * 0.35f),
                cornerRadius = CornerRadius(22.dp.toPx(), 22.dp.toPx())
            )
            // Hair back
            val motherCenter = Offset(size.width * 0.65f, size.height * 0.41f)
            val motherRadius = size.width * 0.105f
            drawCircle(
                color = Color(0xFF2B1911),
                radius = motherRadius * 1.05f,
                center = Offset(motherCenter.x + 2.dp.toPx(), motherCenter.y)
            )
            // Head
            drawCircle(
                color = Color(0xFFCEA078),
                radius = motherRadius * 0.92f,
                center = motherCenter
            )
            // Hair swoop
            val hairPath = Path().apply {
                moveTo(motherCenter.x - motherRadius, motherCenter.y - motherRadius * 0.2f)
                quadraticTo(
                    motherCenter.x,
                    motherCenter.y - motherRadius * 1.25f,
                    motherCenter.x + motherRadius,
                    motherCenter.y + motherRadius * 0.3f
                )
                lineTo(motherCenter.x + motherRadius, motherCenter.y - motherRadius)
                lineTo(motherCenter.x - motherRadius, motherCenter.y - motherRadius)
                close()
            }
            drawPath(path = hairPath, color = Color(0xFF2B1911))

            // Center Foreground Child
            // Torso
            drawRoundRect(
                color = Color(0xFFA97118),
                topLeft = Offset(size.width * 0.35f, size.height * 0.63f),
                size = Size(size.width * 0.29f, size.height * 0.24f),
                cornerRadius = CornerRadius(18.dp.toPx(), 18.dp.toPx())
            )
            // Child Head
            val childCenter = Offset(size.width * 0.495f, size.height * 0.55f)
            val childRadius = size.width * 0.082f
            drawCircle(
                color = Color(0xFFE0B187),
                radius = childRadius,
                center = childCenter
            )
            // Child Hair
            drawArc(
                color = Color(0xFF3A2318),
                startAngle = 185f,
                sweepAngle = 170f,
                useCenter = true,
                topLeft = Offset(childCenter.x - childRadius, childCenter.y - childRadius),
                size = Size(childRadius * 2f, childRadius * 1.4f)
            )

            // Warm candle/plate glow at bottom of child torso
            drawOval(
                color = Color(0xFFD8B878).copy(alpha = 0.85f),
                topLeft = Offset(size.width * 0.42f, size.height * 0.775f),
                size = Size(size.width * 0.15f, size.height * 0.032f)
            )
            drawCircle(
                color = Color(0xFFFFE599),
                radius = 2.2.dp.toPx(),
                center = Offset(size.width * 0.495f, size.height * 0.765f)
            )
        }

        // Top-Left "Oct 2026" Pill
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 10.dp, start = 10.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.46f))
                .border(0.5.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                .padding(horizontal = 9.dp, vertical = 4.dp)
        ) {
            Text(
                text = "Oct 2026",
                fontFamily = PlusJakartaSansFont,
                fontWeight = FontWeight.Medium,
                fontSize = 10.sp,
                color = Color.White.copy(alpha = 0.92f)
            )
        }

        // Bottom Bar ("Family Dinner")
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(36.dp)
                .background(Color(0xFF0E0B0A).copy(alpha = 0.94f))
                .padding(horizontal = 12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFF3B6B))
            )
            Spacer(modifier = Modifier.width(7.dp))
            Text(
                text = "Family Dinner",
                fontFamily = PlusJakartaSansFont,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                color = Color.White
            )
        }
    }
}

@Composable
private fun FloatingStoryBubble(
    handle: String,
    isPapa: Boolean,
    storyRingBrush: Brush,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        // Story-ring circular avatar
        Box(
            modifier = Modifier
                .size(44.dp)
                .shadow(10.dp, CircleShape)
                .clip(CircleShape)
                .background(storyRingBrush)
                .padding(2.dp)
                .clip(CircleShape)
                .background(IgBlack)
                .padding(1.5.dp)
                .clip(CircleShape)
                .background(if (isPapa) Color(0xFF1F242D) else Color(0xFF281D2D)),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                if (isPapa) {
                    // Shoulders
                    drawArc(
                        color = Color(0xFF394150),
                        startAngle = 180f,
                        sweepAngle = 180f,
                        useCenter = true,
                        topLeft = Offset(size.width * 0.18f, size.height * 0.64f),
                        size = Size(size.width * 0.64f, size.height * 0.55f)
                    )
                    // Head
                    drawCircle(
                        color = Color(0xFFE5E7EB),
                        radius = size.width * 0.20f,
                        center = Offset(size.width * 0.5f, size.height * 0.40f)
                    )
                    // Glasses / Brow bar
                    drawRoundRect(
                        color = Color(0xFF1F242D),
                        topLeft = Offset(size.width * 0.35f, size.height * 0.34f),
                        size = Size(size.width * 0.30f, size.height * 0.08f),
                        cornerRadius = CornerRadius(4f, 4f)
                    )
                } else {
                    // Shoulders
                    drawArc(
                        color = Color(0xFF1D4D4F),
                        startAngle = 180f,
                        sweepAngle = 180f,
                        useCenter = true,
                        topLeft = Offset(size.width * 0.20f, size.height * 0.65f),
                        size = Size(size.width * 0.60f, size.height * 0.52f)
                    )
                    // Gold saree border line
                    drawLine(
                        color = Color(0xFFF59E0B),
                        start = Offset(size.width * 0.34f, size.height * 0.92f),
                        end = Offset(size.width * 0.50f, size.height * 0.66f),
                        strokeWidth = 1.5.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = Color(0xFFF59E0B),
                        start = Offset(size.width * 0.66f, size.height * 0.92f),
                        end = Offset(size.width * 0.50f, size.height * 0.66f),
                        strokeWidth = 1.5.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    // Head
                    drawCircle(
                        color = Color(0xFFF3C69F),
                        radius = size.width * 0.18f,
                        center = Offset(size.width * 0.5f, size.height * 0.42f)
                    )
                    // Hair cap
                    drawArc(
                        color = Color(0xFF1E1418),
                        startAngle = 180f,
                        sweepAngle = 180f,
                        useCenter = true,
                        topLeft = Offset(size.width * 0.32f, size.height * 0.23f),
                        size = Size(size.width * 0.36f, size.height * 0.24f)
                    )
                    // Tiny red bindi dot
                    drawCircle(
                        color = Color(0xFFEF4444),
                        radius = 1.2.dp.toPx(),
                        center = Offset(size.width * 0.5f, size.height * 0.38f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Handle pill (@Papa / @Maa)
        Box(
            modifier = Modifier
                .shadow(8.dp, RoundedCornerShape(50))
                .clip(RoundedCornerShape(50))
                .background(Color(0xFF141416))
                .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(50))
                .padding(horizontal = 9.dp, vertical = 3.dp)
        ) {
            Text(
                text = handle,
                fontFamily = PlusJakartaSansFont,
                fontWeight = FontWeight.SemiBold,
                fontSize = 10.sp,
                color = Color.White
            )
        }
    }
}

fun hasAnyMediaPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val hasImages = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_MEDIA_IMAGES
        ) == PackageManager.PERMISSION_GRANTED
        val hasVideos = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_MEDIA_VIDEO
        ) == PackageManager.PERMISSION_GRANTED
        val hasVisual = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            ) == PackageManager.PERMISSION_GRANTED
        } else false
        hasImages || hasVideos || hasVisual
    } else {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }
}
