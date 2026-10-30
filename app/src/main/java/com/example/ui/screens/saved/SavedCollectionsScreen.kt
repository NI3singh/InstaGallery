// InstaGallery v2.0
package com.example.ui.screens.saved

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilledExtendedIcons
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.CropFree
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.OutlinedExtendedIcons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.data.local.OnboardingPreferences
import com.example.data.local.entity.CollectionEntity
import com.example.data.local.entity.MediaEntity
import com.example.data.local.entity.MediaWithAuthors
import com.example.data.local.entity.PersonEntity
import com.example.data.repository.GalleryRepository
import com.example.ui.components.CaptionEditorSheet
import com.example.ui.components.HeartBurstInstance
import com.example.ui.components.HeartThemePickerSheet
import com.example.ui.components.InstaPullToRefreshBox
import com.example.ui.components.PostCard
import com.example.ui.components.RefreshableTab
import com.example.ui.components.SaveToCollectionSheet
import com.example.ui.components.TabRefreshManager
import com.example.ui.components.TapHeartBurstOverlay
import com.example.ui.components.bounceClick
import com.example.ui.components.formatCollectionSubtitle
import com.example.ui.screens.reels.ReelsActionRail
import com.example.ui.screens.reels.ReelsBottomOverlay
import com.example.ui.theme.IgBlack
import com.example.ui.theme.IgBlue
import com.example.ui.theme.IgSurfacePill
import com.example.ui.theme.IgTextPrimary
import com.example.ui.theme.IgTextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

private enum class SavedMediaFilter {
    ALL,
    PHOTOS,
    REELS
}

