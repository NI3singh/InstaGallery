// InstaGallery v1.0
package com.example.ui.screens.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.ui.theme.IgBlack
import com.example.ui.theme.IgBlue
import com.example.ui.theme.IgElevated
import com.example.ui.theme.IgStoryGradient
import com.example.ui.theme.IgTextPrimary
import com.example.ui.theme.IgTextSecondary
import com.example.ui.theme.PlusJakartaSansFont
import java.io.File
import kotlin.math.absoluteValue
import kotlinx.coroutines.launch

private val QUICK_NAME_SUGGESTIONS = listOf("Papa", "Me", "Maa", "Bro", "Sis", "Bestie", "Partner")

private val CardSurfaceColor = Color(0xFF121212)
private val CardBorderColor = Color(0xFF262626)
private val InputFillColor = Color(0xFF1C1C1E)
private val ChipBorderColor = Color(0xFF2C2C2E)
private val SelectedChipAccent = Color(0xFFE1306C)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileSetupScreen(
    viewModel: OnboardingViewModel,
    onSetupCompleted: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val peopleCards = uiState.detectedPeople
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    val activeProfileCount = remember(peopleCards, uiState.markedUnknownIds, uiState.editedNames) {
        peopleCards.count { card ->
            val id = card.person.personId
            !uiState.markedUnknownIds.contains(id) &&
                (uiState.editedNames[id]?.trim()?.isNotBlank() == true)
        }
    }

    // Detect if user typed the same name on multiple active cards (which will auto-merge!)
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

    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { peopleCards.size.coerceAtLeast(1) }
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(IgBlack)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .testTag("profile_setup_screen"),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Header: Title + Subtitle + "3 of 14" Counter Pill
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Who's in your gallery?",
                    fontFamily = PlusJakartaSansFont,
                    fontSize = 23.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = (-0.4).sp
                )
                Text(
                    text = "Swipe cards to name profiles or move\nstrangers to @Other",
                    fontFamily = PlusJakartaSansFont,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color(0xFFA8A8A8),
                    lineHeight = 19.sp
                )
            }

            if (peopleCards.isNotEmpty()) {
                val currentDisplayIndex = (pagerState.currentPage + 1).coerceIn(1, peopleCards.size)
                Box(
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .clip(RoundedCornerShape(50))
                        .background(CardSurfaceColor)
                        .border(1.dp, CardBorderColor, RoundedCornerShape(50))
                        .padding(horizontal = 13.dp, vertical = 6.dp)
                        .testTag("carousel_page_counter"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$currentDisplayIndex of ${peopleCards.size}",
                        fontFamily = PlusJakartaSansFont,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }
        }

        // Center Interactive Horizontal Carousel
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            if (peopleCards.isEmpty()) {
                EmptyFacesCard(
                    unassociatedMediaCount = uiState.unassociatedMediaCount,
                    modifier = Modifier.padding(horizontal = 36.dp)
                )
            } else {
                HorizontalPager(
                    state = pagerState,
                    contentPadding = PaddingValues(horizontal = 36.dp),
                    pageSpacing = 14.dp,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_setup_pager")
                ) { page ->
                    val card = peopleCards[page]
                    val personId = card.person.personId
                    val currentName = uiState.editedNames[personId] ?: card.person.name
                    val isMarkedUnknown = uiState.markedUnknownIds.contains(personId) || currentName.trim().isBlank()
                    val normalizedName = currentName.trim().lowercase()
                    val willAutoMerge = !isMarkedUnknown && (duplicateNameCounts[normalizedName] ?: 0) > 1

                    val pageOffset = (
                        (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                        ).absoluteValue.coerceIn(0f, 1f)
                    val cardScale = lerp(0.90f, 1.0f, 1f - pageOffset)
                    val cardAlpha = lerp(0.50f, 1.0f, 1f - pageOffset)

                    CarouselFaceCard(
                        card = card,
                        currentName = currentName,
                        isMarkedUnknown = isMarkedUnknown,
                        willAutoMerge = willAutoMerge,
                        onNameChange = { viewModel.onNameChanged(personId, it) },
                        onMoveToOther = {
                            focusManager.clearFocus()
                            viewModel.setPersonUnknownStatus(
                                personId = personId,
                                isUnknown = !isMarkedUnknown,
                                defaultName = card.person.name
                            )
                            if (!isMarkedUnknown && page < peopleCards.lastIndex) {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(
                                        page = page + 1,
                                        animationSpec = tween(durationMillis = 320)
                                    )
                                }
                            }
                        },
                        onKeepProfile = {
                            focusManager.clearFocus()
                            viewModel.setPersonUnknownStatus(
                                personId = personId,
                                isUnknown = false,
                                defaultName = card.person.name
                            )
                            if (page < peopleCards.lastIndex) {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(
                                        page = page + 1,
                                        animationSpec = tween(durationMillis = 320)
                                    )
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                scaleX = cardScale
                                scaleY = cardScale
                                alpha = cardAlpha
                            }
                    )
                }
            }
        }

        // Bottom Controls: Prev / Dots / Next + Primary Gradient Finish Button
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            if (peopleCards.isNotEmpty()) {
                CarouselPaginationControls(
                    currentPage = pagerState.currentPage,
                    totalPages = peopleCards.size,
                    onPrevious = {
                        focusManager.clearFocus()
                        if (pagerState.currentPage > 0) {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(
                                    page = pagerState.currentPage - 1,
                                    animationSpec = tween(durationMillis = 300)
                                )
                            }
                        }
                    },
                    onNext = {
                        focusManager.clearFocus()
                        if (pagerState.currentPage < peopleCards.lastIndex) {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(
                                    page = pagerState.currentPage + 1,
                                    animationSpec = tween(durationMillis = 300)
                                )
                            }
                        }
                    }
                )
            }

            // Finish Setup & Open Feed Gradient Pill Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .shadow(
                        elevation = 16.dp,
                        shape = RoundedCornerShape(50),
                        ambientColor = Color(0xFFDD2A7B).copy(alpha = 0.45f),
                        spotColor = Color(0xFFF58529).copy(alpha = 0.45f)
                    )
                    .clip(RoundedCornerShape(50))
                    .background(IgStoryGradient)
                    .clickable(enabled = !uiState.isSavingSetup) {
                        focusManager.clearFocus()
                        viewModel.completeProfileSetup(onSetupCompleted)
                    }
                    .testTag("complete_profile_setup_button"),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = if (uiState.isSavingSetup) {
                            "Generating Profiles..."
                        } else {
                            "Finish Setup & Open Feed ($activeProfileCount)"
                        },
                        fontFamily = PlusJakartaSansFont,
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    if (!uiState.isSavingSetup) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CarouselFaceCard(
    card: PersonPreviewCard,
    currentName: String,
    isMarkedUnknown: Boolean,
    willAutoMerge: Boolean,
    onNameChange: (String) -> Unit,
    onMoveToOther: () -> Unit,
    onKeepProfile: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val mediaCount = (card.photoCount + card.reelCount).coerceAtLeast(card.person.faceCount)

    val inputBorderColor by animateColorAsState(
        targetValue = when {
            isFocused -> IgBlue
            !isMarkedUnknown && currentName.isNotBlank() -> IgBlue.copy(alpha = 0.85f)
            else -> CardBorderColor
        },
        label = "handle_input_border"
    )

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(CardSurfaceColor)
            .border(1.dp, CardBorderColor, RoundedCornerShape(24.dp)),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Section: 130.dp 3-Photo Context Strip + Overlapping 96.dp Story-Ring Face Avatar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(178.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            // 130.dp 3-Column Photo Strip
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(Color(0xFF1A1A1C))
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    val samples = card.sampleMedia
                    for (slotIndex in 0 until 3) {
                        val slotMedia = if (samples.isNotEmpty()) {
                            samples[slotIndex % samples.size]
                        } else null

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(IgElevated)
                        ) {
                            if (slotMedia != null) {
                                AsyncImage(
                                    model = slotMedia.uri,
                                    contentDescription = slotMedia.displayName,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                val cropPath = card.person.avatarCropPath
                                if (cropPath.isNotBlank() && File(cropPath).exists()) {
                                    AsyncImage(
                                        model = File(cropPath),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }
                    }
                }

                // Bottom vignette gradient into #121212 card surface
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0.0f to Color.Black.copy(alpha = 0.15f),
                                0.55f to Color.Transparent,
                                1.0f to CardSurfaceColor
                            )
                        )
                )

                // Top-Right Translucent Pill: "$mediaCount Photos & Reels"
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 10.dp, end = 10.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.68f))
                        .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(50))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    GalleryBadgeIcon(
                        tint = Color(0xFFF59E0B),
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = "$mediaCount Photos & Reels",
                        fontFamily = PlusJakartaSansFont,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }

            // Overlapping 96.dp Story-Ring Face Avatar centered over bottom edge of 130.dp banner
            Box(
                modifier = Modifier
                    .offset(y = 82.dp)
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(
                        if (isMarkedUnknown) SolidColor(Color(0xFF3A3A3C)) else IgStoryGradient
                    )
                    .padding(3.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(CardSurfaceColor)
                        .padding(3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val cropPath = card.person.avatarCropPath
                    if (cropPath.isNotBlank() && File(cropPath).exists()) {
                        AsyncImage(
                            model = File(cropPath),
                            contentDescription = currentName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(IgElevated),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Person,
                                contentDescription = null,
                                tint = IgTextSecondary,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }
                }
            }
        }

        // Card Body Content
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Editable Handle Input Field (14.dp radius, #1C1C1E fill, #0095F6 focused border)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(InputFillColor)
                    .border(1.2.dp, inputBorderColor, RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "@",
                    fontFamily = PlusJakartaSansFont,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isMarkedUnknown) Color(0xFF8E8E93) else IgBlue
                )

                BasicTextField(
                    value = currentName,
                    onValueChange = onNameChange,
                    singleLine = true,
                    interactionSource = interactionSource,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    textStyle = TextStyle(
                        fontFamily = PlusJakartaSansFont,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isMarkedUnknown) Color(0xFF8E8E93) else Color.White
                    ),
                    cursorBrush = SolidColor(IgBlue),
                    decorationBox = { innerTextField ->
                        if (currentName.isEmpty()) {
                            Text(
                                text = "Enter handle or leave for @Other",
                                fontFamily = PlusJakartaSansFont,
                                fontSize = 14.sp,
                                color = Color(0xFF8E8E93),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        innerTextField()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("handle_input_${card.person.personId}")
                )

                if (currentName.isNotEmpty() && isFocused) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear handle",
                        tint = Color(0xFF8E8E93),
                        modifier = Modifier
                            .size(18.dp)
                            .clickable { onNameChange("") }
                    )
                } else {
                    Icon(
                        imageVector = if (currentName.isNotEmpty()) Icons.Outlined.Edit else Icons.Default.Close,
                        contentDescription = "Clear or edit handle",
                        tint = Color(0xFF8E8E93),
                        modifier = Modifier
                            .size(17.dp)
                            .clickable {
                                if (currentName.isNotEmpty()) {
                                    onNameChange("")
                                }
                            }
                    )
                }
            }

            // Live Auto-Merge Detection or Default Merge Tip
            if (willAutoMerge) {
                Text(
                    text = "🔗 Will automatically merge with @${currentName.trim()}",
                    fontFamily = PlusJakartaSansFont,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = IgBlue,
                    textAlign = TextAlign.Center
                )
            } else {
                Text(
                    text = "✨ Giving two cards the same name merges them",
                    fontFamily = PlusJakartaSansFont,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFA8A8A8),
                    textAlign = TextAlign.Center
                )
            }

            // Quick-Tap Name Chips
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QUICK_NAME_SUGGESTIONS.forEach { suggestion ->
                    val isSelected = !isMarkedUnknown && currentName.trim().equals(suggestion, ignoreCase = true)
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (isSelected) SelectedChipAccent.copy(alpha = 0.16f) else InputFillColor
                            )
                            .border(
                                width = 1.dp,
                                color = if (isSelected) SelectedChipAccent else ChipBorderColor,
                                shape = RoundedCornerShape(50)
                            )
                            .clickable { onNameChange(suggestion) }
                            .padding(horizontal = 13.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = suggestion,
                            fontFamily = PlusJakartaSansFont,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (isSelected) Color.White else Color(0xFFE5E5EA)
                        )
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = SelectedChipAccent,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            HorizontalDivider(
                color = Color(0xFF1F1F22),
                thickness = 1.dp,
                modifier = Modifier.fillMaxWidth()
            )

            // Bottom Dual Action Buttons: "Move to @Other" & "Keep Profile ✓"
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Move to @Other Button
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isMarkedUnknown) Color(0xFF2C2C2E) else InputFillColor
                        )
                        .border(
                            width = 1.dp,
                            color = if (isMarkedUnknown) Color(0xFF636366) else ChipBorderColor,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable(onClick = onMoveToOther)
                        .testTag("move_to_other_button_${card.person.personId}"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    EyeOffIcon(
                        tint = if (isMarkedUnknown) Color.White else Color(0xFFA8A8A8),
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isMarkedUnknown) "In @Other" else "Move to @Other",
                        fontFamily = PlusJakartaSansFont,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isMarkedUnknown) Color.White else Color(0xFFD1D1D6)
                    )
                }

                // Keep Profile ✓ Button
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (!isMarkedUnknown) IgBlue else IgBlue.copy(alpha = 0.24f)
                        )
                        .clickable(onClick = onKeepProfile)
                        .testTag("keep_profile_button_${card.person.personId}"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Keep Profile",
                        fontFamily = PlusJakartaSansFont,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun GalleryBadgeIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val stroke = size.width * 0.12f
        drawRoundRect(
            color = tint,
            topLeft = Offset(size.width * 0.08f, size.height * 0.08f),
            size = Size(size.width * 0.84f, size.height * 0.84f),
            cornerRadius = CornerRadius(size.width * 0.18f, size.width * 0.18f),
            style = Stroke(width = stroke)
        )
        drawCircle(
            color = tint,
            radius = size.width * 0.11f,
            center = Offset(size.width * 0.34f, size.height * 0.36f)
        )
        val mountainPath = Path().apply {
            moveTo(size.width * 0.16f, size.height * 0.78f)
            lineTo(size.width * 0.44f, size.height * 0.50f)
            lineTo(size.width * 0.62f, size.height * 0.68f)
            lineTo(size.width * 0.75f, size.height * 0.55f)
            lineTo(size.width * 0.86f, size.height * 0.78f)
        }
        drawPath(
            path = mountainPath,
            color = tint,
            style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

@Composable
private fun EyeOffIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val stroke = size.width * 0.11f
        val eyePath = Path().apply {
            moveTo(size.width * 0.08f, size.height * 0.50f)
            quadraticTo(
                size.width * 0.50f,
                size.height * 0.14f,
                size.width * 0.92f,
                size.height * 0.50f
            )
            quadraticTo(
                size.width * 0.50f,
                size.height * 0.86f,
                size.width * 0.08f,
                size.height * 0.50f
            )
        }
        drawPath(
            path = eyePath,
            color = tint,
            style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        drawCircle(
            color = tint,
            radius = size.width * 0.14f,
            center = Offset(size.width * 0.50f, size.height * 0.50f),
            style = Stroke(width = stroke)
        )
        drawLine(
            color = tint,
            start = Offset(size.width * 0.16f, size.height * 0.16f),
            end = Offset(size.width * 0.84f, size.height * 0.84f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun CarouselPaginationControls(
    currentPage: Int,
    totalPages: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        val canGoPrev = currentPage > 0
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(CardSurfaceColor)
                .border(1.dp, CardBorderColor, CircleShape)
                .clickable(enabled = canGoPrev, onClick = onPrevious)
                .testTag("carousel_prev_button"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "Previous card",
                tint = if (canGoPrev) Color.White else Color(0xFF48484A),
                modifier = Modifier.size(20.dp)
            )
        }

        // Sliding window of up to 7 dot indicators
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val maxDots = 7
            val startPage = when {
                totalPages <= maxDots -> 0
                currentPage <= maxDots / 2 -> 0
                currentPage >= totalPages - (maxDots / 2) - 1 -> totalPages - maxDots
                else -> currentPage - (maxDots / 2)
            }
            val endPage = (startPage + maxDots).coerceAtMost(totalPages)

            for (i in startPage until endPage) {
                val isSelected = i == currentPage
                val dotWidth by animateDpAsState(
                    targetValue = if (isSelected) 20.dp else 6.dp,
                    animationSpec = tween(durationMillis = 250),
                    label = "dot_width"
                )
                Box(
                    modifier = Modifier
                        .width(dotWidth)
                        .height(6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (isSelected) IgStoryGradient else SolidColor(Color(0xFF3A3A3C))
                        )
                )
            }
        }

        val canGoNext = currentPage < totalPages - 1
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(CardSurfaceColor)
                .border(1.dp, CardBorderColor, CircleShape)
                .clickable(enabled = canGoNext, onClick = onNext)
                .testTag("carousel_next_button"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Next card",
                tint = if (canGoNext) Color.White else Color(0xFF48484A),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun EmptyFacesCard(
    unassociatedMediaCount: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(CardSurfaceColor)
            .border(1.dp, CardBorderColor, RoundedCornerShape(24.dp))
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.Face,
            contentDescription = null,
            tint = IgTextSecondary,
            modifier = Modifier.size(48.dp)
        )
        Text(
            text = "No Clear Human Faces Found Yet",
            fontFamily = PlusJakartaSansFont,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = IgTextPrimary,
            textAlign = TextAlign.Center
        )
        Text(
            text = "All $unassociatedMediaCount photos & videos will be organized in your feed and @Other profile.",
            fontFamily = PlusJakartaSansFont,
            fontSize = 13.sp,
            color = IgTextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp
        )
    }
}
