// InstaGallery v1.0
package com.example.ui.screens.home

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.FilledExtendedIcons
import androidx.compose.material.icons.outlined.OutlinedExtendedIcons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import coil.compose.AsyncImage
import com.example.data.local.OnboardingPreferences
import com.example.data.local.entity.PersonEntity
import com.example.ui.components.HeartThemePickerSheet
import com.example.ui.components.IndexingStatusBanner
import com.example.ui.components.NewPeopleDetectedBanner
import com.example.ui.components.PostCard
import com.example.ui.components.bounceClick
import com.example.ui.theme.GrandHotelFont
import com.example.ui.theme.IgBlack
import com.example.ui.theme.IgSurfacePill
import com.example.ui.theme.IgTextPrimary
import com.example.ui.theme.IgTextSecondary
import java.io.File

private val StorySunsetGradient = Brush.linearGradient(
    colors = listOf(
        Color(0xFFFEDA77),
        Color(0xFFF58529),
        Color(0xFFDD2A7B),
        Color(0xFF8134AF)
    )
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeFeedScreen(
    viewModel: HomeViewModel,
    onPersonClick: (Long) -> Unit,
    onReviewNewPeopleClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    LaunchedEffect(context) {
        OnboardingPreferences.init(context)
    }
    val heartTheme by OnboardingPreferences.heartThemeFlow.collectAsStateWithLifecycle()
    var showHeartThemeSheet by remember { mutableStateOf(false) }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(IgBlack)
    ) {
        // Top App Bar (52.dp height, pure #000000 background, no bottom divider)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .background(Color(0xFF000000))
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "InstaGallery",
                fontSize = 30.sp,
                fontWeight = FontWeight.Normal,
                fontFamily = GrandHotelFont,
                color = Color.White,
                modifier = Modifier
                    .bounceClick { viewModel.reshuffle() }
                    .testTag("app_title")
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Favorites filter toggle (24.dp with tiny heartTheme dot indicator when active; long-press opens Heart Color Picker)
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .combinedClickable(
                            onClick = { viewModel.toggleFavoritesFilter() },
                            onLongClick = { showHeartThemeSheet = true }
                        )
                        .testTag("favorites_filter_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (uiState.isFilteringFavorites) {
                            FilledExtendedIcons.Favorite
                        } else {
                            OutlinedExtendedIcons.FavoriteBorder
                        },
                        contentDescription = "Filter Favorites",
                        tint = if (uiState.isFilteringFavorites) heartTheme.primaryColor else Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                    if (uiState.isFilteringFavorites) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(heartTheme.primaryColor)
                        )
                    }
                }

                // Bookmarks / Saved filter toggle (24.dp)
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .bounceClick { viewModel.toggleBookmarksFilter() }
                        .testTag("bookmarks_filter_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (uiState.isFilteringBookmarks) {
                            FilledExtendedIcons.Bookmark
                        } else {
                            OutlinedExtendedIcons.BookmarkBorder
                        },
                        contentDescription = "Filter Saved Posts",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // Compact "Heart Color" pill when Favorites filter is active
        AnimatedVisibility(
            visible = uiState.isFilteringFavorites,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(IgBlack)
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Showing Favorite Photos",
                    color = IgTextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFF18181B))
                        .border(1.dp, heartTheme.primaryColor.copy(alpha = 0.55f), RoundedCornerShape(50))
                        .clickable { showHeartThemeSheet = true }
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                        .testTag("heart_color_pill"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(heartTheme.primaryColor, heartTheme.secondaryColor)
                                )
                            )
                    )
                    Text(
                        text = "Heart Color",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
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
                    .background(IgBlack)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                IndexingStatusBanner(unindexedCount = uiState.unindexedPhotoCount)
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

            // Item 2: "New People Detected" Banner when >= 3 photos of a new undecided person exist
            if (uiState.newUndecidedPeople.isNotEmpty()) {
                item(key = "new_people_detected_banner") {
                    NewPeopleDetectedBanner(
                        newUndecidedPeople = uiState.newUndecidedPeople,
                        onReviewClick = onReviewNewPeopleClick,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
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
                                    HomeFeedFilter.FAVORITES -> OutlinedExtendedIcons.FavoriteBorder
                                    HomeFeedFilter.BOOKMARKED -> OutlinedExtendedIcons.BookmarkBorder
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
            .background(Color(0xFF000000))
    ) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(
                items = people,
                key = { it.personId }
            ) { person ->
                val cleanName = person.name.trim().removePrefix("@").trim()
                val isPlaceholderName = !person.isNamedByUser &&
                    cleanName.matches(Regex("^Person\\s+\\d+$", RegexOption.IGNORE_CASE))
                val storyLabel = if (isPlaceholderName) "@$cleanName" else cleanName

                StoryAvatarItem(
                    label = storyLabel,
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
        HorizontalDivider(color = Color(0xFF161618), thickness = 0.5.dp)
    }
}

@Composable
fun StoryAvatarItem(
    label: String,
    cropPath: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .bounceClick(onClick = onClick)
            .testTag("story_item_${label.replace("@", "")}")
    ) {
        // 74dp outer circle: 2.5dp Instagram Sunset Gradient ring + 3dp pure #000000 inner gap + 63dp face avatar
        Box(
            modifier = Modifier
                .size(74.dp)
                .clip(CircleShape)
                .background(StorySunsetGradient)
                .padding(2.5.dp)
                .clip(CircleShape)
                .background(Color(0xFF000000))
                .padding(3.dp),
            contentAlignment = Alignment.Center
        ) {
            if (cropPath.isNotBlank() && File(cropPath).exists()) {
                AsyncImage(
                    model = File(cropPath),
                    contentDescription = label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(63.dp)
                        .clip(CircleShape)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(63.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF16161A)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Person,
                        contentDescription = label,
                        tint = IgTextSecondary,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = label,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Normal,
            color = Color(0xFFF5F5F7),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(74.dp),
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
            .bounceClick(onClick = onClick)
            .testTag("story_item_other")
    ) {
        Box(
            modifier = Modifier
                .size(74.dp)
                .clip(CircleShape)
                .background(StorySunsetGradient)
                .padding(2.5.dp)
                .clip(CircleShape)
                .background(Color(0xFF000000))
                .padding(3.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(63.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF16161A)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = "Other",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "@Other",
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Normal,
            color = Color(0xFFF5F5F7),
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(74.dp)
        )
    }
}