@kotlin.OptIn(ExperimentalCoroutinesApi::class)
@Composable
fun SavedCollectionsScreen(
    onBackClick: () -> Unit,
    onPersonClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember(context) { GalleryRepository(context) }

    LaunchedEffect(Unit) {
        OnboardingPreferences.init(context)
        repository.ensureDefaultCollectionsSeeded()
    }

    val collections by repository.allCollectionsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val allSavedItems by repository.getAllBookmarkedPhotosWithAuthorsFlow().collectAsStateWithLifecycle(initialValue = emptyList())
    val allPeople by repository.allPeople.collectAsStateWithLifecycle(initialValue = emptyList())

    // 0 = Boards tab, 1 = All saved tab
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    var selectedCollectionId by rememberSaveable { mutableStateOf<Long?>(null) }
    var isViewingAllSavedDetail by rememberSaveable { mutableStateOf(false) }
    var isFeedViewMode by rememberSaveable { mutableStateOf(false) }
    var initialScrollMediaId by rememberSaveable { mutableStateOf<Long?>(null) }
    var activeFilterName by rememberSaveable { mutableStateOf(SavedMediaFilter.ALL.name) }
    val activeFilter = remember(activeFilterName) {
        runCatching { SavedMediaFilter.valueOf(activeFilterName) }.getOrDefault(SavedMediaFilter.ALL)
    }

    var activePlayingVideo by remember { mutableStateOf<MediaEntity?>(null) }
    var showCreateBoardDialog by remember { mutableStateOf(false) }
    var newBoardTitle by remember { mutableStateOf("") }
    var showRenameBoardDialog by remember { mutableStateOf(false) }
    var renameBoardInput by remember { mutableStateOf("") }
    var showDeleteBoardConfirm by remember { mutableStateOf(false) }
    var showBoardOverflowMenu by remember { mutableStateOf(false) }
    var showAddItemsSheet by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }

    val collectionsGridState = rememberLazyGridState()
    val mediaGridState = rememberLazyGridState()
    val feedListState = rememberLazyListState()

    val selectedCollection = remember(collections, selectedCollectionId) {
        collections.find { it.collectionId == selectedCollectionId }
    }

    val isInsideBoardDetail = selectedCollection != null || isViewingAllSavedDetail

    val boardItems by remember(repository) {
        snapshotFlow { Pair(selectedCollectionId, isViewingAllSavedDetail) }
            .flatMapLatest { (colId, isAllDetail) ->
                when {
                    colId != null -> repository.getMediaWithAuthorsForCollectionFlow(colId)
                    isAllDetail -> repository.getAllBookmarkedPhotosWithAuthorsFlow()
                    else -> flowOf(emptyList())
                }
            }
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    val triggerSavedRefresh: () -> Unit = {
        scope.launch {
            runCatching {
                when {
                    isInsideBoardDetail && isFeedViewMode -> feedListState.animateScrollToItem(0)
                    isInsideBoardDetail -> mediaGridState.animateScrollToItem(0)
                    selectedTabIndex == 0 -> collectionsGridState.animateScrollToItem(0)
                    isFeedViewMode -> feedListState.animateScrollToItem(0)
                    else -> mediaGridState.animateScrollToItem(0)
                }
            }
            if (!isRefreshing) {
                isRefreshing = true
                runCatching { repository.performQuickRefreshSync() }
                delay(250)
                isRefreshing = false
            }
        }
    }

    LaunchedEffect(Unit) {
        TabRefreshManager.refreshEvents.collect { tab ->
            if (tab == RefreshableTab.SAVED) {
                triggerSavedRefresh()
            }
        }
    }

    // BackHandlers layered in priority order:
    // 1. SavedReelPlayerDialog has its own BackHandler when activePlayingVideo != null
    // 2. When in vertical PostCard feed mode, Back returns to the 3-column grid
    BackHandler(enabled = activePlayingVideo == null && isFeedViewMode) {
        isFeedViewMode = false
        initialScrollMediaId = null
    }

    // 3. When inside a specific collection board (or All Saved detail from mosaic), Back returns to root Boards
    BackHandler(enabled = activePlayingVideo == null && !isFeedViewMode && isInsideBoardDetail) {
        selectedCollectionId = null
        isViewingAllSavedDetail = false
        activeFilterName = SavedMediaFilter.ALL.name
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("saved_collections_screen")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 2.1 Top App Bar (52.dp height, pure #000000 background)
            if (isInsideBoardDetail) {
                val boardTitle = selectedCollection?.name ?: "All saved"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .background(Color.Black)
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left: Back arrow (24.dp, Color.White) + board name (19.sp, FontWeight.Bold, Color.White)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .bounceClick {
                                    if (isFeedViewMode) {
                                        isFeedViewMode = false
                                        initialScrollMediaId = null
                                    } else {
                                        selectedCollectionId = null
                                        isViewingAllSavedDetail = false
                                        activeFilterName = SavedMediaFilter.ALL.name
                                    }
                                }
                                .testTag("saved_board_back_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Text(
                            text = boardTitle,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Right (spacedBy(10.dp)): + Add squircle button, Grid / Feed toggle icon button, MoreVert button
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (selectedCollection != null) {
                            Row(
                                modifier = Modifier
                                    .height(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF26262A))
                                    .bounceClick { showAddItemsSheet = true }
                                    .padding(horizontal = 10.dp)
                                    .testTag("board_add_items_button"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Add to board",
                                    tint = Color.White,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = "Add",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                            }
                        }

                        // Grid / Feed toggle icon button (32.dp squircle, Color(0xFF26262A))
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF26262A))
                                .bounceClick {
                                    isFeedViewMode = !isFeedViewMode
                                }
                                .testTag("toggle_collection_view_mode"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isFeedViewMode) Icons.Filled.GridOn else OutlinedExtendedIcons.Home,
                                contentDescription = if (isFeedViewMode) "Grid view" else "Feed view",
                                tint = Color.White,
                                modifier = Modifier.size(17.dp)
                            )
                        }

                        // MoreVert icon button to Rename board or Delete board
                        if (selectedCollection != null) {
                            Box {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF26262A))
                                        .bounceClick { showBoardOverflowMenu = true }
                                        .testTag("board_more_menu_button"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "Board options",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                DropdownMenu(
                                    expanded = showBoardOverflowMenu,
                                    onDismissRequest = { showBoardOverflowMenu = false },
                                    modifier = Modifier.background(Color(0xFF1C1C20))
                                ) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = "Rename board",
                                                color = Color.White,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        },
                                        onClick = {
                                            showBoardOverflowMenu = false
                                            renameBoardInput = selectedCollection.name
                                            showRenameBoardDialog = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = "Delete board",
                                                color = Color(0xFFFF453A),
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Outlined.DeleteOutline,
                                                contentDescription = null,
                                                tint = Color(0xFFFF453A),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        },
                                        onClick = {
                                            showBoardOverflowMenu = false
                                            showDeleteBoardConfirm = true
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFF1F1F22), thickness = 0.5.dp)

                InstaPullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = triggerSavedRefresh,
                    modifier = Modifier.fillMaxSize()
                ) {
                    SavedMediaFilterAndContent(
                        items = boardItems,
                        activeFilter = activeFilter,
                        onSelectFilter = { activeFilterName = it.name },
                        isFeedViewMode = isFeedViewMode,
                        onToggleFeedMode = { isFeedViewMode = it },
                        initialScrollMediaId = initialScrollMediaId,
                        onOpenFeedAtPhoto = { mediaId ->
                            initialScrollMediaId = mediaId
                            isFeedViewMode = true
                        },
                        onPlaySavedVideo = { videoMedia ->
                            activePlayingVideo = videoMedia
                        },
                        allPeople = allPeople,
                        onPersonClick = onPersonClick,
                        repository = repository,
                        gridState = mediaGridState,
                        listState = feedListState
                    )
                }
            } else {
                // Root Saved Screen Top Bar (NO back arrow when at root tab!)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .background(Color.Black)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Saved",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.bounceClick { triggerSavedRefresh() }
                    )

                    // Native Instagram matte squircle button (height = 34.dp, RoundedCornerShape(8.dp), Color(0xFF26262A), zero border)
                    Row(
                        modifier = Modifier
                            .height(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF26262A))
                            .bounceClick { showCreateBoardDialog = true }
                            .padding(horizontal = 12.dp)
                            .testTag("new_board_button"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "New board",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "New board",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                }

                // 2.2 Clean Instagram Underline Tab Bar (height = 44.dp, #000000 background, bottom 0.5.dp #1F1F22 hairline)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val tabs = listOf(
                            "Boards",
                            "All saved (${allSavedItems.size})"
                        )
                        tabs.forEachIndexed { index, title ->
                            val isSelected = selectedTabIndex == index
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clickable {
                                        selectedTabIndex = index
                                        isFeedViewMode = false
                                        initialScrollMediaId = null
                                    }
                                    .testTag("saved_tab_$index"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = title,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isSelected) Color.White else Color(0xFF8E8E93)
                                )
                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .fillMaxWidth()
                                            .height(2.dp)
                                            .background(Color.White)
                                    )
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = Color(0xFF1F1F22), thickness = 0.5.dp)
                }

                InstaPullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = triggerSavedRefresh,
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (selectedTabIndex == 0) {
                        // 2.3 Redesigned 2-Column Collection Board Cards (GridCells.Fixed(2), spacedBy(14.dp))
                        val allSavedMediaEntities = remember(allSavedItems) {
                            allSavedItems.map { it.media }
                        }
                        LazyVerticalGrid(
                            state = collectionsGridState,
                            columns = GridCells.Fixed(2),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("collections_mosaic_grid")
                        ) {
                            // Virtual Board 0: "All saved"
                            item(key = "board_all_saved") {
                                CollectionMosaicBoardCard(
                                    title = "All saved",
                                    items = allSavedMediaEntities,
                                    onClick = {
                                        isViewingAllSavedDetail = true
                                        isFeedViewMode = false
                                        activeFilterName = SavedMediaFilter.ALL.name
                                    },
                                    modifier = Modifier.testTag("collection_card_all_saved")
                                )
                            }

                            // User & Default Boards 1..N
                            items(
                                items = collections,
                                key = { "board_${it.collectionId}" }
                            ) { collection ->
                                val boardMedia by remember(repository, collection.collectionId) {
                                    repository.getMediaForCollectionFlow(collection.collectionId)
                                }.collectAsStateWithLifecycle(initialValue = emptyList())

                                CollectionMosaicBoardCard(
                                    title = collection.name,
                                    items = boardMedia,
                                    onClick = {
                                        selectedCollectionId = collection.collectionId
                                        isFeedViewMode = false
                                        activeFilterName = SavedMediaFilter.ALL.name
                                    },
                                    modifier = Modifier.testTag("collection_card_${collection.collectionId}")
                                )
                            }
                        }
                    } else {
                        // Tab 2: All saved unified grid/feed with media-type filter chips
                        SavedMediaFilterAndContent(
                            items = allSavedItems,
                            activeFilter = activeFilter,
                            onSelectFilter = { activeFilterName = it.name },
                            isFeedViewMode = isFeedViewMode,
                            onToggleFeedMode = { isFeedViewMode = it },
                            initialScrollMediaId = initialScrollMediaId,
                            onOpenFeedAtPhoto = { mediaId ->
                                initialScrollMediaId = mediaId
                                isFeedViewMode = true
                            },
                            onPlaySavedVideo = { videoMedia ->
                                activePlayingVideo = videoMedia
                            },
                            allPeople = allPeople,
                            onPersonClick = onPersonClick,
                            repository = repository,
                            gridState = mediaGridState,
                            listState = feedListState
                        )
                    }
                }
            }
        }

        // 2.5 Inline Full-Screen Saved Video Player (SavedReelPlayerDialog)
        val playingVideo = activePlayingVideo
        if (playingVideo != null) {
            val liveVideoWithAuthors = remember(allSavedItems, playingVideo) {
                allSavedItems.firstOrNull { it.media.mediaId == playingVideo.mediaId }
            }
            val liveMedia by remember(repository, playingVideo.mediaId) {
                repository.getMediaById(playingVideo.mediaId)
            }.collectAsStateWithLifecycle(initialValue = liveVideoWithAuthors)

            val resolvedVideo = liveMedia ?: liveVideoWithAuthors ?: MediaWithAuthors(
                media = playingVideo,
                authors = emptyList()
            )

            SavedReelPlayerDialog(
                video = resolvedVideo,
                repository = repository,
                onDismiss = { activePlayingVideo = null }
            )
        }
    }

    // Create New Board Dialog (Instagram Dark Mode styling)
    if (showCreateBoardDialog) {
        InstagramBoardNameDialog(
            title = "New board",
            subtitle = "Create a collection board for your saved posts and reels.",
            value = newBoardTitle,
            placeholder = "Board name...",
            confirmLabel = "Create",
            onValueChange = { newBoardTitle = it },
            onDismiss = {
                showCreateBoardDialog = false
                newBoardTitle = ""
            },
            onConfirm = {
                val trimmed = newBoardTitle.trim()
                if (trimmed.isNotEmpty()) {
                    scope.launch {
                        repository.createCollection(trimmed)
                    }
                    newBoardTitle = ""
                    showCreateBoardDialog = false
                }
            }
        )
    }

    // Rename Board Dialog
    if (showRenameBoardDialog && selectedCollection != null) {
        InstagramBoardNameDialog(
            title = "Rename board",
            subtitle = "Enter a new name for this collection board.",
            value = renameBoardInput,
            placeholder = "Board name...",
            confirmLabel = "Save",
            onValueChange = { renameBoardInput = it },
            onDismiss = { showRenameBoardDialog = false },
            onConfirm = {
                val trimmed = renameBoardInput.trim()
                val targetId = selectedCollection.collectionId
                if (trimmed.isNotEmpty()) {
                    scope.launch {
                        repository.renameCollection(targetId, trimmed)
                    }
                    showRenameBoardDialog = false
                }
            }
        )
    }

    // Delete Board Confirmation Dialog
    if (showDeleteBoardConfirm && selectedCollection != null) {
        AlertDialog(
            onDismissRequest = { showDeleteBoardConfirm = false },
            containerColor = Color(0xFF18181C),
            title = {
                Text(
                    text = "Delete \"${selectedCollection.name}\"?",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            },
            text = {
                Text(
                    text = "Your saved photos and reels will still remain in All saved.",
                    fontSize = 13.5.sp,
                    color = Color(0xFF8E8E93)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val idToDelete = selectedCollection.collectionId
                        showDeleteBoardConfirm = false
                        selectedCollectionId = null
                        scope.launch {
                            repository.deleteCollection(idToDelete)
                        }
                    }
                ) {
                    Text(
                        text = "Delete",
                        color = Color(0xFFFF453A),
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteBoardConfirm = false }) {
                    Text("Cancel", color = Color.White)
                }
            }
        )
    }

    // Add Bookmarked Items to Board Sheet
    if (showAddItemsSheet && selectedCollection != null) {
        AddItemsToBoardSheet(
            collection = selectedCollection,
            allSavedItems = allSavedItems,
            currentBoardMediaIds = boardItems.map { it.media.mediaId }.toSet(),
            repository = repository,
            onDismiss = { showAddItemsSheet = false }
        )
    }
}

