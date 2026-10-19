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
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
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
import com.example.data.local.entity.MediaWithAuthors
import com.example.ui.components.OverlappingAvatars
import com.example.ui.theme.IgBlack
import com.example.ui.theme.IgBlue
import com.example.ui.theme.IgBorderNav
import com.example.ui.theme.IgRed
import com.example.ui.theme.IgSurfaceModal
import com.example.ui.theme.IgSurfacePill
import com.example.ui.theme.IgTextPrimary
import com.example.ui.theme.IgTextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(UnstableApi::class)
@kotlin.OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReelsScreen(
    viewModel: ReelsViewModel,
    onViewProfilesClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val videos = uiState.videos
    var selectedVideoForInfo by remember { mutableStateOf<MediaWithAuthors?>(null) }

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
                    text = when {
                        uiState.onlyFavorites -> "No Favorite Reels Yet"
                        uiState.selectedPersonId != null -> "No Reels for Selected Profile"
                        else -> "No Videos Found"
                    },
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = IgTextPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = when {
                        uiState.onlyFavorites -> "Double-tap any Reel or tap the heart icon to save your favorite videos."
                        uiState.selectedPersonId != null -> "Try selecting another person filter or tap below to view all Reels."
                        else -> "Videos in your device storage will appear here as auto-looping Reels."
                    },
                    fontSize = 13.sp,
                    color = IgTextSecondary,
                    textAlign = TextAlign.Center
                )
                if (uiState.onlyFavorites || uiState.selectedPersonId != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(IgBlue)
                            .clickable {
                                if (uiState.onlyFavorites) viewModel.toggleFavoritesFilter()
                                if (uiState.selectedPersonId != null) viewModel.selectPersonFilter(null)
                            }
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
    var showDoubleTapHeart by remember { mutableStateOf(false) }
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
    DisposableEffect(exoPlayer, settledMediaId) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                renderedFirstFrameMediaId = settledMediaId
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

    LaunchedEffect(showDoubleTapHeart) {
        if (showDoubleTapHeart) {
            delay(750)
            showDoubleTapHeart = false
        }
    }

    // Update mute state without resetting playback
    LaunchedEffect(uiState.isMuted) {
        exoPlayer.volume = if (uiState.isMuted) 0f else 1f
    }

    // Load video ONLY when settledMediaId actually changes (never on Like/Bookmark/Tag updates!)
    LaunchedEffect(settledMediaId) {
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

    // Poll playback progress smoothly
    LaunchedEffect(exoPlayer, isScrubbing) {
        while (isActive) {
            if (!isScrubbing) {
                val dur = exoPlayer.duration
                val pos = exoPlayer.currentPosition
                if (dur > 0) {
                    durationMs = dur
                    currentPositionMs = pos.coerceAtLeast(0L)
                    playbackProgress = (pos.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
                }
            }
            delay(80)
        }
    }

    // Handle Android lifecycle pause/resume
    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> exoPlayer.pause()
                Lifecycle.Event.ON_RESUME -> if (!isPausedByTap) exoPlayer.play()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
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
                            onDoubleTap = {
                                showDoubleTapHeart = true
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
                                if (exoPlayer.isPlaying) {
                                    exoPlayer.pause()
                                    isPausedByTap = true
                                } else {
                                    exoPlayer.play()
                                    isPausedByTap = false
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
                        .padding(top = 96.dp)
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

                // Double-Tap Heart Burst Overlay
                AnimatedVisibility(
                    visible = isSettledPage && showDoubleTapHeart,
                    enter = scaleIn(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        ),
                        initialScale = 0.3f
                    ) + fadeIn(),
                    exit = scaleOut(targetScale = 1.2f) + fadeOut(),
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = "Liked",
                        tint = Color.White.copy(alpha = 0.92f),
                        modifier = Modifier.size(96.dp)
                    )
                }

                // Top scrim gradient
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(128.dp)
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent)
                            )
                        )
                )

                // Bottom scrim gradient
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(256.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))
                            )
                        )
                )

                // Bottom-Left Overlay Details
                ReelsBottomOverlay(
                    video = video,
                    onViewProfilesClick = {
                        val firstPerson = video.authors.firstOrNull()?.personId ?: -1L
                        onViewProfilesClick(firstPerson)
                    },
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 16.dp, bottom = 36.dp)
                )

                // Right-Side Vertical Action Rail
                ReelsActionRail(
                    video = video,
                    isMuted = uiState.isMuted,
                    onToggleFavorite = { viewModel.toggleFavorite(video.media.mediaId, video.media.isFavorite) },
                    onToggleBookmark = { viewModel.toggleBookmark(video.media.mediaId, video.media.isBookmarked) },
                    onToggleMute = { viewModel.toggleMute() },
                    onMoreClick = { selectedVideoForInfo = video },
                    onViewPeopleClick = {
                        val firstPerson = video.authors.firstOrNull()?.personId ?: -1L
                        onViewProfilesClick(firstPerson)
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 14.dp, bottom = 36.dp)
                )
            }
        }

        // Top Overlay Bar + Person Filter Strip
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(top = 12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
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
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
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

                    IconButton(onClick = { viewModel.toggleFavoritesFilter() }) {
                        Icon(
                            imageVector = if (uiState.onlyFavorites) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = "Filter Favorite Reels",
                            tint = if (uiState.onlyFavorites) IgRed else Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // Person Filter Chips Row
            if (uiState.allPeople.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item(key = "filter_all") {
                        ReelPersonChip(
                            label = "All",
                            isSelected = uiState.selectedPersonId == null,
                            onClick = { viewModel.selectPersonFilter(null) }
                        )
                    }
                    items(
                        items = uiState.allPeople,
                        key = { it.personId }
                    ) { person ->
                        ReelPersonChip(
                            label = "@${person.name}",
                            isSelected = uiState.selectedPersonId == person.personId,
                            onClick = { viewModel.selectPersonFilter(person.personId) }
                        )
                    }
                    item(key = "filter_other") {
                        ReelPersonChip(
                            label = "@Other",
                            isSelected = uiState.selectedPersonId == -1L,
                            onClick = { viewModel.selectPersonFilter(-1L) }
                        )
                    }
                }
            }
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

    // Reel Details & Tag Management Modal Bottom Sheet
    selectedVideoForInfo?.let { infoVideo ->
        val sheetState = rememberModalBottomSheetState()
        val liveVideo = videos.find { it.media.mediaId == infoVideo.media.mediaId } ?: infoVideo
        val authors = liveVideo.authors
        val taggedIds = authors.map { it.personId }.toSet()
        val untaggedPeople = uiState.allPeople.filter { !taggedIds.contains(it.personId) }

        ModalBottomSheet(
            onDismissRequest = { selectedVideoForInfo = null },
            sheetState = sheetState,
            containerColor = IgSurfaceModal
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "Reel Info & People Tags",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = IgTextPrimary
                )
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = if (authors.isEmpty()) "Tagged: @Other (No specific person tagged)" else "Tagged in this Reel:",
                    fontSize = 13.sp,
                    color = IgTextSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (authors.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        authors.forEach { person ->
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(IgBlack)
                                    .border(1.dp, IgBorderNav, RoundedCornerShape(50))
                                    .clickable {
                                        selectedVideoForInfo = null
                                        onViewProfilesClick(person.personId)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                OverlappingAvatars(
                                    authors = listOf(person),
                                    avatarSize = 22.dp
                                )
                                Text(
                                    text = "@${person.name}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = IgTextPrimary
                                )
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Untag",
                                    tint = IgTextSecondary,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable {
                                            viewModel.removePersonFromMedia(liveVideo.media.mediaId, person.personId)
                                        }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }

                if (untaggedPeople.isNotEmpty()) {
                    HorizontalDivider(color = IgBorderNav, thickness = 0.5.dp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Tag a person:",
                        fontSize = 13.sp,
                        color = IgTextSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        untaggedPeople.forEach { person ->
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(IgSurfacePill)
                                    .border(1.dp, IgBorderNav, RoundedCornerShape(50))
                                    .clickable {
                                        viewModel.tagPersonInMedia(liveVideo.media.mediaId, person.personId)
                                    }
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Tag",
                                    tint = IgBlue,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "@${person.name}",
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = IgTextPrimary
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }

                HorizontalDivider(color = IgBorderNav, thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "File: ${liveVideo.media.displayName}",
                    fontSize = 12.5.sp,
                    color = IgTextSecondary
                )
                val durationSec = (liveVideo.media.durationMs ?: 0L) / 1000L
                if (durationSec > 0) {
                    Text(
                        text = "Duration: ${durationSec}s",
                        fontSize = 12.5.sp,
                        color = IgTextSecondary
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

/**
 * Renders ExoPlayer video into a hardware-accelerated TextureView with matrix aspect-ratio scaling.
 * Unlike SurfaceView, TextureView participates directly in Compose's render node tree,
 * preventing black surface-punch flickering during Pager swipes or screen transitions.
 */
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
                addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                    applyVideoScaleMatrix(this, videoWidth, videoHeight, isFitMode)
                }
            }
        },
        update = { textureView ->
            exoPlayer.setVideoTextureView(textureView)
            applyVideoScaleMatrix(textureView, videoWidth, videoHeight, isFitMode)
        },
        onReset = { textureView ->
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

@Composable
private fun ReelPersonChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (isSelected) IgBlue else Color.Black.copy(alpha = 0.45f))
            .border(
                width = 1.dp,
                color = if (isSelected) IgBlue else Color.White.copy(alpha = 0.28f),
                shape = RoundedCornerShape(50)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )
    }
}

@Composable
fun ReelsActionRail(
    video: MediaWithAuthors,
    isMuted: Boolean,
    onToggleFavorite: () -> Unit,
    onToggleBookmark: () -> Unit,
    onToggleMute: () -> Unit,
    onMoreClick: () -> Unit,
    onViewPeopleClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val authorsCount = video.authors.size

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Heart Like button with label
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.clickable(onClick = onToggleFavorite)
        ) {
            Icon(
                imageVector = if (video.media.isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                contentDescription = "Like",
                tint = if (video.media.isFavorite) IgRed else Color.White,
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

        // 2. Tagged People Button with blue count badge & label
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.clickable(onClick = onViewPeopleClick)
        ) {
            Box(contentAlignment = Alignment.TopEnd) {
                Icon(
                    imageVector = Icons.Outlined.Person,
                    contentDescription = "Tagged People",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
                if (authorsCount > 0) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(IgBlue)
                            .border(1.5.dp, Color.Black, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = authorsCount.toString(),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (authorsCount > 0) "$authorsCount People" else "@Other",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
        }

        // 3. Bookmark / Save button
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

        // 4. Share button
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

        // 5. Mute / Unmute Speaker button
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

        // 6. 3-dots more options (opens Reel Info & Tag sheet)
        IconButton(
            onClick = onMoreClick,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "More options",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }

        // 7. Miniature 32x32px album thumbnail
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(2.dp, Color.White, RoundedCornerShape(8.dp))
                .clickable(onClick = onMoreClick)
        ) {
            AsyncImage(
                model = video.media.uri,
                contentDescription = "Audio thumbnail",
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
    onViewProfilesClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val authors = video.authors
    val authorHandles = remember(authors) {
        if (authors.isEmpty()) "@Other"
        else authors.take(2).joinToString(" and ") { "@${it.name}" }
    }

    val formattedDate = remember(video.media.dateAddedSeconds) {
        val date = Date(video.media.dateAddedSeconds * 1000L)
        val sdf = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
        sdf.format(date)
    }

    Column(
        modifier = modifier.width(280.dp),
        horizontalAlignment = Alignment.Start
    ) {
        // Authors Row + View Profiles Pill
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OverlappingAvatars(
                authors = authors,
                avatarSize = 36.dp,
                overlap = (-12).dp
            )

            Text(
                text = authorHandles,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .clickable(onClick = onViewProfilesClick)
            )

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.4f))
                    .border(1.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                    .clickable(onClick = onViewProfilesClick)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = if (authors.isEmpty()) "View @Other" else "View Profile",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Caption Text
        Text(
            text = "${video.media.displayName} • $formattedDate",
            fontSize = 13.sp,
            color = Color.White.copy(alpha = 0.95f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Audio Marquee Ticker Pill
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
                contentDescription = "Music",
                tint = Color.White,
                modifier = Modifier.size(13.dp)
            )
            Box(modifier = Modifier.width(180.dp)) {
                Text(
                    text = "${video.media.displayName} • Original Audio • ${video.media.displayName} • Original Audio",
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
