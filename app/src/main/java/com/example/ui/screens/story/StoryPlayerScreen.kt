// InstaGallery v1.0
package com.example.ui.screens.story

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilledExtendedIcons
import androidx.compose.material.icons.outlined.OutlinedExtendedIcons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.local.OnboardingPreferences
import com.example.ui.components.CaptionEditorSheet
import com.example.ui.components.HeartBurstInstance
import com.example.ui.components.InstaCardStudioSheet
import com.example.ui.components.SnapshotStudioIcon
import com.example.ui.components.TapHeartBurstOverlay
import com.example.ui.components.bounceClick
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

private const val STORY_SLIDE_DURATION_MS = 4500

@Composable
fun StoryPlayerScreen(
    viewModel: StoryPlayerViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToProfile: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(context) {
        OnboardingPreferences.init(context)
    }
    val heartTheme by OnboardingPreferences.heartThemeFlow.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val currentDeck = uiState.currentDeck
    val currentSlide = uiState.currentSlide

    var isFingerHolding by remember { mutableStateOf(false) }
    var isHudHiddenByHold by remember { mutableStateOf(false) }
    var isPausedManually by remember { mutableStateOf(false) }
    var showCaptionSheet by remember { mutableStateOf(false) }
    var showInstaCardStudio by remember { mutableStateOf(false) }

    val activeBursts = remember { mutableStateListOf<HeartBurstInstance>() }

    // Mark active deck as viewed whenever currentDeck changes
    LaunchedEffect(currentDeck?.personId) {
        currentDeck?.personId?.let { pid ->
            OnboardingPreferences.markStoryViewed(context, pid)
        }
    }

    // Smoothly hide HUD if finger is held down for > 180ms
    LaunchedEffect(isFingerHolding) {
        if (isFingerHolding) {
            delay(180L)
            if (isFingerHolding) {
                isHudHiddenByHold = true
            }
        } else {
            isHudHiddenByHold = false
        }
    }

    val isTimerPaused = isFingerHolding ||
        showCaptionSheet ||
        showInstaCardStudio ||
        isPausedManually

    val slideProgress = remember { Animatable(0f) }
    val currentOnNavigateBack by rememberUpdatedState(onNavigateBack)

    // Reset progress to 0f when switching deck or slide
    LaunchedEffect(uiState.currentDeckIndex, uiState.currentSlideIndex, currentSlide?.media?.mediaId) {
        slideProgress.snapTo(0f)
    }

    // Drive or pause the 4500ms slide progress timer
    LaunchedEffect(
        uiState.currentDeckIndex,
        uiState.currentSlideIndex,
        currentSlide?.media?.mediaId,
        isTimerPaused,
        uiState.isLoading
    ) {
        if (uiState.isLoading || currentSlide == null) return@LaunchedEffect
        if (isTimerPaused) {
            slideProgress.stop()
        } else {
            val remainingFraction = (1f - slideProgress.value).coerceIn(0f, 1f)
            val remainingMs = (remainingFraction * STORY_SLIDE_DURATION_MS).roundToInt()
            if (remainingMs <= 16) {
                viewModel.nextSlide(onAllFinished = currentOnNavigateBack)
            } else {
                val result = slideProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = remainingMs, easing = LinearEasing)
                )
                if (!isTimerPaused && slideProgress.value >= 0.995f) {
                    viewModel.nextSlide(onAllFinished = currentOnNavigateBack)
                }
            }
        }
    }

    val hudAlpha by animateFloatAsState(
        targetValue = if (isHudHiddenByHold) 0f else 1f,
        animationSpec = tween(durationMillis = 200),
        label = "story_hud_alpha"
    )

    if (uiState.isLoading) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 2.5.dp,
                modifier = Modifier.size(36.dp)
            )
        }
        return
    }

    if (currentDeck == null || currentSlide == null) {
        LaunchedEffect(Unit) {
            onNavigateBack()
        }
        return
    }

    val swipeThresholdPx = with(density) { 120.dp.toPx() }
    var verticalDragOffset by remember { mutableFloatStateOf(0f) }
    var pressDownTimestamp by remember { mutableLongStateOf(0L) }

    val currentMedia by rememberUpdatedState(currentSlide.media)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("story_player_screen")
    ) {
        // 1. 9:16 Rounded Story Stage (RoundedCornerShape(20.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF08080C))
                .pointerInput(currentDeck.personId, uiState.currentSlideIndex) {
                    detectVerticalDragGestures(
                        onDragStart = { verticalDragOffset = 0f },
                        onVerticalDrag = { _, dragAmount ->
                            verticalDragOffset += dragAmount
                            if (verticalDragOffset > swipeThresholdPx) {
                                verticalDragOffset = 0f
                                currentOnNavigateBack()
                            }
                        },
                        onDragEnd = { verticalDragOffset = 0f },
                        onDragCancel = { verticalDragOffset = 0f }
                    )
                }
                .pointerInput(currentDeck.personId) {
                    detectTapGestures(
                        onPress = {
                            pressDownTimestamp = System.currentTimeMillis()
                            isFingerHolding = true
                            try {
                                tryAwaitRelease()
                            } finally {
                                isFingerHolding = false
                            }
                        },
                        onDoubleTap = { tapOffset ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            val randomTilt = ((-15..15).random()).toFloat()
                            activeBursts.add(
                                HeartBurstInstance(
                                    id = System.nanoTime(),
                                    tapOffset = tapOffset,
                                    rotationDeg = randomTilt
                                )
                            )
                            viewModel.setFavoriteTrue(currentMedia)
                        },
                        onTap = { tapOffset ->
                            val heldDuration = System.currentTimeMillis() - pressDownTimestamp
                            if (heldDuration < 220L) {
                                if (tapOffset.x < size.width * 0.32f) {
                                    scope.launch { slideProgress.snapTo(0f) }
                                    viewModel.previousSlide()
                                } else {
                                    scope.launch { slideProgress.snapTo(0f) }
                                    viewModel.nextSlide(onAllFinished = currentOnNavigateBack)
                                }
                            }
                        }
                    )
                }
        ) {
            // Layer 1 — Ambient Blurred/Dimmed Backdrop (zero harsh black bars)
            AsyncImage(
                model = currentSlide.media.uri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = 1.18f
                        scaleY = 1.18f
                    }
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF08080C).copy(alpha = 0.78f))
            )

            // Layer 2 — Foreground Photo with Subtle Ken Burns Motion
            val kenBurnsScale = 1f + (slideProgress.value * 0.045f)
            AsyncImage(
                model = currentSlide.media.uri,
                contentDescription = currentSlide.media.displayName,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = kenBurnsScale
                        scaleY = kenBurnsScale
                    }
            )

            // Layer 3 — Double-Tap Heart Burst Overlay
            TapHeartBurstOverlay(
                bursts = activeBursts,
                heartTheme = heartTheme,
                onBurstFinished = { id -> activeBursts.removeAll { it.id == id } }
            )

            // Top HUD Overlay (Segmented Progress Bars + Author Header)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .alpha(hudAlpha)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.58f),
                                Color.Transparent
                            )
                        )
                    )
                    .padding(bottom = 22.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Segmented Progress Bar Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        currentDeck.slides.forEachIndexed { index, _ ->
                            val fraction = when {
                                index < uiState.currentSlideIndex -> 1f
                                index == uiState.currentSlideIndex -> slideProgress.value.coerceIn(0f, 1f)
                                else -> 0f
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(2.5.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(Color.White.copy(alpha = 0.32f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(fraction)
                                        .clip(RoundedCornerShape(50))
                                        .background(Color.White)
                                )
                            }
                        }
                    }

                    // Author Header Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Left Clickable Profile Pill -> navigates directly to ProfileScreen
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .bounceClick {
                                    onNavigateToProfile(currentDeck.personId)
                                }
                                .testTag("story_header_profile_button"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .border(1.5.dp, Color.White, CircleShape)
                                    .background(Color(0xFF18181E)),
                                contentAlignment = Alignment.Center
                            ) {
                                val avatarPath = currentDeck.avatarPath
                                if (!avatarPath.isNullOrBlank() && File(avatarPath).exists()) {
                                    AsyncImage(
                                        model = File(avatarPath),
                                        contentDescription = currentDeck.handleName,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(CircleShape)
                                    )
                                } else {
                                    Icon(
                                        imageVector = if (currentDeck.personId == -1L) {
                                            Icons.Filled.AutoAwesome
                                        } else {
                                            Icons.Outlined.Person
                                        },
                                        contentDescription = currentDeck.handleName,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Column {
                                Text(
                                    text = buildAnnotatedString {
                                        withStyle(
                                            SpanStyle(
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        ) {
                                            append(currentDeck.handleName)
                                        }
                                        withStyle(
                                            SpanStyle(
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Normal,
                                                color = Color.White.copy(alpha = 0.8f)
                                            )
                                        ) {
                                            append(" • ${currentSlide.formattedDate}")
                                        }
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Spacer(modifier = Modifier.height(3.dp))

                                // Memory Badge Pill
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color.White.copy(alpha = 0.16f))
                                        .padding(horizontal = 7.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = if (uiState.currentSlideIndex == 0) {
                                            "${currentSlide.badge.label} • Tap to view profile"
                                        } else {
                                            currentSlide.badge.label
                                        },
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color.White
                                    )
                                }
                            }
                        }

                        // Right Controls: Pause/Resume + Close
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IconButton(
                                onClick = { isPausedManually = !isPausedManually },
                                modifier = Modifier
                                    .size(34.dp)
                                    .testTag("story_pause_resume_button")
                            ) {
                                StoryPlayPauseIcon(
                                    isPaused = isPausedManually,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            IconButton(
                                onClick = onNavigateBack,
                                modifier = Modifier
                                    .size(34.dp)
                                    .testTag("story_close_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close Story",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. Bottom Interactive Bar (height = 64.dp, padding(horizontal = 14.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 14.dp)
                .alpha(hudAlpha),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Left Pill (Memory Note / Caption Trigger, weight(1f))
            val customCaption = currentSlide.media.customCaption?.takeIf { it.isNotBlank() }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFF16161A))
                    .border(1.dp, Color(0xFF2C2C34), RoundedCornerShape(50))
                    .clickable { showCaptionSheet = true }
                    .padding(horizontal = 16.dp)
                    .testTag("story_caption_pill"),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = customCaption ?: "Add a memory note...",
                    fontSize = 13.sp,
                    color = if (customCaption != null) Color.White else Color(0xFF8E8E93),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Right Action Icons (spacedBy(16.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Heart Icon (26.dp)
                val heartScale by animateFloatAsState(
                    targetValue = if (currentSlide.media.isFavorite) 1.15f else 1.0f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                    label = "story_heart_scale"
                )
                val heartTint by animateColorAsState(
                    targetValue = if (currentSlide.media.isFavorite) heartTheme.primaryColor else Color.White,
                    label = "story_heart_tint"
                )

                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .scale(heartScale)
                        .bounceClick {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.toggleFavorite(currentSlide.media)
                        }
                        .testTag("story_favorite_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (currentSlide.media.isFavorite) {
                            FilledExtendedIcons.Favorite
                        } else {
                            OutlinedExtendedIcons.FavoriteBorder
                        },
                        contentDescription = if (currentSlide.media.isFavorite) "Liked" else "Like",
                        tint = heartTint,
                        modifier = Modifier.size(26.dp)
                    )
                }

                // InstaCard Snapshot Icon (25.dp)
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .bounceClick { showInstaCardStudio = true }
                        .testTag("story_snapshot_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = SnapshotStudioIcon,
                        contentDescription = "Export 1080p InstaCard",
                        tint = Color.White,
                        modifier = Modifier.size(25.dp)
                    )
                }

                // Share Paper Plane Icon (25.dp)
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .bounceClick {
                            shareStoryMedia(context, currentSlide.media.uri)
                        }
                        .testTag("story_share_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = OutlinedExtendedIcons.Send,
                        contentDescription = "Share Story Photo",
                        tint = Color.White,
                        modifier = Modifier.size(25.dp)
                    )
                }
            }
        }
    }

    // Module A Caption Editor Sheet (automatically pauses story timer while open)
    if (showCaptionSheet) {
        CaptionEditorSheet(
            media = currentSlide.media,
            primaryAuthorName = currentDeck.handleName.removePrefix("@"),
            formattedDate = currentSlide.formattedDate,
            onSaveCaption = { newCaption ->
                viewModel.updateCaption(currentSlide.media.mediaId, newCaption)
            },
            onDismiss = { showCaptionSheet = false }
        )
    }

    // Module A InstaCard 1080p Snapshot Studio Sheet (automatically pauses story timer while open)
    if (showInstaCardStudio) {
        InstaCardStudioSheet(
            media = currentSlide.media,
            primaryAuthorName = currentDeck.handleName.removePrefix("@"),
            authorAvatarPath = currentDeck.avatarPath,
            heartColor = heartTheme.primaryColor,
            onDismiss = { showInstaCardStudio = false }
        )
    }
}

@Composable
private fun StoryPlayPauseIcon(
    isPaused: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (isPaused) {
            // Play triangle
            val playPath = Path().apply {
                moveTo(w * 0.22f, h * 0.14f)
                lineTo(w * 0.86f, h * 0.50f)
                lineTo(w * 0.22f, h * 0.86f)
                close()
            }
            drawPath(path = playPath, color = Color.White)
        } else {
            // Two rounded pause bars
            val barW = w * 0.24f
            val barH = h * 0.76f
            val topY = (h - barH) / 2f
            val radius = CornerRadius(barW / 2f, barW / 2f)
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(w * 0.18f, topY),
                size = Size(barW, barH),
                cornerRadius = radius
            )
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(w * 0.58f, topY),
                size = Size(barW, barH),
                cornerRadius = radius
            )
        }
    }
}

private fun shareStoryMedia(context: Context, uriString: String) {
    try {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_STREAM, Uri.parse(uriString))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share memory"))
    } catch (_: Exception) {
        val textIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, uriString)
        }
        context.startActivity(Intent.createChooser(textIntent, "Share memory"))
    }
}
