// InstaGallery v2.0
package com.example.ui.screens.profile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Search
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.local.entity.FaceEntity
import com.example.data.local.entity.MediaWithAuthors
import com.example.data.local.entity.PersonEntity
import com.example.ui.components.InstaPullToRefreshBox
import com.example.ui.components.RefreshableTab
import com.example.ui.components.RotatingStoryRingAvatar
import com.example.ui.components.TabRefreshManager
import com.example.ui.components.bounceClick
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
    onPersonClick: (Long) -> Unit = onMergedToPerson,
    onOpenStory: (Long) -> Unit = {},
    onOpenPeopleDirectory: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    isRootMyProfileTab: Boolean = viewModel.isRootMyProfileTab,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val person = uiState.activePerson
    val resolvedPersonId = uiState.personId
    val isOther = (resolvedPersonId == -1L && !isRootMyProfileTab)
    val isMyProfile = uiState.isMyProfile || isRootMyProfileTab

    val rawName = when {
        isOther -> "Other"
        person != null -> person.name.removePrefix("@").trim().ifEmpty { "Person" }
        isRootMyProfileTab -> "me"
        else -> "Person"
    }
    val cleanHandle = rawName.removePrefix("@")
    val avatarCropPath = person?.avatarCropPath ?: ""
    val photos = uiState.photos

    var showDiscoverPeopleTray by remember(isRootMyProfileTab) { mutableStateOf(isRootMyProfileTab) }
    var showEditProfileSheet by remember { mutableStateOf(false) }
    var showChooseMyProfileSheet by remember { mutableStateOf(false) }
    var showProfileSwitcherSheet by remember { mutableStateOf(false) }
    var showEditNameDialog by remember { mutableStateOf(false) }
    var showMergePickerSheet by remember { mutableStateOf(false) }
    var showAvatarPickerSheet by remember { mutableStateOf(false) }
    var selectedMediaForAction by remember { mutableStateOf<MediaWithAuthors?>(null) }
    var showActionSheet by remember { mutableStateOf(false) }
    val gridState = rememberLazyGridState()

    LaunchedEffect(Unit) {
        TabRefreshManager.refreshEvents.collect { tab ->
            if (tab == RefreshableTab.PROFILE) {
                viewModel.refreshProfile()
                runCatching { gridState.animateScrollToItem(0) }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("profile_screen")
    ) {
        // 3.2 Native Instagram Top Bar (52.dp height, #000000 background)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .background(Color.Black)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left Side
            if (!isRootMyProfileTab) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("profile_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.bounceClick { showProfileSwitcherSheet = true }
                    ) {
                        Text(
                            text = "@$cleanHandle",
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Switch profile",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .bounceClick { showProfileSwitcherSheet = true }
                        .testTag("profile_handle_switcher")
                ) {
                    Text(
                        text = "@$cleanHandle",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Switch profile",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Right Side: People Directory Icon + Hamburger Settings Menu
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .bounceClick { onOpenPeopleDirectory() }
                        .testTag("profile_top_people_directory_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Group,
                        contentDescription = "All People Directory",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .bounceClick { onOpenSettings() }
                        .testTag("profile_top_settings_button"),
                    contentAlignment = Alignment.Center
                ) {
                    InstagramHamburgerIcon(modifier = Modifier.size(24.dp))
                }
            }
        }

        // Scrollable Profile Body + 3-Column Photo Grid wrapped in Pull-to-Refresh
        InstaPullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.refreshProfile() },
            modifier = Modifier.fillMaxSize()
        ) {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("profile_media_grid")
            ) {
            // Header Item (Full Span)
            item(
                key = "profile_header_section",
                span = { GridItemSpan(3) }
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // 3.3.1 "Choose Your Profile" Prompt Banner (Only when needsMyProfileSelection == true)
                    AnimatedVisibility(
                        visible = uiState.needsMyProfileSelection && uiState.allVisiblePeople.isNotEmpty(),
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF16161A))
                                .padding(12.dp)
                                .testTag("choose_my_profile_banner"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Which profile is you?",
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Select your face to personalize your profile tab",
                                    fontSize = 12.sp,
                                    color = Color(0xFF8E8E93)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Box(
                                modifier = Modifier
                                    .height(30.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF0095F6))
                                    .bounceClick { showChooseMyProfileSheet = true }
                                    .padding(horizontal = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Choose Face",
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    // 3.3.2 Avatar + 3 Interactive Stat Columns (posts | solo | together)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RotatingStoryRingAvatar(
                            avatarSize = 86.dp,
                            ringWidth = 2.5.dp,
                            innerCutoutWidth = 2.5.dp,
                            innerCutoutColor = Color.Black,
                            isMuted = isOther,
                            modifier = Modifier
                                .combinedClickable(
                                    onClick = {
                                        if (resolvedPersonId > 0L) {
                                            onOpenStory(resolvedPersonId)
                                        }
                                    },
                                    onLongClick = {
                                        if (!isOther && uiState.faces.isNotEmpty()) {
                                            showAvatarPickerSheet = true
                                        }
                                    }
                                )
                                .testTag("profile_story_avatar")
                        ) {
                            if (avatarCropPath.isNotBlank() && File(avatarCropPath).exists()) {
                                AsyncImage(
                                    model = File(avatarCropPath),
                                    contentDescription = cleanHandle,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color(0xFF18181C)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isOther) Icons.Outlined.Group else Icons.Outlined.Person,
                                        contentDescription = cleanHandle,
                                        tint = IgTextSecondary,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 12.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            InteractiveStatColumn(
                                number = uiState.totalPostsCount.toString(),
                                label = "posts",
                                isActive = uiState.selectedFilter is ProfilePhotoFilter.All,
                                onClick = { viewModel.setFilter(ProfilePhotoFilter.All) }
                            )
                            InteractiveStatColumn(
                                number = uiState.soloPostsCount.toString(),
                                label = "solo",
                                isActive = uiState.selectedFilter is ProfilePhotoFilter.SoloOnly,
                                onClick = {
                                    if (!isOther) {
                                        viewModel.toggleFilter(ProfilePhotoFilter.SoloOnly)
                                    }
                                }
                            )
                            InteractiveStatColumn(
                                number = uiState.togetherPostsCount.toString(),
                                label = "together",
                                isActive = uiState.selectedFilter is ProfilePhotoFilter.TogetherWith,
                                onClick = {
                                    val firstCo = uiState.coOccurringPeople.firstOrNull()?.person
                                    if (!isOther && firstCo != null) {
                                        viewModel.toggleFilter(ProfilePhotoFilter.TogetherWith(firstCo))
                                    }
                                }
                            )
                        }
                    }

                    // 3.3.3 Name & Bio Block (padding(top = 10.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = cleanHandle,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            if (isMyProfile && !isOther) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFF26262A))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "You",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFFA0A0A8)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(3.dp))

                        Text(
                            text = uiState.customBio,
                            fontSize = 13.5.sp,
                            color = Color(0xFFE4E4E7)
                        )
                    }

                    // 3.4 Authentic Instagram Matte Action Buttons Row (Color(0xFF26262A), RoundedCornerShape(8.dp), zero border, 34.dp height)
                    if (!isOther) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isMyProfile) {
                                // 1. Edit profile
                                MatteInstagramButton(
                                    text = "Edit profile",
                                    onClick = { showEditProfileSheet = true },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("profile_edit_profile_button")
                                )

                                // 2. All People (X)
                                MatteInstagramButton(
                                    text = "All People (${uiState.allVisiblePeople.size})",
                                    onClick = onOpenPeopleDirectory,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("profile_all_people_button")
                                )
                            } else {
                                // Viewing another person's profile
                                MatteInstagramButton(
                                    text = "Edit name",
                                    onClick = { showEditNameDialog = true },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("profile_edit_name_button")
                                )

                                MatteInstagramButton(
                                    text = "Merge",
                                    onClick = { showMergePickerSheet = true },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("profile_merge_button")
                                )

                                val isHidden = person?.isHiddenToOther == true
                                MatteInstagramButton(
                                    text = if (isHidden) "Restore" else "Move to @Other",
                                    onClick = {
                                        if (isHidden) {
                                            viewModel.restoreProfileFromOther()
                                        } else {
                                            viewModel.moveProfileToOther {
                                                onBackClick()
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("profile_move_other_button")
                                )
                            }

                            // 3/4. Discover People Toggle Button (34.dp x 34.dp square)
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (showDiscoverPeopleTray) Color(0xFF36363C) else Color(0xFF26262A)
                                    )
                                    .bounceClick { showDiscoverPeopleTray = !showDiscoverPeopleTray }
                                    .testTag("profile_discover_people_toggle"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.GroupAdd,
                                    contentDescription = "Discover People in Gallery",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    // 3.5 Collapsible "People in Gallery" Tray (Instagram "Discover People" Style)
                    AnimatedVisibility(
                        visible = showDiscoverPeopleTray && uiState.allVisiblePeople.isNotEmpty(),
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "People in your gallery",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                                Text(
                                    text = "See all",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF0095F6),
                                    modifier = Modifier
                                        .bounceClick { onOpenPeopleDirectory() }
                                        .testTag("discover_tray_see_all")
                                )
                            }

                            val trayPeople = remember(
                                uiState.allVisiblePeople,
                                resolvedPersonId,
                                uiState.discoverShuffleSeed
                            ) {
                                val filtered = uiState.allVisiblePeople.filter { it.personId != resolvedPersonId }
                                if (uiState.discoverShuffleSeed == 0L) {
                                    filtered
                                } else {
                                    filtered.shuffled(java.util.Random(uiState.discoverShuffleSeed))
                                }
                            }

                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("discover_people_lazy_row")
                            ) {
                                // First Card: Quick Search & Directory Card
                                item(key = "discover_search_directory_card") {
                                    Column(
                                        modifier = Modifier
                                            .width(124.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0xFF121215))
                                            .border(0.5.dp, Color(0xFF26262A), RoundedCornerShape(10.dp))
                                            .bounceClick { onOpenPeopleDirectory() }
                                            .padding(12.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(64.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF1C1C22))
                                                .border(1.5.dp, Color(0xFF2E2E36), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Search,
                                                contentDescription = "Search People",
                                                tint = Color.White,
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "Search All",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${uiState.allVisiblePeople.size} people",
                                            fontSize = 11.5.sp,
                                            color = Color(0xFF8E8E93),
                                            maxLines = 1
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(28.dp)
                                                .clip(RoundedCornerShape(7.dp))
                                                .background(Color(0xFF26262A)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "Directory",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }

                                items(
                                    items = trayPeople,
                                    key = { "discover_person_${it.personId}" }
                                ) { otherPerson ->
                                    val otherCleanName = otherPerson.name.removePrefix("@")
                                    Column(
                                        modifier = Modifier
                                            .width(124.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0xFF121215))
                                            .border(0.5.dp, Color(0xFF26262A), RoundedCornerShape(10.dp))
                                            .bounceClick { onPersonClick(otherPerson.personId) }
                                            .padding(12.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(64.dp)
                                                .clip(CircleShape)
                                                .background(IgStoryGradient)
                                                .padding(1.5.dp)
                                                .clip(CircleShape)
                                                .background(Color.Black)
                                                .padding(1.5.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (otherPerson.avatarCropPath.isNotBlank() && File(otherPerson.avatarCropPath).exists()) {
                                                AsyncImage(
                                                    model = File(otherPerson.avatarCropPath),
                                                    contentDescription = otherCleanName,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .clip(CircleShape)
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = Icons.Outlined.Person,
                                                    contentDescription = otherCleanName,
                                                    tint = IgTextSecondary,
                                                    modifier = Modifier.size(26.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "@$otherCleanName",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${otherPerson.faceCount} posts",
                                            fontSize = 11.5.sp,
                                            color = Color(0xFF8E8E93),
                                            maxLines = 1
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(28.dp)
                                                .clip(RoundedCornerShape(7.dp))
                                                .background(Color(0xFF0095F6))
                                                .bounceClick { onPersonClick(otherPerson.personId) },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "View",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 3.6 "Together With" Co-Occurrence Filter Strip
                    if (!isOther && (uiState.coOccurringPeople.isNotEmpty() || uiState.soloPostsCount > 0)) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("profile_cooccurrence_filter_row")
                        ) {
                            item(key = "filter_all") {
                                val isSelected = uiState.selectedFilter is ProfilePhotoFilter.All
                                Box(
                                    modifier = Modifier
                                        .height(34.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) Color.White else Color(0xFF26262A))
                                        .bounceClick { viewModel.setFilter(ProfilePhotoFilter.All) }
                                        .padding(horizontal = 14.dp)
                                        .testTag("filter_chip_all"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (uiState.totalPostsCount > 0) "All Photos (${uiState.totalPostsCount})" else "All Photos",
                                        fontSize = 12.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                        color = if (isSelected) Color.Black else Color.White
                                    )
                                }
                            }

                            item(key = "filter_solo") {
                                val isSelected = uiState.selectedFilter is ProfilePhotoFilter.SoloOnly
                                Box(
                                    modifier = Modifier
                                        .height(34.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) Color.White else Color(0xFF26262A))
                                        .bounceClick { viewModel.toggleFilter(ProfilePhotoFilter.SoloOnly) }
                                        .padding(horizontal = 14.dp)
                                        .testTag("filter_chip_solo"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Person,
                                            contentDescription = "Solo",
                                            tint = if (isSelected) Color.Black else Color.White,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Text(
                                            text = "Solo (${uiState.soloPostsCount})",
                                            fontSize = 12.5.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                            color = if (isSelected) Color.Black else Color.White
                                        )
                                    }
                                }
                            }

                            items(
                                items = uiState.coOccurringPeople,
                                key = { "co_${it.person.personId}" }
                            ) { coPerson ->
                                val currentFilter = uiState.selectedFilter
                                val isSelected = currentFilter is ProfilePhotoFilter.TogetherWith &&
                                    currentFilter.otherPerson.personId == coPerson.person.personId
                                val cleanCoName = coPerson.person.name.removePrefix("@")

                                Row(
                                    modifier = Modifier
                                        .height(34.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(if (isSelected) Color(0xFF0095F6) else Color(0xFF26262A))
                                        .bounceClick {
                                            viewModel.toggleFilter(ProfilePhotoFilter.TogetherWith(coPerson.person))
                                        }
                                        .padding(start = 4.dp, end = 12.dp)
                                        .testTag("filter_chip_together_${coPerson.person.personId}"),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(26.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isSelected) {
                                                    Brush.linearGradient(listOf(Color.White, Color.White))
                                                } else {
                                                    IgStoryGradient
                                                }
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        val coAvatarPath = coPerson.person.avatarCropPath
                                        if (coAvatarPath.isNotBlank() && File(coAvatarPath).exists()) {
                                            AsyncImage(
                                                model = File(coAvatarPath),
                                                contentDescription = cleanCoName,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .size(23.dp)
                                                    .clip(CircleShape)
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .size(23.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFF27272A)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Outlined.Face,
                                                    contentDescription = cleanCoName,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                            }
                                        }
                                    }

                                    Text(
                                        text = if (isSelected) {
                                            "with @$cleanCoName • ${coPerson.sharedPhotoCount}"
                                        } else {
                                            "@$cleanCoName (${coPerson.sharedPhotoCount})"
                                        },
                                        fontSize = 12.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                        color = Color.White
                                    )
                                }
                            }
                        }

                        // Active Filter Banner when TogetherWith is active
                        val activeTogetherFilter = uiState.selectedFilter as? ProfilePhotoFilter.TogetherWith
                        if (activeTogetherFilter != null) {
                            val otherPerson = activeTogetherFilter.otherPerson
                            val cleanOther = otherPerson.name.removePrefix("@")
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF121216))
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Showing ${photos.size} photos of @$cleanHandle & @$cleanOther",
                                    fontSize = 12.sp,
                                    color = Color(0xFFE4E4E7),
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color(0xFF26262A))
                                            .clickable { onPersonClick(otherPerson.personId) }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "View @$cleanOther",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.White
                                        )
                                    }
                                    Text(
                                        text = "Clear",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = IgBlue,
                                        modifier = Modifier.clickable {
                                            viewModel.setFilter(ProfilePhotoFilter.All)
                                        }
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = Color(0xFF1F1F22), thickness = 0.5.dp)
                }
            }

            // Empty State or 3-Column Square Photo Grid Items
            if (photos.isEmpty()) {
                item(
                    key = "profile_empty_state",
                    span = { GridItemSpan(3) }
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 64.dp, horizontal = 32.dp),
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
                                    imageVector = Icons.Outlined.PhotoCamera,
                                    contentDescription = "No photos",
                                    tint = IgTextSecondary,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No Photos Yet",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = IgTextPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Photos featuring @$cleanHandle will appear in this grid.",
                                fontSize = 12.5.sp,
                                color = IgTextSecondary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(
                    items = photos,
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
                    }
                }
            }
        }
        }
    }

    // Edit Profile Sheet (for My Profile: Handle + Custom Bio + Avatar + Change 'Me' Profile)
    if (showEditProfileSheet) {
        EditProfileModalSheet(
            currentName = cleanHandle,
            currentBio = uiState.customBio,
            avatarCropPath = avatarCropPath,
            faces = uiState.faces,
            onSave = { updatedName, updatedBio ->
                viewModel.updateProfileAndBio(updatedName, updatedBio)
                showEditProfileSheet = false
            },
            onSelectAvatarCrop = { cropPath ->
                viewModel.changeProfileAvatar(cropPath)
            },
            onChangeMeProfileClick = {
                showEditProfileSheet = false
                showChooseMyProfileSheet = true
            },
            onDismiss = { showEditProfileSheet = false }
        )
    }

    // Choose My Profile ("Me") 3-Column Visual Face Picker Sheet
    if (showChooseMyProfileSheet) {
        ChooseMyProfileSheet(
            people = uiState.allVisiblePeople,
            currentMyProfileId = resolvedPersonId,
            onSelectPerson = { chosenPerson ->
                viewModel.selectMyProfilePerson(chosenPerson.personId)
                showChooseMyProfileSheet = false
                if (!isRootMyProfileTab) {
                    onPersonClick(chosenPerson.personId)
                }
            },
            onDismiss = { showChooseMyProfileSheet = false }
        )
    }

    // Quick Profile Switcher Sheet (when tapping @handle ⌄ in Top Bar)
    if (showProfileSwitcherSheet) {
        ProfileQuickSwitcherSheet(
            people = uiState.allVisiblePeople,
            activePersonId = resolvedPersonId,
            myProfilePersonId = if (isMyProfile) resolvedPersonId else -1L,
            onSelectPerson = { chosen ->
                showProfileSwitcherSheet = false
                if (isRootMyProfileTab) {
                    viewModel.selectMyProfilePerson(chosen.personId)
                } else {
                    onPersonClick(chosen.personId)
                }
            },
            onOpenAllPeopleDirectory = {
                showProfileSwitcherSheet = false
                onOpenPeopleDirectory()
            },
            onDismiss = { showProfileSwitcherSheet = false }
        )
    }

    // Edit Profile Name Dialog (for non-Me profiles)
    if (showEditNameDialog) {
        var newName by remember { mutableStateOf(cleanHandle) }
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

    // Visual Merge Picker Bottom Sheet
    if (showMergePickerSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showMergePickerSheet = false },
            sheetState = sheetState,
            containerColor = Color(0xFF121216)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Merge @$cleanHandle with...",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Tap another profile to combine all photos and facial embeddings",
                    fontSize = 12.5.sp,
                    color = Color(0xFF8E8E93)
                )
                Spacer(modifier = Modifier.height(16.dp))

                if (uiState.otherPeople.isEmpty()) {
                    Text(
                        text = "No other detected profiles available to merge with.",
                        fontSize = 13.5.sp,
                        color = Color(0xFF8E8E93),
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(340.dp)
                    ) {
                        items(
                            items = uiState.otherPeople,
                            key = { it.personId }
                        ) { other ->
                            val otherName = other.name.removePrefix("@")
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF1A1A1E))
                                    .bounceClick {
                                        showMergePickerSheet = false
                                        viewModel.mergeWithPerson(other.personId) { mergedTargetId ->
                                            onMergedToPerson(mergedTargetId)
                                        }
                                    }
                                    .padding(10.dp)
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
                                    if (other.avatarCropPath.isNotBlank() && File(other.avatarCropPath).exists()) {
                                        AsyncImage(
                                            model = File(other.avatarCropPath),
                                            contentDescription = otherName,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(CircleShape)
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Outlined.Person,
                                            contentDescription = otherName,
                                            tint = IgTextSecondary,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "@$otherName",
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${other.faceCount} posts",
                                    fontSize = 11.sp,
                                    color = Color(0xFF8E8E93)
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
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

    // Long-press Photo Action Sheet
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
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
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
                            text = "Remove photo from @$cleanHandle",
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

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                showActionSheet = false
                                viewModel.selectMyProfilePerson(resolvedPersonId)
                            }
                            .padding(vertical = 14.dp, horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Set @$cleanHandle as My Profile (Me)",
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
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun MatteInstagramButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(34.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF26262A))
            .bounceClick { onClick() }
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun InteractiveStatColumn(
    number: String,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .bounceClick { onClick() }
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = number,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = if (isActive) Color.White else Color.White.copy(alpha = 0.92f)
        )
        Text(
            text = label,
            fontSize = 12.5.sp,
            color = if (isActive) Color.White else Color(0xFFE4E4E7)
        )
    }
}

