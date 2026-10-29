// InstaGallery v1.0
package com.example.ui.screens.reels

import android.content.Context
import android.content.Intent
import android.graphics.Matrix
import android.net.Uri
import android.view.TextureView
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FilledExtendedIcons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.OutlinedExtendedIcons
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.AsyncImage
import com.example.data.local.HeartTheme
import com.example.data.local.OnboardingPreferences
import com.example.data.local.entity.MediaWithAuthors
import com.example.ui.components.HeartBurstInstance
import com.example.ui.components.HeartThemePickerSheet
import com.example.ui.components.TapHeartBurstOverlay
import com.example.ui.theme.IgBlack
import com.example.ui.theme.IgBlue
import com.example.ui.theme.IgRed
import com.example.ui.theme.IgStoryGradient
import com.example.ui.theme.IgSurfacePill
import com.example.ui.theme.IgTextPrimary
import com.example.ui.theme.IgTextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

@OptIn(UnstableApi::class)
@kotlin.OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReelsScreen(
    viewModel: ReelsViewModel,
    onViewProfilesClick: (Long) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(context) {
        OnboardingPreferences.init(context)
    }
    val heartTheme by OnboardingPreferences.heartThemeFlow.collectAsStateWithLifecycle()
    var showHeartThemeSheet by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val videos = uiState.videos

    if (videos.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(IgBlack)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(IgSurfacePill),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (uiState.onlyFavorites) Icons.Outlined.FavoriteBorder else Icons.Outlined.Movie,
                        contentDescription = "No reels",
                        tint = IgTextSecondary,
                        modifier = Modifier.size(36.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = if (uiState.onlyFavorites) "No Favorite Reels Yet" else "No Videos Found",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = IgTextPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (uiState.onlyFavorites) {
                        "Double-tap any Reel or tap the heart icon to save your favorite videos."
                    } else {
                        "Videos in your device storage will appear here as auto-looping Reels."
                    },
                    fontSize = 13.sp,
                    color = IgTextSecondary,
                    textAlign = TextAlign.Center
                )
                if (uiState.onlyFavorites) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(IgBlue)
                            .clickable { viewModel.toggleFavoritesFilter() }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "Show All Reels",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                }
            }
        }
        return
    }

    val pagerState = rememberPagerState(pageCount = { videos.size })
    val settledPage = pagerState.settledPage.coerceIn(0, (videos.size - 1).coerceAtLeast(0))
    val settledVideo = videos.getOrNull(settledPage)
    val settledMediaId = settledVideo?.media?.mediaId ?: -1L
    val settledMediaUri = settledVideo?.media?.uri ?: ""

    var renderedFirstFrameMediaId by remember { mutableLongStateOf(-1L) }
    var activeVideoWidth by remember { mutableIntStateOf(0) }
    var activeVideoHeight by remember { mutableIntStateOf(0) }
    var playbackProgress by remember { mutableFloatStateOf(0f) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var isPausedByTap by remember { mutableStateOf(false) }
    var isFastForward2x by remember { mutableStateOf(false) }
    var isScrubbing by remember { mutableStateOf(false) }
    val activeBursts = remember { mutableStateListOf<HeartBurstInstance>() }
    var errorRetryCount by remember { mutableIntStateOf(0) }

    // Configure ExoPlayer with hardware decoder fallback + audio focus so all phone videos play cleanly
    val exoPlayer = remember {
        val renderersFactory = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        ExoPlayer.Builder(context, renderersFactory)
            .setAudioAttributes(audioAttributes, true)
            .build()
            .apply {
                repeatMode = Player.REPEAT_MODE_ONE
                playWhenReady = true
            }
    }

    // Attach Player.Listener to track first-frame rendering (eliminates flicker) and recover from codec errors
    DisposableEffect(exoPlayer, settledMediaId, settledMediaUri) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                renderedFirstFrameMediaId = settledMediaId
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying && settledMediaId != -1L) {
                    renderedFirstFrameMediaId = settledMediaId
                }
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    activeVideoWidth = videoSize.width
                    activeVideoHeight = videoSize.height
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                if (errorRetryCount < 1 && settledMediaUri.isNotBlank()) {
                    errorRetryCount++
                    exoPlayer.stop()
                    exoPlayer.setMediaItem(MediaItem.fromUri(settledMediaUri))
                    exoPlayer.prepare()
                    exoPlayer.playWhenReady = !isPausedByTap
                    if (!isPausedByTap) {
                        exoPlayer.play()
                    }
                } else if (settledMediaId != -1L) {
                    viewModel.markVideoUnplayable(settledMediaId)
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
        }
    }

    // Update mute state without resetting playback
    LaunchedEffect(uiState.isMuted) {
        exoPlayer.volume = if (uiState.isMuted) 0f else 1f
    }

    // Load video ONLY when settledMediaId actually changes (never on Like/Bookmark updates!)
    LaunchedEffect(settledMediaId, settledMediaUri) {
        if (settledMediaId != -1L && settledMediaUri.isNotBlank()) {
            errorRetryCount = 0
            isPausedByTap = false
            isFastForward2x = false
            renderedFirstFrameMediaId = -1L
            playbackProgress = 0f
            exoPlayer.setPlaybackParameters(PlaybackParameters.DEFAULT)
            exoPlayer.stop()
            exoPlayer.setMediaItem(MediaItem.fromUri(settledMediaUri))
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
            exoPlayer.play()
        }
    }

    // Poll playback progress smoothly and ensure thumbnail shield clears once video advances
    LaunchedEffect(exoPlayer, settledMediaId, isScrubbing) {
        while (isActive) {
            if (!isScrubbing) {
                val dur = exoPlayer.duration
                val pos = exoPlayer.currentPosition
                if (dur > 0) {
                    durationMs = dur
                    currentPositionMs = pos.coerceAtLeast(0L)
                    playbackProgress = (pos.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
                }
                if (exoPlayer.isPlaying && pos > 60L && settledMediaId != -1L) {
                    renderedFirstFrameMediaId = settledMediaId
                }
            }
            delay(80)
        }
    }

    // Handle Android lifecycle pause/resume without prematurely releasing ExoPlayer if lifecycleOwner changes
    DisposableEffect(lifecycleOwner, exoPlayer, settledMediaUri) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> exoPlayer.pause()
                Lifecycle.Event.ON_RESUME -> {
                    if (!isPausedByTap && settledMediaUri.isNotBlank()) {
                        if (exoPlayer.playbackState == Player.STATE_IDLE) {
                            exoPlayer.setMediaItem(MediaItem.fromUri(settledMediaUri))
                            exoPlayer.prepare()
                        } else if (exoPlayer.playbackState == Player.STATE_ENDED) {
                            exoPlayer.seekTo(0L)
                        }
                        exoPlayer.playWhenReady = true
                        exoPlayer.play()
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Release ExoPlayer strictly when ReelsScreen leaves composition
    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    val contentScaleMode = if (uiState.isFitMode) ContentScale.Fit else ContentScale.Crop

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(IgBlack)
            .testTag("reels_screen")
    ) {
        VerticalPager(
            state = pagerState,
            beyondViewportPageCount = 1,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val video = videos[page]
            val isSettledPage = (page == settledPage)
            val isFirstFrameReady = isSettledPage && (renderedFirstFrameMediaId == video.media.mediaId)

            // Smooth crossfade of thumbnail shield ONLY after ExoPlayer has rendered the first frame
            val thumbnailAlpha by animateFloatAsState(
                targetValue = if (isFirstFrameReady) 0f else 1f,
                animationSpec = tween(durationMillis = 160),
                label = "thumbnail_shield_alpha"
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(IgBlack)
                    .pointerInput(video.media.mediaId, video.media.isFavorite) {
                        detectTapGestures(
                            onDoubleTap = { tapOffset ->
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                val randomTilt = Random.nextInt(-15, 16).toFloat()
                                activeBursts.add(
                                    HeartBurstInstance(
                                        id = System.nanoTime(),
                                        tapOffset = tapOffset,
                                        rotationDeg = randomTilt
                                    )
                                )
                                if (!video.media.isFavorite) {
                                    viewModel.toggleFavorite(video.media.mediaId, false)
                                }
                            },
                            onLongPress = {
                                isFastForward2x = true
                                exoPlayer.setPlaybackParameters(PlaybackParameters(2.0f))
                            },
                            onPress = {
                                tryAwaitRelease()
                                if (isFastForward2x) {
                                    isFastForward2x = false
                                    exoPlayer.setPlaybackParameters(PlaybackParameters.DEFAULT)
                                }
                            },
                            onTap = {
                                if (!isPausedByTap && (exoPlayer.isPlaying || exoPlayer.playWhenReady)) {
                                    isPausedByTap = true
                                    exoPlayer.playWhenReady = false
                                    exoPlayer.pause()
                                } else {
                                    isPausedByTap = false
                                    if (exoPlayer.playbackState == Player.STATE_IDLE && settledMediaUri.isNotBlank()) {
                                        exoPlayer.setMediaItem(MediaItem.fromUri(settledMediaUri))
                                        exoPlayer.prepare()
                                    } else if (exoPlayer.playbackState == Player.STATE_ENDED) {
                                        exoPlayer.seekTo(0L)
                                    }
                                    exoPlayer.playWhenReady = true
                                    exoPlayer.play()
                                }
                            }
                        )
                    }
            ) {
                // Zero-Flicker TextureView Renderer (bound when settled on this page)
                if (isSettledPage) {
                    ZeroFlickerTextureVideoPlayer(
                        exoPlayer = exoPlayer,
                        videoWidth = activeVideoWidth.takeIf { it > 0 } ?: video.media.width,
                        videoHeight = activeVideoHeight.takeIf { it > 0 } ?: video.media.height,
                        isFitMode = uiState.isFitMode,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Thumbnail Shield: stays visible until ExoPlayer renders first frame -> 0% flicker!
                if (thumbnailAlpha > 0.01f) {
                    AsyncImage(
                        model = video.media.uri,
                        contentDescription = video.media.displayName,
                        contentScale = contentScaleMode,
                        modifier = Modifier
                            .fillMaxSize()
                            .alpha(thumbnailAlpha)
                            .background(IgBlack)
                    )
                }

                // Paused Indicator Overlay
                AnimatedVisibility(
                    visible = isSettledPage && isPausedByTap,
                    enter = fadeIn() + scaleIn(initialScale = 0.8f),
                    exit = fadeOut() + scaleOut(targetScale = 0.8f),
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.55f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Paused",
                            tint = Color.White,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }

                // 2x Speed Indicator Pill (when long-pressing)
                AnimatedVisibility(
                    visible = isSettledPage && isFastForward2x,
                    enter = fadeIn() + scaleIn(initialScale = 0.9f),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 72.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.Black.copy(alpha = 0.72f))
                            .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(50))
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "2x Speed ⏩",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                // Double-Tap Heart Burst Overlay (Exact Tap-Coordinate Canvas Physics, Zero Bounding Box)
                if (isSettledPage) {
                    TapHeartBurstOverlay(
                        bursts = activeBursts,
                        heartTheme = heartTheme,
                        onBurstFinished = { burstId -> activeBursts.removeAll { it.id == burstId } },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Top scrim gradient
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(108.dp)
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.72f), Color.Transparent)
                            )
                        )
                )

                // Bottom scrim gradient
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.88f))
                            )
                        )
                )

                // Bottom-Left Overlay Details (Pure Standalone @Reels Identity)
                ReelsBottomOverlay(
                    video = video,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 16.dp, bottom = 36.dp)
                )

                // Right-Side Vertical Action Rail (Pure Video Controls)
                ReelsActionRail(
                    video = video,
                    isMuted = uiState.isMuted,
                    heartColor = heartTheme.primaryColor,
                    onToggleFavorite = { viewModel.toggleFavorite(video.media.mediaId, video.media.isFavorite) },
                    onLongPressFavorite = { showHeartThemeSheet = true },
                    onToggleBookmark = { viewModel.toggleBookmark(video.media.mediaId, video.media.isBookmarked) },
                    onToggleMute = { viewModel.toggleMute() },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 14.dp, bottom = 36.dp)
                )
            }
        }

        // Top Overlay Bar (No Person Filter Chips)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(top = 12.dp, start = 16.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.clickable { viewModel.reshuffle() }
            ) {
                Text(
                    text = if (uiState.onlyFavorites) "Favorite Reels" else "Reels",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Shuffle Reels",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Crop / Fit Aspect Ratio Pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.45f))
                        .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(50))
                        .clickable { viewModel.toggleFitMode() }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (uiState.isFitMode) "Fit" else "Crop",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .combinedClickable(
                            onClick = { viewModel.toggleFavoritesFilter() },
                            onLongClick = { showHeartThemeSheet = true }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (uiState.onlyFavorites) FilledExtendedIcons.Favorite else OutlinedExtendedIcons.FavoriteBorder,
                        contentDescription = "Filter Favorite Reels",
                        tint = if (uiState.onlyFavorites) heartTheme.primaryColor else Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        if (showHeartThemeSheet) {
            HeartThemePickerSheet(
                currentTheme = heartTheme,
                onSelectTheme = { selected ->
                    OnboardingPreferences.setHeartTheme(context, selected)
                    showHeartThemeSheet = false
                },
                onDismiss = { showHeartThemeSheet = false }
            )
        }

        // Scrubbing Time Indicator Pill
        AnimatedVisibility(
            visible = isScrubbing && durationMs > 0L,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 26.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.78f))
                    .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(50))
                    .padding(horizontal = 14.dp, vertical = 5.dp)
            ) {
                Text(
                    text = "${formatDurationMs(currentPositionMs)} / ${formatDurationMs(durationMs)}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        // Interactive Scrubbable Seekbar at bottom of viewport
        var barWidthPx by remember { mutableFloatStateOf(1f) }
        val animatedProgress by animateFloatAsState(
            targetValue = playbackProgress,
            label = "reel_progress"
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(18.dp)
                .align(Alignment.BottomCenter)
                .onSizeChanged { barWidthPx = it.width.toFloat().coerceAtLeast(1f) }
                .pointerInput(durationMs) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            isScrubbing = true
                            val fraction = (offset.x / barWidthPx).coerceIn(0f, 1f)
                            playbackProgress = fraction
                            if (durationMs > 0) {
                                currentPositionMs = (fraction * durationMs).toLong()
                            }
                        },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            val fraction = (change.position.x / barWidthPx).coerceIn(0f, 1f)
                            playbackProgress = fraction
                            if (durationMs > 0) {
                                currentPositionMs = (fraction * durationMs).toLong()
                            }
                        },
                        onDragEnd = {
                            if (durationMs > 0) {
                                exoPlayer.seekTo((playbackProgress * durationMs).toLong())
                            }
                            isScrubbing = false
                        },
                        onDragCancel = {
                            isScrubbing = false
                        }
                    )
                },
            contentAlignment = Alignment.BottomStart
        ) {
            val barHeight = if (isScrubbing) 5.dp else 2.5.dp
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(barHeight)
                    .background(Color.White.copy(alpha = 0.24f))
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress.coerceIn(0f, 1f))
                    .height(barHeight)
                    .background(Color.White)
            )
        }
    }
}

