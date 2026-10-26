// InstaGallery v1.0
package com.example.ui.screens.onboarding

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import com.example.ml.SimilarProfileCandidate
import com.example.ui.components.LivingAuroraBackground
import com.example.ui.components.RotatingStoryRingAvatar
import com.example.ui.components.bounceClick
import com.example.ui.theme.IgBlue
import com.example.ui.theme.IgElevated
import com.example.ui.theme.IgTextPrimary
import com.example.ui.theme.IgTextSecondary
import com.example.ui.theme.PlusJakartaSansFont
import java.io.File
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val CardSurfaceTopColor = Color(0xFF1A1A20)
private val CardSurfaceBottomColor = Color(0xFF101014)
private val CardBorderColor = Color(0xFF262629)
private val InputFillColor = Color(0xFF1C1C1F)
private val SquircleChipUnselectedBg = Color(0xFF1E1E22)
private val LookalikeContainerBg = Color(0xFF18181C)
private val DangerRed = Color(0xFFFF3040)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSetupScreen(
    viewModel: OnboardingViewModel,
    onSetupCompleted: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val profiles = uiState.detectedPeople
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current

    var showGridMergeSheet by remember { mutableStateOf(false) }
    var gridMergeInitialAnchorId by remember { mutableStateOf<Long?>(null) }

    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { profiles.size.coerceAtLeast(1) }
    )

    val flingBehavior = PagerDefaults.flingBehavior(
        state = pagerState,
        snapAnimationSpec = spring(
            dampingRatio = 0.78f,
            stiffness = 300f
        )
    )

    val safeCurrentPage = if (profiles.isEmpty()) {
        0
    } else {
        pagerState.currentPage.coerceIn(0, profiles.lastIndex)
    }

    val currentCard = profiles.getOrNull(safeCurrentPage)
    val currentDecision = currentCard?.let {
        uiState.decisionMap[it.person.personId] ?: ProfileDecisionState.UNDECIDED
    } ?: ProfileDecisionState.UNDECIDED

    // Load top similar candidates whenever the active card or profile count changes
    LaunchedEffect(currentCard?.person?.personId, uiState.allDetectedCount) {
        val activePersonId = currentCard?.person?.personId ?: return@LaunchedEffect
        viewModel.loadSimilarCandidatesFor(activePersonId)
    }

    // Show toast confirmation when profiles are merged
    LaunchedEffect(uiState.feedbackMessage) {
        val msg = uiState.feedbackMessage ?: return@LaunchedEffect
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        viewModel.clearFeedbackMessage()
    }

    // 2.1 State-Reactive LivingAuroraBackground colors
    val (orbPrimary, orbSecondary, orbTertiary) = when (currentDecision) {
        ProfileDecisionState.KEPT -> Triple(
            Color(0xFF0095F6),
            Color(0xFF00D2FF),
            Color(0xFF1D4ED8)
        )
        ProfileDecisionState.MOVED_TO_OTHER -> Triple(
            Color(0xFFFF3040),
            Color(0xFF881337),
            Color(0xFF27272A)
        )
        ProfileDecisionState.UNDECIDED -> Triple(
            Color(0xFF8134AF),
            Color(0xFFDD2A7B),
            Color(0xFFF58529)
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF000000))
            .testTag("profile_setup_screen")
    ) {
        // 2.1 Living Aurora Background
        LivingAuroraBackground(
            primaryOrbColor = orbPrimary,
            secondaryOrbColor = orbSecondary,
            tertiaryOrbColor = orbTertiary,
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 1.2 Top Header Row & Segmented Filter Bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Who's in your gallery?",
                        fontFamily = PlusJakartaSansFont,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        letterSpacing = (-0.3).sp
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // "Grid Merge" Button (#1C1C1F, 1.dp #2C2C30 border)
                        Row(
                            modifier = Modifier
                                .height(32.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF1C1C1F))
                                .border(1.dp, Color(0xFF2C2C30), RoundedCornerShape(10.dp))
                                .bounceClick {
                                    focusManager.clearFocus()
                                    gridMergeInitialAnchorId = currentCard?.person?.personId
                                    showGridMergeSheet = true
                                }
                                .padding(horizontal = 10.dp)
                                .testTag("grid_merge_top_button"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Grid2x2Icon(
                                tint = Color(0xFFD4D4D8),
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "Grid Merge",
                                fontFamily = PlusJakartaSansFont,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFE4E4E7)
                            )
                        }

                        // Counter Pill ("${currentPage + 1} of ${profiles.size}")
                        if (profiles.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .height(32.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(Color(0xFF1A1A1D))
                                    .border(1.dp, CardBorderColor, RoundedCornerShape(50))
                                    .padding(horizontal = 12.dp)
                                    .testTag("carousel_page_counter"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${safeCurrentPage + 1} of ${profiles.size}",
                                    fontFamily = PlusJakartaSansFont,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }

                // 1.3 Segmented Filter Bar (Top People vs All Detected)
                SegmentedSetupFilterBar(
                    filterMode = uiState.filterMode,
                    topCount = uiState.topPeopleCount,
                    totalCount = uiState.allDetectedCount,
                    onSelectMode = { mode ->
                        focusManager.clearFocus()
                        viewModel.setFilterMode(mode)
                        coroutineScope.launch {
                            pagerState.scrollToPage(0)
                        }
                    }
                )
            }

            // 2. Center Interactive Horizontal Carousel with 3D Perspective Physics
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                if (profiles.isEmpty()) {
                    EmptyFacesCard(
                        unassociatedMediaCount = uiState.unassociatedMediaCount,
                        isScanning = uiState.isScanning,
                        processedCount = uiState.processedCount,
                        totalMediaCount = uiState.totalMediaCount,
                        onRescanClick = { viewModel.triggerFullRescan() },
                        modifier = Modifier.padding(horizontal = 28.dp)
                    )
                } else {
                    HorizontalPager(
                        state = pagerState,
                        flingBehavior = flingBehavior,
                        contentPadding = PaddingValues(horizontal = 26.dp),
                        pageSpacing = 12.dp,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("profile_setup_pager")
                    ) { page ->
                        val card = profiles.getOrNull(page) ?: return@HorizontalPager
                        val person = card.person
                        val personId = person.personId
                        val nameInput = uiState.editedNames[personId] ?: person.name
                        val decision = uiState.decisionMap[personId] ?: ProfileDecisionState.UNDECIDED

                        val cardCandidates = if (uiState.similarCandidatesForPersonId == personId) {
                            uiState.similarCandidates
                        } else {
                            emptyList()
                        }

                        val pageOffset = (
                            (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                            ).coerceIn(-1f, 1f)
                        val absOffset = abs(pageOffset)

                        CarouselFaceCard(
                            card = card,
                            nameInput = nameInput,
                            decision = decision,
                            pageOffset = pageOffset,
                            quickNameChips = uiState.quickNameChips,
                            similarCandidates = cardCandidates,
                            onNameChange = { updated ->
                                viewModel.onNameChanged(personId, updated)
                            },
                            onQuickChipSelected = { chipName ->
                                focusManager.clearFocus()
                                viewModel.markKeepProfile(personId, chipName)
                                if (page < profiles.lastIndex) {
                                    coroutineScope.launch {
                                        delay(220L)
                                        val nextTarget = (page + 1).coerceAtMost(profiles.lastIndex)
                                        pagerState.animateScrollToPage(
                                            page = nextTarget,
                                            animationSpec = tween(durationMillis = 300)
                                        )
                                    }
                                }
                            },
                            onMergeLookalike = { candidate ->
                                focusManager.clearFocus()
                                viewModel.mergeLookalikeIntoCurrent(
                                    currentPerson = person,
                                    lookalikePersonId = candidate.person.personId
                                )
                            },
                            onOpenGridMerge = {
                                focusManager.clearFocus()
                                gridMergeInitialAnchorId = personId
                                showGridMergeSheet = true
                            },
                            onToggleMoveToOther = {
                                focusManager.clearFocus()
                                val wasMoved = decision == ProfileDecisionState.MOVED_TO_OTHER
                                viewModel.toggleMoveToOther(personId)
                                if (!wasMoved && page < profiles.lastIndex) {
                                    coroutineScope.launch {
                                        delay(220L)
                                        val nextTarget = (page + 1).coerceAtMost(profiles.lastIndex)
                                        pagerState.animateScrollToPage(
                                            page = nextTarget,
                                            animationSpec = tween(durationMillis = 300)
                                        )
                                    }
                                }
                            },
                            onKeepProfile = {
                                focusManager.clearFocus()
                                val finalName = nameInput.trim().ifBlank { person.name }
                                viewModel.markKeepProfile(personId, finalName)
                                if (page < profiles.lastIndex) {
                                    coroutineScope.launch {
                                        delay(220L)
                                        val nextTarget = (page + 1).coerceAtMost(profiles.lastIndex)
                                        pagerState.animateScrollToPage(
                                            page = nextTarget,
                                            animationSpec = tween(durationMillis = 300)
                                        )
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    cameraDistance = 16f * density
                                    rotationY = -pageOffset * 14f
                                    rotationZ = -pageOffset * 1.8f
                                    val scale = lerp(1f, 0.90f, absOffset)
                                    scaleX = scale
                                    scaleY = scale
                                    alpha = lerp(1f, 0.52f, absOffset)
                                    translationY = absOffset * 12.dp.toPx()
                                }
                        )
                    }
                }
            }

            // 4. Bottom Controls: Auto-Move 1-Photo Strangers Toggle + Solid White Finish Pill Button
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Auto-move unnamed 1-photo faces to @Other switch row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                        .clickable {
                            viewModel.toggleAutoHideSinglePhotoStrangers()
                        }
                        .testTag("auto_hide_single_photo_strangers_row"),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Auto-move unnamed 1-photo faces to @Other",
                        fontFamily = PlusJakartaSansFont,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFA8A8A8)
                    )

                    Switch(
                        checked = uiState.autoHideSinglePhotoStrangers,
                        onCheckedChange = { viewModel.setAutoHideSinglePhotoStrangers(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = IgBlue,
                            uncheckedThumbColor = Color(0xFFA1A1AA),
                            uncheckedTrackColor = Color(0xFF26262A),
                            uncheckedBorderColor = Color.Transparent
                        ),
                        modifier = Modifier.height(24.dp)
                    )
                }

                // Full-width 52.dp Solid Pure White Pill Button with Pure Black Bold Text + bounceClick
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .shadow(
                            elevation = 12.dp,
                            shape = RoundedCornerShape(50)
                        )
                        .clip(RoundedCornerShape(50))
                        .background(Color.White)
                        .bounceClick(
                            enabled = !uiState.isSavingSetup,
                            hapticType = HapticFeedbackType.LongPress
                        ) {
                            focusManager.clearFocus()
                            viewModel.finalizeOnboarding(onSetupCompleted)
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
                                "Finish Setup & Open Feed (${profiles.size}) →"
                            },
                            fontFamily = PlusJakartaSansFont,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    }
                }
            }
        }

        // 3. Multi-Merge Face Grid Bottom Sheet
        if (showGridMergeSheet) {
            MultiMergeFaceGridBottomSheet(
                allDetectedCards = uiState.allDetectedPeople,
                editedNames = uiState.editedNames,
                initialAnchorPersonId = gridMergeInitialAnchorId,
                onDismiss = { showGridMergeSheet = false },
                onMergeSelected = { selectedIds, targetName ->
                    viewModel.mergeMultipleProfiles(selectedIds, targetName)
                    showGridMergeSheet = false
                }
            )
        }
    }
}