/**
 * 2.3 Redesigned 2-Column Collection Board Card:
 * - Case A (items.size >= 4): 2x2 Edge-to-Edge Mosaic separated by 1.dp #000000 inner hairline cross
 * - Case B (items.size in 1..3): Full-Bleed Cover with subtle bottom-right count pill if items.size > 1
 * - Case C (items.isEmpty()): Minimal Architectural Dark Grid (2x2 dark bento cells in #16161A separated by 1.dp #222226 hairlines) with 20.dp BookmarkBorder at 30% white alpha (NO "Empty Board" text!)
 */
@Composable
private fun CollectionMosaicBoardCard(
    title: String,
    items: List<MediaEntity>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val subtitle = remember(items) {
        formatCollectionSubtitle(items)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .bounceClick { onClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF141418))
                .border(0.5.dp, Color(0xFF26262A), RoundedCornerShape(14.dp))
        ) {
            when {
                items.size >= 4 -> {
                    // Case A — 2x2 Edge-to-Edge Mosaic separated by 1.dp #000000 inner hairline cross
                    val topFour = items.take(4)
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black),
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(1.dp)
                        ) {
                            MosaicCoverTile(
                                media = topFour[0],
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                            )
                            MosaicCoverTile(
                                media = topFour[1],
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                            )
                        }
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(1.dp)
                        ) {
                            MosaicCoverTile(
                                media = topFour[2],
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                            )
                            MosaicCoverTile(
                                media = topFour[3],
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                            )
                        }
                    }
                }
                items.isNotEmpty() -> {
                    // Case B — Full-Bleed Cover (1..3 items)
                    val lead = items.first()
                    Box(modifier = Modifier.fillMaxSize()) {
                        AsyncImage(
                            model = lead.uri,
                            contentDescription = title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        if (lead.isVideo) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Video",
                                tint = Color.White,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                                    .size(14.dp)
                            )
                        }
                        if (items.size > 1) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(8.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.Black.copy(alpha = 0.65f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "${items.size}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
                else -> {
                    // Case C — Minimal Architectural Dark Grid (NO "Empty Board" text!)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF222226)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(1.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(1.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxSize()
                                        .background(Color(0xFF16161A))
                                )
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxSize()
                                        .background(Color(0xFF16161A))
                                )
                            }
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(1.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxSize()
                                        .background(Color(0xFF16161A))
                                )
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxSize()
                                        .background(Color(0xFF16161A))
                                )
                            }
                        }

                        Icon(
                            imageVector = OutlinedExtendedIcons.BookmarkBorder,
                            contentDescription = title,
                            tint = Color.White.copy(alpha = 0.30f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier.padding(top = 8.dp, start = 2.dp)
        ) {
            Text(
                text = title,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = Color(0xFF8E8E93),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun MosaicCoverTile(
    media: MediaEntity,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.background(Color(0xFF141418))) {
        AsyncImage(
            model = media.uri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        if (media.isVideo) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Reel",
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(12.dp)
            )
        }
    }
}

/**
 * 2.4 Media-Type Filter Squircle Chips (All, Photos, Reels) & Grid/Feed View
 */
@Composable
private fun SavedMediaFilterAndContent(
    items: List<MediaWithAuthors>,
    activeFilter: SavedMediaFilter,
    onSelectFilter: (SavedMediaFilter) -> Unit,
    isFeedViewMode: Boolean,
    onToggleFeedMode: (Boolean) -> Unit,
    initialScrollMediaId: Long?,
    onOpenFeedAtPhoto: (Long) -> Unit,
    onPlaySavedVideo: (MediaEntity) -> Unit,
    allPeople: List<PersonEntity>,
    onPersonClick: (Long) -> Unit,
    repository: GalleryRepository,
    gridState: LazyGridState,
    listState: LazyListState
) {
    val scope = rememberCoroutineScope()
    val photoCount = remember(items) { items.count { !it.media.isVideo } }
    val reelCount = remember(items) { items.count { it.media.isVideo } }

    val filteredItems = remember(items, activeFilter) {
        when (activeFilter) {
            SavedMediaFilter.ALL -> items
            SavedMediaFilter.PHOTOS -> items.filter { !it.media.isVideo }
            SavedMediaFilter.REELS -> items.filter { it.media.isVideo }
        }
    }

    LaunchedEffect(isFeedViewMode, initialScrollMediaId, filteredItems) {
        if (isFeedViewMode && initialScrollMediaId != null) {
            val targetIdx = filteredItems.indexOfFirst { it.media.mediaId == initialScrollMediaId }
            if (targetIdx >= 0) {
                runCatching { listState.scrollToItem(targetIdx) }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top Filter & View Controls Row (padding(horizontal = 16.dp, vertical = 10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: 3 native squircle filter chips (height = 32.dp, RoundedCornerShape(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SavedFilterChip(
                    label = "All (${items.size})",
                    selected = activeFilter == SavedMediaFilter.ALL,
                    onClick = { onSelectFilter(SavedMediaFilter.ALL) },
                    testTag = "saved_filter_all"
                )
                SavedFilterChip(
                    label = "Photos ($photoCount)",
                    selected = activeFilter == SavedMediaFilter.PHOTOS,
                    onClick = { onSelectFilter(SavedMediaFilter.PHOTOS) },
                    testTag = "saved_filter_photos"
                )
                SavedFilterChip(
                    label = "Reels ($reelCount)",
                    selected = activeFilter == SavedMediaFilter.REELS,
                    onClick = { onSelectFilter(SavedMediaFilter.REELS) },
                    testTag = "saved_filter_reels"
                )
            }

            // Right: Segmented icon toggle (32.dp height, Color(0xFF26262A), RoundedCornerShape(8.dp))
            Row(
                modifier = Modifier
                    .height(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF26262A))
                    .padding(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (!isFeedViewMode) Color(0xFF3A3A40) else Color.Transparent)
                        .clickable { onToggleFeedMode(false) }
                        .testTag("saved_view_grid_toggle"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.GridOn,
                        contentDescription = "3-Column Grid",
                        tint = if (!isFeedViewMode) Color.White else Color(0xFF8E8E93),
                        modifier = Modifier.size(16.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isFeedViewMode) Color(0xFF3A3A40) else Color.Transparent)
                        .clickable { onToggleFeedMode(true) }
                        .testTag("saved_view_feed_toggle"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isFeedViewMode) FilledExtendedIcons.Home else OutlinedExtendedIcons.Home,
                        contentDescription = "Vertical Post Feed",
                        tint = if (isFeedViewMode) Color.White else Color(0xFF8E8E93),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        if (filteredItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(IgSurfacePill),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (activeFilter == SavedMediaFilter.REELS) OutlinedExtendedIcons.Movie else OutlinedExtendedIcons.BookmarkBorder,
                            contentDescription = "No saved items",
                            tint = IgTextSecondary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = when (activeFilter) {
                            SavedMediaFilter.REELS -> "No Saved Reels Yet"
                            SavedMediaFilter.PHOTOS -> "No Saved Photos Yet"
                            SavedMediaFilter.ALL -> "No Saved Items Yet"
                        },
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = IgTextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Tap the bookmark icon on any post or reel to save it here.",
                        fontSize = 13.sp,
                        color = IgTextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else if (!isFeedViewMode) {
            // 3-Column Grid Tile Enhancements (GridCells.Fixed(3), spacedBy(2.dp))
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(
                    items = filteredItems,
                    key = { it.media.mediaId }
                ) { item ->
                    val media = item.media
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .background(Color(0xFF121214))
                            .clickable {
                                if (media.isVideo) {
                                    onPlaySavedVideo(media)
                                } else {
                                    onOpenFeedAtPhoto(media.mediaId)
                                }
                            }
                            .testTag("saved_grid_tile_${media.mediaId}")
                    ) {
                        AsyncImage(
                            model = media.uri,
                            contentDescription = media.displayName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )

                        if (media.isVideo) {
                            // Subtle top-right scrim + Reels clapperboard/play icon (16.dp, Color.White)
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp)
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.42f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = OutlinedExtendedIcons.Movie,
                                    contentDescription = "Saved Reel",
                                    tint = Color.White,
                                    modifier = Modifier.size(15.dp)
                                )
                            }

                            // Subtle bottom gradient + bottom-left formatted duration label ("0:18", 11.sp, FontWeight.SemiBold)
                            val durationLabel = remember(media.durationMs) {
                                formatGridVideoDuration(media.durationMs)
                            }
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .height(28.dp)
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color.Transparent,
                                                Color.Black.copy(alpha = 0.65f)
                                            )
                                        )
                                    )
                            )
                            if (durationLabel != null) {
                                Text(
                                    text = durationLabel,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White,
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(horizontal = 6.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // Vertical Scrollable PostCard Feed
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize()
            ) {
                items(
                    items = filteredItems,
                    key = { it.media.mediaId }
                ) { item ->
                    if (item.media.isVideo) {
                        SavedVideoFeedCard(
                            item = item,
                            onPlayClick = { onPlaySavedVideo(item.media) },
                            onToggleFavorite = {
                                scope.launch { repository.toggleFavorite(item.media) }
                            },
                            onToggleBookmark = {
                                scope.launch { repository.toggleBookmark(item.media) }
                            }
                        )
                    } else {
                        PostCard(
                            item = item,
                            onAuthorClick = onPersonClick,
                            onToggleFavorite = { mediaId, current ->
                                scope.launch {
                                    repository.toggleFavorite(mediaId, current)
                                }
                            },
                            onToggleBookmark = { mediaId, current ->
                                scope.launch {
                                    repository.toggleBookmarked(mediaId, current)
                                }
                            },
                            onUpdateCaption = { newCaption ->
                                scope.launch {
                                    repository.updateMediaCaption(item.media.mediaId, newCaption)
                                }
                            },
                            allPeople = allPeople,
                            onTagPerson = { mediaId, personId ->
                                scope.launch {
                                    repository.tagPersonInMedia(mediaId, personId)
                                }
                            },
                            onUntagPerson = { mediaId, personId ->
                                scope.launch {
                                    repository.removeMediaFromPerson(mediaId, personId)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SavedFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Box(
        modifier = Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) Color.White else Color(0xFF26262A))
            .bounceClick { onClick() }
            .padding(horizontal = 12.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) Color.Black else Color(0xFFE4E4E7)
        )
    }
}

@Composable
private fun SavedVideoFeedCard(
    item: MediaWithAuthors,
    onPlayClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleBookmark: () -> Unit
) {
    val media = item.media
    val formattedDate = remember(media.dateAddedSeconds) {
        SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
            .format(Date(media.dateAddedSeconds * 1000L))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black)
            .padding(bottom = 14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1C1C20)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = OutlinedExtendedIcons.Movie,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Column {
                    Text(
                        text = "@Reels",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = formattedDate,
                        fontSize = 11.sp,
                        color = Color(0xFF8E8E93)
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 5f)
                .background(Color(0xFF121214))
                .clickable { onPlayClick() },
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = media.uri,
                contentDescription = media.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.52f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play Reel",
                    tint = Color.White,
                    modifier = Modifier.size(30.dp)
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(
                imageVector = if (media.isFavorite) FilledExtendedIcons.Favorite else OutlinedExtendedIcons.FavoriteBorder,
                contentDescription = "Like",
                tint = if (media.isFavorite) Color(0xFFFF3040) else Color.White,
                modifier = Modifier
                    .size(26.dp)
                    .bounceClick { onToggleFavorite() }
            )
            Icon(
                imageVector = if (media.isBookmarked) FilledExtendedIcons.Bookmark else OutlinedExtendedIcons.BookmarkBorder,
                contentDescription = "Bookmark",
                tint = Color.White,
                modifier = Modifier
                    .size(26.dp)
                    .bounceClick { onToggleBookmark() }
            )
        }
    }
}

/**
 * 2.5 Inline Full-Screen Saved Video Player (SavedReelPlayerDialog)
 */
@OptIn(UnstableApi::class)
@Composable
private fun SavedReelPlayerDialog(
    video: MediaWithAuthors,
    repository: GalleryRepository,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val media = video.media
    val heartTheme by OnboardingPreferences.heartThemeFlow.collectAsStateWithLifecycle()

    var isFitMode by rememberSaveable { mutableStateOf(false) }
    var isMuted by rememberSaveable { mutableStateOf(false) }
    var isPausedByUser by remember { mutableStateOf(false) }
    var showHeartThemeSheet by remember { mutableStateOf(false) }
    var showCaptionSheet by remember { mutableStateOf(false) }
    var showSaveCollectionSheet by remember { mutableStateOf(false) }
    var showSavedFloatingToast by remember { mutableStateOf(false) }
    var savedToastTriggerCount by remember { mutableIntStateOf(0) }
    val heartBursts = remember { mutableStateListOf<HeartBurstInstance>() }

    BackHandler {
        onDismiss()
    }

    LaunchedEffect(savedToastTriggerCount) {
        if (savedToastTriggerCount > 0) {
            showSavedFloatingToast = true
            delay(3000L)
            showSavedFloatingToast = false
        }
    }

    val exoPlayer = remember(context, media.uri) {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true
            )
            setMediaItem(MediaItem.fromUri(Uri.parse(media.uri)))
            prepare()
            playWhenReady = true
        }
    }

    LaunchedEffect(isMuted) {
        exoPlayer.volume = if (isMuted) 0f else 1f
    }

    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> exoPlayer.pause()
                Lifecycle.Event.ON_RESUME -> {
                    if (!isPausedByUser && !showCaptionSheet && !showSaveCollectionSheet) {
                        exoPlayer.playWhenReady = true
                        exoPlayer.play()
                    }
                }
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
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(media.mediaId, media.isFavorite) {
                detectTapGestures(
                    onDoubleTap = { tapOffset ->
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        val burst = HeartBurstInstance(
                            id = System.nanoTime(),
                            tapOffset = tapOffset,
                            rotationDeg = (Random.nextFloat() - 0.5f) * 28f
                        )
                        heartBursts.add(burst)
                        if (!media.isFavorite) {
                            scope.launch {
                                repository.setFavorite(media.mediaId, true)
                            }
                        }
                    },
                    onTap = {
                        if (exoPlayer.isPlaying) {
                            exoPlayer.pause()
                            isPausedByUser = true
                        } else {
                            isPausedByUser = false
                            exoPlayer.playWhenReady = true
                            exoPlayer.play()
                        }
                    }
                )
            }
            .testTag("saved_reel_player_dialog")
    ) {
        // ExoPlayer inside AndroidView(PlayerView) with useController = false
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    useController = false
                    player = exoPlayer
                    resizeMode = if (isFitMode) {
                        AspectRatioFrameLayout.RESIZE_MODE_FIT
                    } else {
                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    }
                }
            },
            update = { playerView ->
                playerView.player = exoPlayer
                playerView.resizeMode = if (isFitMode) {
                    AspectRatioFrameLayout.RESIZE_MODE_FIT
                } else {
                    AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                }
            },
            onRelease = { playerView ->
                playerView.player = null
            },
            modifier = Modifier.fillMaxSize()
        )

        // Subtle Top & Bottom Gradient Scrims
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.65f),
                            Color.Transparent
                        )
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.82f)
                        )
                    )
                )
        )

        // Double-Tap Heart Burst Overlay
        TapHeartBurstOverlay(
            bursts = heartBursts,
            heartTheme = heartTheme,
            onBurstFinished = { burstId -> heartBursts.removeAll { it.id == burstId } },
            modifier = Modifier.fillMaxSize()
        )

        // Center Play/Pause Overlay (68.dp circular dark translucent container at Alignment.Center)
        AnimatedVisibility(
            visible = isPausedByUser,
            enter = fadeIn(tween(150)) + scaleIn(initialScale = 0.85f),
            exit = fadeOut(tween(150)) + scaleOut(targetScale = 0.85f),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.52f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Paused",
                    tint = Color.White,
                    modifier = Modifier.size(30.dp)
                )
            }
        }

        // Top Bar: Top-left <- Back button + "Saved Reel" title + top-right matte squircle Fit / Fill toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.42f))
                        .bounceClick { onDismiss() }
                        .testTag("saved_reel_back_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Text(
                    text = "Saved Reel",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            // Matte squircle Fit / Fill toggle
            Row(
                modifier = Modifier
                    .height(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF26262A).copy(alpha = 0.85f))
                    .bounceClick { isFitMode = !isFitMode }
                    .padding(horizontal = 10.dp)
                    .testTag("saved_reel_aspect_ratio_button"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isFitMode) Icons.Outlined.AspectRatio else Icons.Outlined.CropFree,
                    contentDescription = if (isFitMode) "Fit Mode" else "Fill Mode",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isFitMode) "Fit" else "Fill",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        }

        // Right-side vertical action rail (Heart, Comment/Caption, Bookmark, Share, Mute)
        ReelsActionRail(
            media = media,
            isMuted = isMuted,
            heartColor = heartTheme.primaryColor,
            onToggleFavorite = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                scope.launch {
                    repository.toggleFavorite(media)
                }
            },
            onLongPressFavorite = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                showHeartThemeSheet = true
            },
            onOpenCaptionEditor = {
                exoPlayer.pause()
                isPausedByUser = true
                showCaptionSheet = true
            },
            onToggleBookmark = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                val willBeBookmarked = !media.isBookmarked
                scope.launch {
                    repository.toggleBookmark(media)
                }
                if (willBeBookmarked) {
                    savedToastTriggerCount++
                } else {
                    showSavedFloatingToast = false
                }
            },
            onLongPressBookmark = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                exoPlayer.pause()
                isPausedByUser = true
                showSaveCollectionSheet = true
            },
            onShareVideo = {
                shareSavedVideo(context, media.uri)
            },
            onToggleMute = {
                isMuted = !isMuted
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 14.dp, bottom = 24.dp)
        )

        // Bottom-left @Reels • $date header and interactive caption line
        ReelsBottomOverlay(
            video = video,
            onOpenCaptionEditor = {
                exoPlayer.pause()
                isPausedByUser = true
                showCaptionSheet = true
            },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(0.78f)
                .padding(start = 16.dp, bottom = 24.dp)
        )

        // Floating "Saved • Add to Collection ▸" Bottom Toast Bar
        AnimatedVisibility(
            visible = showSavedFloatingToast,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 22.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF18181E).copy(alpha = 0.96f))
                    .border(0.5.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(12.dp))
                    .clickable {
                        showSavedFloatingToast = false
                        exoPlayer.pause()
                        isPausedByUser = true
                        showSaveCollectionSheet = true
                    }
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Saved",
                    fontSize = 13.5.sp,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Add to Collection ▸",
                    fontSize = 13.5.sp,
                    color = Color(0xFF0095F6),
                    fontWeight = FontWeight.Bold
                )
            }
        }
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
        val formattedDate = remember(media.dateAddedSeconds) {
            SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
                .format(Date(media.dateAddedSeconds * 1000L))
        }
        CaptionEditorSheet(
            media = media,
            primaryAuthorName = "Reels",
            formattedDate = formattedDate,
            onSaveCaption = { newCaption ->
                scope.launch {
                    repository.updateMediaCaption(media.mediaId, newCaption)
                }
            },
            onDismiss = { showCaptionSheet = false }
        )
    }

    if (showSaveCollectionSheet) {
        SaveToCollectionSheet(
            media = media,
            onDismiss = { showSaveCollectionSheet = false }
        )
    }
}

