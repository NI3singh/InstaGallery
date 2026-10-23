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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.local.entity.MediaWithAuthors
import com.example.data.local.entity.PersonEntity
import com.example.ui.theme.IgBlack
import com.example.ui.theme.IgBlue
import com.example.ui.theme.IgBorderNav
import com.example.ui.theme.IgRed
import com.example.ui.theme.IgSurfaceModal
import com.example.ui.theme.IgSurfacePill
import com.example.ui.theme.IgTextPrimary
import com.example.ui.theme.IgTextSecondary
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PostCard(
    item: MediaWithAuthors,
    onAuthorClick: (Long) -> Unit,
    onToggleFavorite: (Long, Boolean) -> Unit,
    onToggleBookmark: (Long, Boolean) -> Unit,
    allPeople: List<PersonEntity> = emptyList(),
    onTagPerson: ((Long, Long) -> Unit)? = null,
    onUntagPerson: ((Long, Long) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val media = item.media
    val authors = item.authors

    var showMenu by remember { mutableStateOf(false) }
    var showInfoSheet by remember { mutableStateOf(false) }
    var showTagOverlay by remember { mutableStateOf(false) }
    var showDoubleTapHeart by remember { mutableStateOf(false) }

    var decodedAspectRatio by remember(media.mediaId) {
        val dbRatio = if (media.width > 0 && media.height > 0 && !(media.width == 1080 && media.height == 1350)) {
            (media.width.toFloat() / media.height.toFloat()).coerceIn(0.56f, 1.91f)
        } else {
            0.8f // 4:5 fallback until Coil decodes true intrinsic size
        }
        mutableFloatStateOf(dbRatio)
    }

    var forceCropMode by rememberSaveable(media.mediaId) { mutableStateOf(false) }

    LaunchedEffect(showDoubleTapHeart) {
        if (showDoubleTapHeart) {
            delay(750)
            showDoubleTapHeart = false
        }
    }

    val formattedDate = remember(media.dateAddedSeconds) {
        val date = Date(media.dateAddedSeconds * 1000L)
        val sdf = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
        sdf.format(date)
    }

    val authorHandles = remember(authors) {
        if (authors.isEmpty()) {
            "@Other"
        } else {
            when (authors.size) {
                1 -> "@${authors[0].name}"
                2 -> "@${authors[0].name} and @${authors[1].name}"
                else -> {
                    val firstTwo = authors.take(2).joinToString(", ") { "@${it.name}" }
                    val remaining = authors.size - 2
                    if (remaining == 1) "$firstTwo and @${authors[2].name}"
                    else "$firstTwo and $remaining others"
                }
            }
        }
    }

    val taggedNames = remember(authors) {
        if (authors.isEmpty()) "@Other"
        else authors.joinToString(", ") { "@${it.name}" }
    }

    val containerAspectRatio = if (forceCropMode) 4f / 5f else decodedAspectRatio
    val imageContentScale = if (forceCropMode) ContentScale.Crop else ContentScale.Fit

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(IgBlack)
            .testTag("post_card_${media.mediaId}")
    ) {
        // Post Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        if (authors.isNotEmpty()) {
                            onAuthorClick(authors[0].personId)
                        } else {
                            onAuthorClick(-1L)
                        }
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                OverlappingAvatars(
                    authors = authors,
                    avatarSize = 32.dp,
                    overlap = (-10).dp
                )

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Text(
                        text = authorHandles,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = IgTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = formattedDate,
                        fontSize = 11.sp,
                        color = IgTextSecondary,
                        maxLines = 1
                    )
                }
            }

            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More options",
                        tint = IgTextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier.background(IgSurfaceModal)
                ) {
                    DropdownMenuItem(
                        text = { Text("Details & Manage Tags", color = IgTextPrimary) },
                        onClick = {
                            showMenu = false
                            showInfoSheet = true
                        }
                    )
                    if (authors.isNotEmpty()) {
                        DropdownMenuItem(
                            text = { Text("View @${authors[0].name} profile", color = IgTextPrimary) },
                            onClick = {
                                showMenu = false
                                onAuthorClick(authors[0].personId)
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Share media", color = IgTextPrimary) },
                        onClick = {
                            showMenu = false
                            shareMedia(context, media.uri, media.isVideo)
                        }
                    )
                }
            }
        }

        // Media Viewport (True Intrinsic Aspect Ratio with Uncropped ContentScale.Fit by default)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(containerAspectRatio)
                .background(Color(0xFF0A0A0A))
                .pointerInput(media.mediaId, media.isFavorite, authors.size) {
                    detectTapGestures(
                        onDoubleTap = {
                            showDoubleTapHeart = true
                            if (!media.isFavorite) {
                                onToggleFavorite(media.mediaId, false)
                            }
                        },
                        onTap = {
                            if (authors.isNotEmpty()) {
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

            // Double-Tap Big Animated Heart Overlay
            androidx.compose.animation.AnimatedVisibility(
                visible = showDoubleTapHeart,
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
        }

        // Instagram Post Action Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Heart Like Button with subtle spring pop
                val heartScale by animateFloatAsState(
                    targetValue = if (media.isFavorite) 1.15f else 1.0f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                    label = "heart_scale"
                )
                val heartTint by animateColorAsState(
                    targetValue = if (media.isFavorite) IgRed else Color.White,
                    label = "heart_tint"
                )

                IconButton(
                    onClick = { onToggleFavorite(media.mediaId, media.isFavorite) },
                    modifier = Modifier
                        .size(26.dp)
                        .scale(heartScale)
                        .testTag("like_button_${media.mediaId}")
                ) {
                    Icon(
                        imageVector = if (media.isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = if (media.isFavorite) "Liked" else "Like",
                        tint = heartTint,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Comment / Details & Tags Button
                IconButton(
                    onClick = { showInfoSheet = true },
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ChatBubbleOutline,
                        contentDescription = "Details & Tags",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Share Button
                IconButton(
                    onClick = { shareMedia(context, media.uri, media.isVideo) },
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Send,
                        contentDescription = "Share",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Bookmark Button
            IconButton(
                onClick = { onToggleBookmark(media.mediaId, media.isBookmarked) },
                modifier = Modifier
                    .size(26.dp)
                    .testTag("bookmark_button_${media.mediaId}")
            ) {
                Icon(
                    imageVector = if (media.isBookmarked) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
                    contentDescription = if (media.isBookmarked) "Saved" else "Save",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // Captions & Metadata
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 14.dp, bottom = 18.dp)
        ) {
            val taggedText = buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = IgTextPrimary)) {
                    append("Tagged: ")
                }
                withStyle(SpanStyle(fontWeight = FontWeight.Normal, color = IgTextPrimary)) {
                    append(taggedNames)
                }
            }
            Text(
                text = taggedText,
                fontSize = 13.5.sp,
                lineHeight = 18.sp,
                modifier = Modifier.clickable {
                    if (authors.isNotEmpty()) {
                        onAuthorClick(authors[0].personId)
                    } else {
                        showInfoSheet = true
                    }
                }
            )

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = formattedDate,
                fontSize = 11.5.sp,
                color = IgTextSecondary
            )
        }
    }

    // Modal Bottom Sheet showing photo metadata, tagged people & manual tagging
    if (showInfoSheet) {
        val sheetState = rememberModalBottomSheetState()
        val taggedIds = remember(authors) { authors.map { it.personId }.toSet() }
        val untaggedPeople = remember(allPeople, taggedIds) {
            allPeople.filter { !taggedIds.contains(it.personId) }
        }

        ModalBottomSheet(
            onDismissRequest = { showInfoSheet = false },
            sheetState = sheetState,
            containerColor = IgSurfaceModal
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "Media Info & People Tags",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = IgTextPrimary
                )
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = if (authors.isEmpty()) "No people tagged yet" else "People in this memory:",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
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
                                        showInfoSheet = false
                                        onAuthorClick(person.personId)
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
                                if (onUntagPerson != null) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove tag",
                                        tint = IgTextSecondary,
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clickable {
                                                onUntagPerson(media.mediaId, person.personId)
                                            }
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Manual tagging section if there are other people available
                if (onTagPerson != null && untaggedPeople.isNotEmpty()) {
                    HorizontalDivider(color = IgBorderNav, thickness = 0.5.dp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Tag a person:",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
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
                                        onTagPerson(media.mediaId, person.personId)
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
                    Spacer(modifier = Modifier.height(16.dp))
                }

                HorizontalDivider(color = IgBorderNav, thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "File: ${media.displayName}",
                    fontSize = 12.5.sp,
                    color = IgTextSecondary
                )
                if (media.width > 0 && media.height > 0) {
                    Text(
                        text = "Resolution: ${media.width} × ${media.height}",
                        fontSize = 12.5.sp,
                        color = IgTextSecondary
                    )
                }
                Text(
                    text = "Type: ${media.mimeType}",
                    fontSize = 12.5.sp,
                    color = IgTextSecondary
                )
                Text(
                    text = "Captured: $formattedDate",
                    fontSize = 12.5.sp,
                    color = IgTextSecondary
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
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
