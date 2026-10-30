// InstaGallery v2.0
package com.example.ui.screens.reels

import android.content.Context
import android.content.Intent
import android.graphics.Matrix
import android.net.Uri
import android.view.TextureView
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilledExtendedIcons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.CropFree
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.OutlinedExtendedIcons
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
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
import androidx.compose.ui.unit.Dp
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
import com.example.data.local.OnboardingPreferences
import com.example.data.local.entity.MediaEntity
import com.example.data.local.entity.MediaWithAuthors
import com.example.data.repository.GalleryRepository
import com.example.ui.components.CaptionEditorSheet
import com.example.ui.components.HeartBurstInstance
import com.example.ui.components.HeartThemePickerSheet
import com.example.ui.components.InstaPullToRefreshBox
import com.example.ui.components.RefreshableTab
import com.example.ui.components.SaveToCollectionSheet
import com.example.ui.components.TabRefreshManager
import com.example.ui.components.TapHeartBurstOverlay
import com.example.ui.components.bounceClick
import com.example.ui.components.isMachineGeneratedFilename
import com.example.ui.theme.IgBlack
import com.example.ui.theme.IgBlue
import com.example.ui.theme.IgStoryGradient
import com.example.ui.theme.IgSurfacePill
import com.example.ui.theme.IgTextPrimary
import com.example.ui.theme.IgTextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
    val coroutineScope = rememberCoroutineScope()
    val galleryRepository = remember(context) { GalleryRepository(context) }

    LaunchedEffect(context) {
        OnboardingPreferences.init(context)
    }
    val heartTheme by OnboardingPreferences.heartThemeFlow.collectAsStateWithLifecycle()
    var showHeartThemeSheet by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Pressing Back while filtering Liked Reels returns to all Reels instead of exiting
    BackHandler(enabled = uiState.onlyFavorites) {
        viewModel.setFavoritesFilter(false)
    }

    val videos = uiState.videos
    var refreshSeed by rememberSaveable { mutableLongStateOf(System.currentTimeMillis()) }
    var avoidLeadMediaId by rememberSaveable { mutableLongStateOf(-1L) }
    var isRefreshing by remember { mutableStateOf(false) }

    val videoIdsKey = remember(videos.size, uiState.onlyFavorites) {
        videos.map { it.media.mediaId }.sorted()
    }
    val shuffledOrderIds = remember(videoIdsKey, refreshSeed, avoidLeadMediaId) {
        if (videoIdsKey.size <= 1) {
            videoIdsKey
        } else {
            val shuffled = videoIdsKey.shuffled(java.util.Random(refreshSeed))
            if (avoidLeadMediaId != -1L && shuffled.first() == avoidLeadMediaId) {
                shuffled.drop(1) + shuffled.first()
            } else {
                shuffled
            }
        }
    }
    val videoMap = remember(videos) { videos.associateBy { it.media.mediaId } }
    val shuffledVideos = remember(shuffledOrderIds, videoMap) {
        shuffledOrderIds.mapNotNull { videoMap[it] }
    }

    if (shuffledVideos.isEmpty()) {
        LaunchedEffect(Unit) {
            TabRefreshManager.refreshEvents.collect { tab ->
                if (tab == RefreshableTab.REELS && !isRefreshing) {
                    isRefreshing = true
                    runCatching { galleryRepository.performQuickRefreshSync() }
                    refreshSeed = System.nanoTime()
                    delay(250)
                    isRefreshing = false
                }
            }
        }
        InstaPullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                if (!isRefreshing) {
                    coroutineScope.launch {
                        isRefreshing = true
                        runCatching { galleryRepository.performQuickRefreshSync() }
                        refreshSeed = System.nanoTime()
                        delay(250)
                        isRefreshing = false
                    }
                }
            },
            modifier = modifier
                .fillMaxSize()
                .background(IgBlack)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
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
                        text = if (uiState.onlyFavorites) "No Liked Reels Yet" else "No Videos Found",
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
                                .clip(RoundedCornerShape(8.dp))
                                .background(IgBlue)
                                .bounceClick { viewModel.setFavoritesFilter(false) }
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
        }
        return
    }

    val pagerState = rememberPagerState(pageCount = { shuffledVideos.size })
    val settledPage = pagerState.settledPage.coerceIn(0, (shuffledVideos.size - 1).coerceAtLeast(0))
    val settledVideo = shuffledVideos.getOrNull(settledPage)
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

    // Caption & Save-to-Collection Sheet + Floating Toast State
    var activeCaptionMediaId by remember { mutableStateOf<Long?>(null) }
    var activeSaveCollectionMediaId by remember { mutableStateOf<Long?>(null) }
    var showSavedFloatingToast by remember { mutableStateOf(false) }
    var savedToastTrigger by remember { mutableIntStateOf(0) }
    var savedToastMediaId by remember { mutableLongStateOf(-1L) }

    LaunchedEffect(showSavedFloatingToast, savedToastTrigger) {
        if (showSavedFloatingToast) {
            delay(3000L)
            showSavedFloatingToast = false
        }
    }

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

    val pauseReelPlayback: () -> Unit = {
        isPausedByTap = true
        exoPlayer.playWhenReady = false
        exoPlayer.pause()
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

    // Load video ONLY when settledMediaId actually changes (never on Like/Bookmark/Caption updates!)
    LaunchedEffect(settledMediaId, settledMediaUri) {
        if (settledMediaId != -1L && settledMediaUri.isNotBlank()) {
            errorRetryCount = 0
            isPausedByTap = false
            isFastForward2x = false
            renderedFirstFrameMediaId = -1L
            playbackProgress = 0f
            showSavedFloatingToast = false
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

    val triggerReelsRefresh: () -> Unit = {
        if (!isRefreshing) {
            coroutineScope.launch {
                isRefreshing = true
                val previousLeadId = settledMediaId
                runCatching { galleryRepository.performQuickRefreshSync() }
                avoidLeadMediaId = previousLeadId
                refreshSeed = System.nanoTime()
                if (shuffledVideos.isNotEmpty()) {
                    runCatching { pagerState.scrollToPage(0) }
                }
                if (shuffledVideos.size == 1 && settledMediaUri.isNotBlank()) {
                    isPausedByTap = false
                    exoPlayer.seekTo(0L)
                    exoPlayer.playWhenReady = true
                    exoPlayer.play()
                }
                delay(250)
                isRefreshing = false
            }
        }
    }

    LaunchedEffect(Unit) {
        TabRefreshManager.refreshEvents.collect { tab ->
            if (tab == RefreshableTab.REELS) {
                triggerReelsRefresh()
            }
        }
    }

    val flingBehavior = PagerDefaults.flingBehavior(
        state = pagerState,
        pagerSnapDistance = PagerSnapDistance.atMost(1),
        snapAnimationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        )
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(IgBlack)
            .testTag("reels_screen")
    ) {
        InstaPullToRefreshBox(
            isRefreshing = isRefreshing,
            enabled = pagerState.currentPage == 0,
            canStartPull = {
                pagerState.currentPage == 0 && pagerState.currentPageOffsetFraction == 0f
            },
            onRefresh = triggerReelsRefresh,
            modifier = Modifier.fillMaxSize()
        ) {
            VerticalPager(
                state = pagerState,
                key = { page -> shuffledVideos[page].media.mediaId },
                flingBehavior = flingBehavior,
                beyondViewportPageCount = 1,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val video = shuffledVideos[page]
                val media = video.media
                val currentMedia by rememberUpdatedState(media)
                val isSettledPage = (page == settledPage)
                val isFirstFrameReady = isSettledPage && (renderedFirstFrameMediaId == media.mediaId)

                // Smooth crossfade of thumbnail shield ONLY after ExoPlayer has rendered the first frame
                val thumbnailAlpha by animateFloatAsState(
                    targetValue = if (isFirstFrameReady) 0f else 1f,
                    animationSpec = tween(durationMillis = 160),
                    label = "thumbnail_shield_alpha"
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clipToBounds()
                        .background(IgBlack)
                        .pointerInput(media.mediaId) {
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
                                    val latestMedia = currentMedia
                                    if (!latestMedia.isFavorite) {
                                        coroutineScope.launch {
                                            galleryRepository.toggleFavorite(latestMedia)
                                        }
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
                            videoWidth = activeVideoWidth.takeIf { it > 0 } ?: media.width,
                            videoHeight = activeVideoHeight.takeIf { it > 0 } ?: media.height,
                            isFitMode = uiState.isFitMode,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Thumbnail Shield: stays visible until ExoPlayer renders first frame -> 0% flicker!
                    if (thumbnailAlpha > 0.01f) {
                        AsyncImage(
                            model = media.uri,
                            contentDescription = media.displayName,
                            contentScale = contentScaleMode,
                            modifier = Modifier
                                .fillMaxSize()
                                .alpha(thumbnailAlpha)
                                .background(IgBlack)
                        )
                    }

                    // 2.4 Center Play / Pause Overlay (Matches Instagram Reference: 68.dp dark translucent circle + 30.dp white Play icon)
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
                                .background(Color.Black.copy(alpha = 0.52f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Paused",
                                tint = Color.White,
                                modifier = Modifier.size(30.dp)
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
                            .height(220.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.88f))
                                )
                            )
                    )

                    // 2.5 Clean Bottom-Left Overlay (Author + Date + Interactive Caption, No Music Ticker)
                    ReelsBottomOverlay(
                        video = video,
                        onOpenCaptionEditor = {
                            pauseReelPlayback()
                            activeCaptionMediaId = media.mediaId
                        },
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 16.dp, end = 76.dp, bottom = 28.dp)
                    )

                    // 2.3 Right-Side Vertical Action Rail (padding(end = 14.dp, bottom = 24.dp), spacedBy(22.dp))
                    ReelsActionRail(
                        media = media,
                        isMuted = uiState.isMuted,
                        heartColor = heartTheme.primaryColor,
                        onToggleFavorite = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            coroutineScope.launch {
                                galleryRepository.toggleFavorite(media)
                            }
                        },
                        onLongPressFavorite = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            showHeartThemeSheet = true
                        },
                        onOpenCaptionEditor = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            pauseReelPlayback()
                            activeCaptionMediaId = media.mediaId
                        },
                        onToggleBookmark = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            val wasBookmarked = media.isBookmarked
                            coroutineScope.launch {
                                galleryRepository.toggleBookmark(media)
                            }
                            if (!wasBookmarked) {
                                savedToastMediaId = media.mediaId
                                savedToastTrigger++
                                showSavedFloatingToast = true
                            } else {
                                showSavedFloatingToast = false
                            }
                        },
                        onLongPressBookmark = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (!media.isBookmarked) {
                                coroutineScope.launch {
                                    galleryRepository.toggleBookmark(media)
                                }
                            }
                            showSavedFloatingToast = false
                            pauseReelPlayback()
                            activeSaveCollectionMediaId = media.mediaId
                        },
                        onShareVideo = {
                            shareVideo(context, media.uri)
                        },
                        onToggleMute = {
                            viewModel.toggleMute()
                        },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 14.dp, bottom = 24.dp)
                    )
                }
            }
        }

        // 2.4 Redesigned Top Bar (padding(horizontal = 16.dp, vertical = 14.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: "Reels" (22.sp, FontWeight.Bold, Color.White) — no downward chevron
            Text(
                text = "Reels",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier
                    .bounceClick { triggerReelsRefresh() }
                    .testTag("reels_header_title")
            )

            // Right (spacedBy(10.dp)): Fit / Fill Aspect Ratio Button + Liked Reels Filter Button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Fit / Fill Aspect Ratio Button (matte squircle icon+label pill, height = 32.dp, zero border)
                Row(
                    modifier = Modifier
                        .height(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF26262A).copy(alpha = 0.85f))
                        .bounceClick { viewModel.toggleFitMode() }
                        .padding(horizontal = 10.dp)
                        .testTag("reels_aspect_ratio_button"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (uiState.isFitMode) Icons.Outlined.AspectRatio else Icons.Outlined.CropFree,
                        contentDescription = if (uiState.isFitMode) "Fit Mode" else "Fill Mode",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (uiState.isFitMode) "Fit" else "Fill",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }

                // Liked Reels Filter Button (32.dp squircle button, RoundedCornerShape(8.dp))
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF26262A).copy(alpha = 0.85f))
                        .combinedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.toggleFavoritesFilter()
                            },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                showHeartThemeSheet = true
                            }
                        )
                        .testTag("reels_liked_filter_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (uiState.onlyFavorites) FilledExtendedIcons.Favorite else OutlinedExtendedIcons.FavoriteBorder,
                        contentDescription = "Filter Liked Reels",
                        tint = if (uiState.onlyFavorites) heartTheme.primaryColor else Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Floating "Saved • Add to Collection ▸" Bottom Toast Bar (3000ms)
        AnimatedVisibility(
            visible = showSavedFloatingToast,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 22.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF18181E).copy(alpha = 0.96f))
                    .border(0.5.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(12.dp))
                    .clickable {
                        showSavedFloatingToast = false
                        pauseReelPlayback()
                        val targetId = if (savedToastMediaId != -1L) savedToastMediaId else settledMediaId
                        if (targetId != -1L) {
                            activeSaveCollectionMediaId = targetId
                        }
                    }
                    .padding(horizontal = 14.dp, vertical = 11.dp)
                    .testTag("reels_saved_floating_toast"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Saved",
                    fontSize = 13.5.sp,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Add to Collection ▸",
                    fontSize = 13.5.sp,
                    color = Color(0xFF0095F6),
                    fontWeight = FontWeight.Bold
                )
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

        val captionTargetVideo = remember(shuffledVideos, activeCaptionMediaId) {
            activeCaptionMediaId?.let { targetId ->
                shuffledVideos.firstOrNull { it.media.mediaId == targetId }
            }
        }

        if (captionTargetVideo != null) {
            val formattedDate = remember(captionTargetVideo.media.dateAddedSeconds) {
                SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
                    .format(Date(captionTargetVideo.media.dateAddedSeconds * 1000L))
            }
            CaptionEditorSheet(
                media = captionTargetVideo.media,
                primaryAuthorName = "Reels",
                formattedDate = formattedDate,
                onSaveCaption = { newCaption ->
                    coroutineScope.launch {
                        galleryRepository.updateMediaCaption(captionTargetVideo.media.mediaId, newCaption)
                    }
                },
                onDismiss = { activeCaptionMediaId = null }
            )
        }

        val collectionTargetVideo = remember(shuffledVideos, activeSaveCollectionMediaId) {
            activeSaveCollectionMediaId?.let { targetId ->
                shuffledVideos.firstOrNull { it.media.mediaId == targetId }
            }
        }

        if (collectionTargetVideo != null) {
            SaveToCollectionSheet(
                media = collectionTargetVideo.media,
                onDismiss = { activeSaveCollectionMediaId = null }
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

/**
 * Clean, zero-ripple, spring-bouncing action rail icon composable.
 * Completely eliminates the transparent square ripple box when tapping Like/Comment/Save/Share.
 */
@kotlin.OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReelActionButton(
    icon: ImageVector,
    label: String? = null,
    tint: Color = Color.White,
    iconSize: Dp = 28.dp,
    hasDotBadge: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.90f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "reel_action_scale"
    )

    val clickModifier = if (onLongClick != null) {
        Modifier
            .scale(scale)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            )
    } else {
        Modifier.bounceClick(onClick = onClick)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.then(clickModifier)
    ) {
        Box(
            modifier = Modifier.size(28.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label ?: "Reel action",
                tint = tint,
                modifier = Modifier.size(iconSize)
            )
            if (hasDotBadge) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF0095F6))
                )
            }
        }
        if (label != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.9f)
            )
        }
    }
}