@Composable
fun InstagramHamburgerIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color.White
) {
    Canvas(modifier = modifier) {
        val stroke = 2.1.dp.toPx()
        val xStart = size.width * 0.14f
        val xEnd = size.width * 0.86f
        val y1 = size.height * 0.26f
        val y2 = size.height * 0.50f
        val y3 = size.height * 0.74f

        drawLine(
            color = tint,
            start = Offset(xStart, y1),
            end = Offset(xEnd, y1),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = Offset(xStart, y2),
            end = Offset(xEnd, y2),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = Offset(xStart, y3),
            end = Offset(xEnd, y3),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChooseMyProfileSheet(
    people: List<PersonEntity>,
    currentMyProfileId: Long,
    onSelectPerson: (PersonEntity) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
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
                .testTag("choose_my_profile_sheet")
        ) {
            Text(
                text = "Choose Your Profile (Me)",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Select your face so the Profile tab & bottom navbar open your photos directly.",
                fontSize = 12.5.sp,
                color = Color(0xFF8E8E93)
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (people.isEmpty()) {
                Text(
                    text = "No detected people profiles yet. Scan your gallery first.",
                    fontSize = 13.5.sp,
                    color = Color(0xFF8E8E93),
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp)
                ) {
                    items(
                        items = people,
                        key = { it.personId }
                    ) { person ->
                        val isSelected = person.personId == currentMyProfileId
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
                                .bounceClick { onSelectPerson(person) }
                                .padding(10.dp)
                        ) {
                            Box(
                                modifier = Modifier.size(68.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(68.dp)
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
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                }

                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .align(Alignment.BottomEnd)
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
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileModalSheet(
    currentName: String,
    currentBio: String,
    avatarCropPath: String,
    faces: List<FaceEntity> = emptyList(),
    onSave: (String, String) -> Unit,
    onSelectAvatarCrop: (String) -> Unit = {},
    onChangeMeProfileClick: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var nameInput by remember(currentName) { mutableStateOf(currentName.removePrefix("@")) }
    var bioInput by remember(currentBio) { mutableStateOf(currentBio) }
    var showFaceCropGrid by remember { mutableStateOf(false) }

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
                .testTag("edit_profile_modal_sheet"),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Cancel",
                    fontSize = 15.sp,
                    color = Color(0xFFE4E4E7),
                    modifier = Modifier.clickable { onDismiss() }
                )
                Text(
                    text = "Edit profile",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Done",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0095F6),
                    modifier = Modifier.clickable {
                        onSave(nameInput.trim(), bioInput.trim())
                    }
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Box(
                modifier = Modifier
                    .size(82.dp)
                    .clip(CircleShape)
                    .background(IgStoryGradient)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(Color.Black)
                    .clickable(enabled = faces.isNotEmpty()) {
                        showFaceCropGrid = !showFaceCropGrid
                    },
                contentAlignment = Alignment.Center
            ) {
                if (avatarCropPath.isNotBlank() && File(avatarCropPath).exists()) {
                    AsyncImage(
                        model = File(avatarCropPath),
                        contentDescription = "Avatar",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Person,
                        contentDescription = "Avatar",
                        tint = IgTextSecondary,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (faces.isNotEmpty()) {
                    Text(
                        text = "Edit picture",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF0095F6),
                        modifier = Modifier.clickable { showFaceCropGrid = !showFaceCropGrid }
                    )
                }
                Text(
                    text = "Change 'Me' Profile",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF0095F6),
                    modifier = Modifier.clickable { onChangeMeProfileClick() }
                )
            }

            AnimatedVisibility(visible = showFaceCropGrid && faces.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(faces) { face ->
                        if (face.cropPath.isNotBlank() && File(face.cropPath).exists()) {
                            AsyncImage(
                                model = File(face.cropPath),
                                contentDescription = "Face option",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .border(1.dp, Color(0xFF26262A), CircleShape)
                                    .clickable {
                                        onSelectAvatarCrop(face.cropPath)
                                        showFaceCropGrid = false
                                    }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = nameInput,
                onValueChange = { nameInput = it },
                label = { Text("Username / Handle") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF0095F6),
                    unfocusedBorderColor = Color(0xFF26262A),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = bioInput,
                onValueChange = { bioInput = it },
                label = { Text("Bio") },
                maxLines = 3,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF0095F6),
                    unfocusedBorderColor = Color(0xFF26262A),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileQuickSwitcherSheet(
    people: List<PersonEntity>,
    activePersonId: Long,
    myProfilePersonId: Long,
    onSelectPerson: (PersonEntity) -> Unit,
    onOpenAllPeopleDirectory: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
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
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Switch Profile",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "All People (${people.size})",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF0095F6),
                    modifier = Modifier.clickable { onOpenAllPeopleDirectory() }
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(
                    items = people,
                    key = { "quick_switch_${it.personId}" }
                ) { person ->
                    val isCurrent = person.personId == activePersonId
                    val isMe = person.personId == myProfilePersonId
                    val cleanName = person.name.removePrefix("@")
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .width(76.dp)
                            .bounceClick { onSelectPerson(person) }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isCurrent) {
                                        Brush.linearGradient(listOf(Color(0xFF0095F6), Color(0xFF00E5FF)))
                                    } else {
                                        IgStoryGradient
                                    }
                                )
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
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (isMe) "@$cleanName (You)" else "@$cleanName",
                            fontSize = 12.sp,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                            color = if (isCurrent) Color.White else Color(0xFFE4E4E7),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
