// InstaGallery v1.0
package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.local.entity.PersonEntity
import com.example.ui.theme.IgTextSecondary
import java.io.File

private val PostAvatarSunsetGradient = Brush.linearGradient(
    colors = listOf(
        Color(0xFFFEDA77),
        Color(0xFFF58529),
        Color(0xFFDD2A7B),
        Color(0xFF8134AF)
    ),
    start = Offset(0f, 100f),
    end = Offset(100f, 0f)
)

@Composable
fun OverlappingAvatars(
    authors: List<PersonEntity>,
    modifier: Modifier = Modifier,
    avatarSize: Dp = 34.dp,
    overlap: Dp = (-10).dp,
    maxVisible: Int = 3
) {
    if (authors.isEmpty()) {
        Box(
            modifier = modifier
                .size(avatarSize)
                .clip(CircleShape)
                .background(PostAvatarSunsetGradient)
                .padding(1.8.dp)
                .clip(CircleShape)
                .background(Color.Black)
                .padding(1.5.dp)
                .clip(CircleShape)
                .background(Color(0xFF16161A)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.AutoAwesome,
                contentDescription = "@Other Gallery",
                tint = Color.White,
                modifier = Modifier.size(avatarSize * 0.46f)
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
                        .background(PostAvatarSunsetGradient)
                        .padding(1.8.dp)
                        .clip(CircleShape)
                        .background(Color.Black)
                        .padding(1.5.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF16161A)),
                    contentAlignment = Alignment.Center
                ) {
                    if (person.avatarCropPath.isNotBlank() && File(person.avatarCropPath).exists()) {
                        AsyncImage(
                            model = File(person.avatarCropPath),
                            contentDescription = person.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.Person,
                            contentDescription = person.name,
                            tint = IgTextSecondary,
                            modifier = Modifier.size(avatarSize * 0.48f)
                        )
                    }
                }
            }
        }
    }
}