@Composable
private fun SegmentedSetupFilterBar(
    filterMode: SetupFilterMode,
    topCount: Int,
    totalCount: Int,
    onSelectMode: (SetupFilterMode) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF141417))
            .border(1.dp, CardBorderColor, RoundedCornerShape(12.dp))
            .padding(3.dp)
            .testTag("setup_filter_segmented_bar"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val isTopActive = filterMode == SetupFilterMode.TOP_PEOPLE
        val isAllActive = filterMode == SetupFilterMode.ALL_DETECTED

        Box(
            modifier = Modifier
                .weight(1f)
                .height(32.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(if (isTopActive) Color(0xFF28282E) else Color.Transparent)
                .bounceClick { onSelectMode(SetupFilterMode.TOP_PEOPLE) }
                .testTag("filter_top_people_tab"),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Top People ($topCount)",
                fontFamily = PlusJakartaSansFont,
                fontSize = 12.5.sp,
                fontWeight = if (isTopActive) FontWeight.Bold else FontWeight.Medium,
                color = if (isTopActive) Color.White else Color(0xFF8E8E93)
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .height(32.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(if (isAllActive) Color(0xFF28282E) else Color.Transparent)
                .bounceClick { onSelectMode(SetupFilterMode.ALL_DETECTED) }
                .testTag("filter_all_detected_tab"),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "All Detected ($totalCount)",
                fontFamily = PlusJakartaSansFont,
                fontSize = 12.5.sp,
                fontWeight = if (isAllActive) FontWeight.Bold else FontWeight.Medium,
                color = if (isAllActive) Color.White else Color(0xFF8E8E93)
            )
        }
    }
}

