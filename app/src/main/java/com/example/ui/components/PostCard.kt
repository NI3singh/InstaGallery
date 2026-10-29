// InstaGallery v1.0
package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilledExtendedIcons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.OutlinedExtendedIcons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.local.OnboardingPreferences
import com.example.data.local.entity.MediaWithAuthors
import com.example.data.local.entity.PersonEntity
import com.example.ui.theme.IgBlack
import com.example.ui.theme.IgBlue
import com.example.ui.theme.IgStoryGradient
import com.example.ui.theme.IgTextSecondary
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun PostCard(
    item: MediaWithAuthors,
    onAuthorClick: (Long) -> Unit,
    onToggleFavorite: (Long, Boolean) -> Unit,
    onToggleBookmark: (Long, Boolean) -> Unit,
    onUpdateCaption: (String?) -> Unit = {},
    allPeople: List<PersonEntity> = emptyList(),
    onTagPerson: ((Long, Long) -> Unit)? = null,
    onUntagPerson: ((Long, Long) -> Unit)? = null,
    onImageClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val media = item.media
    val authors = item.authors

    remember(context) {
        OnboardingPreferences.init(context)
        true
    }
    val heartTheme by OnboardingPreferences.heartThemeFlow.collectAsState()
    val defaultCropMode by OnboardingPreferences.defaultCropModeFlow.collectAsState()

    var showInspectorSheet by remember { mutableStateOf(false) }
    var showCaptionSheet by remember { mutableStateOf(false) }
    var showInstaCardStudio by remember { mutableStateOf(false) }
    var showTagOverlay by remember { mutableStateOf(false) }
    var showHeartThemeSheet by remember { mutableStateOf(false) }
    var showSaveToCollectionSheet by remember { mutableStateOf(false) }
    var showCollabAuthorsSheet by remember { mutableStateOf(false) }
    var showSavedFloatingBanner by remember { mutableStateOf(false) }
    val activeBursts = remember { mutableStateListOf<HeartBurstInstance>() }

    LaunchedEffect(showSavedFloatingBanner) {
        if (showSavedFloatingBanner) {
            delay(3500L)
            showSavedFloatingBanner = false
        }
    }

    var decodedAspectRatio by remember(media.mediaId) {
        val dbRatio = if (media.width > 0 && media.height > 0 && !(media.width == 1080 && media.height == 1350)) {
            (media.width.toFloat() / media.height.toFloat()).coerceIn(0.56f, 1.91f)
        } else {
            0.8f // 4:5 fallback until Coil decodes true intrinsic size
        }
        mutableFloatStateOf(dbRatio)
    }

    var forceCropMode by rememberSaveable(media.mediaId, defaultCropMode) { mutableStateOf(defaultCropMode) }

    val formattedDate = remember(media.dateAddedSeconds) {
        val date = Date(media.dateAddedSeconds * 1000L)
        val sdf = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
        sdf.format(date)
    }

    val formattedShortDate = remember(media.dateAddedSeconds) {
        val date = Date(media.dateAddedSeconds * 1000L)
        val sdf = SimpleDateFormat("MMM d", Locale.getDefault())
        sdf.format(date)
    }

    val visibleAuthors = remember(authors) {
        val visible = authors.filter { !it.isHiddenToOther }
        visible.ifEmpty { authors }
    }

    val primaryAuthorName = remember(visibleAuthors) {
        if (visibleAuthors.isEmpty()) "Other" else visibleAuthors[0].name.removePrefix("@")
    }

    val primaryAuthorAvatarPath = remember(visibleAuthors) {
        visibleAuthors.firstOrNull()?.avatarCropPath
    }

    val authorHandles = remember(visibleAuthors) {
        if (visibleAuthors.isEmpty()) {
            "@Other"
        } else {
            when (visibleAuthors.size) {
                1 -> {
                    val p = visibleAuthors[0]
                    "@${p.name.removePrefix("@")}"
                }
                2 -> "@${visibleAuthors[0].name.removePrefix("@")} & @${visibleAuthors[1].name.removePrefix("@")}"
                else -> {
                    val firstTwo = visibleAuthors.take(2).joinToString(", ") { "@${it.name.removePrefix("@")}" }
                    val remaining = visibleAuthors.size - 2
                    "$firstTwo +$remaining"
                }
            }
        }
    }

    val currentIsFavorite by rememberUpdatedState(media.isFavorite)
    val currentHasAuthors by rememberUpdatedState(visibleAuthors.isNotEmpty())
    val currentOnImageClick by rememberUpdatedState(onImageClick)

    val containerAspectRatio = if (forceCropMode) 4f / 5f else decodedAspectRatio
    val imageContentScale = if (forceCropMode) ContentScale.Crop else ContentScale.Fit

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(IgBlack)
            .testTag("post_card_${media.mediaId}")
    ) {
        // 1. Refined Post Header (Supports Single Author & Multi-Person Collab Attribution)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        when {
                            visibleAuthors.size >= 2 -> showCollabAuthorsSheet = true
                            visibleAuthors.size == 1 -> onAuthorClick(visibleAuthors[0].personId)
                            else -> onAuthorClick(-1L)
                        }
                    }
                    .testTag("post_header_authors_${media.mediaId}"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (visibleAuthors.size >= 2) {
                    // Collab Avatar Stack: 2 overlapping 30.dp circular face avatars
                    Box(
                        modifier = Modifier
                            .width(42.dp)
                            .height(38.dp)
                    ) {
                        val firstPerson = visibleAuthors[0]
                        val secondPerson = visibleAuthors[1]

                        // First Avatar at top-left (0.dp, 0.dp)
                        CollabHeaderAvatar(
                            person = firstPerson,
                            modifier = Modifier
                                .size(30.dp)
                                .align(Alignment.TopStart)
                        )

                        // Second Avatar offset by (12.dp, 8.dp) with 2.dp #000000 cutout border and sunset ring
                        Box(
                            modifier = Modifier
                                .offset(x = 12.dp, y = 8.dp)
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(IgBlack),
                            contentAlignment = Alignment.Center
                        ) {
                            CollabHeaderAvatar(
                                person = secondPerson,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                } else {
                    OverlappingAvatars(
                        authors = visibleAuthors,
                        avatarSize = 34.dp,
                        overlap = (-10).dp
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Text(
                    text = buildAnnotatedString {
                        withStyle(
                            SpanStyle(
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        ) {
                            append(authorHandles)
                        }
                        withStyle(
                            SpanStyle(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Normal,
                                color = Color(0xFF8E8E93)
                            )
                        ) {
                            append(" • $formattedShortDate")
                        }
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = { showInspectorSheet = true },
                modifier = Modifier
                    .size(32.dp)
                    .testTag("more_menu_button_${media.mediaId}")
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Post Inspector & Options",
                    tint = IgTextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // 2. Media Viewport (True Intrinsic Aspect Ratio with Tap-Coordinate Heart Burst)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(containerAspectRatio)
                .background(Color(0xFF0A0A0A))
                .pointerInput(media.mediaId) {
                    detectTapGestures(
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
                            if (!currentIsFavorite) {
                                onToggleFavorite(media.mediaId, false)
                            }
                        },
                        onTap = {
                            if (currentOnImageClick != null) {
                                currentOnImageClick?.invoke()
                            } else if (currentHasAuthors) {
                                showTagOverlay = !showTagOverlay
                            }
                        }
                    )
                }
        ) {
            AsyncImage(
                model = media.uri,
                contentDescription = media.displayName,
                contentScale = imageContentScale,
                onSuccess = { success ->
                    val intrinsicSize = success.painter.intrinsicSize
                    if (intrinsicSize.width > 0f && intrinsicSize.height > 0f &&
                        intrinsicSize.width.isFinite() && intrinsicSize.height.isFinite()
                    ) {
                        decodedAspectRatio = (intrinsicSize.width / intrinsicSize.height).coerceIn(0.56f, 1.91f)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Floating Instagram-style Person Tag Bubbles on Photo
            androidx.compose.animation.AnimatedVisibility(
                visible = showTagOverlay && authors.isNotEmpty(),
                enter = fadeIn() + scaleIn(initialScale = 0.85f),
                exit = fadeOut() + scaleOut(targetScale = 0.85f),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 48.dp, bottom = 12.dp, end = 48.dp)
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    authors.forEach { person ->
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black.copy(alpha = 0.82f))
                                .border(0.5.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                                .clickable { onAuthorClick(person.personId) }
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "@${person.name}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            // Circular Tag Badge (bottom-left)
            if (authors.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.78f))
                        .clickable { showTagOverlay = !showTagOverlay },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Person,
                        contentDescription = "Tagged people",
                        tint = Color.White,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            // Circular Fit / Crop Aspect Ratio Toggle Button (bottom-right)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.70f))
                    .clickable { forceCropMode = !forceCropMode }
                    .testTag("aspect_ratio_toggle_${media.mediaId}"),
                contentAlignment = Alignment.Center
            ) {
                FitCropFrameIcon(
                    isCropMode = forceCropMode,
                    modifier = Modifier.size(14.dp)
                )
            }

            // Top overlay: Pure-Canvas Tap-Coordinate Heart Burst Engine
            TapHeartBurstOverlay(
                bursts = activeBursts,
                heartTheme = heartTheme,
                onBurstFinished = { id -> activeBursts.removeAll { it.id == id } }
            )

            // Floating "Saved to bookmarks • Add to Collection ▸" Bottom Overlay Banner (3500ms)
            androidx.compose.animation.AnimatedVisibility(
                visible = showSavedFloatingBanner,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF18181E).copy(alpha = 0.95f))
                        .border(0.5.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(12.dp))
                        .clickable {
                            showSavedFloatingBanner = false
                            showSaveToCollectionSheet = true
                        }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .testTag("saved_floating_banner_${media.mediaId}"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Saved to bookmarks",
                        fontSize = 13.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Add to Collection ▸",
                        fontSize = 13.sp,
                        color = Color(0xFF0095F6),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 3. Polished Bottom Action Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // Heart Like Button with scale bounce + long-press HeartThemePickerSheet
                val heartScale by animateFloatAsState(
                    targetValue = if (media.isFavorite) 1.15f else 1.0f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                    label = "heart_scale"
                )
                val heartTint by animateColorAsState(
                    targetValue = if (media.isFavorite) heartTheme.primaryColor else Color.White,
                    label = "heart_tint"
                )

                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .scale(heartScale)
                        .combinedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onToggleFavorite(media.mediaId, media.isFavorite)
                            },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                showHeartThemeSheet = true
                            }
                        )
                        .testTag("like_button_${media.mediaId}"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (media.isFavorite) FilledExtendedIcons.Favorite else OutlinedExtendedIcons.FavoriteBorder,
                        contentDescription = if (media.isFavorite) "Liked" else "Like",
                        tint = heartTint,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Comment Bubble Button (with #0095F6 dot indicator if customCaption exists)
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .bounceClick { showCaptionSheet = true }
                        .testTag("comment_button_${media.mediaId}"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = OutlinedExtendedIcons.ChatBubbleOutline,
                        contentDescription = "Add or Edit Memory Note",
                        tint = Color.White,
                        modifier = Modifier.size(23.dp)
                    )
                    if (!media.customCaption.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(IgBlue)
                        )
                    }
                }

                // Share Paper Plane Button
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .bounceClick { shareMedia(context, media.uri, media.isVideo) }
                        .testTag("share_button_${media.mediaId}"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = OutlinedExtendedIcons.Send,
                        contentDescription = "Share Original",
                        tint = Color.White,
                        modifier = Modifier.size(23.dp)
                    )
                }

                // InstaCard 1080p Snapshot Studio Button
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .bounceClick { showInstaCardStudio = true }
                        .testTag("snapshot_studio_button_${media.mediaId}"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = SnapshotStudioIcon,
                        contentDescription = "InstaCard Snapshot Studio",
                        tint = Color.White,
                        modifier = Modifier.size(23.dp)
                    )
                }
            }

            // Bookmark Button (Single tap toggles bookmark + shows 3.5s Add to Collection banner; Long press opens SaveToCollectionSheet)
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            val wasBookmarked = media.isBookmarked
                            onToggleBookmark(media.mediaId, wasBookmarked)
                            showSavedFloatingBanner = !wasBookmarked
                        },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (!media.isBookmarked) {
                                onToggleBookmark(media.mediaId, false)
                            }
                            showSavedFloatingBanner = false
                            showSaveToCollectionSheet = true
                        }
                    )
                    .testTag("bookmark_button_${media.mediaId}"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (media.isBookmarked) FilledExtendedIcons.Bookmark else OutlinedExtendedIcons.BookmarkBorder,
                    contentDescription = if (media.isBookmarked) "Saved" else "Save",
                    tint = Color.White,
                    modifier = Modifier.size(23.dp)
                )
            }
        }

        // 4. Smart Post Caption Footer (Custom Memory Note / Meaningful Title / Subtle Prompt)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
        ) {
            val hasCustomCaption = !media.customCaption.isNullOrBlank()
            val isMachineFilename = remember(media.displayName) {
                isMachineGeneratedFilename(media.displayName)
            }

            if (hasCustomCaption) {
                val captionText = buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = Color.White)) {
                        append("@$primaryAuthorName")
                    }
                    withStyle(SpanStyle(fontWeight = FontWeight.Normal, color = Color.White)) {
                        append("  ${media.customCaption}")
                    }
                }
                Text(
                    text = captionText,
                    fontSize = 13.5.sp,
                    lineHeight = 18.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .clickable { showCaptionSheet = true }
                        .testTag("post_caption_text_${media.mediaId}")
                )
            } else if (!isMachineFilename) {
                val cleanDisplayName = remember(media.displayName) {
                    media.displayName.substringBeforeLast('.')
                }
                val captionText = buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = Color.White)) {
                        append("@$primaryAuthorName")
                    }
                    withStyle(SpanStyle(fontWeight = FontWeight.Normal, color = Color(0xFFE5E5EA))) {
                        append("  $cleanDisplayName")
                    }
                }
                Text(
                    text = captionText,
                    fontSize = 13.5.sp,
                    lineHeight = 18.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .clickable { showCaptionSheet = true }
                        .testTag("post_caption_text_${media.mediaId}")
                )
            } else {
                Text(
                    text = "Add a memory note...",
                    fontSize = 12.5.sp,
                    color = Color(0xFF737373),
                    modifier = Modifier
                        .clickable { showCaptionSheet = true }
                        .testTag("add_memory_note_prompt_${media.mediaId}")
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = formattedDate,
                fontSize = 11.sp,
                color = Color(0xFF737373)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        HorizontalDivider(
            color = Color(0xFF141416),
            thickness = 0.5.dp
        )
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

    if (showCaptionSheet) {
        CaptionEditorSheet(
            media = media,
            primaryAuthorName = primaryAuthorName,
            formattedDate = formattedDate,
            onSaveCaption = { newCaption ->
                onUpdateCaption(newCaption)
            },
            onDismiss = { showCaptionSheet = false }
        )
    }

    if (showInstaCardStudio) {
        InstaCardStudioSheet(
            media = media,
            primaryAuthorName = primaryAuthorName,
            authorAvatarPath = primaryAuthorAvatarPath,
            heartColor = heartTheme.primaryColor,
            onDismiss = { showInstaCardStudio = false }
        )
    }

    if (showInspectorSheet) {
        PostInspectorSheet(
            media = media,
            authors = authors,
            allPeople = allPeople,
            formattedDate = formattedDate,
            onOpenInstaCardStudio = { showInstaCardStudio = true },
            onOpenCaptionEditor = { showCaptionSheet = true },
            onOpenHeartThemePicker = { showHeartThemeSheet = true },
            onShareOriginal = { shareMedia(context, media.uri, media.isVideo) },
            onAuthorClick = onAuthorClick,
            onTagPerson = onTagPerson,
            onUntagPerson = onUntagPerson,
            onToggleBookmark = {
                val wasBookmarked = media.isBookmarked
                onToggleBookmark(media.mediaId, wasBookmarked)
                showSavedFloatingBanner = !wasBookmarked
            },
            onDismiss = { showInspectorSheet = false }
        )
    }

    if (showSaveToCollectionSheet) {
        SaveToCollectionSheet(
            media = media,
            onDismiss = { showSaveToCollectionSheet = false }
        )
    }

    if (showCollabAuthorsSheet) {
        CollabAuthorsSheet(
            authors = visibleAuthors,
            onSelectPerson = onAuthorClick,
            onDismiss = { showCollabAuthorsSheet = false }
        )
    }
}

@Composable
private fun CollabHeaderAvatar(
    person: PersonEntity,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(IgStoryGradient)
            .padding(1.5.dp)
            .clip(CircleShape)
            .background(IgBlack),
        contentAlignment = Alignment.Center
    ) {
        val cropPath = person.avatarCropPath
        if (cropPath.isNotBlank() && File(cropPath).exists()) {
            AsyncImage(
                model = File(cropPath),
                contentDescription = person.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
            )
        } else {
            Icon(
                imageVector = Icons.Outlined.Face,
                contentDescription = person.name,
                tint = IgTextSecondary,
                modifier = Modifier.size(15.dp)
            )
        }
    }
}

