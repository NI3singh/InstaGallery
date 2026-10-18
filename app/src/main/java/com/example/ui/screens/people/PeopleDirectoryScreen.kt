package com.example.ui.screens.people

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.example.ui.theme.IgAmber
import com.example.ui.theme.IgBlack
import com.example.ui.theme.IgBlue
import com.example.ui.theme.IgBorderNav
import com.example.ui.theme.IgElevated
import com.example.ui.theme.IgStoryGradient
import com.example.ui.theme.IgSurfaceHigh
import com.example.ui.theme.IgSurfaceModal
import com.example.ui.theme.IgTextPrimary
import com.example.ui.theme.IgTextSecondary
import kotlinx.coroutines.delay
import java.io.File

@Composable
fun PeopleDirectoryScreen(
    viewModel: PeopleViewModel,
    onPersonClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showMergeDialog by remember { mutableStateOf(false) }

    // Auto-dismiss toast
    LaunchedEffect(uiState.toastMessage) {
        if (uiState.toastMessage != null) {
            delay(3500)
            viewModel.dismissToast()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(IgBlack)
            .testTag("people_directory_screen")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Sub-Header / Directory Action Bar (48.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(IgBlack)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "People",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = IgTextPrimary
                    )

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(IgSurfaceModal)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${uiState.people.size} detected",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFBFC7D4)
                        )
                    }
                }

                Text(
                    text = if (uiState.isSelectionMode) "Cancel" else "Merge",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = IgBlue,
                    modifier = Modifier
                        .clickable { viewModel.toggleSelectionMode() }
                        .padding(vertical = 4.dp, horizontal = 6.dp)
                        .testTag("merge_toggle_button")
                )
            }

            // Search Input Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .clip(RoundedCornerShape(50))
                        .background(IgSurfaceModal)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = "Search",
                        tint = IgTextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                    BasicTextField(
                        value = uiState.searchQuery,
                        onValueChange = { viewModel.onSearchQueryChange(it) },
                        singleLine = true,
                        textStyle = TextStyle(
                            fontSize = 14.sp,
                            color = IgTextPrimary
                        ),
                        cursorBrush = SolidColor(IgBlue),
                        decorationBox = { innerTextField ->
                            if (uiState.searchQuery.isEmpty()) {
                                Text(
                                    text = "Search people...",
                                    fontSize = 14.sp,
                                    color = IgTextSecondary
                                )
                            }
                            innerTextField()
                        },
                        modifier = Modifier.weight(1f)
                    )
                    if (uiState.searchQuery.isNotEmpty()) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear search",
                            tint = IgTextSecondary,
                            modifier = Modifier
                                .size(16.dp)
                                .clickable { viewModel.onSearchQueryChange("") }
                        )
                    }
                    Icon(
                        imageVector = Icons.Outlined.Tune,
                        contentDescription = "Sort people (${uiState.sortOrder.label})",
                        tint = IgTextSecondary,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable { viewModel.cycleSortOrder() }
                    )
                }
            }

            // 3-Column People Grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // Micro Banner: Smart Duplicate Detection
                val dup = uiState.duplicatePair
                if (dup != null && !uiState.isSelectionMode) {
                    item(span = { GridItemSpan(3) }) {
                        DuplicateSuggestionBanner(
                            pair = dup,
                            onReview = { viewModel.reviewDuplicate(dup) },
                            onDismiss = { viewModel.ignoreDuplicatePair(dup) }
                        )
                    }
                }

                // Detected people cards
                items(
                    items = uiState.filteredPeople,
                    key = { it.personId }
                ) { person ->
                    val isSelected = uiState.selectedPersonIds.contains(person.personId)
                    PersonGridItem(
                        person = person,
                        isSelectionMode = uiState.isSelectionMode,
                        isSelected = isSelected,
                        onClick = {
                            if (uiState.isSelectionMode) {
                                viewModel.togglePersonSelection(person.personId)
                            } else {
                                onPersonClick(person.personId)
                            }
                        },
                        onLongClick = {
                            viewModel.togglePersonSelection(person.personId)
                        }
                    )
                }

                // Final Grid Item: @Other
                item {
                    OtherGridItem(
                        count = uiState.unassociatedCount,
                        onClick = { onPersonClick(-1L) }
                    )
                }
            }
        }

        // Floating Merge Action Pill at bottom center
        AnimatedVisibility(
            visible = uiState.selectedPersonIds.size >= 2,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
        ) {
            val count = uiState.selectedPersonIds.size
            val selectedPeople = uiState.people.filter { uiState.selectedPersonIds.contains(it.personId) }
            val totalPosts = selectedPeople.sumOf { it.faceCount }

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(IgBlue)
                    .clickable { showMergeDialog = true }
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .testTag("floating_merge_button"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Link,
                    contentDescription = "Merge",
                    tint = Color.White,
                    modifier = Modifier.size(19.dp)
                )
                Text(
                    text = "Merge $count Profiles",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Text(
                    text = "• $totalPosts posts",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
        }

        // Top Delight Toast Banner
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
                    .clip(RoundedCornerShape(50))
                    .background(IgSurfaceHigh.copy(alpha = 0.95f))
                    .border(1.dp, IgBorderNav, RoundedCornerShape(50))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(IgBlue),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Verified,
                        contentDescription = "Success",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
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
                    color = IgBlue,
                    modifier = Modifier.clickable { viewModel.dismissToast() }
                )
            }
        }
    }

    // Merge Confirmation Dialog
    if (showMergeDialog) {
        val selectedPeople = uiState.people.filter { uiState.selectedPersonIds.contains(it.personId) }
        var targetName by remember { mutableStateOf(selectedPeople.firstOrNull()?.name ?: "Family") }

        AlertDialog(
            onDismissRequest = { showMergeDialog = false },
            containerColor = IgSurfaceModal,
            title = {
                Text(
                    text = "Merge ${selectedPeople.size} Profiles",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = IgTextPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "All photos and facial embeddings from these profiles will be combined under a single profile.",
                        fontSize = 13.sp,
                        color = IgTextSecondary
                    )
                    OutlinedTextField(
                        value = targetName,
                        onValueChange = { targetName = it },
                        label = { Text("Profile Name") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = IgBlue,
                            unfocusedBorderColor = IgBorderNav,
                            focusedTextColor = IgTextPrimary,
                            unfocusedTextColor = IgTextPrimary
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showMergeDialog = false
                        viewModel.mergeSelectedPeople(targetName.trim())
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = IgBlue)
                ) {
                    Text("Confirm Merge")
                }
            },
            dismissButton = {
                TextButton(onClick = { showMergeDialog = false }) {
                    Text("Cancel", color = IgTextSecondary)
                }
            }
        )
    }
}

@Composable
fun DuplicateSuggestionBanner(
    pair: Pair<PersonEntity, PersonEntity>,
    onReview: () -> Unit,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(Color(0xFF1F1F1F))
            .border(1.dp, IgBorderNav, RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE07304).copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.AutoFixHigh,
                    contentDescription = "Duplicate",
                    tint = IgAmber,
                    modifier = Modifier.size(18.dp)
                )
            }
            Column {
                Text(
                    text = "Possible Duplicate Found",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = IgTextPrimary
                )
                Text(
                    text = "@${pair.first.name} and @${pair.second.name} look very similar",
                    fontSize = 11.5.sp,
                    color = IgTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(IgSurfaceModal)
                    .clickable(onClick = onReview)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "Review",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = IgBlue
                )
            }

            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Dismiss duplicate suggestion",
                tint = IgTextSecondary,
                modifier = Modifier
                    .size(18.dp)
                    .clickable(onClick = onDismiss)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PersonGridItem(
    person: PersonEntity,
    isSelectionMode: Boolean,
    isSelected: Boolean,
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
            // Gradient Ring + Selection Glow
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
            text = "@${person.name}",
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
