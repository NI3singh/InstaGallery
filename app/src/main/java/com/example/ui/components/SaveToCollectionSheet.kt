// InstaGallery v2.0
package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.OutlinedExtendedIcons
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.local.entity.MediaEntity
import com.example.data.local.entity.PersonEntity
import com.example.data.repository.GalleryRepository
import com.example.ui.theme.IgBlack
import com.example.ui.theme.IgBlue
import com.example.ui.theme.IgStoryGradient
import com.example.ui.theme.IgTextPrimary
import com.example.ui.theme.IgTextSecondary
import java.io.File
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaveToCollectionSheet(
    media: MediaEntity,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val repository = remember(context) { GalleryRepository(context) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(Unit) {
        repository.ensureDefaultCollectionsSeeded()
    }

    val collections by repository.allCollectionsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val selectedCollectionIds by remember(media.mediaId) {
        repository.getCollectionIdsForMediaFlow(media.mediaId)
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    var showNewBoardInput by remember { mutableStateOf(false) }
    var newBoardName by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF121214),
        contentColor = Color.White,
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = Color(0xFF3A3A3E))
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 20.dp)
                .testTag("save_to_collection_sheet")
        ) {
            // Header Row (padding(horizontal = 16.dp, vertical = 8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1C1C20))
                    ) {
                        AsyncImage(
                            model = media.uri,
                            contentDescription = media.displayName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        if (media.isVideo) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(3.dp)
                                    .size(12.dp)
                            )
                        }
                    }
                    Text(
                        text = "Save to",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                // Right: Matte squircle button (Color(0xFF26262A), RoundedCornerShape(8.dp), height = 32.dp, padding(horizontal = 12.dp)) "+ New board"
                Row(
                    modifier = Modifier
                        .height(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF26262A))
                        .bounceClick { showNewBoardInput = !showNewBoardInput }
                        .padding(horizontal = 12.dp)
                        .testTag("new_collection_toggle_button"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "New board",
                        tint = Color.White,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "New board",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }

            // Inline New Board Input Row (borderless #1C1C20 rounded container + solid #0095F6 squircle Create button)
            AnimatedVisibility(
                visible = showNewBoardInput,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF1C1C20))
                            .padding(horizontal = 14.dp)
                            .testTag("new_collection_name_input"),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (newBoardName.isEmpty()) {
                            Text(
                                text = "Board name...",
                                fontSize = 14.sp,
                                color = Color(0xFF8E8E93)
                            )
                        }
                        BasicTextField(
                            value = newBoardName,
                            onValueChange = { newBoardName = it },
                            singleLine = true,
                            textStyle = TextStyle(
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            cursorBrush = SolidColor(Color(0xFF0095F6)),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    val canCreate = newBoardName.trim().isNotEmpty()
                    Box(
                        modifier = Modifier
                            .height(42.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (canCreate) Color(0xFF0095F6) else Color(0xFF0095F6).copy(alpha = 0.4f)
                            )
                            .bounceClick {
                                val trimmed = newBoardName.trim()
                                if (trimmed.isNotEmpty()) {
                                    scope.launch {
                                        val newId = repository.createCollection(trimmed)
                                        if (newId > 0L) {
                                            repository.toggleMediaInCollection(
                                                collectionId = newId,
                                                mediaId = media.mediaId,
                                                isCurrentlyInCollection = false
                                            )
                                        }
                                        newBoardName = ""
                                        showNewBoardInput = false
                                    }
                                }
                            }
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Create",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.5.sp,
                            color = Color.White
                        )
                    }
                }
            }

            HorizontalDivider(
                color = Color(0xFF1F1F22),
                thickness = 0.5.dp,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            // Collections List
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(
                    items = collections,
                    key = { it.collectionId }
                ) { collection ->
                    val isSelected = selectedCollectionIds.contains(collection.collectionId)
                    val collectionMedia by remember(repository, collection.collectionId) {
                        repository.getMediaForCollectionFlow(collection.collectionId)
                    }.collectAsStateWithLifecycle(initialValue = emptyList())
                    val coverMedia = collectionMedia.firstOrNull()
                    val subtitleText = remember(collectionMedia) {
                        formatCollectionSubtitle(collectionMedia)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                scope.launch {
                                    repository.toggleMediaInCollection(
                                        collectionId = collection.collectionId,
                                        mediaId = media.mediaId,
                                        isCurrentlyInCollection = isSelected
                                    )
                                }
                            }
                            .padding(horizontal = 4.dp, vertical = 8.dp)
                            .testTag("collection_row_${collection.collectionId}"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            // 48.dp RoundedCornerShape(10.dp) cover thumbnail or #1C1C20 box with subtle bookmark glyph
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF1C1C20)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (coverMedia != null) {
                                    AsyncImage(
                                        model = coverMedia.uri,
                                        contentDescription = collection.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Icon(
                                        imageVector = OutlinedExtendedIcons.BookmarkBorder,
                                        contentDescription = collection.name,
                                        tint = Color.White.copy(alpha = 0.35f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            Column {
                                Text(
                                    text = collection.name,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = subtitleText,
                                    fontSize = 12.sp,
                                    color = Color(0xFF8E8E93)
                                )
                            }
                        }

                        // 24.dp circular selection indicator (#0095F6 filled circle with white checkmark when saved)
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) Color(0xFF0095F6) else Color.Transparent)
                                .border(
                                    width = 1.5.dp,
                                    color = if (isSelected) Color(0xFF0095F6) else Color(0xFF3A3A3E),
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = Color.White,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun formatCollectionSubtitle(items: List<MediaEntity>): String {
    if (items.isEmpty()) return "No saved items"
    val photoCount = items.count { !it.isVideo }
    val reelCount = items.count { it.isVideo }
    return when {
        photoCount > 0 && reelCount > 0 -> "$photoCount photos • $reelCount reels"
        reelCount > 0 -> "$reelCount ${if (reelCount == 1) "reel" else "reels"}"
        else -> "$photoCount ${if (photoCount == 1) "photo" else "photos"}"
    }
}

/**
 * ModalBottomSheet listing every detected person in a Collab / Group photo so the user can jump
 * directly to any co-star's profile in 1 tap.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollabAuthorsSheet(
    authors: List<PersonEntity>,
    onSelectPerson: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF121214),
        contentColor = Color.White,
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = Color(0xFF3A3A3E))
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
                .testTag("collab_authors_sheet")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "In This Photo (${authors.size})",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Co-occurring people detected in this memory",
                    fontSize = 12.5.sp,
                    color = Color(0xFF8E8E93)
                )
            }

            HorizontalDivider(
                color = Color(0xFF1F1F22),
                thickness = 0.5.dp,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = authors,
                    key = { it.personId }
                ) { person ->
                    val cleanHandle = person.name.removePrefix("@")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF18181E))
                            .border(1.dp, Color(0xFF26262E), RoundedCornerShape(14.dp))
                            .clickable {
                                onDismiss()
                                onSelectPerson(person.personId)
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                            .testTag("collab_person_row_${person.personId}"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(IgStoryGradient),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(IgBlack),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (person.avatarCropPath.isNotBlank() && File(person.avatarCropPath).exists()) {
                                        AsyncImage(
                                            model = File(person.avatarCropPath),
                                            contentDescription = cleanHandle,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clip(CircleShape)
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Outlined.Face,
                                            contentDescription = cleanHandle,
                                            tint = IgTextSecondary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }

                            Column {
                                Text(
                                    text = "@$cleanHandle",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = IgTextPrimary
                                )
                                Text(
                                    text = "${person.faceCount} photos on device",
                                    fontSize = 12.sp,
                                    color = IgTextSecondary
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF26262A))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "View Profile →",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = IgBlue
                            )
                        }
                    }
                }
            }
        }
    }
}
