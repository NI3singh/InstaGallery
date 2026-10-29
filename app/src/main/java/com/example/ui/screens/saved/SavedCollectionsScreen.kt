// InstaGallery v1.0
package com.example.ui.screens.saved

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FilledExtendedIcons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.OutlinedExtendedIcons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.local.entity.CollectionEntity
import com.example.data.local.entity.MediaEntity
import com.example.data.local.entity.MediaWithAuthors
import com.example.data.repository.GalleryRepository
import com.example.ui.components.PostCard
import com.example.ui.components.bounceClick
import com.example.ui.theme.IgBlack
import com.example.ui.theme.IgBlue
import com.example.ui.theme.IgSurfacePill
import com.example.ui.theme.IgTextPrimary
import com.example.ui.theme.IgTextSecondary
import kotlinx.coroutines.launch

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
        repository.ensureDefaultCollectionsSeeded()
    }

    val collections by repository.allCollectionsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val allBookmarkedPhotos by repository.bookmarkedPhotosWithAuthors.collectAsStateWithLifecycle(initialValue = emptyList())
    val allCrossRefs by repository.getAllCollectionCrossRefsFlow().collectAsStateWithLifecycle(initialValue = emptyList())
    val allPhotos by repository.photosWithAuthors.collectAsStateWithLifecycle(initialValue = emptyList())
    val allPeople by repository.allPeople.collectAsStateWithLifecycle(initialValue = emptyList())

    val mediaWithAuthorsById = remember(allPhotos) {
        allPhotos.associateBy { it.media.mediaId }
    }

    // 0 = Collections tab, 1 = All Saved tab
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    var activeCollectionId by rememberSaveable { mutableStateOf<Long?>(null) }
    var isFeedViewMode by rememberSaveable { mutableStateOf(false) }
    var showCreateBoardDialog by remember { mutableStateOf(false) }
    var newBoardTitle by remember { mutableStateOf("") }

    val activeCollection = remember(collections, activeCollectionId) {
        collections.find { it.collectionId == activeCollectionId }
    }

    // Handle system back when viewing a specific collection detail view
    BackHandler(enabled = activeCollectionId != null) {
        activeCollectionId = null
        isFeedViewMode = false
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(IgBlack)
            .testTag("saved_collections_screen")
    ) {
        if (activeCollection != null || activeCollectionId == -1L) {
            // Collection Detail View (-1L represents "All Saved" opened from mosaic card)
            val boardTitle = if (activeCollectionId == -1L) "All Saved" else activeCollection?.name ?: "Collection"
            val boardPhotos: List<MediaWithAuthors> = remember(activeCollectionId, allBookmarkedPhotos, allCrossRefs, mediaWithAuthorsById) {
                if (activeCollectionId == -1L) {
                    allBookmarkedPhotos
                } else {
                    val refs = allCrossRefs
                        .filter { it.collectionId == activeCollectionId }
                        .sortedByDescending { it.addedAt }
                    refs.mapNotNull { mediaWithAuthorsById[it.mediaId] }
                }
            }

            // Detail Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .background(Color.Black)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = {
                            activeCollectionId = null
                            isFeedViewMode = false
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Collections",
                            tint = Color.White
                        )
                    }
                    Column {
                        Text(
                            text = boardTitle,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${boardPhotos.size} saved photos",
                            fontSize = 11.5.sp,
                            color = Color(0xFF8E8E93)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Toggle between 3-Column Grid and Vertical PostCard Feed
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF1C1C22))
                            .border(1.dp, Color(0xFF2C2C34), RoundedCornerShape(10.dp))
                            .clickable { isFeedViewMode = !isFeedViewMode }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .testTag("toggle_collection_view_mode"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (isFeedViewMode) OutlinedExtendedIcons.Home else FilledExtendedIcons.Home,
                            contentDescription = "Toggle View",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (isFeedViewMode) "Grid" else "Feed",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }

                    // Delete board button for custom user boards (collectionId > 4)
                    if (activeCollection != null && activeCollection.collectionId > 4L) {
                        IconButton(
                            onClick = {
                                val idToDelete = activeCollection.collectionId
                                activeCollectionId = null
                                scope.launch {
                                    repository.deleteCollection(idToDelete)
                                }
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = "Delete Board",
                                tint = Color(0xFFFF453A),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = Color(0xFF1A1A1E), thickness = 1.dp)

            SavedMediaGridOrFeed(
                photos = boardPhotos,
                isFeedViewMode = isFeedViewMode,
                allPeople = allPeople,
                onPersonClick = onPersonClick,
                onOpenFeedAtPhoto = { isFeedViewMode = true },
                repository = repository
            )
        } else {
            // Main Saved Screen Top Bar (52.dp height, #000000 background)
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                    Text(
                        text = "Saved",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                // "+ New Board" solid dark pill button (#1E1E22, 13.sp white text)
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFF1E1E22))
                        .border(1.dp, Color(0xFF2E2E36), RoundedCornerShape(50))
                        .bounceClick { showCreateBoardDialog = true }
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                        .testTag("new_board_button"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "New Board",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "New Board",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }

            // 2-Tab Segmented Switcher ("Collections" vs "All Saved")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .height(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF141418))
                    .padding(3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val tabs = listOf("Collections (${collections.size})", "All Saved (${allBookmarkedPhotos.size})")
                tabs.forEachIndexed { index, title ->
                    val isSelected = selectedTabIndex == index
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (isSelected) Color(0xFF26262E) else Color.Transparent)
                            .clickable { selectedTabIndex = index }
                            .testTag("saved_tab_$index"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = title,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.White else Color(0xFF8E8E93)
                        )
                    }
                }
            }

            if (selectedTabIndex == 0) {
                // Tab 1: Collections (2-Column Mosaic Cover Grid)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("collections_mosaic_grid")
                ) {
                    // Card 0: "All Saved" showing up to 4 recent bookmarked photos in a 2x2 mini-mosaic
                    item(key = "board_all_saved") {
                        val recentBookmarkedMedia = remember(allBookmarkedPhotos) {
                            allBookmarkedPhotos.take(4).map { it.media }
                        }
                        CollectionMosaicBoardCard(
                            title = "All Saved",
                            photoCount = allBookmarkedPhotos.size,
                            coverPhotos = recentBookmarkedMedia,
                            onClick = {
                                activeCollectionId = -1L
                                isFeedViewMode = false
                            },
                            modifier = Modifier.testTag("collection_card_all_saved")
                        )
                    }

                    // Cards 1..N: Each CollectionEntity
                    items(
                        items = collections,
                        key = { "board_${it.collectionId}" }
                    ) { collection ->
                        val refs = remember(allCrossRefs, collection.collectionId) {
                            allCrossRefs
                                .filter { it.collectionId == collection.collectionId }
                                .sortedByDescending { it.addedAt }
                        }
                        val boardMedia = remember(refs, mediaWithAuthorsById) {
                            refs.mapNotNull { mediaWithAuthorsById[it.mediaId]?.media }
                        }
                        CollectionMosaicBoardCard(
                            title = collection.name,
                            photoCount = boardMedia.size,
                            coverPhotos = boardMedia.take(4),
                            onClick = {
                                activeCollectionId = collection.collectionId
                                isFeedViewMode = false
                            },
                            modifier = Modifier.testTag("collection_card_${collection.collectionId}")
                        )
                    }
                }
            } else {
                // Tab 2: All Saved (with Grid / Feed toggle bar)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${allBookmarkedPhotos.size} Bookmarked Memories",
                        fontSize = 12.5.sp,
                        color = Color(0xFF8E8E93)
                    )
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1A1A20))
                            .clickable { isFeedViewMode = !isFeedViewMode }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = if (isFeedViewMode) OutlinedExtendedIcons.Home else FilledExtendedIcons.Home,
                            contentDescription = "Switch View",
                            tint = IgBlue,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = if (isFeedViewMode) "3-Column Grid" else "Vertical Feed",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = IgBlue
                        )
                    }
                }

                SavedMediaGridOrFeed(
                    photos = allBookmarkedPhotos,
                    isFeedViewMode = isFeedViewMode,
                    allPeople = allPeople,
                    onPersonClick = onPersonClick,
                    onOpenFeedAtPhoto = { isFeedViewMode = true },
                    repository = repository
                )
            }
        }
    }

    if (showCreateBoardDialog) {
        AlertDialog(
            onDismissRequest = { showCreateBoardDialog = false },
            containerColor = Color(0xFF18181E),
            title = {
                Text(
                    text = "New Collection Board",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            },
            text = {
                Column {
                    Text(
                        text = "Create a local collection board for organizing your favorite memories.",
                        fontSize = 13.sp,
                        color = Color(0xFFA1A1AA)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newBoardTitle,
                        onValueChange = { newBoardTitle = it },
                        placeholder = {
                            Text(
                                text = "e.g., Summer Trip, Wallpapers",
                                color = Color(0xFF71717A)
                            )
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = IgBlue,
                            unfocusedBorderColor = Color(0xFF2E2E36),
                            cursorColor = IgBlue
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = newBoardTitle.trim()
                        if (trimmed.isNotEmpty()) {
                            scope.launch {
                                repository.createCollection(trimmed)
                            }
                            newBoardTitle = ""
                            showCreateBoardDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = IgBlue)
                ) {
                    Text("Create", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateBoardDialog = false }) {
                    Text("Cancel", color = Color(0xFFA1A1AA))
                }
            }
        )
    }
}

@Composable
private fun CollectionMosaicBoardCard(
    title: String,
    photoCount: Int,
    coverPhotos: List<MediaEntity>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .bounceClick { onClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF16161C))
                .border(1.dp, Color(0xFF26262E), RoundedCornerShape(16.dp))
        ) {
            when {
                coverPhotos.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = OutlinedExtendedIcons.BookmarkBorder,
                                contentDescription = title,
                                tint = Color(0xFF52525B),
                                modifier = Modifier.size(34.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Empty Board",
                                fontSize = 11.sp,
                                color = Color(0xFF71717A)
                            )
                        }
                    }
                }
                coverPhotos.size < 4 -> {
                    AsyncImage(
                        model = coverPhotos.first().uri,
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                else -> {
                    // 2x2 Mini-Mosaic Grid
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            AsyncImage(
                                model = coverPhotos[0].uri,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                            )
                            AsyncImage(
                                model = coverPhotos[1].uri,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                            )
                        }
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            AsyncImage(
                                model = coverPhotos[2].uri,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                            )
                            AsyncImage(
                                model = coverPhotos[3].uri,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = title,
            fontSize = 14.5.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp)
        )

        Text(
            text = if (photoCount == 1) "1 photo" else "$photoCount photos",
            fontSize = 12.sp,
            color = Color(0xFF8E8E93),
            modifier = Modifier.padding(horizontal = 2.dp)
        )
    }
}

