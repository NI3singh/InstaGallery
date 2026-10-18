package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.local.entity.PersonEntity
import com.example.ui.theme.IgBlack
import com.example.ui.theme.IgStoryGradient
import com.example.ui.theme.IgSurfacePill
import com.example.ui.theme.IgTextSecondary
import java.io.File

@Composable
fun OverlappingAvatars(
    authors: List<PersonEntity>,
    modifier: Modifier = Modifier,
    avatarSize: Dp = 32.dp,
    overlap: Dp = (-10).dp,
    maxVisible: Int = 3
) {
    if (authors.isEmpty()) {
        Box(
            modifier = modifier
                .size(avatarSize)
                .clip(CircleShape)
                .background(IgSurfacePill)
                .border(1.dp, IgBlack, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Photo,
                contentDescription = "Gallery",
                tint = IgTextSecondary,
                modifier = Modifier.size(avatarSize * 0.55f)
            )
        }
    } else {
        val visibleAuthors = authors.take(maxVisible)
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(overlap),
            verticalAlignment = Alignment.CenterVertically
        ) {
            visibleAuthors.forEach { person ->
                Box(
                    modifier = Modifier
                        .size(avatarSize)
                        .clip(CircleShape)
                        .background(IgStoryGradient)
                        .border(1.5.dp, Color.Transparent, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(avatarSize - 3.dp)
                            .clip(CircleShape)
                            .background(IgBlack)
                            .border(1.dp, IgBlack, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (person.avatarCropPath.isNotBlank() && File(person.avatarCropPath).exists()) {
                            AsyncImage(
                                model = File(person.avatarCropPath),
                                contentDescription = person.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(avatarSize - 3.dp)
                                    .clip(CircleShape)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.Person,
                                contentDescription = person.name,
                                tint = IgTextSecondary,
                                modifier = Modifier.size(avatarSize * 0.5f)
                            )
                        }
                    }
                }
            }
        }
    }
}