@Composable
private fun InstagramBoardNameDialog(
    title: String,
    subtitle: String,
    value: String,
    placeholder: String,
    confirmLabel: String,
    onValueChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF18181C),
        title = {
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        },
        text = {
            Column {
                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    color = Color(0xFF8E8E93)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF26262A))
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            fontSize = 14.sp,
                            color = Color(0xFF8E8E93)
                        )
                    }
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
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
            }
        },
        confirmButton = {
            val enabled = value.trim().isNotEmpty()
            Box(
                modifier = Modifier
                    .height(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (enabled) Color(0xFF0095F6) else Color(0xFF0095F6).copy(alpha = 0.4f))
                    .bounceClick {
                        if (enabled) onConfirm()
                    }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = confirmLabel,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF8E8E93))
            }
        }
    )
}

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddItemsToBoardSheet(
    collection: CollectionEntity,
    allSavedItems: List<MediaWithAuthors>,
    currentBoardMediaIds: Set<Long>,
    repository: GalleryRepository,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
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
                .padding(bottom = 20.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Add to ${collection.name}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Select from your bookmarked photos and reels",
                        fontSize = 12.sp,
                        color = Color(0xFF8E8E93)
                    )
                }

                Box(
                    modifier = Modifier
                        .height(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0095F6))
                        .bounceClick { onDismiss() }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Done",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }

            HorizontalDivider(
                color = Color(0xFF1F1F22),
                thickness = 0.5.dp,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            if (allSavedItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No bookmarked items available yet.",
                        fontSize = 13.5.sp,
                        color = Color(0xFF8E8E93)
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                ) {
                    items(
                        items = allSavedItems,
                        key = { it.media.mediaId }
                    ) { item ->
                        val isInBoard = currentBoardMediaIds.contains(item.media.mediaId)
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .background(Color(0xFF16161A))
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    scope.launch {
                                        repository.toggleMediaInCollection(
                                            collectionId = collection.collectionId,
                                            mediaId = item.media.mediaId,
                                            isCurrentlyInCollection = isInBoard
                                        )
                                    }
                                }
                        ) {
                            AsyncImage(
                                model = item.media.uri,
                                contentDescription = item.media.displayName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            if (item.media.isVideo) {
                                Icon(
                                    imageVector = OutlinedExtendedIcons.Movie,
                                    contentDescription = "Reel",
                                    tint = Color.White,
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(6.dp)
                                        .size(14.dp)
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp)
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(if (isInBoard) Color(0xFF0095F6) else Color.Black.copy(alpha = 0.45f))
                                    .border(
                                        width = 1.5.dp,
                                        color = if (isInBoard) Color(0xFF0095F6) else Color.White.copy(alpha = 0.8f),
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isInBoard) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "In board",
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatGridVideoDuration(durationMs: Long?): String? {
    val ms = durationMs ?: return null
    if (ms <= 0L) return null
    val totalSeconds = (ms / 1000L).coerceAtLeast(1L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return String.format(Locale.US, "%d:%02d", minutes, seconds)
}

private fun shareSavedVideo(context: Context, uriString: String) {
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