@Composable
private fun SavedMediaGridOrFeed(
    photos: List<MediaWithAuthors>,
    isFeedViewMode: Boolean,
    allPeople: List<com.example.data.local.entity.PersonEntity>,
    onPersonClick: (Long) -> Unit,
    onOpenFeedAtPhoto: (Long) -> Unit,
    repository: GalleryRepository
) {
    val scope = rememberCoroutineScope()

    if (photos.isEmpty()) {
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
                        imageVector = OutlinedExtendedIcons.BookmarkBorder,
                        contentDescription = "No saved photos",
                        tint = IgTextSecondary,
                        modifier = Modifier.size(30.dp)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "No Saved Photos Yet",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = IgTextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Tap or long-press the bookmark icon on any post to organize photos into collections.",
                    fontSize = 12.5.sp,
                    color = IgTextSecondary,
                    textAlign = TextAlign.Center
                )
            }
        }
    } else if (!isFeedViewMode) {
        // 3-Column Square Photo Grid
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(
                items = photos,
                key = { it.media.mediaId }
            ) { item ->
                Box(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .background(Color(0xFF121214))
                        .clickable { onOpenFeedAtPhoto(item.media.mediaId) }
                ) {
                    AsyncImage(
                        model = item.media.uri,
                        contentDescription = item.media.displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    } else {
        // Vertical Scrollable PostCard Feed
        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            items(
                items = photos,
                key = { it.media.mediaId }
            ) { item ->
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