@Composable
private fun CarouselFaceCard(
    card: PersonPreviewCard,
    nameInput: String,
    decision: ProfileDecisionState,
    pageOffset: Float,
    quickNameChips: List<String>,
    similarCandidates: List<SimilarProfileCandidate>,
    onNameChange: (String) -> Unit,
    onQuickChipSelected: (String) -> Unit,
    onMergeLookalike: (SimilarProfileCandidate) -> Unit,
    onOpenGridMerge: () -> Unit,
    onToggleMoveToOther: () -> Unit,
    onKeepProfile: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val photoCount = card.photoCount.coerceAtLeast(card.person.faceCount)
    val displayHandle = nameInput.trim().removePrefix("@").trim().ifBlank { card.person.name }

    // 2.4 Confirmation Shimmer Sweep (Animatable 0f..1f over 450ms)
    var shimmerTriggerCount by remember(card.person.personId) { mutableIntStateOf(0) }
    val shimmerProgress = remember(card.person.personId) { Animatable(0f) }

    LaunchedEffect(shimmerTriggerCount) {
        if (shimmerTriggerCount > 0) {
            shimmerProgress.snapTo(0f)
            shimmerProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing)
            )
            shimmerProgress.snapTo(0f)
        }
    }

    // 2.4 Dynamic Specular Rim-Light Border Brush shifting with pageOffset
    val rimColors = when (decision) {
        ProfileDecisionState.KEPT -> listOf(
            Color(0xFF0095F6),
            Color(0xFF60A5FA).copy(alpha = 0.7f),
            Color(0xFF0095F6).copy(alpha = 0.25f)
        )
        ProfileDecisionState.MOVED_TO_OTHER -> listOf(
            Color(0xFFFF3040).copy(alpha = 0.8f),
            Color(0xFF3F1D24),
            Color(0xFF1F1F23)
        )
        ProfileDecisionState.UNDECIDED -> listOf(
            Color.White.copy(alpha = 0.34f),
            Color.White.copy(alpha = 0.10f),
            Color(0xFF222226)
        )
    }

    val specularRimBrush = Brush.linearGradient(
        colors = rimColors,
        start = Offset(x = (0.5f - pageOffset * 0.45f) * 600f, y = 0f),
        end = Offset(x = (0.5f + pageOffset * 0.45f) * 600f + 420f, y = 900f)
    )

    val cardSurfaceGradient = remember {
        Brush.verticalGradient(
            colors = listOf(CardSurfaceTopColor, CardSurfaceBottomColor)
        )
    }

    val inputBorderColor by animateColorAsState(
        targetValue = when {
            isFocused -> IgBlue
            decision == ProfileDecisionState.KEPT -> IgBlue
            decision == ProfileDecisionState.MOVED_TO_OTHER -> DangerRed.copy(alpha = 0.55f)
            else -> Color(0xFF2E2E33)
        },
        label = "handle_input_border"
    )

    val bannerAlpha = if (decision == ProfileDecisionState.MOVED_TO_OTHER) 0.45f else 1.0f

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(cardSurfaceGradient)
            .border(1.5.dp, specularRimBrush, RoundedCornerShape(24.dp))
            .drawWithContent {
                drawContent()
                val progress = shimmerProgress.value
                if (progress > 0f && progress < 1f) {
                    val diagWidth = size.width * 0.55f
                    val centerX = lerp(-diagWidth, size.width + diagWidth, progress)
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = 0.14f),
                                Color.Transparent
                            ),
                            start = Offset(centerX - diagWidth * 0.6f, 0f),
                            end = Offset(centerX + diagWidth * 0.6f, size.height)
                        )
                    )
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Section: 118.dp 3-Photo Context Strip + Overlapping 92.dp Rotating Story-Ring Face Avatar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(164.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            // 118.dp 3-Column Photo Strip with internal Multi-Plane Parallax
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(118.dp)
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(Color(0xFF1A1A1D))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationX = pageOffset * 24.dp.toPx()
                            scaleX = 1.12f
                            scaleY = 1.05f
                        }
                        .alpha(bannerAlpha),
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

                // Bottom vignette gradient into top card surface color
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0.0f to Color.Black.copy(alpha = 0.15f),
                                0.58f to Color.Transparent,
                                1.0f to CardSurfaceTopColor
                            )
                        )
                )

                // Top-Left Status Badge on the 3-Photo Banner
                androidx.compose.animation.AnimatedVisibility(
                    visible = decision != ProfileDecisionState.UNDECIDED,
                    enter = fadeIn(tween(180)),
                    exit = fadeOut(tween(180)),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(top = 10.dp, start = 10.dp)
                ) {
                    val badgeBg = if (decision == ProfileDecisionState.KEPT) IgBlue else DangerRed
                    val badgeText = if (decision == ProfileDecisionState.KEPT) {
                        "✓ Kept as @$displayHandle"
                    } else {
                        "Moved to @Other"
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(badgeBg)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                            .testTag("card_status_badge_${card.person.personId}")
                    ) {
                        Text(
                            text = badgeText,
                            fontFamily = PlusJakartaSansFont,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // 2.5 Top-Right Translucent Pill with Odometer AnimatedContent: "$photoCount Photos"
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 10.dp, end = 10.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.70f))
                        .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(50))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    GalleryBadgeIcon(
                        tint = Color(0xFFF58529),
                        modifier = Modifier.size(12.dp)
                    )
                    AnimatedContent(
                        targetState = photoCount,
                        transitionSpec = {
                            (slideInVertically { it } + fadeIn(tween(220))) togetherWith
                                (slideOutVertically { -it } + fadeOut(tween(180)))
                        },
                        label = "odometer_photo_count"
                    ) { targetCount ->
                        Text(
                            text = "$targetCount Photos",
                            fontFamily = PlusJakartaSansFont,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                }
            }

            // 2.3 Overlapping 92.dp RotatingStoryRingAvatar with 14.dp shadow & counter-parallax
            RotatingStoryRingAvatar(
                avatarSize = 92.dp,
                ringWidth = 3.dp,
                innerCutoutWidth = 3.dp,
                innerCutoutColor = CardSurfaceTopColor,
                isMuted = decision == ProfileDecisionState.MOVED_TO_OTHER,
                modifier = Modifier
                    .offset(y = 72.dp)
                    .graphicsLayer {
                        translationX = -pageOffset * 10.dp.toPx()
                    }
                    .shadow(14.dp, CircleShape)
            ) {
                val cropPath = card.person.avatarCropPath
                if (cropPath.isNotBlank() && File(cropPath).exists()) {
                    AsyncImage(
                        model = File(cropPath),
                        contentDescription = displayHandle,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .alpha(bannerAlpha)
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
                            modifier = Modifier.size(38.dp)
                        )
                    }
                }
            }
        }

        // Card Body Content
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // c) Editable Handle Input Field (46.dp height, 12.dp radius, #1C1C1F fill)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(InputFillColor)
                    .border(1.5.dp, inputBorderColor, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "@",
                    fontFamily = PlusJakartaSansFont,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (decision == ProfileDecisionState.MOVED_TO_OTHER) Color(0xFF8E8E93) else IgBlue
                )

                BasicTextField(
                    value = nameInput,
                    onValueChange = onNameChange,
                    singleLine = true,
                    interactionSource = interactionSource,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                            if (nameInput.trim().isNotBlank()) {
                                shimmerTriggerCount++
                                onKeepProfile()
                            }
                        }
                    ),
                    textStyle = TextStyle(
                        fontFamily = PlusJakartaSansFont,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (decision == ProfileDecisionState.MOVED_TO_OTHER) Color(0xFF8E8E93) else Color.White
                    ),
                    cursorBrush = SolidColor(IgBlue),
                    decorationBox = { innerTextField ->
                        if (nameInput.isEmpty()) {
                            Text(
                                text = "Name person or tap a chip below...",
                                fontFamily = PlusJakartaSansFont,
                                fontSize = 13.5.sp,
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

                if (nameInput.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2A2A2E))
                            .bounceClick { onNameChange("") },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear name",
                            tint = Color(0xFFA8A8A8),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            // d) Native Instagram Squircle Quick-Chips with bounceClick
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(quickNameChips, key = { it }) { chip ->
                    val isSelected = decision != ProfileDecisionState.MOVED_TO_OTHER &&
                        nameInput.trim().removePrefix("@").trim().equals(chip, ignoreCase = true)

                    Box(
                        modifier = Modifier
                            .height(32.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) Color.White else SquircleChipUnselectedBg)
                            .bounceClick {
                                shimmerTriggerCount++
                                onQuickChipSelected(chip)
                            }
                            .padding(horizontal = 14.dp)
                            .testTag("quick_chip_${chip.lowercase()}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = chip,
                            fontFamily = PlusJakartaSansFont,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.Black else Color(0xFFE4E4E7)
                        )
                    }
                }
            }

            // e) In-Card "Lookalike / Same Person" Visual Merge Strip (#18181C, 14.dp radius, 10.dp padding)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(LookalikeContainerBg)
                    .border(1.dp, CardBorderColor, RoundedCornerShape(14.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Same person? Tap face to merge with @$displayHandle:",
                        fontFamily = PlusJakartaSansFont,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFA8A8A8),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "View All",
                        fontFamily = PlusJakartaSansFont,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = IgBlue,
                        modifier = Modifier
                            .bounceClick(onClick = onOpenGridMerge)
                            .testTag("lookalike_view_all_${card.person.personId}")
                    )
                }

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(
                        items = similarCandidates,
                        key = { it.person.personId }
                    ) { candidate ->
                        LookalikeCandidateItem(
                            candidate = candidate,
                            onClick = { onMergeLookalike(candidate) }
                        )
                    }

                    // Trailing "All" grid button item inside strip
                    item(key = "all_grid_button") {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .bounceClick(onClick = onOpenGridMerge)
                                .padding(vertical = 2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF26262A))
                                    .border(1.dp, Color(0xFF3A3A3E), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Grid3x3Icon(
                                    tint = Color(0xFFA8A8A8),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "All",
                                fontFamily = PlusJakartaSansFont,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFFA8A8A8)
                            )
                        }
                    }
                }
            }

            HorizontalDivider(
                color = Color(0xFF1F1F22),
                thickness = 1.dp,
                modifier = Modifier.fillMaxWidth()
            )

            // f) Bottom Card Decision Buttons (With bounceClick, Shimmer & Auto-Advance)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val isMoved = decision == ProfileDecisionState.MOVED_TO_OTHER
                val isKept = decision == ProfileDecisionState.KEPT

                // Left Button: Move to @Other / Undo Move ↺
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isMoved) Color(0xFF3A1D22) else SquircleChipUnselectedBg
                        )
                        .border(
                            width = 1.dp,
                            color = if (isMoved) DangerRed.copy(alpha = 0.5f) else Color(0xFF2E2E32),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .bounceClick(onClick = onToggleMoveToOther)
                        .testTag("move_to_other_button_${card.person.personId}"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (!isMoved) {
                        EyeOffIcon(
                            tint = Color(0xFF8E8E93),
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = if (isMoved) "Undo Move ↺" else "Move to @Other",
                        fontFamily = PlusJakartaSansFont,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isMoved) Color(0xFFFF6B78) else Color(0xFFD4D4D8)
                    )
                }

                // Right Button: Keep Profile / Kept Profile ✓
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isKept) IgBlue else IgBlue.copy(alpha = 0.85f)
                        )
                        .bounceClick(
                            hapticType = HapticFeedbackType.LongPress,
                            onClick = {
                                shimmerTriggerCount++
                                onKeepProfile()
                            }
                        )
                        .testTag("keep_profile_button_${card.person.personId}"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = if (isKept) "Kept Profile ✓" else "Keep Profile",
                        fontFamily = PlusJakartaSansFont,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun LookalikeCandidateItem(
    candidate: SimilarProfileCandidate,
    onClick: () -> Unit
) {
    val person = candidate.person
    val subtitle = if (person.isNamedByUser) {
        "@${person.name.removePrefix("@").trim()}"
    } else {
        "${candidate.photoCount}p"
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .bounceClick(
                hapticType = HapticFeedbackType.LongPress,
                onClick = onClick
            )
            .padding(vertical = 2.dp)
            .testTag("lookalike_candidate_${person.personId}")
    ) {
        Box(
            modifier = Modifier.size(46.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(IgElevated)
                    .border(
                        width = if (person.isNamedByUser) 1.5.dp else 1.dp,
                        color = if (person.isNamedByUser) IgBlue else Color(0xFF343438),
                        shape = CircleShape
                    )
            ) {
                val cropPath = person.avatarCropPath
                if (cropPath.isNotBlank() && File(cropPath).exists()) {
                    AsyncImage(
                        model = File(cropPath),
                        contentDescription = person.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Person,
                        contentDescription = null,
                        tint = IgTextSecondary,
                        modifier = Modifier
                            .size(22.dp)
                            .align(Alignment.Center)
                    )
                }
            }

            // Bottom-Right '+' Badge (#0095F6 circle)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(IgBlue)
                    .border(1.5.dp, LookalikeContainerBg, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Merge",
                    tint = Color.White,
                    modifier = Modifier.size(10.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = subtitle,
            fontFamily = PlusJakartaSansFont,
            fontSize = 10.sp,
            fontWeight = if (person.isNamedByUser) FontWeight.SemiBold else FontWeight.Medium,
            color = if (person.isNamedByUser) IgBlue else Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MultiMergeFaceGridBottomSheet(
    allDetectedCards: List<PersonPreviewCard>,
    editedNames: Map<Long, String>,
    initialAnchorPersonId: Long?,
    onDismiss: () -> Unit,
    onMergeSelected: (List<Long>, String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val selectedIds = remember {
        mutableStateListOf<Long>().apply {
            if (initialAnchorPersonId != null) add(initialAnchorPersonId)
        }
    }
    var searchQuery by remember { mutableStateOf("") }
    var targetMergeName by remember { mutableStateOf("") }
    var hasUserEditedMergeName by remember { mutableStateOf(false) }

    // Pre-fill targetMergeName from the first user-named profile in selection, or first selected profile
    LaunchedEffect(selectedIds.toList()) {
        if (!hasUserEditedMergeName && selectedIds.isNotEmpty()) {
            val selectedCards = selectedIds.mapNotNull { id ->
                allDetectedCards.firstOrNull { it.person.personId == id }
            }
            val firstNamed = selectedCards.firstOrNull { card ->
                val name = editedNames[card.person.personId] ?: card.person.name
                card.person.isNamedByUser || !name.matches(Regex("^Person\\s+\\d+$", RegexOption.IGNORE_CASE))
            }
            val chosenCard = firstNamed ?: selectedCards.firstOrNull()
            if (chosenCard != null) {
                targetMergeName = (editedNames[chosenCard.person.personId] ?: chosenCard.person.name)
                    .removePrefix("@")
                    .trim()
            }
        }
    }

    val filteredCards = remember(allDetectedCards, editedNames, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) {
            allDetectedCards
        } else {
            allDetectedCards.filter { card ->
                val name = (editedNames[card.person.personId] ?: card.person.name).lowercase()
                name.contains(q)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF121214),
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .windowInsetsPadding(WindowInsets.navigationBars)
        ) {
            // 3.1 Top Sheet Bar
            Text(
                text = "Select Faces of the Same Person",
                fontFamily = PlusJakartaSansFont,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Tap 2 or more faces below to combine them into one profile",
                fontFamily = PlusJakartaSansFont,
                fontSize = 12.5.sp,
                color = Color(0xFFA8A8A8)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 3.2 Search / Filter Input
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                singleLine = true,
                placeholder = {
                    Text(
                        text = "Search by @name or Person number...",
                        fontFamily = PlusJakartaSansFont,
                        fontSize = 13.sp,
                        color = Color(0xFF8E8E93)
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = Color(0xFF8E8E93),
                        modifier = Modifier.size(18.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear search",
                            tint = Color(0xFF8E8E93),
                            modifier = Modifier
                                .size(18.dp)
                                .clickable { searchQuery = "" }
                        )
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF1C1C1F),
                    unfocusedContainerColor = Color(0xFF1C1C1F),
                    focusedBorderColor = IgBlue,
                    unfocusedBorderColor = Color(0xFF2C2C30),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = IgBlue
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 4-Column Face Grid (GridCells.Fixed(4))
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(
                    items = filteredCards,
                    key = { it.person.personId }
                ) { card ->
                    val personId = card.person.personId
                    val isSelected = selectedIds.contains(personId)
                    val displayName = (editedNames[personId] ?: card.person.name)
                        .removePrefix("@")
                        .trim()
                    val count = card.photoCount.coerceAtLeast(card.person.faceCount)

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .bounceClick {
                                if (isSelected) {
                                    selectedIds.remove(personId)
                                } else {
                                    selectedIds.add(personId)
                                }
                            }
                            .testTag("grid_merge_face_$personId")
                    ) {
                        Box(
                            modifier = Modifier.size(74.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .background(IgElevated)
                                    .border(
                                        width = if (isSelected) 2.5.dp else 1.dp,
                                        color = if (isSelected) IgBlue else Color(0xFF2E2E32),
                                        shape = CircleShape
                                    )
                            ) {
                                val cropPath = card.person.avatarCropPath
                                if (cropPath.isNotBlank() && File(cropPath).exists()) {
                                    AsyncImage(
                                        model = File(cropPath),
                                        contentDescription = displayName,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(CircleShape)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Outlined.Person,
                                        contentDescription = null,
                                        tint = IgTextSecondary,
                                        modifier = Modifier
                                            .size(32.dp)
                                            .align(Alignment.Center)
                                    )
                                }
                            }

                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(IgBlue)
                                        .border(2.dp, Color(0xFF121214), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = Color.White,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(5.dp))

                        Text(
                            text = "@$displayName",
                            fontFamily = PlusJakartaSansFont,
                            fontSize = 11.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                            color = if (isSelected) IgBlue else Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = "$count photos",
                            fontFamily = PlusJakartaSansFont,
                            fontSize = 10.sp,
                            color = Color(0xFF8E8E93),
                            maxLines = 1
                        )
                    }
                }
            }

            // 3.3 Bottom Merge Bar (visible when selectedIds.size >= 2)
            AnimatedVisibility(visible = selectedIds.size >= 2) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF18181C), RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                        .border(1.dp, CardBorderColor, RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = targetMergeName,
                        onValueChange = {
                            hasUserEditedMergeName = true
                            targetMergeName = it
                        },
                        singleLine = true,
                        label = {
                            Text(
                                text = "Combined Profile Name",
                                fontFamily = PlusJakartaSansFont,
                                fontSize = 12.sp
                            )
                        },
                        prefix = {
                            Text(
                                text = "@",
                                fontFamily = PlusJakartaSansFont,
                                fontWeight = FontWeight.Bold,
                                color = IgBlue
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFF121214),
                            unfocusedContainerColor = Color(0xFF121214),
                            focusedBorderColor = IgBlue,
                            unfocusedBorderColor = Color(0xFF2E2E32),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedLabelColor = IgBlue,
                            unfocusedLabelColor = Color(0xFFA8A8A8),
                            cursorColor = IgBlue
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(IgBlue)
                            .bounceClick(hapticType = HapticFeedbackType.LongPress) {
                                val finalName = targetMergeName.trim().ifBlank { "Person" }
                                onMergeSelected(selectedIds.toList(), finalName)
                            }
                            .testTag("confirm_multi_merge_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Merge ${selectedIds.size} Faces Together",
                            fontFamily = PlusJakartaSansFont,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Grid2x2Icon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val cell = size.width * 0.40f
        val gap = size.width * 0.20f
        val r = CornerRadius(size.width * 0.08f, size.width * 0.08f)
        drawRoundRect(color = tint, topLeft = Offset(0f, 0f), size = Size(cell, cell), cornerRadius = r)
        drawRoundRect(color = tint, topLeft = Offset(cell + gap, 0f), size = Size(cell, cell), cornerRadius = r)
        drawRoundRect(color = tint, topLeft = Offset(0f, cell + gap), size = Size(cell, cell), cornerRadius = r)
        drawRoundRect(color = tint, topLeft = Offset(cell + gap, cell + gap), size = Size(cell, cell), cornerRadius = r)
    }
}

@Composable
private fun Grid3x3Icon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val cell = size.width * 0.24f
        val step = size.width * 0.38f
        val r = CornerRadius(size.width * 0.05f, size.width * 0.05f)
        for (row in 0..2) {
            for (col in 0..2) {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(col * step, row * step),
                    size = Size(cell, cell),
                    cornerRadius = r
                )
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
private fun EmptyFacesCard(
    unassociatedMediaCount: Int,
    isScanning: Boolean = false,
    processedCount: Int = 0,
    totalMediaCount: Int = 0,
    onRescanClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(CardSurfaceTopColor, CardSurfaceBottomColor)
                )
            )
            .border(1.dp, CardBorderColor, RoundedCornerShape(24.dp))
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.Face,
            contentDescription = null,
            tint = IgTextSecondary,
            modifier = Modifier.size(48.dp)
        )
        Text(
            text = if (isScanning && totalMediaCount > 0) {
                "Scanning Photos ($processedCount / $totalMediaCount)..."
            } else {
                "No Clear Human Faces Found Yet"
            },
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
        if (!isScanning) {
            Box(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(InputFillColor)
                    .border(1.dp, IgBlue.copy(alpha = 0.7f), RoundedCornerShape(50))
                    .bounceClick(onClick = onRescanClick)
                    .padding(horizontal = 18.dp, vertical = 10.dp)
                    .testTag("empty_faces_rescan_button"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Rescan Gallery Photos",
                    fontFamily = PlusJakartaSansFont,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = IgBlue
                )
            }
        }
    }
}