@kotlin.OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AnimatedReelHeartButton(
    isFavorite: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val scale = remember { Animatable(1f) }
    val ringProgress = remember { Animatable(0f) }
    var prevFavorite by remember { mutableStateOf(isFavorite) }

    LaunchedEffect(isFavorite) {
        if (isFavorite != prevFavorite) {
            prevFavorite = isFavorite
            if (isFavorite) {
                launch {
                    ringProgress.snapTo(0f)
                    ringProgress.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing)
                    )
                }
                scale.animateTo(0.72f, tween(65, easing = LinearEasing))
                scale.animateTo(
                    targetValue = 1.32f,
                    animationSpec = spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium)
                )
                scale.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMediumLow)
                )
            } else {
                scale.animateTo(0.82f, tween(80))
                scale.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMedium)
                )
            }
        }
    }

    val tintColor by animateColorAsState(
        targetValue = if (isFavorite) activeColor else Color.White,
        animationSpec = tween(durationMillis = 200),
        label = "reelHeartTint"
    )

    Box(
        modifier = modifier
            .size(42.dp)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        // Subtle expanding pulse ring on like
        if (ringProgress.value in 0.01f..0.99f) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val p = ringProgress.value
                drawCircle(
                    color = activeColor.copy(alpha = (1f - p) * 0.55f),
                    radius = (size.minDimension * 0.28f) + (size.minDimension * 0.35f * p),
                    style = Stroke(width = 2.dp.toPx() * (1f - p * 0.5f))
                )
            }
        }

        Icon(
            imageVector = if (isFavorite) FilledExtendedIcons.Favorite else OutlinedExtendedIcons.FavoriteBorder,
            contentDescription = "Like",
            tint = tintColor,
            modifier = Modifier
                .size(28.dp)
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                }
        )
    }
}