/**
 * Renders ExoPlayer video into a hardware-accelerated TextureView with matrix aspect-ratio scaling.
 * Unlike SurfaceView, TextureView participates directly in Compose's render node tree,
 * preventing black surface-punch flickering during Pager swipes or screen transitions.
 */
private data class VideoScaleSpec(
    val width: Int,
    val height: Int,
    val isFitMode: Boolean
)

@Composable
private fun ZeroFlickerTextureVideoPlayer(
    exoPlayer: ExoPlayer,
    videoWidth: Int,
    videoHeight: Int,
    isFitMode: Boolean,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { ctx ->
            TextureView(ctx).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                isOpaque = false
                tag = VideoScaleSpec(videoWidth, videoHeight, isFitMode)
                addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
                    val spec = (view.tag as? VideoScaleSpec)
                        ?: VideoScaleSpec(videoWidth, videoHeight, isFitMode)
                    applyVideoScaleMatrix(this, spec.width, spec.height, spec.isFitMode)
                }
            }
        },
        update = { textureView ->
            textureView.tag = VideoScaleSpec(videoWidth, videoHeight, isFitMode)
            exoPlayer.setVideoTextureView(textureView)
            applyVideoScaleMatrix(textureView, videoWidth, videoHeight, isFitMode)
        },
        onRelease = { textureView ->
            exoPlayer.clearVideoTextureView(textureView)
        },
        modifier = modifier
    )
}