/**
 * Crisp Instagram-style 4-corner Fit/Expand frame icon drawn with Canvas.
 */
@Composable
private fun FitCropFrameIcon(
    isCropMode: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.6.dp.toPx()
        val arm = w * 0.32f
        val inset = if (isCropMode) w * 0.12f else 0f

        val left = inset
        val top = inset
        val right = w - inset
        val bottom = h - inset

        // Top-left corner
        drawLine(
            color = Color.White,
            start = Offset(left, top),
            end = Offset(left + arm, top),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color.White,
            start = Offset(left, top),
            end = Offset(left, top + arm),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )

        // Top-right corner
        drawLine(
            color = Color.White,
            start = Offset(right, top),
            end = Offset(right - arm, top),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color.White,
            start = Offset(right, top),
            end = Offset(right, top + arm),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )

        // Bottom-left corner
        drawLine(
            color = Color.White,
            start = Offset(left, bottom),
            end = Offset(left + arm, bottom),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color.White,
            start = Offset(left, bottom),
            end = Offset(left, bottom - arm),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )

        // Bottom-right corner
        drawLine(
            color = Color.White,
            start = Offset(right, bottom),
            end = Offset(right - arm, bottom),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color.White,
            start = Offset(right, bottom),
            end = Offset(right, bottom - arm),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

private fun shareMedia(context: Context, uriString: String, isVideo: Boolean) {
    try {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = if (isVideo) "video/*" else "image/*"
            putExtra(Intent.EXTRA_STREAM, Uri.parse(uriString))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share memory"))
    } catch (e: Exception) {
        val textIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, uriString)
        }
        context.startActivity(Intent.createChooser(textIntent, "Share memory"))
    }
}
