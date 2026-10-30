// InstaGallery v2.0
package com.example.ui.screens.people

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.local.entity.PersonEntity
import com.example.ui.components.IndexingStatusBanner
import com.example.ui.components.NewPeopleDetectedBanner
import com.example.ui.components.bounceClick
import com.example.ui.screens.profile.InstagramHamburgerIcon
import com.example.ui.theme.IgBlack
import com.example.ui.theme.IgBlue
import com.example.ui.theme.IgBorderNav
import com.example.ui.theme.IgElevated
import com.example.ui.theme.IgRed
import com.example.ui.theme.IgStoryGradient
import com.example.ui.theme.IgSurfaceHigh
import com.example.ui.theme.IgSurfaceModal
import com.example.ui.theme.IgTextPrimary
import com.example.ui.theme.IgTextSecondary
import com.example.ml.FaceEmbeddingExtractor
import kotlinx.coroutines.delay
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeopleDirectoryScreen(
    viewModel: PeopleViewModel,
    onPersonClick: (Long) -> Unit,
    onBackClick: () -> Unit = {},
    onOpenSetupClick: () -> Unit = {},
    onOpenSettingsClick: () -> Unit = {},
    onRescanGalleryClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showMultiMergeSheet by remember { mutableStateOf(false) }
    var preselectedMergePersonId by remember { mutableStateOf<Long?>(null) }
    var longPressedPerson by remember { mutableStateOf<PersonEntity?>(null) }
    var personToRename by remember { mutableStateOf<PersonEntity?>(null) }

    LaunchedEffect(Unit) {
        viewModel.runAutoDeduplication()
    }

    LaunchedEffect(uiState.toastMessage) {
        if (uiState.toastMessage != null) {
            delay(3500)
            viewModel.dismissToast()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("people_directory_screen")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 1. Clean Top Bar (52.dp height, #000000)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .background(Color.Black)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("people_directory_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Text(
                        text = "People",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Text(
                        text = "${uiState.people.size}",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF8E8E93)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Merge",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF0095F6),
                        modifier = Modifier
                            .bounceClick {
                                preselectedMergePersonId = null
                                showMultiMergeSheet = true
                            }
                            .padding(vertical = 4.dp, horizontal = 4.dp)
                            .testTag("merge_toggle_button")
                    )

                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .bounceClick { onOpenSettingsClick() }
                            .testTag("people_directory_settings_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        InstagramHamburgerIcon(modifier = Modifier.size(22.dp))
                    }
                }
            }

            // 2. Sleek #1C1C1E Search Bar (height = 40.dp, RoundedCornerShape(10.dp), zero border)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF1C1C1E))
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = "Search",
                        tint = Color(0xFF8E8E93),
                        modifier = Modifier.size(18.dp)
                    )
                    BasicTextField(
                        value = uiState.searchQuery,
                        onValueChange = { viewModel.onSearchQueryChange(it) },
                        singleLine = true,
                        textStyle = TextStyle(
                            fontSize = 14.sp,
                            color = Color.White
                        ),
                        cursorBrush = SolidColor(Color(0xFF0095F6)),
                        decorationBox = { innerTextField ->
                            if (uiState.searchQuery.isEmpty()) {
                                Text(
                                    text = "Search people...",
                                    fontSize = 14.sp,
                                    color = Color(0xFF8E8E93)
                                )
                            }
                            innerTextField()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("people_search_input")
                    )
                    if (uiState.searchQuery.isNotEmpty()) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear search",
                            tint = Color(0xFF8E8E93),
                            modifier = Modifier
                                .size(16.dp)
                                .clickable { viewModel.onSearchQueryChange("") }
                        )
                    }
                    Icon(
                        imageVector = Icons.Outlined.Tune,
                        contentDescription = "Sort people (${uiState.sortOrder.label})",
                        tint = Color(0xFF8E8E93),
                        modifier = Modifier
                            .size(18.dp)
                            .clickable { viewModel.cycleSortOrder() }
                    )
                }
            }

            // Native Filter Chips Row (All, Named, Unnamed, Hidden (@Other))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(
                    items = PeopleFilterTab.entries,
                    key = { it.name }
                ) { tab ->
                    val isSelected = uiState.filterTab == tab
                    val labelText = when (tab) {
                        PeopleFilterTab.ALL -> "All (${uiState.people.size})"
                        PeopleFilterTab.HIDDEN_OTHER ->
                            if (uiState.hiddenPeople.isNotEmpty()) "Hidden (@Other) (${uiState.hiddenPeople.size})"
                            else "Hidden (@Other)"
                        else -> tab.label
                    }
                    Box(
                        modifier = Modifier
                            .height(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) Color.White else Color(0xFF1C1C1E))
                            .bounceClick { viewModel.setFilterTab(tab) }
                            .padding(horizontal = 14.dp)
                            .testTag("people_filter_chip_${tab.name.lowercase()}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = labelText,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                            color = if (isSelected) Color.Black else Color(0xFFE4E4E7)
                        )
                    }
                }
            }

            // Live Background Indexing Pill
            AnimatedVisibility(
                visible = uiState.unindexedPhotoCount > 0,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black)
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    IndexingStatusBanner(unindexedCount = uiState.unindexedPhotoCount)
                }
            }

            // 4. 3-Column People Grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // "New People Detected" Banner when >= 3 photos of a new undecided person exist
                if (uiState.newUndecidedPeople.isNotEmpty()) {
                    item(span = { GridItemSpan(3) }) {
                        NewPeopleDetectedBanner(
                            newUndecidedPeople = uiState.newUndecidedPeople,
                            onReviewClick = onOpenSetupClick
                        )
                    }
                }

                // 3. Redesigned Visual Duplicate Merge Banner (Shows Both Face Avatars Side-by-Side)
                val dup = uiState.duplicatePair
                if (dup != null && uiState.filterTab != PeopleFilterTab.HIDDEN_OTHER) {
                    item(span = { GridItemSpan(3) }) {
                        VisualDuplicateMergeCard(
                            pair = dup,
                            onMerge = { viewModel.mergeDuplicatePairDirectly(dup) },
                            onDismiss = { viewModel.ignoreDuplicatePair(dup) }
                        )
                    }
                }

                // Detected people cards
                items(
                    items = uiState.filteredPeople,
                    key = { it.personId }
                ) { person ->
                    val isMe = (person.personId == uiState.myProfilePersonId)
                    PersonGridItem(
                        person = person,
                        isMyProfile = isMe,
                        isSelectionMode = false,
                        isSelected = false,
                        onClick = { onPersonClick(person.personId) },
                        onLongClick = { longPressedPerson = person }
                    )
                }

                // Final Grid Item: @Other (unassigned photos)
                if (uiState.filterTab == PeopleFilterTab.ALL || uiState.filterTab == PeopleFilterTab.HIDDEN_OTHER) {
                    item(key = "other_unassigned_bucket") {
                        OtherGridItem(
                            count = uiState.unassociatedCount,
                            onClick = { onPersonClick(-1L) }
                        )
                    }
                }
            }
        }

        // Top Toast Banner
        AnimatedVisibility(
            visible = uiState.toastMessage != null,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp, start = 16.dp, end = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(IgSurfaceHigh.copy(alpha = 0.96f))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(IgBlue),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Verified,
                        contentDescription = "Success",
                        tint = Color.White,
                        modifier = Modifier.size(15.dp)
                    )
                }
                Text(
                    text = uiState.toastMessage ?: "",
                    fontSize = 13.sp,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "Dismiss",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = IgBlue,
                    modifier = Modifier.clickable { viewModel.dismissToast() }
                )
            }
        }
    }

    // Long-press Quick Action Sheet for any Person in the Grid
    longPressedPerson?.let { targetPerson ->
        val sheetState = rememberModalBottomSheetState()
        val cleanName = targetPerson.name.removePrefix("@")
        val isCurrentlyMe = (targetPerson.personId == uiState.myProfilePersonId)
        val isHidden = targetPerson.isHiddenToOther

        ModalBottomSheet(
            onDismissRequest = { longPressedPerson = null },
            sheetState = sheetState,
            containerColor = Color(0xFF121216)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(IgStoryGradient)
                            .padding(1.5.dp)
                            .clip(CircleShape)
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        if (targetPerson.avatarCropPath.isNotBlank() && File(targetPerson.avatarCropPath).exists()) {
                            AsyncImage(
                                model = File(targetPerson.avatarCropPath),
                                contentDescription = cleanName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.Person,
                                contentDescription = cleanName,
                                tint = IgTextSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "@$cleanName",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "${targetPerson.faceCount} posts",
                            fontSize = 12.sp,
                            color = Color(0xFF8E8E93)
                        )
                    }
                }

                HorizontalDivider(color = Color(0xFF26262A), thickness = 0.5.dp)

                // 1. Set as My Profile (Me)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            val selected = targetPerson
                            longPressedPerson = null
                            viewModel.setAsMyProfile(selected)
                        }
                        .padding(horizontal = 12.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (isCurrentlyMe) "Current Primary Profile (You)" else "Set as My Profile (Me)",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF0095F6)
                    )
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Set as Me",
                        tint = Color(0xFF0095F6),
                        modifier = Modifier.size(20.dp)
                    )
                }

                HorizontalDivider(color = Color(0xFF26262A), thickness = 0.5.dp)

                // 2. Rename
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            val selected = targetPerson
                            longPressedPerson = null
                            personToRename = selected
                        }
                        .padding(horizontal = 12.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Rename",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                    )
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = "Rename",
                        tint = Color(0xFF8E8E93),
                        modifier = Modifier.size(20.dp)
                    )
                }

                HorizontalDivider(color = Color(0xFF26262A), thickness = 0.5.dp)

                // 3. Merge with...
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            val selected = targetPerson
                            longPressedPerson = null
                            preselectedMergePersonId = selected.personId
                            showMultiMergeSheet = true
                        }
                        .padding(horizontal = 12.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Merge with...",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                    )
                    Icon(
                        imageVector = Icons.Outlined.GroupAdd,
                        contentDescription = "Merge",
                        tint = Color(0xFF8E8E93),
                        modifier = Modifier.size(20.dp)
                    )
                }

                HorizontalDivider(color = Color(0xFF26262A), thickness = 0.5.dp)

                // 4. Move to @Other / Restore
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            val selected = targetPerson
                            longPressedPerson = null
                            if (isHidden) {
                                viewModel.restorePersonFromOther(selected)
                            } else {
                                viewModel.movePersonToOther(selected)
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (isHidden) "Restore from @Other" else "Move to @Other",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isHidden) Color(0xFF0095F6) else IgRed
                    )
                    Icon(
                        imageVector = if (isHidden) Icons.Default.Refresh else Icons.Outlined.DeleteOutline,
                        contentDescription = if (isHidden) "Restore" else "Move to @Other",
                        tint = if (isHidden) Color(0xFF0095F6) else IgRed,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Rename Dialog
    personToRename?.let { target ->
        var newName by remember(target.personId) { mutableStateOf(target.name.removePrefix("@")) }
        AlertDialog(
            onDismissRequest = { personToRename = null },
            containerColor = IgSurfaceModal,
            title = {
                Text(
                    text = "Rename Profile",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = IgTextPrimary
                )
            },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = IgBlue,
                        unfocusedBorderColor = IgBorderNav,
                        focusedTextColor = IgTextPrimary,
                        unfocusedTextColor = IgTextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val pid = target.personId
                        personToRename = null
                        viewModel.renamePerson(pid, newName)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = IgBlue)
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { personToRename = null }) {
                    Text("Cancel", color = IgTextSecondary)
                }
            }
        )
    }

    // Multi-Merge Face Grid Bottom Sheet
    if (showMultiMergeSheet) {
        MultiMergeFaceGridSheet(
            people = uiState.people,
            initialSelectedId = preselectedMergePersonId,
            onMergeSelected = { selectedIds, targetName ->
                showMultiMergeSheet = false
                viewModel.mergePeopleIds(selectedIds, targetName)
            },
            onDismiss = { showMultiMergeSheet = false }
        )
    }
}

