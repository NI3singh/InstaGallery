package com.example.ui.screens.profile

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.FolderShared
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Tune
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.local.entity.MediaWithAuthors
import com.example.ui.theme.IgBlack
import com.example.ui.theme.IgBlue
import com.example.ui.theme.IgBorderNav
import com.example.ui.theme.IgElevated
import com.example.ui.theme.IgRed
import com.example.ui.theme.IgStoryGradient
import com.example.ui.theme.IgSurfaceModal
import com.example.ui.theme.IgSurfacePill
import com.example.ui.theme.IgTextPrimary
import com.example.ui.theme.IgTextSecondary
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    onBackClick: () -> Unit,
    onMediaClick: (Long) -> Unit,
    onMergedToPerson: (Long) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val person = uiState.person
    val isOther = (uiState.personId == -1L)

    val profileName = if (isOther) "Other" else person?.name ?: "Person"
    val avatarCropPath = person?.avatarCropPath ?: ""

    var showEditNameDialog by remember { mutableStateOf(false) }
    var showMergePickerSheet by remember { mutableStateOf(false) }
    var showAvatarPickerSheet by remember { mutableStateOf(false) }
    var selectedMediaForAction by remember { mutableStateOf<MediaWithAuthors?>(null) }
    var showActionSheet by remember { mutableStateOf(false) }

    val activeMediaList = if (uiState.selectedTab == 0) uiState.photos else uiState.videos

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(IgBlack)
            .testTag("profile_screen")
    ) {
        // Top Navigation Bar (48.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = IgTextPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Text(
                    text = profileName.lowercase(),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = IgTextPrimary
                )

                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(IgBlue),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Face,
                        contentDescription = "Verified profile",
                        tint = Color.White,
                        modifier = Modifier.size(11.dp)
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!isOther) {
                    IconButton(
                        onClick = { showEditNameDialog = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = "Edit Profile Name",
                            tint = IgTextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                IconButton(
                    onClick = { showActionSheet = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreHoriz,
                        contentDescription = "More options",
                        tint = IgTextPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // Profile Header Details
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // 86.dp Avatar with gradient ring (tappable to change avatar crop)
            Box(
                modifier = Modifier
                    .size(86.dp)
                    .clip(CircleShape)
                    .background(IgStoryGradient)
                    .clickable(enabled = !isOther && uiState.faces.isNotEmpty()) {
                        showAvatarPickerSheet = true
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(82.dp)
                        .clip(CircleShape)
                        .background(IgBlack),
                    contentAlignment = Alignment.Center
                ) {
                    if (avatarCropPath.isNotBlank() && File(avatarCropPath).exists()) {
                        AsyncImage(
                            model = File(avatarCropPath),
                            contentDescription = profileName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(78.dp)
                                .clip(CircleShape)
                        )
                    } else {
                        Icon(
                            imageVector = if (isOther) Icons.Outlined.Group else Icons.Outlined.Face,
                            contentDescription = profileName,
                            tint = IgTextSecondary,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                }

                // Bottom-right Sparkle Badge
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .align(Alignment.BottomEnd)
                        .clip(CircleShape)
                        .background(IgBlue)
                        .border(1.5.dp, IgBlack, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "AI Clustered",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            // Memory Metrics (Posts, Reels, Total)
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatColumn(
                    number = uiState.photoCount.toString(),
                    label = "Posts",
                    onClick = { viewModel.selectTab(0) }
                )
                StatColumn(
                    number = uiState.reelCount.toString(),
                    label = "Reels",
                    onClick = { viewModel.selectTab(1) }
                )
                StatColumn(
                    number = uiState.totalCount.toString(),
                    label = "Total"
                )
            }
        }

        // Bio & Recognition info
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = profileName,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = IgTextPrimary
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(IgSurfaceModal)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (isOther) "Unclustered" else "Family",
                        fontSize = 11.sp,
                        color = IgTextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = "Auto-generated local profile • ${uiState.totalCount} memories on device",
                fontSize = 12.sp,
                color = IgTextSecondary
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.FolderShared,
                    contentDescription = "Synced",
                    tint = IgBlue,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "Synced across Local Vault & Photos library",
                    fontSize = 12.sp,
                    color = IgBlue
                )
            }
        }

        // Action Buttons Row ("Edit Profile Name" & "Merge with Person")
        if (!isOther) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(IgSurfaceModal)
                        .clickable { showEditNameDialog = true },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = "Edit",
                            tint = IgTextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Edit Profile Name",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = IgTextPrimary
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(IgSurfaceModal)
                        .clickable { showMergePickerSheet = true },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.GroupAdd,
                            contentDescription = "Merge",
                            tint = IgTextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Merge with Person",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = IgTextPrimary
                        )
                    }
                }
            }
        }

        // Tab Switcher Header (Grid vs Reels)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .background(IgBlack)
        ) {
            // Tab 0: Photos Grid
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clickable { viewModel.selectTab(0) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.GridOn,
                        contentDescription = "Photos",
                        tint = if (uiState.selectedTab == 0) IgTextPrimary else IgTextSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    if (uiState.selectedTab == 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.5.dp)
                                .background(IgTextPrimary)
                        )
                    }
                }
            }

            // Tab 1: Reels
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clickable { viewModel.selectTab(1) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Outlined.Movie,
                        contentDescription = "Reels",
                        tint = if (uiState.selectedTab == 1) IgTextPrimary else IgTextSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    if (uiState.selectedTab == 1) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.5.dp)
                                .background(IgTextPrimary)
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = IgBorderNav, thickness = 0.5.dp)

        // 3-Column Square Media Grid or Empty State
        if (activeMediaList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(IgSurfacePill),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (uiState.selectedTab == 0) Icons.Outlined.PhotoCamera else Icons.Outlined.Movie,
                            contentDescription = "No media",
                            tint = IgTextSecondary,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (uiState.selectedTab == 0) "No Photos Yet" else "No Reels Yet",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = IgTextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (uiState.selectedTab == 0) {
                            "Photos featuring @$profileName will appear in this grid."
                        } else {
                            "Videos featuring @$profileName will appear here."
                        },
                        fontSize = 12.5.sp,
                        color = IgTextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("profile_media_grid")
            ) {
                items(
                    items = activeMediaList,
                    key = { it.media.mediaId }
                ) { item ->
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .background(IgElevated)
                            .combinedClickable(
                                onClick = { onMediaClick(item.media.mediaId) },
                                onLongClick = {
                                    selectedMediaForAction = item
                                    showActionSheet = true
                                }
                            )
                    ) {
                        AsyncImage(
                            model = item.media.uri,
                            contentDescription = item.media.displayName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )

                        if (item.media.isFavorite) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = "Favorite",
                                tint = IgRed,
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(6.dp)
                                    .size(14.dp)
                            )
                        }

                        // Video overlay badge
                        if (item.media.isVideo) {
                            Icon(
                                imageVector = Icons.Outlined.Movie,
                                contentDescription = "Video",
                                tint = Color.White,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp)
                                    .size(16.dp)
                            )
                            Row(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp)
                                )
                                val durationSec = (item.media.durationMs ?: 0L) / 1000L
                                Text(
                                    text = "${durationSec}s",
                                    fontSize = 11.sp,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Edit Profile Name Dialog
    if (showEditNameDialog) {
        var newName by remember { mutableStateOf(profileName) }
        AlertDialog(
            onDismissRequest = { showEditNameDialog = false },
            containerColor = IgSurfaceModal,
            title = {
                Text(
                    text = "Edit Profile Name",
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
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = IgBlue,
                        unfocusedBorderColor = IgBorderNav,
                        focusedTextColor = IgTextPrimary,
                        unfocusedTextColor = IgTextPrimary
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showEditNameDialog = false
                        viewModel.updateProfileName(newName)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = IgBlue)
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditNameDialog = false }) {
                    Text("Cancel", color = IgTextSecondary)
                }
            }
        )
    }

    // Merge with Person Picker Dialog
    if (showMergePickerSheet) {
        AlertDialog(
            onDismissRequest = { showMergePickerSheet = false },
            containerColor = IgSurfaceModal,
            title = {
                Text(
                    text = "Merge with Another Profile",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = IgTextPrimary
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (uiState.otherPeople.isEmpty()) {
                        Text(
                            text = "No other detected profiles available to merge with.",
                            fontSize = 13.sp,
                            color = IgTextSecondary
                        )
                    } else {
                        uiState.otherPeople.forEach { other ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        showMergePickerSheet = false
                                        viewModel.mergeWithPerson(other.personId) { mergedTargetId ->
                                            onMergedToPerson(mergedTargetId)
                                        }
                                    }
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(IgStoryGradient),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (other.avatarCropPath.isNotBlank() && File(other.avatarCropPath).exists()) {
                                        AsyncImage(
                                            model = File(other.avatarCropPath),
                                            contentDescription = other.name,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(CircleShape)
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Outlined.Person,
                                            contentDescription = other.name,
                                            tint = IgTextSecondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "@${other.name} (${other.faceCount} posts)",
                                    fontSize = 14.sp,
                                    color = IgTextPrimary
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showMergePickerSheet = false }) {
                    Text("Cancel", color = IgTextSecondary)
                }
            }
        )
    }

    // Avatar Selection Picker
    if (showAvatarPickerSheet) {
        AlertDialog(
            onDismissRequest = { showAvatarPickerSheet = false },
            containerColor = IgSurfaceModal,
            title = {
                Text(
                    text = "Select Profile Avatar",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = IgTextPrimary
                )
            },
            text = {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth().height(260.dp)
                ) {
                    items(uiState.faces) { face ->
                        if (face.cropPath.isNotBlank() && File(face.cropPath).exists()) {
                            AsyncImage(
                                model = File(face.cropPath),
                                contentDescription = "Face thumbnail",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(70.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        showAvatarPickerSheet = false
                                        viewModel.changeProfileAvatar(face.cropPath)
                                    }
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAvatarPickerSheet = false }) {
                    Text("Cancel", color = IgTextSecondary)
                }
            }
        )
    }

    // Compact Instagram-Style Modal Bottom Sheet
    if (showActionSheet) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = {
                showActionSheet = false
                selectedMediaForAction = null
            },
            sheetState = sheetState,
            containerColor = IgSurfaceModal,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // Drag Handle
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(36.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(IgTextSecondary.copy(alpha = 0.4f))
                    )
                }

                // Action 1: Remove photo from person (if a media item was long-pressed)
                if (selectedMediaForAction != null && !isOther) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                selectedMediaForAction?.let {
                                    viewModel.removePhotoFromPerson(it.media.mediaId)
                                }
                                showActionSheet = false
                                selectedMediaForAction = null
                            }
                            .padding(vertical = 14.dp, horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Remove photo from @$profileName",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = IgRed
                        )
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = "Remove",
                            tint = IgRed,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    HorizontalDivider(color = IgBorderNav, thickness = 0.5.dp)
                }

                // Action 2: Change profile avatar
                if (!isOther) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                showActionSheet = false
                                showAvatarPickerSheet = true
                            }
                            .padding(vertical = 14.dp, horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Change profile avatar",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = IgTextPrimary
                        )
                        Icon(
                            imageVector = Icons.Outlined.AddAPhoto,
                            contentDescription = "Avatar",
                            tint = IgTextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    HorizontalDivider(color = IgBorderNav, thickness = 0.5.dp)
                }

                // Action 3: Face recognition settings info
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 14.dp, horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Face recognition settings",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = IgTextPrimary
                        )
                        Text(
                            text = "${uiState.totalCount} items auto-clustered on device",
                            fontSize = 12.sp,
                            color = IgTextSecondary
                        )
                    }
                    Icon(
                        imageVector = Icons.Outlined.Tune,
                        contentDescription = "Settings",
                        tint = IgTextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Dismiss Button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(IgSurfacePill)
                        .clickable {
                            showActionSheet = false
                            selectedMediaForAction = null
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Dismiss",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = IgTextPrimary
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun StatColumn(
    number: String,
    label: String,
    onClick: (() -> Unit)? = null
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    ) {
        Text(
            text = number,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = IgTextPrimary
        )
        Text(
            text = label,
            fontSize = 12.sp,
            color = IgTextSecondary
        )
    }
}
