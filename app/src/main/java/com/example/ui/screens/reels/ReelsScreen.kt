package com.example.ui.screens.reels

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
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
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
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

    // Single ExoPlayer instance shared across the Reels pager
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            playWhenReady = true
        }
    }

    var playbackProgress by remember { mutableFloatStateOf(0f) }
    var isPausedByTap by remember { mutableStateOf(false) }
    var showDoubleTapHeart by remember { mutableStateOf(false) }

    LaunchedEffect(showDoubleTapHeart) {
        if (showDoubleTapHeart) {
            delay(750)
            showDoubleTapHeart = false
        }
    }

    // Update mute state
    LaunchedEffect(uiState.isMuted) {
        exoPlayer.volume = if (uiState.isMuted) 0f else 1f
    }

    // Attach currently settled page media item to player
    LaunchedEffect(pagerState.settledPage, videos) {
        if (videos.isNotEmpty() && pagerState.settledPage < videos.size) {
            val currentVideo = videos[pagerState.settledPage]
            isPausedByTap = false
            exoPlayer.stop()
            exoPlayer.setMediaItem(MediaItem.fromUri(currentVideo.media.uri))
            exoPlayer.prepare()
            exoPlayer.play()
        }
    }

    // Poll playback progress
    LaunchedEffect(exoPlayer) {
        while (isActive) {
            val duration = exoPlayer.duration
            val position = exoPlayer.currentPosition
            if (duration > 0) {
                playbackProgress = (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
            }
            delay(100)
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(IgBlack)
            .testTag("reels_screen")
    ) {
        VerticalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val video = videos[page]
            val isCurrentPage = (page == pagerState.settledPage)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(video.media.mediaId, video.media.isFavorite) {
                        detectTapGestures(
                            onDoubleTap = {
                                showDoubleTapHeart = true
                                if (!video.media.isFavorite) {
                                    viewModel.toggleFavorite(video.media.mediaId, false)
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
                if (isCurrentPage) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                useController = false
                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                player = exoPlayer
                                layoutParams = FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    AsyncImage(
                        model = video.media.uri,
                        contentDescription = video.media.displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Paused Indicator Overlay
                AnimatedVisibility(
                    visible = isCurrentPage && isPausedByTap,
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

                // Double-Tap Heart Burst Overlay
                AnimatedVisibility(
                    visible = isCurrentPage && showDoubleTapHeart,
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

                // Top scrim gradient (112.dp)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(112.dp)
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)
                            )
                        )
                )

                // Bottom scrim gradient (256.dp)
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
                        .padding(start = 16.dp, bottom = 68.dp)
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
                        .padding(end = 14.dp, bottom = 72.dp)
                )
            }
        }

        // Top Overlay Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, start = 16.dp, end = 16.dp)
                .align(Alignment.TopCenter),
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

            IconButton(onClick = { viewModel.toggleFavoritesFilter() }) {
                Icon(
                    imageVector = if (uiState.onlyFavorites) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = "Filter Favorite Reels",
                    tint = if (uiState.onlyFavorites) IgRed else Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // Slim 2.dp Playback Progress Bar at bottom of viewport
        val animatedProgress by animateFloatAsState(
            targetValue = playbackProgress,
            label = "reel_progress"
        )
        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .align(Alignment.BottomCenter),
            color = Color.White,
            trackColor = Color.White.copy(alpha = 0.2f)
        )
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
                    text = if (authors.isEmpty()) "No people tagged in this Reel" else "Tagged in this Reel:",
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
                text = if (authorsCount > 0) "$authorsCount People" else "People",
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
        if (authors.isEmpty()) "@From your Reels"
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
                modifier = Modifier.weight(1f, fill = false)
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
                    text = "View Profiles",
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
