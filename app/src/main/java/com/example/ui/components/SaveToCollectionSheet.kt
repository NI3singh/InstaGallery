// InstaGallery v1.0
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.OutlinedExtendedIcons
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
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
    val allCrossRefs by repository.getAllCollectionCrossRefsFlow().collectAsStateWithLifecycle(initialValue = emptyList())
    val allPhotos by repository.photosWithAuthors.collectAsStateWithLifecycle(initialValue = emptyList())

    val mediaById = remember(allPhotos) {
        allPhotos.associate { it.media.mediaId to it.media }
    }

    var showNewBoardInput by remember { mutableStateOf(false) }
    var newBoardName by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF121216),
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 20.dp)
                .testTag("save_to_collection_sheet")
        ) {
            // Top Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    AsyncImage(
                        model = media.uri,
                        contentDescription = media.displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF1E1E24))
                    )
                    Column {
                        Text(
                            text = "Save to Collection",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Organize this photo into local boards",
                            fontSize = 12.sp,
                            color = Color(0xFF8E8E93)
                        )
                    }
                }

                // "+ New" Pill Button
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFF1C2536))
                        .border(1.dp, IgBlue.copy(alpha = 0.5f), RoundedCornerShape(50))
                        .clickable { showNewBoardInput = !showNewBoardInput }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .testTag("new_collection_toggle_button"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "New Collection",
                        tint = IgBlue,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "New",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = IgBlue
                    )
                }
            }

            // Inline New Collection Input Row
            AnimatedVisibility(
                visible = showNewBoardInput,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = newBoardName,
                        onValueChange = { newBoardName = it },
                        placeholder = {
                            Text(
                                text = "e.g., Goa Trip, Certificates...",
                                fontSize = 13.sp,
                                color = Color(0xFF71717A)
                            )
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = IgBlue,
                            unfocusedBorderColor = Color(0xFF2E2E36),
                            cursorColor = IgBlue,
                            focusedContainerColor = Color(0xFF1A1A20),
                            unfocusedContainerColor = Color(0xFF1A1A20)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("new_collection_name_input")
                    )

                    Button(
                        onClick = {
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
                        },
                        enabled = newBoardName.trim().isNotEmpty(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = IgBlue,
                            contentColor = Color.White
                        ),
                        modifier = Modifier.height(50.dp)
                    ) {
                        Text(
                            text = "Create",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            HorizontalDivider(
                color = Color(0xFF222228),
                thickness = 0.5.dp,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            // Collections List
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(
                    items = collections,
                    key = { it.collectionId }
                ) { collection ->
                    val isSelected = selectedCollectionIds.contains(collection.collectionId)
                    val refsForCollection = remember(allCrossRefs, collection.collectionId) {
                        allCrossRefs
                            .filter { it.collectionId == collection.collectionId }
                            .sortedByDescending { it.addedAt }
                    }
                    val itemCount = refsForCollection.size
                    val coverMedia = refsForCollection.firstNotNullOfOrNull { mediaById[it.mediaId] }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isSelected) Color(0xFF1A2232) else Color(0xFF18181E))
                            .border(
                                width = 1.dp,
                                color = if (isSelected) IgBlue.copy(alpha = 0.6f) else Color(0xFF24242C),
                                shape = RoundedCornerShape(14.dp)
                            )
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
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                            .testTag("collection_row_${collection.collectionId}"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            // 48.dp rounded cover thumbnail or folder glyph
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF23232B)),
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
                                        tint = Color(0xFF8E8E93),
                                        modifier = Modifier.size(22.dp)
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
                                    text = if (itemCount == 1) "1 photo" else "$itemCount photos",
                                    fontSize = 12.sp,
                                    color = Color(0xFF8E8E93)
                                )
                            }
                        }

                        // Right circular selection indicator
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) IgBlue else Color.Transparent)
                                .border(
                                    width = 1.5.dp,
                                    color = if (isSelected) IgBlue else Color(0xFF52525B),
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
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
        containerColor = Color(0xFF121216),
        contentColor = Color.White
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
                color = Color(0xFF222228),
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
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF22222A))
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