@Composable
fun VisualDuplicateMergeCard(
    pair: Pair<PersonEntity, PersonEntity>,
    onMerge: () -> Unit,
    onDismiss: () -> Unit
) {
    val personA = pair.first
    val personB = pair.second
    val nameA = personA.name.removePrefix("@")
    val nameB = personB.name.removePrefix("@")
    val similarityPct = remember(personA.personId, personB.personId) {
        val vecA = FaceEmbeddingExtractor.bytesToFloatArray(personA.centroidEmbeddingBlob)
        val vecB = FaceEmbeddingExtractor.bytesToFloatArray(personB.centroidEmbeddingBlob)
        val sim = if (vecA.isNotEmpty() && vecA.size == vecB.size) {
            FaceEmbeddingExtractor.cosineSimilarity(vecA, vecB)
        } else {
            0.60f
        }
        (sim * 100f).toInt().coerceIn(52, 99)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF16161A))
            .padding(12.dp)
            .testTag("visual_duplicate_merge_banner"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left: Two overlapping 38.dp circular face avatars
        Box(
            modifier = Modifier
                .width(64.dp)
                .height(38.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color.Black)
                    .border(1.5.dp, Color(0xFF26262A), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (personA.avatarCropPath.isNotBlank() && File(personA.avatarCropPath).exists()) {
                    AsyncImage(
                        model = File(personA.avatarCropPath),
                        contentDescription = nameA,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Person,
                        contentDescription = nameA,
                        tint = IgTextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .offset(x = 24.dp)
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color.Black)
                    .border(2.dp, Color(0xFF16161A), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (personB.avatarCropPath.isNotBlank() && File(personB.avatarCropPath).exists()) {
                    AsyncImage(
                        model = File(personB.avatarCropPath),
                        contentDescription = nameB,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Person,
                        contentDescription = nameB,
                        tint = IgTextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Center: Two-line text with full weight so names never truncate prematurely
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp)
        ) {
            Text(
                text = "@$nameA & @$nameB",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${personA.faceCount + personB.faceCount} total photos • $similarityPct% match",
                fontSize = 12.sp,
                color = Color(0xFF0095F6),
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Right: Solid #0095F6 "Merge" squircle button + 32x32 matte #26262A ✕ dismiss button
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .height(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0095F6))
                    .bounceClick { onMerge() }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Merge",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF26262A))
                    .bounceClick { onDismiss() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss duplicate suggestion",
                    tint = Color(0xFF8E8E93),
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PersonGridItem(
    person: PersonEntity,
    isMyProfile: Boolean = false,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .testTag("person_grid_item_${person.personId}")
    ) {
        Box(
            modifier = Modifier.size(88.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(IgStoryGradient)
                    .then(
                        if (isSelected) Modifier.border(4.dp, IgBlue.copy(alpha = 0.5f), CircleShape)
                        else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(82.dp)
                        .clip(CircleShape)
                        .background(IgBlack),
                    contentAlignment = Alignment.Center
                ) {
                    if (person.avatarCropPath.isNotBlank() && File(person.avatarCropPath).exists()) {
                        AsyncImage(
                            model = File(person.avatarCropPath),
                            contentDescription = person.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(78.dp)
                                .clip(CircleShape)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.Person,
                            contentDescription = person.name,
                            tint = IgTextSecondary,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                }
            }

            // "You" Pill Badge on Primary User's Avatar Ring
            if (isMyProfile && !isSelected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFF0095F6))
                        .border(1.5.dp, Color.Black, RoundedCornerShape(50))
                        .padding(horizontal = 7.dp, vertical = 1.5.dp)
                ) {
                    Text(
                        text = "You",
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            // Selection Checkmark Badge
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .align(Alignment.TopEnd)
                        .clip(CircleShape)
                        .background(IgBlue)
                        .border(1.5.dp, Color.Black, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = Color.White,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "@${person.name.removePrefix("@")}",
            fontSize = 13.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = IgTextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )

        Text(
            text = "${person.faceCount} posts",
            fontSize = 11.5.sp,
            color = IgTextSecondary,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun OtherGridItem(
    count: Int,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .testTag("person_grid_item_other")
    ) {
        Box(
            modifier = Modifier.size(88.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(IgStoryGradient),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(82.dp)
                        .clip(CircleShape)
                        .background(IgElevated),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Group,
                        contentDescription = "Other",
                        tint = IgTextSecondary,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "@Other",
            fontSize = 13.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = IgTextPrimary,
            textAlign = TextAlign.Center
        )

        Text(
            text = "$count posts",
            fontSize = 11.5.sp,
            color = IgTextSecondary,
            textAlign = TextAlign.Center
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiMergeFaceGridSheet(
    people: List<PersonEntity>,
    initialSelectedId: Long? = null,
    onMergeSelected: (List<Long>, String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val selectedIds = remember(initialSelectedId) {
        mutableStateListOf<Long>().apply {
            if (initialSelectedId != null && initialSelectedId > 0L) {
                add(initialSelectedId)
            }
        }
    }
    var customMergeName by remember { mutableStateOf("") }

    val selectedPeople = remember(selectedIds.toList(), people) {
        selectedIds.mapNotNull { id -> people.firstOrNull { it.personId == id } }
    }

    LaunchedEffect(selectedPeople.firstOrNull()?.personId) {
        if (customMergeName.isBlank() && selectedPeople.isNotEmpty()) {
            customMergeName = selectedPeople.first().name.removePrefix("@")
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF121216)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .testTag("multi_merge_face_grid_sheet")
        ) {
            Text(
                text = "Merge Duplicate Profiles",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Select 2 or more profiles of the same person to combine their photos",
                fontSize = 12.5.sp,
                color = Color(0xFF8E8E93)
            )
            Spacer(modifier = Modifier.height(14.dp))

            if (selectedIds.size >= 2) {
                OutlinedTextField(
                    value = customMergeName,
                    onValueChange = { customMergeName = it },
                    label = { Text("Merged Profile Name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF0095F6),
                        unfocusedBorderColor = Color(0xFF26262A),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                )
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp)
            ) {
                items(
                    items = people,
                    key = { "merge_item_${it.personId}" }
                ) { person ->
                    val isSelected = selectedIds.contains(person.personId)
                    val cleanName = person.name.removePrefix("@")
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) Color(0xFF1E2638) else Color(0xFF18181C))
                            .border(
                                width = if (isSelected) 1.5.dp else 0.5.dp,
                                color = if (isSelected) Color(0xFF0095F6) else Color(0xFF26262A),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .bounceClick {
                                if (isSelected) {
                                    selectedIds.remove(person.personId)
                                } else {
                                    selectedIds.add(person.personId)
                                    if (customMergeName.isBlank()) {
                                        customMergeName = cleanName
                                    }
                                }
                            }
                            .padding(10.dp)
                    ) {
                        Box(
                            modifier = Modifier.size(64.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(IgStoryGradient)
                                    .padding(2.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black),
                                contentAlignment = Alignment.Center
                            ) {
                                if (person.avatarCropPath.isNotBlank() && File(person.avatarCropPath).exists()) {
                                    AsyncImage(
                                        model = File(person.avatarCropPath),
                                        contentDescription = cleanName,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(CircleShape)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Outlined.Person,
                                        contentDescription = cleanName,
                                        tint = IgTextSecondary,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }

                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .align(Alignment.TopEnd)
                                        .clip(CircleShape)
                                        .background(Color(0xFF0095F6))
                                        .border(1.5.dp, Color.Black, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = Color.White,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "@$cleanName",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${person.faceCount} posts",
                            fontSize = 11.sp,
                            color = Color(0xFF8E8E93)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = {
                    val finalName = customMergeName.trim().ifEmpty {
                        selectedPeople.firstOrNull()?.name?.removePrefix("@") ?: "Person"
                    }
                    onMergeSelected(selectedIds.toList(), finalName)
                },
                enabled = selectedIds.size >= 2,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF0095F6),
                    disabledContainerColor = Color(0xFF26262A),
                    contentColor = Color.White,
                    disabledContentColor = Color(0xFF8E8E93)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
            ) {
                Text(
                    text = if (selectedIds.size >= 2) {
                        "Merge ${selectedIds.size} Profiles into @${customMergeName.ifBlank { "Profile" }}"
                    } else {
                        "Select at least 2 profiles to merge"
                    },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
