// InstaGallery v1.0
package com.example.ui.screens.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.ui.theme.IgAmber
import com.example.ui.theme.IgBlack
import com.example.ui.theme.IgBlue
import com.example.ui.theme.IgBorder
import com.example.ui.theme.IgBorderNav
import com.example.ui.theme.IgElevated
import com.example.ui.theme.IgStoryGradient
import com.example.ui.theme.IgSurfaceModal
import com.example.ui.theme.IgSurfacePill
import com.example.ui.theme.IgTextPrimary
import com.example.ui.theme.IgTextSecondary
import java.io.File

private val QUICK_NAME_SUGGESTIONS = listOf("Me", "Maa", "Papa", "Bro", "Sis", "Bestie", "Partner")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileSetupScreen(
    viewModel: OnboardingViewModel,
    onSetupCompleted: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val peopleCards = uiState.detectedPeople

    val activeProfileCount = remember(peopleCards, uiState.markedUnknownIds, uiState.editedNames) {
        peopleCards.count { card ->
            val id = card.person.personId
            !uiState.markedUnknownIds.contains(id) &&
                (uiState.editedNames[id]?.trim()?.isNotBlank() == true)
        }
    }

    val unknownFaceCount = peopleCards.size - activeProfileCount

    // Detect if user typed the same name on multiple cards (which will auto-merge!)
    val duplicateNameCounts = remember(uiState.editedNames, uiState.markedUnknownIds) {
        val counts = mutableMapOf<String, Int>()
        for ((id, name) in uiState.editedNames) {
            if (!uiState.markedUnknownIds.contains(id)) {
                val key = name.trim().lowercase()
                if (key.isNotBlank()) {
                    counts[key] = (counts[key] ?: 0) + 1
                }
            }
        }
        counts
    }

    Scaffold(
        containerColor = IgBlack,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(IgBlack)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(IgStoryGradient),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Name Detected Faces",
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold,
                                color = IgTextPrimary
                            )
                            Text(
                                text = "$activeProfileCount active profiles • $unknownFaceCount unknown → @Other",
                                fontSize = 12.sp,
                                color = IgTextSecondary
                            )
                        }
                    }

                    // Quick Action: Move 1-photo random background faces to @Other
                    val hasSinglePhotoFaces = peopleCards.any { it.person.faceCount <= 1 }
                    if (hasSinglePhotoFaces) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(IgSurfaceModal)
                                .border(1.dp, IgBorderNav, RoundedCornerShape(50))
                                .clickable { viewModel.markSinglePhotoFacesAsUnknown() }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "1-Photo → @Other",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = IgBlue
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(IgBlack)
            ) {
                HorizontalDivider(color = IgBorder, thickness = 1.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { viewModel.completeProfileSetup(onSetupCompleted) },
                        enabled = !uiState.isSavingSetup,
                        colors = ButtonDefaults.buttonColors(containerColor = IgBlue),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("complete_profile_setup_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (uiState.isSavingSetup) {
                                "Generating Profiles..."
                            } else {
                                "Complete Setup & Open Feed ($activeProfileCount Profiles)"
                            },
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        },
        modifier = modifier
            .fillMaxSize()
            .testTag("profile_setup_screen")
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = innerPadding.calculateTopPadding() + 4.dp,
                bottom = innerPadding.calculateBottomPadding() + 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // Instructions Banner
            item(key = "setup_instructions") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(IgSurfaceModal)
                        .border(1.dp, IgBorderNav, RoundedCornerShape(14.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "How Profile Setup Works:",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = IgTextPrimary
                    )
                    Text(
                        text = "• Type a real name (or keep the default @Person name) to create their personal profile.\n" +
                            "• Give two cards the same name to automatically merge them into one profile!\n" +
                            "• Tap \"Unknown (@Other)\" on any stranger face to move their photos into @Other.",
                        fontSize = 12.sp,
                        color = IgTextSecondary,
                        lineHeight = 17.sp
                    )
                }
            }

            if (peopleCards.isEmpty()) {
                item(key = "no_faces_detected") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(IgSurfaceModal)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Face,
                            contentDescription = null,
                            tint = IgTextSecondary,
                            modifier = Modifier.size(42.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "No Clear Human Faces Found Yet",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = IgTextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "All ${uiState.unassociatedMediaCount} photos & videos will be available in your feed and @Other profile.",
                            fontSize = 12.5.sp,
                            color = IgTextSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                items(
                    items = peopleCards,
                    key = { it.person.personId }
                ) { card ->
                    val personId = card.person.personId
                    val currentName = uiState.editedNames[personId] ?: card.person.name
                    val isMarkedUnknown = uiState.markedUnknownIds.contains(personId) || currentName.trim().isBlank()
                    val normalizedName = currentName.trim().lowercase()
                    val willAutoMerge = !isMarkedUnknown && (duplicateNameCounts[normalizedName] ?: 0) > 1

                    DetectedFaceSetupCard(
                        card = card,
                        currentName = currentName,
                        isMarkedUnknown = isMarkedUnknown,
                        willAutoMerge = willAutoMerge,
                        onNameChange = { viewModel.onNameChanged(personId, it) },
                        onToggleUnknown = { viewModel.toggleUnknownStatus(personId) }
                    )
                }
            }

            // Summary Card for @Other (No-face images + Unknown faces)
            item(key = "other_summary_card") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(IgSurfaceModal)
                        .border(1.dp, IgBorderNav, RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(IgStoryGradient),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(IgElevated),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Group,
                                contentDescription = "@Other",
                                tint = IgTextSecondary,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "@Other Profile",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = IgTextPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${uiState.unassociatedMediaCount} scenic/no-face items + $unknownFaceCount unknown face clusters will be organized under @Other.",
                            fontSize = 12.sp,
                            color = IgTextSecondary,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetectedFaceSetupCard(
    card: PersonPreviewCard,
    currentName: String,
    isMarkedUnknown: Boolean,
    willAutoMerge: Boolean,
    onNameChange: (String) -> Unit,
    onToggleUnknown: () -> Unit
) {
    val borderColor by animateColorAsState(
        targetValue = when {
            isMarkedUnknown -> IgBorderNav
            willAutoMerge -> IgAmber
            else -> IgBlue.copy(alpha = 0.55f)
        },
        label = "card_border"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(IgSurfaceModal)
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .padding(14.dp)
            .alpha(if (isMarkedUnknown) 0.72f else 1f),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Circular Face Avatar Crop
            Box(
                modifier = Modifier
                    .size(66.dp)
                    .clip(CircleShape)
                    .background(if (isMarkedUnknown) SolidColor(IgBorderNav) else IgStoryGradient),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(62.dp)
                        .clip(CircleShape)
                        .background(IgBlack),
                    contentAlignment = Alignment.Center
                ) {
                    val cropPath = card.person.avatarCropPath
                    if (cropPath.isNotBlank() && File(cropPath).exists()) {
                        AsyncImage(
                            model = File(cropPath),
                            contentDescription = currentName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(58.dp)
                                .clip(CircleShape)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.Person,
                            contentDescription = null,
                            tint = IgTextSecondary,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
            }

            // Editable Name Input + Counts
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = buildString {
                            append("${card.photoCount} photos")
                            if (card.reelCount > 0) append(" • ${card.reelCount} reels")
                        },
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = IgTextSecondary
                    )

                    // Status Toggle Pill: Active Profile vs Unknown (@Other)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (isMarkedUnknown) IgElevated else IgBlue.copy(alpha = 0.18f))
                            .border(
                                1.dp,
                                if (isMarkedUnknown) IgBorderNav else IgBlue,
                                RoundedCornerShape(50)
                            )
                            .clickable(onClick = onToggleUnknown)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (isMarkedUnknown) "Unknown (@Other)" else "Keep Profile ✓",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isMarkedUnknown) IgTextSecondary else IgBlue
                        )
                    }
                }

                // Name Input Box
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(IgBlack)
                        .border(1.dp, IgBorderNav, RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "@",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isMarkedUnknown) IgTextSecondary else IgBlue
                    )

                    BasicTextField(
                        value = currentName,
                        onValueChange = onNameChange,
                        singleLine = true,
                        textStyle = TextStyle(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = IgTextPrimary
                        ),
                        cursorBrush = SolidColor(IgBlue),
                        decorationBox = { innerTextField ->
                            if (currentName.isEmpty()) {
                                Text(
                                    text = "Enter name (or leave blank for @Other)",
                                    fontSize = 12.5.sp,
                                    color = IgTextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            innerTextField()
                        },
                        modifier = Modifier.weight(1f)
                    )

                    if (currentName.isNotEmpty()) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear name",
                            tint = IgTextSecondary,
                            modifier = Modifier
                                .size(16.dp)
                                .clickable { onNameChange("") }
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = "Edit name",
                            tint = IgTextSecondary,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }

                if (willAutoMerge) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AutoFixHigh,
                            contentDescription = null,
                            tint = IgAmber,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "Will auto-merge with other @${currentName.trim()} card",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = IgAmber
                        )
                    }
                }
            }
        }

        // Quick Name Suggestion Chips + Sample Photo Strip
        if (!isMarkedUnknown) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                QUICK_NAME_SUGGESTIONS.forEach { suggestion ->
                    val isSelected = currentName.equals(suggestion, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (isSelected) IgBlue else IgSurfacePill)
                            .clickable { onNameChange(suggestion) }
                            .padding(horizontal = 9.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = suggestion,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isSelected) Color.White else IgTextSecondary
                        )
                    }
                }
            }
        }

        // Miniature Sample Memories Strip so user recognizes who this face belongs to
        if (card.sampleMedia.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(
                    items = card.sampleMedia,
                    key = { it.mediaId }
                ) { media ->
                    AsyncImage(
                        model = media.uri,
                        contentDescription = media.displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(IgElevated)
                    )
                }
            }
        }
    }
}