private fun applyVideoScaleMatrix(
    textureView: TextureView,
    videoWidth: Int,
    videoHeight: Int,
    isFitMode: Boolean
) {
    val viewWidth = textureView.width.toFloat()
    val viewHeight = textureView.height.toFloat()
    if (viewWidth <= 0f || viewHeight <= 0f || videoWidth <= 0 || videoHeight <= 0) return

    val videoAspect = videoWidth.toFloat() / videoHeight.toFloat()
    val viewAspect = viewWidth / viewHeight

    var scaleX = 1f
    var scaleY = 1f

    if (isFitMode) {
        // Fit Center (letterbox/pillarbox without cropping)
        if (videoAspect > viewAspect) {
            scaleY = viewAspect / videoAspect
        } else {
            scaleX = videoAspect / viewAspect
        }
    } else {
        // Center Crop (full-bleed Reels standard)
        if (videoAspect > viewAspect) {
            scaleX = videoAspect / viewAspect
        } else {
            scaleY = viewAspect / videoAspect
        }
    }

    val pivotX = viewWidth / 2f
    val pivotY = viewHeight / 2f
    val matrix = Matrix()
    matrix.setScale(scaleX, scaleY, pivotX, pivotY)
    textureView.setTransform(matrix)
}

@kotlin.OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReelsActionRail(
    video: MediaWithAuthors,
    isMuted: Boolean,
    heartColor: Color,
    onToggleFavorite: () -> Unit,
    onLongPressFavorite: () -> Unit,
    onToggleBookmark: () -> Unit,
    onToggleMute: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Heart (Favorite) button (long-press opens Heart Color Picker)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.combinedClickable(
                onClick = onToggleFavorite,
                onLongClick = onLongPressFavorite
            )
        ) {
            Icon(
                imageVector = if (video.media.isFavorite) FilledExtendedIcons.Favorite else OutlinedExtendedIcons.FavoriteBorder,
                contentDescription = "Like",
                tint = if (video.media.isFavorite) heartColor else Color.White,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (video.media.isFavorite) "Liked" else "Like",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
        }

        // 2. Bookmark / Save button
        IconButton(
            onClick = onToggleBookmark,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = if (video.media.isBookmarked) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
                contentDescription = if (video.media.isBookmarked) "Saved" else "Save",
                tint = Color.White,
                modifier = Modifier.size(26.dp)
            )
        }

        // 3. Share paper-plane button (Intent.ACTION_SEND)
        IconButton(
            onClick = { shareVideo(context, video.media.uri) },
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Send,
                contentDescription = "Share",
                tint = Color.White,
                modifier = Modifier.size(26.dp)
            )
        }

        // 4. Mute / Unmute Speaker button
        IconButton(
            onClick = onToggleMute,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = if (isMuted) Icons.Outlined.VolumeOff else Icons.Outlined.VolumeUp,
                contentDescription = if (isMuted) "Unmute" else "Mute",
                tint = Color.White,
                modifier = Modifier.size(26.dp)
            )
        }

        // 5. Bottom rounded square video thumbnail
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(2.dp, Color.White, RoundedCornerShape(8.dp))
        ) {
            AsyncImage(
                model = video.media.uri,
                contentDescription = "Video thumbnail",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReelsBottomOverlay(
    video: MediaWithAuthors,
    modifier: Modifier = Modifier
) {
    val formattedDate = remember(video.media.dateAddedSeconds) {
        val date = Date(video.media.dateAddedSeconds * 1000L)
        val sdf = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
        sdf.format(date)
    }

    Column(
        modifier = modifier.width(280.dp),
        horizontalAlignment = Alignment.Start
    ) {
        // Standalone @Reels Identity Badge Row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.65f))
                    .border(2.dp, IgStoryGradient, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Movie,
                    contentDescription = "Reels",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }

            Text(
                text = "@Reels",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Subtitle below: "${video.media.displayName} • ${formattedDate}"
        Text(
            text = "${video.media.displayName} • $formattedDate",
            fontSize = 13.sp,
            color = Color.White.copy(alpha = 0.9f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Scrolling audio ticker pill below: "${video.media.displayName} • Local Video Audio"
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Color.Black.copy(alpha = 0.5f))
                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(50))
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.MusicNote,
                contentDescription = "Audio",
                tint = Color.White,
                modifier = Modifier.size(13.dp)
            )
            Box(modifier = Modifier.width(180.dp)) {
                Text(
                    text = "${video.media.displayName} • Local Video Audio",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee()
                )
            }
        }
    }
}

private fun formatDurationMs(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return String.format(Locale.US, "%d:%02d", minutes, seconds)
}

private fun shareVideo(context: Context, uriString: String) {
    try {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "video/*"
            putExtra(Intent.EXTRA_STREAM, Uri.parse(uriString))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share reel"))
    } catch (e: Exception) {
        val textIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, uriString)
        }
        context.startActivity(Intent.createChooser(textIntent, "Share reel"))
    }
}
