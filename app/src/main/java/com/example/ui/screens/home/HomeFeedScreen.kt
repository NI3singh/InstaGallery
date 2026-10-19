// InstaGallery v1.0
package com.example.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.local.entity.PersonEntity
import com.example.ui.components.IndexingStatusBanner
import com.example.ui.components.PostCard
import com.example.ui.theme.IgBlack
import com.example.ui.theme.IgBlue
import com.example.ui.theme.IgBorder
import com.example.ui.theme.GrandHotelFont
import com.example.ui.theme.IgRed
import com.example.ui.theme.IgStoryGradient
import com.example.ui.theme.IgSurfacePill
import com.example.ui.theme.IgTextPrimary
import com.example.ui.theme.IgTextSecondary
import java.io.File

@Composable
fun HomeFeedScreen(
    viewModel: HomeViewModel,
    onPersonClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(IgBlack)
    ) {
        // Top App Bar (56.dp height)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(IgBlack)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "InstaGallery",
                fontSize = 31.sp,
                fontWeight = FontWeight.Normal,
                fontFamily = GrandHotelFont,
                color = IgTextPrimary,
                modifier = Modifier
                    .clickable { viewModel.reshuffle() }
                    .testTag("app_title")
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Live face scanning status indicator
                if (uiState.isScanning) {
                    val current = uiState.scannedCount
                    val total = if (uiState.totalToScan > 0) uiState.totalToScan else 100
                    IndexingStatusBanner(processed = current, total = total)
                }

                // Favorites filter toggle
                IconButton(
                    onClick = { viewModel.toggleFavoritesFilter() },
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("favorites_filter_button")
                ) {
                    Icon(
                        imageVector = if (uiState.isFilteringFavorites) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Filter Favorites",
                        tint = if (uiState.isFilteringFavorites) IgRed else Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Bookmarks / Saved filter toggle
                IconButton(
                    onClick = { viewModel.toggleBookmarksFilter() },
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("bookmarks_filter_button")
                ) {
                    Icon(
                        imageVector = if (uiState.isFilteringBookmarks) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = "Filter Saved Posts",
                        tint = if (uiState.isFilteringBookmarks) IgBlue else Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        HorizontalDivider(color = IgBorder, thickness = 1.dp)

        // Main Feed (LazyColumn)
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("home_feed_list")
        ) {
            // Item 1: Stories-Style People Tray
            item(key = "stories_tray") {
                StoriesPeopleTray(
                    people = uiState.people,
                    unassociatedCount = uiState.unassociatedCount,
                    onPersonClick = onPersonClick
                )
            }

            // Empty state when no media matches filter or device storage is empty
            if (uiState.photos.isEmpty()) {
                item(key = "empty_feed") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp, vertical = 64.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(IgSurfacePill),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = when (uiState.activeFilter) {
                                    HomeFeedFilter.FAVORITES -> Icons.Outlined.FavoriteBorder
                                    HomeFeedFilter.BOOKMARKED -> Icons.Outlined.BookmarkBorder
                                    HomeFeedFilter.ALL -> Icons.Outlined.PhotoCamera
                                },
                                contentDescription = "No photos",
                                tint = IgTextSecondary,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = when (uiState.activeFilter) {
                                HomeFeedFilter.FAVORITES -> "No favorite photos yet"
                                HomeFeedFilter.BOOKMARKED -> "No saved posts yet"
                                HomeFeedFilter.ALL -> "No photos found on device"
                            },
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = IgTextPrimary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = when (uiState.activeFilter) {
                                HomeFeedFilter.FAVORITES -> "Double-tap any photo or tap the heart icon to add it to your favorites."
                                HomeFeedFilter.BOOKMARKED -> "Tap the bookmark icon on any post to save it to your private collection."
                                HomeFeedFilter.ALL -> "Take some photos or download images to your phone storage to see your feed come alive."
                            },
                            fontSize = 13.sp,
                            color = IgTextSecondary,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )
                    }
                }
            } else {
                items(
                    items = uiState.photos,
                    key = { it.media.mediaId }
                ) { item ->
                    PostCard(
                        item = item,
                        onAuthorClick = onPersonClick,
                        onToggleFavorite = { id, current -> viewModel.toggleFavorite(id, current) },
                        onToggleBookmark = { id, current -> viewModel.toggleBookmark(id, current) },
                        allPeople = uiState.people,
                        onTagPerson = { mediaId, personId -> viewModel.tagPersonInMedia(mediaId, personId) },
                        onUntagPerson = { mediaId, personId -> viewModel.removePersonFromMedia(mediaId, personId) }
                    )
                    HorizontalDivider(color = IgBorder, thickness = 1.dp)
                }
            }
        }
    }
}

@Composable
fun StoriesPeopleTray(
    people: List<PersonEntity>,
    unassociatedCount: Int,
    onPersonClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(IgBlack)
    ) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(
                items = people,
                key = { it.personId }
            ) { person ->
                StoryAvatarItem(
                    name = "@${person.name}",
                    cropPath = person.avatarCropPath,
                    onClick = { onPersonClick(person.personId) }
                )
            }

            // Final Static Item: @Other (items without identified faces)
            item(key = "story_other") {
                StoryOtherItem(
                    count = unassociatedCount,
                    onClick = { onPersonClick(-1L) }
                )
            }
        }
        HorizontalDivider(color = IgBorder, thickness = 1.dp)
    }
}

@Composable
fun StoryAvatarItem(
    name: String,
    cropPath: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .testTag("story_item_${name.replace("@", "")}")
    ) {
        // 68dp outer circle with 2.5dp Instagram story gradient
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(IgStoryGradient),
            contentAlignment = Alignment.Center
        ) {
            // 2dp inner black border
            Box(
                modifier = Modifier
                    .size(63.dp)
                    .clip(CircleShape)
                    .background(IgBlack),
                contentAlignment = Alignment.Center
            ) {
                if (cropPath.isNotBlank() && File(cropPath).exists()) {
                    AsyncImage(
                        model = File(cropPath),
                        contentDescription = name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(59.dp)
                            .clip(CircleShape)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Person,
                        contentDescription = name,
                        tint = IgTextSecondary,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = name,
            fontSize = 11.sp,
            color = IgTextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(68.dp),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun StoryOtherItem(
    count: Int,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .testTag("story_item_other")
    ) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(IgStoryGradient),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(63.dp)
                    .clip(CircleShape)
                    .background(IgSurfacePill),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Group,
                    contentDescription = "Other",
                    tint = IgTextSecondary,
                    modifier = Modifier.size(26.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "@Other",
            fontSize = 11.sp,
            color = IgTextPrimary,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(68.dp)
        )
    }
}
