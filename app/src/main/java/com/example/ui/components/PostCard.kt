package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
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
import com.example.ui.theme.IgBlack
import com.example.ui.theme.IgElevated
import com.example.ui.theme.IgRed
import com.example.ui.theme.IgSurfaceModal
import com.example.ui.theme.IgTextPrimary
import com.example.ui.theme.IgTextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostCard(
    item: MediaWithAuthors,
    onAuthorClick: (Long) -> Unit,
    onToggleFavorite: (Long, Boolean) -> Unit,
    onToggleBookmark: (Long, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val media = item.media
    val authors = item.authors

    var showMenu by remember { mutableStateOf(false) }
    var showInfoSheet by remember { mutableStateOf(false) }

    val formattedDate = remember(media.dateAddedSeconds) {
        val date = Date(media.dateAddedSeconds * 1000L)
        val sdf = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
        sdf.format(date)
    }

    val authorHandles = remember(authors) {
        if (authors.isEmpty()) {
            "From your gallery"
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
        if (authors.isEmpty()) "No faces detected"
        else authors.joinToString(", ") { "@${it.name}" }
    }

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
                        text = { Text("Details & Tags", color = IgTextPrimary) },
                        onClick = {
                            showMenu = false
                            showInfoSheet = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Share photo", color = IgTextPrimary) },
                        onClick = {
                            showMenu = false
                            shareMedia(context, media.uri)
                        }
                    )
                }
            }
        }

        // Media Viewport (4:5 Aspect Ratio portrait standard)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 5f)
                .background(IgElevated)
        ) {
            AsyncImage(
                model = media.uri,
                contentDescription = media.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 5f)
            )

            // Circular Tag Badge (bottom-left)
            if (authors.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.75f))
                        .clickable { showInfoSheet = true },
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

                // Comment / Details Button
                IconButton(
                    onClick = { showInfoSheet = true },
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ChatBubbleOutline,
                        contentDescription = "Details",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Share Button
                IconButton(
                    onClick = { shareMedia(context, media.uri) },
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
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = formattedDate,
                fontSize = 11.5.sp,
                color = IgTextSecondary
            )
        }
    }

    // Modal Bottom Sheet showing photo metadata & tagged people
    if (showInfoSheet) {
        val sheetState = rememberModalBottomSheetState()
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
                    text = "Photo Info & Tagged People",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = IgTextPrimary
                )
                Spacer(modifier = Modifier.height(12.dp))

                if (authors.isNotEmpty()) {
                    Text(
                        text = "People in this photo:",
                        fontSize = 13.sp,
                        color = IgTextSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        authors.forEach { person ->
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(IgBlack)
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
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                Text(
                    text = "File: ${media.displayName}",
                    fontSize = 12.5.sp,
                    color = IgTextSecondary
                )
                Text(
                    text = "Resolution: ${media.width} x ${media.height}",
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

private fun shareMedia(context: Context, uriString: String) {
    try {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_STREAM, Uri.parse(uriString))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share photo"))
    } catch (e: Exception) {
        val textIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, uriString)
        }
        context.startActivity(Intent.createChooser(textIntent, "Share photo"))
    }
}
