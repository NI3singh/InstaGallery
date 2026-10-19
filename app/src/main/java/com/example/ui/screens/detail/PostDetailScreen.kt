// InstaGallery v1.0
package com.example.ui.screens.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.MediaWithAuthors
import com.example.data.local.entity.PersonEntity
import com.example.ui.components.PostCard
import com.example.ui.theme.IgBlack
import com.example.ui.theme.IgBorder
import com.example.ui.theme.IgTextPrimary

@Composable
fun PostDetailScreen(
    mediaList: List<MediaWithAuthors>,
    initialMediaId: Long,
    onBackClick: () -> Unit,
    onPersonClick: (Long) -> Unit,
    onToggleFavorite: (Long, Boolean) -> Unit,
    onToggleBookmark: (Long, Boolean) -> Unit,
    allPeople: List<PersonEntity> = emptyList(),
    onTagPerson: ((Long, Long) -> Unit)? = null,
    onUntagPerson: ((Long, Long) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val initialIndex = remember(mediaList, initialMediaId) {
        val idx = mediaList.indexOfFirst { it.media.mediaId == initialMediaId }
        if (idx >= 0) idx else 0
    }

    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(IgBlack)
            .testTag("post_detail_screen")
    ) {
        // Top App Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
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
                text = "Posts",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = IgTextPrimary,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        HorizontalDivider(color = IgBorder, thickness = 1.dp)

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize()
        ) {
            items(
                items = mediaList,
                key = { it.media.mediaId }
            ) { item ->
                PostCard(
                    item = item,
                    onAuthorClick = onPersonClick,
                    onToggleFavorite = onToggleFavorite,
                    onToggleBookmark = onToggleBookmark,
                    allPeople = allPeople,
                    onTagPerson = onTagPerson,
                    onUntagPerson = onUntagPerson
                )
                HorizontalDivider(color = IgBorder, thickness = 1.dp)
            }
        }
    }
}