@Composable
fun ReelsActionRail(
    media: MediaEntity,
    isMuted: Boolean,
    heartColor: Color,
    onToggleFavorite: () -> Unit,
    onLongPressFavorite: () -> Unit,
    onOpenCaptionEditor: () -> Unit,
    onToggleBookmark: () -> Unit,
    onLongPressBookmark: () -> Unit,
    onShareVideo: () -> Unit,
    onToggleMute: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        // 1. Heart (Like) Button — Elastic Spring Pop & Pulse Ring, Zero Text Label
        AnimatedReelHeartButton(
            isFavorite = media.isFavorite,
            activeColor = heartColor,
            onClick = onToggleFavorite,
            onLongClick = onLongPressFavorite,
            modifier = Modifier.testTag("reel_like_button_${media.mediaId}")
        )

        // 2. Comment / Caption Button (NEW)
        ReelActionButton(
            icon = OutlinedExtendedIcons.ChatBubbleOutline,
            label = null,
            tint = Color.White,
            iconSize = 27.dp,
            hasDotBadge = !media.customCaption.isNullOrBlank(),
            onClick = onOpenCaptionEditor,
            modifier = Modifier.testTag("reel_caption_button_${media.mediaId}")
        )

        // 3. Bookmark (Save) Button
        ReelActionButton(
            icon = if (media.isBookmarked) FilledExtendedIcons.Bookmark else OutlinedExtendedIcons.BookmarkBorder,
            label = null,
            tint = Color.White,
            iconSize = 28.dp,
            onClick = onToggleBookmark,
            onLongClick = onLongPressBookmark,
            modifier = Modifier.testTag("reel_bookmark_button_${media.mediaId}")
        )

        // 4. Share Paper Plane Button
        ReelActionButton(
            icon = OutlinedExtendedIcons.Send,
            label = null,
            tint = Color.White,
            iconSize = 26.dp,
            onClick = onShareVideo,
            modifier = Modifier.testTag("reel_share_button_${media.mediaId}")
        )

        // 5. Mute / Unmute Audio Button inside 36.dp dark translucent circle (no square thumbnail below)
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color(0xFF18181C).copy(alpha = 0.75f))
                .bounceClick { onToggleMute() }
                .testTag("reel_mute_button_${media.mediaId}"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isMuted) Icons.Outlined.VolumeOff else Icons.Outlined.VolumeUp,
                contentDescription = if (isMuted) "Unmute" else "Mute",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun ReelsBottomOverlay(
    video: MediaWithAuthors,
    onOpenCaptionEditor: () -> Unit,
    modifier: Modifier = Modifier
) {
    val media = video.media
    val formattedDate = remember(media.dateAddedSeconds) {
        val date = Date(media.dateAddedSeconds * 1000L)
        val sdf = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
        sdf.format(date)
    }

    val cleanStem = remember(media.displayName) {
        media.displayName.substringBeforeLast('.').trim()
    }
    val isCleanHumanTitle = remember(media.displayName, cleanStem) {
        cleanStem.isNotEmpty() &&
            !isMachineGeneratedFilename(media.displayName) &&
            !cleanStem.contains('[') &&
            !cleanStem.contains(']')
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.Start
    ) {
        // 2.5 Clean Author & Date Header: 34.dp circular Sunset gradient badge + "@Reels" + " • $formattedDate"
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF141418))
                    .border(1.8.dp, IgStoryGradient, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = OutlinedExtendedIcons.Movie,
                    contentDescription = "Reels",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Text(
                text = "@Reels",
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = " • $formattedDate",
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.78f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Interactive Caption Line (padding(top = 8.dp))
        Box(
            modifier = Modifier
                .padding(top = 8.dp)
                .bounceClick { onOpenCaptionEditor() }
                .testTag("reel_bottom_caption_${media.mediaId}")
        ) {
            when {
                !media.customCaption.isNullOrBlank() -> {
                    Text(
                        text = media.customCaption,
                        fontSize = 14.sp,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                isCleanHumanTitle -> {
                    Text(
                        text = cleanStem,
                        fontSize = 13.5.sp,
                        color = Color.White.copy(alpha = 0.9f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                else -> {
                    Text(
                        text = "Add a caption...",
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.65f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
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
