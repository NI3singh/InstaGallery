// InstaGallery v1.0
package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.example.data.local.entity.PersonEntity
import com.example.ui.theme.IgStoryGradient
import com.example.ui.theme.IgTextSecondary
import java.io.File

@Composable
fun NewPeopleDetectedBanner(
    newUndecidedPeople: List<PersonEntity>,
    onReviewClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (newUndecidedPeople.isEmpty()) return

    val displayedAvatars = newUndecidedPeople.take(3)
    val stackWidth = (32 + (displayedAvatars.size - 1).coerceAtLeast(0) * 18).dp

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF141418))
            .border(1.dp, Color(0xFF26262C), RoundedCornerShape(16.dp))
            .clickable(onClick = onReviewClick)
            .padding(12.dp)
            .testTag("new_people_detected_banner"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Left: Overlapping avatar stack of up to 3 circular face crops (32.dp each, 2.dp #141418 border, Instagram gradient ring)
        Box(
            modifier = Modifier
                .width(stackWidth)
                .size(32.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            displayedAvatars.forEachIndexed { index, person ->
                Box(
                    modifier = Modifier
                        .offset(x = (index * 18).dp)
                        .zIndex((displayedAvatars.size - index).toFloat())
                        .size(32.dp)
                        .clip(CircleShape)
                        .border(2.dp, Color(0xFF141418), CircleShape)
                        .padding(1.5.dp)
                        .clip(CircleShape)
                        .background(IgStoryGradient),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(25.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF141418)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (person.avatarCropPath.isNotBlank() && File(person.avatarCropPath).exists()) {
                            AsyncImage(
                                model = File(person.avatarCropPath),
                                contentDescription = person.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(23.dp)
                                    .clip(CircleShape)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.Person,
                                contentDescription = person.name,
                                tint = IgTextSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }

        // Center: Title + Subtitle
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = "${newUndecidedPeople.size} New ${if (newUndecidedPeople.size == 1) "Person" else "People"} Found",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "From your recent photos • Tap to name or merge",
                fontSize = 12.sp,
                color = Color(0xFFA0A0AB),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Right: Solid white pill button ("Review")
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Color.White)
                .clickable(onClick = onReviewClick)
                .padding(horizontal = 14.dp, vertical = 7.dp)
                .testTag("review_new_people_button"),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Review",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
        }
    }
}
