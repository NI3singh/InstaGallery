// InstaGallery v2.0
package com.example.ui.screens.settings

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FilledExtendedIcons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.CropFree
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material.icons.outlined.OutlinedExtendedIcons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.local.HeartTheme
import com.example.data.local.OnboardingPreferences
import com.example.data.local.entity.MediaEntity
import com.example.data.local.entity.PersonEntity
import com.example.data.repository.GalleryRepository
import com.example.ml.FaceEmbeddingExtractor
import com.example.ui.components.HeartThemePickerSheet
import com.example.ui.components.InstaCardTemplate
import com.example.ui.components.RotatingStoryRingAvatar
import com.example.ui.components.bounceClick
import com.example.ui.theme.IgBlack
import com.example.ui.theme.IgBlue
import com.example.ui.theme.IgRed
import com.example.ui.theme.IgStoryGradient
import com.example.ui.theme.IgTextPrimary
import com.example.ui.theme.IgTextSecondary
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SettingsSubPage {
    MAIN_HUB,
    EDIT_PROFILE,
    CHOOSE_MY_FACE,
    SCANNER_CONSOLE,
    HIDDEN_PROFILES_MANAGER,
    MERGE_PROFILES_STUDIO,
    APPEARANCE_STUDIO,
    DATABASE_INSPECTOR
}

private data class LookalikeSuggestion(
    val personA: PersonEntity,
    val personB: PersonEntity,
    val similarity: Float,
    val pairKey: String
)

@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    onOpenProfileSetup: () -> Unit,
    onResetAndRescanTriggered: () -> Unit,
    onNavigateToProfile: (Long) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember(context) { GalleryRepository(context) }

    LaunchedEffect(context) {
        OnboardingPreferences.init(context)
    }

    val visiblePeople by repository.allPeople.collectAsStateWithLifecycle(initialValue = emptyList())
    val allPeopleIncludeHidden by repository.allPeopleIncludeHidden.collectAsStateWithLifecycle(initialValue = emptyList())
    val hiddenPeople = remember(allPeopleIncludeHidden) {
        allPeopleIncludeHidden.filter { it.isHiddenToOther }
    }

    val totalPhotosCount by repository.totalMediaCount.collectAsStateWithLifecycle(initialValue = 0)
    val processedPhotosCount by repository.processedCount.collectAsStateWithLifecycle(initialValue = 0)
    val totalDetectedFaces by repository.totalDetectedFacesCount.collectAsStateWithLifecycle(initialValue = 0)
    val videosWithAuthors by repository.videosWithAuthors.collectAsStateWithLifecycle(initialValue = emptyList())
    val collections by repository.allCollectionsFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    val myProfilePersonId by OnboardingPreferences.myProfilePersonIdFlow.collectAsStateWithLifecycle()
    val customProfileBio by OnboardingPreferences.customProfileBioFlow.collectAsStateWithLifecycle()
    val heartTheme by OnboardingPreferences.heartThemeFlow.collectAsStateWithLifecycle()
    val defaultCropMode by OnboardingPreferences.defaultCropModeFlow.collectAsStateWithLifecycle()
    val hideOtherFromHomeFeed by OnboardingPreferences.hideOtherFromHomeFeedFlow.collectAsStateWithLifecycle()
    val storyDurationSeconds by OnboardingPreferences.storyDurationSecondsFlow.collectAsStateWithLifecycle()
    val defaultInstaCardTemplateId by OnboardingPreferences.defaultInstaCardTemplateFlow.collectAsStateWithLifecycle()

    val resolvedMyPerson = remember(visiblePeople, myProfilePersonId) {
        visiblePeople.firstOrNull { it.personId == myProfilePersonId }
            ?: visiblePeople.firstOrNull()
    }

    var currentSubPageName by rememberSaveable { mutableStateOf(SettingsSubPage.MAIN_HUB.name) }
    val currentSubPage = remember(currentSubPageName) {
        runCatching { SettingsSubPage.valueOf(currentSubPageName) }.getOrDefault(SettingsSubPage.MAIN_HUB)
    }

    var showHeartThemeSheet by remember { mutableStateOf(false) }

    // 2. Sub-Page BackHandler returning to MAIN_HUB
    BackHandler(enabled = currentSubPage != SettingsSubPage.MAIN_HUB) {
        currentSubPageName = SettingsSubPage.MAIN_HUB.name
    }

    val unindexedCount = (totalPhotosCount - processedPhotosCount).coerceAtLeast(0)

    Scaffold(
        containerColor = Color.Black,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .statusBarsPadding()
                .padding(innerPadding)
        ) {
            AnimatedContent(
                targetState = currentSubPage,
                transitionSpec = {
                    if (targetState != SettingsSubPage.MAIN_HUB) {
                        (slideInHorizontally { it / 3 } + fadeIn(tween(220))) togetherWith
                            (slideOutHorizontally { -it / 3 } + fadeOut(tween(180)))
                    } else {
                        (slideInHorizontally { -it / 3 } + fadeIn(tween(220))) togetherWith
                            (slideOutHorizontally { it / 3 } + fadeOut(tween(180)))
                    }
                },
                label = "settings_subpage_transition"
            ) { page ->
                when (page) {
                    SettingsSubPage.MAIN_HUB -> {
                        SettingsMainHubContent(
                            myPerson = resolvedMyPerson,
                            customProfileBio = customProfileBio,
                            visiblePeopleCount = visiblePeople.size,
                            hiddenPeopleCount = hiddenPeople.size,
                            totalPhotos = totalPhotosCount,
                            unindexedCount = unindexedCount,
                            totalVideos = videosWithAuthors.size,
                            defaultCropMode = defaultCropMode,
                            storyDuration = storyDurationSeconds,
                            heartTheme = heartTheme,
                            hideOtherFromHomeFeed = hideOtherFromHomeFeed,
                            onBackClick = onBackClick,
                            onNavigateSubPage = { currentSubPageName = it.name },
                            onOpenProfileSetup = onOpenProfileSetup,
                            onOpenHeartThemeSheet = { showHeartThemeSheet = true },
                            onToggleHideOther = { checked ->
                                OnboardingPreferences.setHideOtherFromHomeFeed(context, checked)
                            }
                        )
                    }

                    SettingsSubPage.EDIT_PROFILE -> {
                        EditProfileSubScreen(
                            myPerson = resolvedMyPerson,
                            currentBio = customProfileBio,
                            repository = repository,
                            onBack = { currentSubPageName = SettingsSubPage.MAIN_HUB.name },
                            onSwitchMeFaceClick = { currentSubPageName = SettingsSubPage.CHOOSE_MY_FACE.name }
                        )
                    }

                    SettingsSubPage.CHOOSE_MY_FACE -> {
                        ChooseMyFaceSubScreen(
                            people = visiblePeople,
                            currentMyProfileId = resolvedMyPerson?.personId ?: -1L,
                            onSelectPerson = { chosen ->
                                OnboardingPreferences.setMyProfilePersonId(context, chosen.personId)
                                Toast.makeText(
                                    context,
                                    "Primary profile set to @${chosen.name.removePrefix("@")}",
                                    Toast.LENGTH_SHORT
                                ).show()
                                currentSubPageName = SettingsSubPage.MAIN_HUB.name
                            },
                            onBack = { currentSubPageName = SettingsSubPage.MAIN_HUB.name }
                        )
                    }

                    SettingsSubPage.SCANNER_CONSOLE -> {
                        ScannerConsoleSubScreen(
                            indexedPhotoCount = processedPhotosCount,
                            unindexedCount = unindexedCount,
                            totalVideos = videosWithAuthors.size,
                            totalDetectedFaces = totalDetectedFaces,
                            repository = repository,
                            onBack = { currentSubPageName = SettingsSubPage.MAIN_HUB.name }
                        )
                    }

                    SettingsSubPage.HIDDEN_PROFILES_MANAGER -> {
                        HiddenProfilesManagerSubScreen(
                            hiddenPeople = hiddenPeople,
                            repository = repository,
                            onBack = { currentSubPageName = SettingsSubPage.MAIN_HUB.name }
                        )
                    }

                    SettingsSubPage.MERGE_PROFILES_STUDIO -> {
                        MergeProfilesStudioSubScreen(
                            visiblePeople = visiblePeople,
                            repository = repository,
                            onBack = { currentSubPageName = SettingsSubPage.MAIN_HUB.name }
                        )
                    }

                    SettingsSubPage.APPEARANCE_STUDIO -> {
                        AppearanceStudioSubScreen(
                            myPerson = resolvedMyPerson,
                            defaultCropMode = defaultCropMode,
                            heartTheme = heartTheme,
                            storyDurationSeconds = storyDurationSeconds,
                            defaultInstaCardTemplateId = defaultInstaCardTemplateId,
                            repository = repository,
                            onSelectCropMode = { crop4x5 ->
                                OnboardingPreferences.setDefaultCropMode(context, crop4x5)
                            },
                            onSelectHeartTheme = { selected ->
                                OnboardingPreferences.setHeartTheme(context, selected)
                            },
                            onSelectStoryDuration = { secs ->
                                OnboardingPreferences.setStoryDurationSeconds(context, secs)
                            },
                            onSelectInstaCardTemplate = { templateId ->
                                OnboardingPreferences.setDefaultInstaCardTemplate(context, templateId)
                            },
                            onBack = { currentSubPageName = SettingsSubPage.MAIN_HUB.name }
                        )
                    }

                    SettingsSubPage.DATABASE_INSPECTOR -> {
                        DatabaseInspectorSubScreen(
                            totalPhotos = totalPhotosCount,
                            totalVideos = videosWithAuthors.size,
                            activeProfilesCount = visiblePeople.size,
                            hiddenProfilesCount = hiddenPeople.size,
                            totalDetectedFaces = totalDetectedFaces,
                            collectionsCount = collections.size,
                            repository = repository,
                            onResetAndRescanTriggered = onResetAndRescanTriggered,
                            onBack = { currentSubPageName = SettingsSubPage.MAIN_HUB.name }
                        )
                    }
                }
            }
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
}

/**
 * 3. Main Hub Layout (SettingsSubPage.MAIN_HUB)
 */
@Composable
private fun SettingsMainHubContent(
    myPerson: PersonEntity?,
    customProfileBio: String,
    visiblePeopleCount: Int,
    hiddenPeopleCount: Int,
    totalPhotos: Int,
    unindexedCount: Int,
    totalVideos: Int,
    defaultCropMode: Boolean,
    storyDuration: Int,
    heartTheme: HeartTheme,
    hideOtherFromHomeFeed: Boolean,
    onBackClick: () -> Unit,
    onNavigateSubPage: (SettingsSubPage) -> Unit,
    onOpenProfileSetup: () -> Unit,
    onOpenHeartThemeSheet: () -> Unit,
    onToggleHideOther: (Boolean) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Top Bar (52.dp height, #000000 background)
        SettingsTopBar(
            title = "Settings and activity",
            onBackClick = onBackClick,
            backTestTag = "settings_back_button"
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("settings_lazy_column"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Hero Identity Card (Top of Main Hub)
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF121216))
                        .border(0.5.dp, Color(0xFF222228), RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    // Top Row: 64.dp RotatingStoryRingAvatar + Bold @Handle, Bio, Live Stat
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RotatingStoryRingAvatar(
                            avatarSize = 64.dp,
                            ringWidth = 2.dp,
                            innerCutoutWidth = 2.dp,
                            innerCutoutColor = Color(0xFF121216)
                        ) {
                            if (myPerson != null &&
                                myPerson.avatarCropPath.isNotBlank() &&
                                File(myPerson.avatarCropPath).exists()
                            ) {
                                AsyncImage(
                                    model = File(myPerson.avatarCropPath),
                                    contentDescription = myPerson.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color(0xFF26262A)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Person,
                                        contentDescription = "Select Profile",
                                        tint = Color.White,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "@${myPerson?.name?.removePrefix("@") ?: "Select Profile"}",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = customProfileBio.ifBlank { OnboardingPreferences.DEFAULT_PROFILE_BIO },
                                fontSize = 13.sp,
                                color = Color(0xFFA0A0A8),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${myPerson?.faceCount ?: 0} photos indexed",
                                fontSize = 12.sp,
                                color = Color(0xFF0095F6),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Bottom Action Row (padding(top = 14.dp), spacedBy(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Button 1: "Edit profile"
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF26262A))
                                .bounceClick { onNavigateSubPage(SettingsSubPage.EDIT_PROFILE) }
                                .testTag("settings_edit_profile_row"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Edit profile",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }

                        // Button 2: "Switch 'Me' face"
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF26262A))
                                .bounceClick { onNavigateSubPage(SettingsSubPage.CHOOSE_MY_FACE) }
                                .testTag("settings_primary_profile_row"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Switch 'Me' face",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            // Section 1 — Face engine & people tools (#121216 rounded card)
            item {
                SettingsSectionCard(title = "Face engine & people tools") {
                    // Row 1: Name & Review Faces
                    SettingsRowItem(
                        icon = Icons.Outlined.Face,
                        title = "Name & Review Faces",
                        subtitle = "Swipe 3D cards to name or categorize people",
                        onClick = onOpenProfileSetup,
                        testTag = "settings_name_faces_row"
                    )

                    HorizontalDivider(color = Color(0xFF1F1F24), thickness = 0.5.dp)

                    // Row 2: Merge Duplicate Profiles
                    SettingsRowItem(
                        icon = Icons.Outlined.GroupAdd,
                        title = "Merge Duplicate Profiles",
                        subtitle = "Review AI lookalike suggestions or merge manually",
                        onClick = { onNavigateSubPage(SettingsSubPage.MERGE_PROFILES_STUDIO) },
                        testTag = "settings_merge_duplicates_row"
                    )

                    HorizontalDivider(color = Color(0xFF1F1F24), thickness = 0.5.dp)

                    // Row 3: Hidden & @Other Profiles
                    SettingsRowItem(
                        icon = Icons.Outlined.Shield,
                        title = "Hidden & @Other Profiles",
                        subtitle = "$hiddenPeopleCount hidden profiles • Inspect or restore",
                        onClick = { onNavigateSubPage(SettingsSubPage.HIDDEN_PROFILES_MANAGER) },
                        testTag = "settings_manage_hidden_row"
                    )

                    HorizontalDivider(color = Color(0xFF1F1F24), thickness = 0.5.dp)

                    // Row 4: Gallery Sync & Face Scanner
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateSubPage(SettingsSubPage.SCANNER_CONSOLE) }
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                            .testTag("settings_rescan_now_row"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Gallery Sync & Face Scanner",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = if (unindexedCount > 0) {
                                    "Indexing $unindexedCount photos..."
                                } else {
                                    "All $totalPhotos photos synced & indexed"
                                },
                                color = IgTextSecondary,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        // Live status dot (#10B981 green when synced, #0095F6 when active)
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (unindexedCount > 0) Color(0xFF0095F6) else Color(0xFF10B981)
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                            contentDescription = null,
                            tint = IgTextSecondary
                        )
                    }
                }
            }

            // Section 2 — Feed, stories & appearance (#121216 rounded card)
            item {
                SettingsSectionCard(title = "Feed, stories & appearance") {
                    // Row 1: Feed, Aspect Ratio & Story Studio
                    SettingsRowItem(
                        icon = Icons.Outlined.Photo,
                        title = "Feed, Aspect Ratio & Story Studio",
                        subtitle = "${if (defaultCropMode) "Instagram 4:5 Crop" else "Original Uncropped (Fit)"} • ${storyDuration}s stories",
                        onClick = { onNavigateSubPage(SettingsSubPage.APPEARANCE_STUDIO) },
                        testTag = "settings_aspect_ratio_row"
                    )

                    HorizontalDivider(color = Color(0xFF1F1F24), thickness = 0.5.dp)

                    // Row 2: Heart Reaction Color (Clean 20.dp filled Heart icon tinted in heartTheme.primaryColor, NO white circle border!)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenHeartThemeSheet() }
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                            .testTag("settings_heart_theme_row"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = OutlinedExtendedIcons.FavoriteBorder,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Heart Reaction Color",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = heartTheme.label,
                                color = IgTextSecondary,
                                fontSize = 12.sp
                            )
                        }
                        Icon(
                            imageVector = FilledExtendedIcons.Favorite,
                            contentDescription = heartTheme.label,
                            tint = heartTheme.primaryColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                            contentDescription = null,
                            tint = IgTextSecondary
                        )
                    }

                    HorizontalDivider(color = Color(0xFF1F1F24), thickness = 0.5.dp)

                    // Row 3: Hide @Other from Home Feed (Inline #0095F6 Switch, NO > chevron)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggleHideOther(!hideOtherFromHomeFeed) }
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .testTag("settings_hide_other_switch_row"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Group,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Hide @Other from Home Feed",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Only show photos of detected people on Home",
                                color = IgTextSecondary,
                                fontSize = 12.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Switch(
                            checked = hideOtherFromHomeFeed,
                            onCheckedChange = onToggleHideOther,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF0095F6),
                                uncheckedThumbColor = Color(0xFF8E8E93),
                                uncheckedTrackColor = Color(0xFF26262A)
                            )
                        )
                    }
                }
            }

            // Section 3 — Data & on-device storage (#121216 rounded card)
            item {
                SettingsSectionCard(title = "Data & on-device storage") {
                    SettingsRowItem(
                        icon = Icons.Default.Build,
                        title = "Database & Storage Inspector",
                        subtitle = "$totalPhotos photos • $totalVideos reels • $visiblePeopleCount profiles",
                        onClick = { onNavigateSubPage(SettingsSubPage.DATABASE_INSPECTOR) },
                        testTag = "settings_database_inspector_row"
                    )
                }
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "InstaGallery v2.0.5",
                        color = Color(0xFF6E6E77),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "100% On-Device • Zero Cloud Uploads",
                        color = Color(0xFF4E4E57),
                        fontSize = 11.sp
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

/**
 * Sub-Screen: EDIT_PROFILE
 */
@Composable
private fun EditProfileSubScreen(
    myPerson: PersonEntity?,
    currentBio: String,
    repository: GalleryRepository,
    onBack: () -> Unit,
    onSwitchMeFaceClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var handleInput by remember(myPerson?.personId) {
        mutableStateOf(myPerson?.name?.removePrefix("@") ?: "")
    }
    var bioInput by remember(myPerson?.personId, currentBio) {
        mutableStateOf(currentBio)
    }

    val faces by remember(myPerson?.personId) {
        if (myPerson != null) {
            repository.getFacesForPerson(myPerson.personId)
        } else {
            kotlinx.coroutines.flow.flowOf(emptyList())
        }
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .background(Color.Black)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .bounceClick { onBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Text(
                    text = "Edit profile",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Box(
                modifier = Modifier
                    .height(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0095F6))
                    .bounceClick {
                        if (myPerson != null) {
                            scope.launch {
                                val cleanHandle = handleInput.trim().removePrefix("@").trim()
                                if (cleanHandle.isNotBlank()) {
                                    repository.renamePerson(myPerson.personId, cleanHandle)
                                }
                                OnboardingPreferences.setCustomProfileBio(
                                    context = context,
                                    personId = myPerson.personId,
                                    bio = bioInput
                                )
                                Toast.makeText(context, "Profile updated", Toast.LENGTH_SHORT).show()
                                onBack()
                            }
                        } else {
                            onBack()
                        }
                    }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Save",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        }

        HorizontalDivider(color = Color(0xFF1F1F24), thickness = 0.5.dp)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF121216))
                        .border(0.5.dp, Color(0xFF222228), RoundedCornerShape(16.dp))
                        .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    RotatingStoryRingAvatar(
                        avatarSize = 84.dp,
                        ringWidth = 2.5.dp,
                        innerCutoutWidth = 2.5.dp,
                        innerCutoutColor = Color(0xFF121216)
                    ) {
                        if (myPerson != null &&
                            myPerson.avatarCropPath.isNotBlank() &&
                            File(myPerson.avatarCropPath).exists()
                        ) {
                            AsyncImage(
                                model = File(myPerson.avatarCropPath),
                                contentDescription = myPerson.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color(0xFF26262A)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Person,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Box(
                        modifier = Modifier
                            .height(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF26262A))
                            .bounceClick { onSwitchMeFaceClick() }
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Switch 'Me' face",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }

                    if (faces.size > 1 && myPerson != null) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Choose avatar crop",
                            fontSize = 12.sp,
                            color = Color(0xFF8E8E93),
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(
                                items = faces.filter { it.cropPath.isNotBlank() && File(it.cropPath).exists() }.take(12),
                                key = { it.faceId }
                            ) { face ->
                                val isCurrentAvatar = face.cropPath == myPerson.avatarCropPath
                                Box(
                                    modifier = Modifier
                                        .size(50.dp)
                                        .clip(CircleShape)
                                        .border(
                                            width = if (isCurrentAvatar) 2.dp else 0.5.dp,
                                            color = if (isCurrentAvatar) Color(0xFF0095F6) else Color(0xFF2A2A32),
                                            shape = CircleShape
                                        )
                                        .bounceClick {
                                            scope.launch {
                                                repository.updateAvatar(myPerson.personId, face.cropPath)
                                            }
                                        }
                                ) {
                                    AsyncImage(
                                        model = File(face.cropPath),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF121216))
                        .border(0.5.dp, Color(0xFF222228), RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Column {
                        Text(
                            text = "Username (@handle)",
                            fontSize = 12.sp,
                            color = Color(0xFF8E8E93),
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF1C1C22))
                                .padding(horizontal = 14.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            BasicTextField(
                                value = handleInput,
                                onValueChange = { handleInput = it },
                                singleLine = true,
                                textStyle = TextStyle(
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                cursorBrush = SolidColor(Color(0xFF0095F6)),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    Column {
                        Text(
                            text = "Bio",
                            fontSize = 12.sp,
                            color = Color(0xFF8E8E93),
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(72.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF1C1C22))
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            contentAlignment = Alignment.TopStart
                        ) {
                            if (bioInput.isEmpty()) {
                                Text(
                                    text = "Add a personal bio for your profile...",
                                    fontSize = 14.sp,
                                    color = Color(0xFF71717A)
                                )
                            }
                            BasicTextField(
                                value = bioInput,
                                onValueChange = { bioInput = it },
                                textStyle = TextStyle(
                                    color = Color.White,
                                    fontSize = 14.sp
                                ),
                                cursorBrush = SolidColor(Color(0xFF0095F6)),
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Sub-Screen: CHOOSE_MY_FACE
 */
@Composable
private fun ChooseMyFaceSubScreen(
    people: List<PersonEntity>,
    currentMyProfileId: Long,
    onSelectPerson: (PersonEntity) -> Unit,
    onBack: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredPeople = remember(people, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) {
            people
        } else {
            people.filter { it.name.lowercase().contains(q) }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SettingsTopBar(
            title = "Switch 'Me' Face",
            onBackClick = onBack
        )

        SettingsSearchBar(
            query = searchQuery,
            placeholder = "Search people by @name...",
            onQueryChange = { searchQuery = it },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(
                items = filteredPeople,
                key = { it.personId }
            ) { person ->
                val isCurrentMe = person.personId == currentMyProfileId
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF121216))
                        .border(
                            width = if (isCurrentMe) 1.5.dp else 0.5.dp,
                            color = if (isCurrentMe) Color(0xFF0095F6) else Color(0xFF222228),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .bounceClick { onSelectPerson(person) }
                        .padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF26262A)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (person.avatarCropPath.isNotBlank() && File(person.avatarCropPath).exists()) {
                            AsyncImage(
                                model = File(person.avatarCropPath),
                                contentDescription = person.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.Person,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "@${person.name.removePrefix("@")}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = "${person.faceCount} photos",
                        fontSize = 11.5.sp,
                        color = Color(0xFF8E8E93),
                        maxLines = 1
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(30.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(if (isCurrentMe) Color(0xFF0095F6) else Color(0xFF26262A)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isCurrentMe) "Active @Me" else "Select",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

/**
 * 4.1 SettingsSubPage.SCANNER_CONSOLE (Gallery Sync & Face Scanner Console)
 */
@Composable
private fun ScannerConsoleSubScreen(
    indexedPhotoCount: Int,
    unindexedCount: Int,
    totalVideos: Int,
    totalDetectedFaces: Int,
    repository: GalleryRepository,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var isScanning by remember { mutableStateOf(false) }
    var lastSyncMessage by remember {
        mutableStateOf("Last check: +0 new photos, 0 removed")
    }

    val infiniteTransition = rememberInfiniteTransition(label = "scanner_ring_rotation")
    val ringAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scanner_angle"
    )

    val isActiveScan = isScanning || unindexedCount > 0

    Column(modifier = Modifier.fillMaxSize()) {
        SettingsTopBar(
            title = "Gallery Sync & Scanner",
            onBackClick = onBack
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Live Scanner Status Hero Card (#121216, RoundedCornerShape(16.dp), padding(20.dp))
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF121216))
                        .border(0.5.dp, Color(0xFF222228), RoundedCornerShape(16.dp))
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier.size(72.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val strokeW = 4.dp.toPx()
                            if (isActiveScan) {
                                withTransform({
                                    rotate(degrees = ringAngle, pivot = center)
                                }) {
                                    drawCircle(
                                        brush = Brush.sweepGradient(
                                            colors = listOf(
                                                Color(0xFFFEDA77),
                                                Color(0xFFF58529),
                                                Color(0xFFDD2A7B),
                                                Color(0xFF8134AF),
                                                Color(0xFFFEDA77)
                                            ),
                                            center = center
                                        ),
                                        style = Stroke(width = strokeW, cap = StrokeCap.Round)
                                    )
                                }
                            } else {
                                drawCircle(
                                    color = Color(0xFF10B981),
                                    style = Stroke(width = strokeW)
                                )
                            }
                        }

                        Icon(
                            imageVector = if (isActiveScan) Icons.Default.Refresh else Icons.Default.Check,
                            contentDescription = null,
                            tint = if (isActiveScan) Color.White else Color(0xFF10B981),
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = if (isActiveScan) "Scanning & Indexing Media..." else "Gallery 100% Synced",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = lastSyncMessage,
                        fontSize = 13.sp,
                        color = Color(0xFF8E8E93),
                        textAlign = TextAlign.Center
                    )
                }
            }

            // 4-Card Telemetry Bento Grid (2x2 grid, spacedBy(10.dp))
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        TelemetryBentoCard(
                            title = "Indexed Photos",
                            value = "$indexedPhotoCount",
                            subtitle = "Ready in feed",
                            modifier = Modifier.weight(1f)
                        )
                        TelemetryBentoCard(
                            title = "Pending Queue",
                            value = "$unindexedCount",
                            subtitle = "Waiting for ML Kit",
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        TelemetryBentoCard(
                            title = "Reels Videos",
                            value = "$totalVideos",
                            subtitle = "Ready in Reels",
                            modifier = Modifier.weight(1f)
                        )
                        TelemetryBentoCard(
                            title = "Face Embeddings",
                            value = "$totalDetectedFaces",
                            subtitle = "192-D MobileFaceNet",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Action Controls Card (padding(top = 16.dp), spacedBy(10.dp))
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Primary Button: "Run Quick Delta Sync Now"
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White)
                            .bounceClick(enabled = !isScanning) {
                                isScanning = true
                                scope.launch {
                                    val diff = repository.performQuickRefreshSync()
                                    lastSyncMessage =
                                        "Synced: ${diff.addedPhotosCount} new photos, ${diff.addedVideosCount} new videos, ${diff.deletedCount} cleaned up"
                                    isScanning = false
                                }
                            }
                            .testTag("scanner_quick_delta_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Run Quick Delta Sync Now",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    }

                    // Secondary Button: "Full Storage & Avatar Verification"
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF26262A))
                            .bounceClick(enabled = !isScanning) {
                                isScanning = true
                                scope.launch {
                                    val verifiedTotal = repository.runFullStorageAndAvatarVerification()
                                    lastSyncMessage =
                                        "Full verification complete: $verifiedTotal media items & all profile avatars verified"
                                    isScanning = false
                                }
                            }
                            .testTag("scanner_full_verify_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Full Storage & Avatar Verification",
                            fontSize = 14.sp,
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
private fun TelemetryBentoCard(
    title: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF121216))
            .border(0.5.dp, Color(0xFF222228), RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Text(
            text = title,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF8E8E93)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = value,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = subtitle,
            fontSize = 11.5.sp,
            color = Color(0xFF0095F6)
        )
    }
}

/**
 * 4.2 SettingsSubPage.HIDDEN_PROFILES_MANAGER (Manage Hidden / @Other Profiles)
 */
@Composable
private fun HiddenProfilesManagerSubScreen(
    hiddenPeople: List<PersonEntity>,
    repository: GalleryRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }

    val filteredHidden = remember(hiddenPeople, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) {
            hiddenPeople
        } else {
            hiddenPeople.filter {
                it.name.lowercase().contains(q) || it.personId.toString().contains(q)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top Bar: <- Back + "Hidden / @Other Profiles (${hiddenPeople.size})" + right matte squircle button "Restore All"
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .background(Color.Black)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .bounceClick { onBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Text(
                    text = "Hidden / @Other Profiles (${hiddenPeople.size})",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (hiddenPeople.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .height(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF26262A))
                        .bounceClick {
                            val allIds = hiddenPeople.map { it.personId }
                            scope.launch {
                                repository.restoreAllHiddenPeople(allIds)
                                Toast.makeText(
                                    context,
                                    "Restored ${allIds.size} profiles",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                        .padding(horizontal = 12.dp)
                        .testTag("restore_all_hidden_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Restore All",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }
        }

        HorizontalDivider(color = Color(0xFF1F1F24), thickness = 0.5.dp)

        // Search Bar
        SettingsSearchBar(
            query = searchQuery,
            placeholder = "Filter hidden profiles by @Name or ID...",
            onQueryChange = { searchQuery = it },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
        )

        if (filteredHidden.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Outlined.Shield,
                        contentDescription = null,
                        tint = Color(0xFF8E8E93),
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = if (hiddenPeople.isEmpty()) "No Hidden Profiles" else "No Matching Profiles",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Profiles moved to @Other can be inspected and restored here anytime.",
                        fontSize = 13.sp,
                        color = Color(0xFF8E8E93),
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            // 3-Column Visual Face Card Grid (GridCells.Fixed(3), spacedBy(10.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(
                    items = filteredHidden,
                    key = { it.personId }
                ) { person ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF121216))
                            .border(0.5.dp, Color(0xFF222228), RoundedCornerShape(12.dp))
                            .padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(68.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF26262A)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (person.avatarCropPath.isNotBlank() && File(person.avatarCropPath).exists()) {
                                AsyncImage(
                                    model = File(person.avatarCropPath),
                                    contentDescription = person.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Outlined.Person,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "@${person.name.removePrefix("@")}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(
                            text = "${person.faceCount} photos",
                            fontSize = 11.5.sp,
                            color = Color(0xFF8E8E93),
                            maxLines = 1
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(30.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(Color(0xFF26262A))
                                .bounceClick {
                                    scope.launch {
                                        repository.restorePersonFromOther(person.personId)
                                    }
                                }
                                .testTag("restore_person_${person.personId}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Restore",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF0095F6)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 4.3 SettingsSubPage.MERGE_PROFILES_STUDIO (AI Suggested Pairs + Multi-Select Merge)
 */
@Composable
private fun MergeProfilesStudioSubScreen(
    visiblePeople: List<PersonEntity>,
    repository: GalleryRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }
    val selectedIds = remember { mutableStateListOf<Long>() }
    val dismissedPairKeys = remember {
        mutableStateListOf<String>().apply {
            addAll(OnboardingPreferences.getDismissedDuplicatePairs(context))
        }
    }

    LaunchedEffect(Unit) {
        repository.runAutoDeduplication()
    }

    // Section A: Compute pairwise cosine similarity between visible PersonEntity centroids where similarity in 0.52f..0.679f
    var lookalikePairs by remember { mutableStateOf<List<LookalikeSuggestion>>(emptyList()) }

    LaunchedEffect(visiblePeople, dismissedPairKeys.size) {
        lookalikePairs = withContext(Dispatchers.Default) {
            val rawCandidates = mutableListOf<LookalikeSuggestion>()
            val decoded = visiblePeople
                .filter { it.faceCount >= 1 }
                .mapNotNull { p ->
                    val vec = decodeEmbeddingBlob(p.centroidEmbeddingBlob)
                    if (vec != null && vec.isNotEmpty()) p to vec else null
                }
            for (i in 0 until decoded.size) {
                for (j in i + 1 until decoded.size) {
                    val (personA, vecA) = decoded[i]
                    val (personB, vecB) = decoded[j]
                    if (personA.personId == personB.personId) continue
                    val key = if (personA.personId < personB.personId) {
                        "${personA.personId}_${personB.personId}"
                    } else {
                        "${personB.personId}_${personA.personId}"
                    }
                    if (dismissedPairKeys.contains(key)) continue
                    val sim = cosineSimilarity(vecA, vecB)
                    if (sim in 0.52f..0.679f) {
                        val (primary, secondary) = if (personA.isNamedByUser && !personB.isNamedByUser) {
                            personA to personB
                        } else if (!personA.isNamedByUser && personB.isNamedByUser) {
                            personB to personA
                        } else if (personA.faceCount >= personB.faceCount) {
                            personA to personB
                        } else {
                            personB to personA
                        }
                        rawCandidates.add(
                            LookalikeSuggestion(
                                personA = primary,
                                personB = secondary,
                                similarity = sim,
                                pairKey = key
                            )
                        )
                    }
                }
            }
            val sortedCandidates = rawCandidates.sortedByDescending { it.similarity }
            val usedPersonIds = HashSet<Long>()
            val nonOverlapping = mutableListOf<LookalikeSuggestion>()
            for (candidate in sortedCandidates) {
                if (candidate.personA.personId in usedPersonIds ||
                    candidate.personB.personId in usedPersonIds
                ) {
                    continue
                }
                usedPersonIds.add(candidate.personA.personId)
                usedPersonIds.add(candidate.personB.personId)
                nonOverlapping.add(candidate)
                if (nonOverlapping.size >= 8) break
            }
            nonOverlapping
        }
    }

    val filteredPeople = remember(visiblePeople, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) {
            visiblePeople
        } else {
            visiblePeople.filter { it.name.lowercase().contains(q) }
        }
    }

    val primaryTarget = remember(visiblePeople, selectedIds.toList()) {
        val selectedEntities = selectedIds.mapNotNull { id ->
            visiblePeople.firstOrNull { it.personId == id }
        }
        selectedEntities.firstOrNull { it.isNamedByUser } ?: selectedEntities.firstOrNull()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            SettingsTopBar(
                title = "Merge Duplicate Profiles",
                onBackClick = onBack
            )

            SettingsSearchBar(
                query = searchQuery,
                placeholder = "Search profiles to merge...",
                onQueryChange = { searchQuery = it },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 4.dp,
                    bottom = if (selectedIds.size >= 2) 96.dp else 24.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // Section A — AI Lookalike Suggestions (or clean auto-merged status banner)
                if (searchQuery.isBlank()) {
                    if (lookalikePairs.isNotEmpty()) {
                        item(span = { GridItemSpan(3) }) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "AI Lookalike Suggestions (${lookalikePairs.size})",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF8E8E93)
                                )

                                lookalikePairs.forEach { pair ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color(0xFF16161C))
                                            .border(0.5.dp, Color(0xFF26262E), RoundedCornerShape(12.dp))
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Row(horizontalArrangement = Arrangement.spacedBy((-10).dp)) {
                                                PersonCircleAvatar(
                                                    person = pair.personA,
                                                    size = 44.dp
                                                )
                                                PersonCircleAvatar(
                                                    person = pair.personB,
                                                    size = 44.dp
                                                )
                                            }

                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "@${pair.personA.name.removePrefix("@")} & @${pair.personB.name.removePrefix("@")}",
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "${pair.personA.faceCount + pair.personB.faceCount} total photos • ${(pair.similarity * 100).toInt()}% match",
                                                    fontSize = 12.sp,
                                                    color = Color(0xFF0095F6),
                                                    fontWeight = FontWeight.Medium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .height(32.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(Color(0xFF0095F6))
                                                    .bounceClick {
                                                        scope.launch {
                                                            val targetName = pair.personA.name.removePrefix("@")
                                                            repository.mergeProfiles(
                                                                targetId = pair.personA.personId,
                                                                sourceIds = listOf(pair.personB.personId),
                                                                finalName = targetName
                                                            )
                                                            selectedIds.remove(pair.personB.personId)
                                                            Toast.makeText(
                                                                context,
                                                                "Merged into @$targetName",
                                                                Toast.LENGTH_SHORT
                                                            ).show()
                                                        }
                                                    }
                                                    .padding(horizontal = 12.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "Merge",
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                            }

                                            Box(
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(Color(0xFF26262A))
                                                    .bounceClick {
                                                        if (!dismissedPairKeys.contains(pair.pairKey)) {
                                                            dismissedPairKeys.add(pair.pairKey)
                                                        }
                                                        OnboardingPreferences.addDismissedDuplicatePair(
                                                            context,
                                                            pair.pairKey
                                                        )
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "Dismiss suggestion",
                                                    tint = Color(0xFF8E8E93),
                                                    modifier = Modifier.size(15.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        item(span = { GridItemSpan(3) }) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF16161C))
                                    .border(0.5.dp, Color(0xFF26262E), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color(0xFF0095F6),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "All duplicate profiles have been automatically merged • Select any 2 profiles below to merge manually",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF8E8E93)
                                )
                            }
                        }
                    }
                }

                // Section B — Manual Multi-Select Face Grid Header
                item(span = { GridItemSpan(3) }) {
                    Text(
                        text = "Select 2 or more profiles to merge (${selectedIds.size} selected)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF8E8E93),
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                    )
                }

                items(
                    items = filteredPeople,
                    key = { it.personId }
                ) { person ->
                    val isSelected = selectedIds.contains(person.personId)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF121216))
                            .border(
                                width = if (isSelected) 2.dp else 0.5.dp,
                                color = if (isSelected) Color(0xFF0095F6) else Color(0xFF222228),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .bounceClick {
                                if (isSelected) {
                                    selectedIds.remove(person.personId)
                                } else {
                                    selectedIds.add(person.personId)
                                }
                            }
                            .padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(modifier = Modifier.size(68.dp)) {
                            PersonCircleAvatar(
                                person = person,
                                size = 68.dp
                            )
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF0095F6)),
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

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "@${person.name.removePrefix("@")}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(
                            text = "${person.faceCount} photos",
                            fontSize = 11.5.sp,
                            color = Color(0xFF8E8E93),
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // Sticky Bottom Merge Bar when 2 or more profiles are selected
        AnimatedVisibility(
            visible = selectedIds.size >= 2 && primaryTarget != null,
            enter = fadeIn() + slideInHorizontally { 0 },
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color(0xFF121216))
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            val target = primaryTarget
            if (target != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF0095F6))
                        .bounceClick {
                            val targetId = target.personId
                            val targetName = target.name.removePrefix("@")
                            val sources = selectedIds.filter { it != targetId }
                            scope.launch {
                                repository.mergeProfiles(
                                    targetId = targetId,
                                    sourceIds = sources,
                                    finalName = targetName
                                )
                                selectedIds.clear()
                                Toast.makeText(
                                    context,
                                    "Merged into @$targetName",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                        .testTag("merge_selected_profiles_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Merge ${selectedIds.size} Profiles into @${target.name.removePrefix("@")}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

/**
 * 4.4 SettingsSubPage.APPEARANCE_STUDIO (Live Interactive Post Preview + Feed & Story Controls)
 */
@Composable
private fun AppearanceStudioSubScreen(
    myPerson: PersonEntity?,
    defaultCropMode: Boolean,
    heartTheme: HeartTheme,
    storyDurationSeconds: Int,
    defaultInstaCardTemplateId: String,
    repository: GalleryRepository,
    onSelectCropMode: (Boolean) -> Unit,
    onSelectHeartTheme: (HeartTheme) -> Unit,
    onSelectStoryDuration: (Int) -> Unit,
    onSelectInstaCardTemplate: (String) -> Unit,
    onBack: () -> Unit
) {
    var samplePhoto by remember { mutableStateOf<MediaEntity?>(null) }
    var heartPopTrigger by remember { mutableIntStateOf(0) }
    val heartScale = remember { Animatable(1f) }

    LaunchedEffect(myPerson?.personId) {
        val personId = myPerson?.personId ?: -1L
        val samples = if (personId > 0L) {
            repository.getSamplePhotosForPersonSync(personId, 1)
        } else {
            emptyList()
        }
        samplePhoto = samples.firstOrNull()
    }

    LaunchedEffect(heartPopTrigger) {
        if (heartPopTrigger > 0) {
            heartScale.snapTo(0.75f)
            heartScale.animateTo(
                targetValue = 1.28f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
            heartScale.animateTo(1f, animationSpec = tween(140))
        }
    }

    val animatedPreviewAspect by animateFloatAsState(
        targetValue = if (defaultCropMode) 0.8f else 1.35f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "preview_aspect_ratio"
    )

    Column(modifier = Modifier.fillMaxSize()) {
        SettingsTopBar(
            title = "Feed, Stories & Appearance",
            onBackClick = onBack
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Live Interactive Mini-Post Preview Card (#121216, RoundedCornerShape(16.dp), padding(14.dp))
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF121216))
                        .border(0.5.dp, Color(0xFF222228), RoundedCornerShape(16.dp))
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            PersonCircleAvatar(person = myPerson, size = 32.dp)
                            Column {
                                Text(
                                    text = "@${myPerson?.name?.removePrefix("@") ?: "your_profile"}",
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = if (defaultCropMode) "Instagram 4:5 Crop Preview" else "Original Uncropped (Fit) Preview",
                                    fontSize = 11.sp,
                                    color = Color(0xFF8E8E93)
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF26262A))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Live Preview",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF0095F6)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(animatedPreviewAspect.coerceIn(0.75f, 1.5f))
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF0A0A0D)),
                        contentAlignment = Alignment.Center
                    ) {
                        val previewUri = samplePhoto?.uri
                        if (previewUri != null) {
                            AsyncImage(
                                model = previewUri,
                                contentDescription = "Sample preview",
                                contentScale = if (defaultCropMode) ContentScale.Crop else ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else if (myPerson != null &&
                            myPerson.avatarCropPath.isNotBlank() &&
                            File(myPerson.avatarCropPath).exists()
                        ) {
                            AsyncImage(
                                model = File(myPerson.avatarCropPath),
                                contentDescription = "Sample preview",
                                contentScale = if (defaultCropMode) ContentScale.Crop else ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.Photo,
                                contentDescription = null,
                                tint = Color(0xFF8E8E93),
                                modifier = Modifier.size(42.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Icon(
                                imageVector = FilledExtendedIcons.Favorite,
                                contentDescription = heartTheme.label,
                                tint = heartTheme.primaryColor,
                                modifier = Modifier
                                    .size(26.dp)
                                    .scale(heartScale.value)
                                    .bounceClick { heartPopTrigger++ }
                            )
                            Icon(
                                imageVector = OutlinedExtendedIcons.ChatBubbleOutline,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                            Icon(
                                imageVector = OutlinedExtendedIcons.Send,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(23.dp)
                            )
                        }
                        Icon(
                            imageVector = FilledExtendedIcons.Bookmark,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // Control Group 1 — Default Photo Aspect Ratio
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Default Photo Aspect Ratio",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF8E8E93)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Option A: Original Uncropped (Fit)
                        AspectRatioOptionCard(
                            title = "Original Uncropped (Fit)",
                            subtitle = "Shows full camera frame",
                            icon = Icons.Outlined.AspectRatio,
                            selected = !defaultCropMode,
                            onClick = { onSelectCropMode(false) },
                            modifier = Modifier.weight(1f)
                        )
                        // Option B: Instagram 4:5 Crop
                        AspectRatioOptionCard(
                            title = "Instagram 4:5 Crop",
                            subtitle = "Uniform vertical crop",
                            icon = Icons.Outlined.CropFree,
                            selected = defaultCropMode,
                            onClick = { onSelectCropMode(true) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Control Group 2 — Heart Reaction Theme Swatches (6-item grid)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Heart Reaction Theme",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF8E8E93)
                    )
                    val themes = HeartTheme.entries
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        themes.chunked(3).forEach { rowThemes ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowThemes.forEach { theme ->
                                    val isSelected = theme == heartTheme
                                    Row(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(44.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (isSelected) Color(0xFF26262A) else Color(0xFF141418))
                                            .border(
                                                width = if (isSelected) 1.5.dp else 0.5.dp,
                                                color = if (isSelected) Color.White else Color(0xFF222228),
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                            .bounceClick {
                                                onSelectHeartTheme(theme)
                                                heartPopTrigger++
                                            }
                                            .padding(horizontal = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = FilledExtendedIcons.Favorite,
                                            contentDescription = theme.label,
                                            tint = theme.primaryColor,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = theme.label,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Control Group 3 — Story Player Slide Timer (3-segment squircle bar)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Story Player Slide Timer",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF8E8E93)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(3, 5, 8).forEach { seconds ->
                            val isSelected = storyDurationSeconds == seconds
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) Color.White else Color(0xFF26262A))
                                    .bounceClick { onSelectStoryDuration(seconds) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "$seconds Seconds",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isSelected) Color.Black else Color.White
                                )
                            }
                        }
                    }
                }
            }

            // Control Group 4 — Default InstaCard Snapshot Style (3-segment squircle bar)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Default InstaCard Snapshot Style",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF8E8E93)
                    )
                    val templates = listOf(
                        InstaCardTemplate.OLED_DARK.name to "OLED Dark",
                        InstaCardTemplate.CHAMELEON_GLASS.name to "Ambient Glass",
                        InstaCardTemplate.POLAROID_STAMP.name to "Polaroid EXIF"
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        templates.forEach { (id, label) ->
                            val isSelected = defaultInstaCardTemplateId == id
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) Color.White else Color(0xFF26262A))
                                    .bounceClick { onSelectInstaCardTemplate(id) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isSelected) Color.Black else Color.White,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AspectRatioOptionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(64.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Color(0xFF26262A) else Color(0xFF141418))
            .border(
                width = if (selected) 1.5.dp else 0.5.dp,
                color = if (selected) Color.White else Color(0xFF222228),
                shape = RoundedCornerShape(10.dp)
            )
            .bounceClick { onClick() }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = Color.White,
            modifier = Modifier.size(20.dp)
        )
        Column {
            Text(
                text = title,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = Color(0xFF8E8E93),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * 4.5 SettingsSubPage.DATABASE_INSPECTOR (SQLite & On-Device Storage Inspector)
 */
@Composable
private fun DatabaseInspectorSubScreen(
    totalPhotos: Int,
    totalVideos: Int,
    activeProfilesCount: Int,
    hiddenProfilesCount: Int,
    totalDetectedFaces: Int,
    collectionsCount: Int,
    repository: GalleryRepository,
    onResetAndRescanTriggered: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var faceCropsBytes by remember { mutableLongStateOf(0L) }
    var sqliteDbBytes by remember { mutableLongStateOf(0L) }
    var statusBannerText by remember { mutableStateOf<String?>(null) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }

    LaunchedEffect(totalPhotos, totalDetectedFaces, activeProfilesCount) {
        withContext(Dispatchers.IO) {
            val cropsDir = context.filesDir.resolve("face_crops")
            faceCropsBytes = if (cropsDir.exists()) {
                cropsDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            } else {
                0L
            }
            val dbFile = context.getDatabasePath("instagallery_offline.db")
            val walFile = File(dbFile.path + "-wal")
            val shmFile = File(dbFile.path + "-shm")
            sqliteDbBytes = (if (dbFile.exists()) dbFile.length() else 0L) +
                (if (walFile.exists()) walFile.length() else 0L) +
                (if (shmFile.exists()) shmFile.length() else 0L)
        }
    }

    val cropsMb = remember(faceCropsBytes) {
        String.format(Locale.US, "%.2f MB", faceCropsBytes.toDouble() / (1024.0 * 1024.0))
    }
    val sqliteMb = remember(sqliteDbBytes) {
        String.format(Locale.US, "%.2f MB", sqliteDbBytes.toDouble() / (1024.0 * 1024.0))
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SettingsTopBar(
            title = "Database & Storage",
            onBackClick = onBack
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Storage & SQLite Breakdown Card (#121216, RoundedCornerShape(16.dp), padding(16.dp))
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF121216))
                        .border(0.5.dp, Color(0xFF222228), RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "On-Device Storage Footprint",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    DiagnosticStatRow(
                        label = "SQLite Database (instagallery_offline.db)",
                        value = sqliteMb
                    )
                    DiagnosticStatRow(
                        label = "Face Avatar Crops (filesDir/face_crops)",
                        value = cropsMb
                    )

                    HorizontalDivider(color = Color(0xFF222228), thickness = 0.5.dp)

                    Text(
                        text = "Live SQLite Table Row Counts",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    DiagnosticStatRow(
                        label = "media_items (Photos & Videos)",
                        value = "${totalPhotos + totalVideos} ($totalPhotos photos, $totalVideos reels)"
                    )
                    DiagnosticStatRow(
                        label = "people (Active & Hidden Profiles)",
                        value = "${activeProfilesCount + hiddenProfilesCount} ($activeProfilesCount active, $hiddenProfilesCount hidden)"
                    )
                    DiagnosticStatRow(
                        label = "detected_faces (Cropped Face Vectors)",
                        value = "$totalDetectedFaces"
                    )
                    DiagnosticStatRow(
                        label = "collections (Saved Boards)",
                        value = "$collectionsCount"
                    )
                }
            }

            if (statusBannerText != null) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF102A20))
                            .border(0.5.dp, Color(0xFF10B981), RoundedCornerShape(10.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = statusBannerText ?: "",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF10B981)
                        )
                    }
                }
            }

            // Maintenance & Integrity Actions
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Matte Squircle Button 1 (#26262A): "Repair & Reconcile Profile Counts"
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF26262A))
                            .bounceClick {
                                scope.launch {
                                    repository.repairAndSyncDatabaseCounts()
                                    statusBannerText = "All profile photo counts & avatars verified"
                                }
                            }
                            .testTag("settings_repair_db_row"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Repair & Reconcile Profile Counts",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }

                    // Danger Zone Card (#1A1014, 0.5.dp #FF3040 alpha border, RoundedCornerShape(14.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF1A1014))
                            .border(
                                width = 0.5.dp,
                                color = Color(0xFFFF3040).copy(alpha = 0.45f),
                                shape = RoundedCornerShape(14.dp)
                            )
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Reset Face Clusters & Re-Index",
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFF3040)
                        )
                        Text(
                            text = "Clears generated face profiles and re-scans your photos from scratch. Your Saved boards, bookmarks, and custom captions will NOT be deleted.",
                            fontSize = 12.5.sp,
                            color = Color(0xFFD4D4D8)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF2A1215))
                                .bounceClick { showResetConfirmDialog = true }
                                .testTag("settings_reset_clusters_row"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Reset Face Engine...",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFF3040)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            containerColor = Color(0xFF18181C),
            title = {
                Text(
                    text = "Reset Face Clusters & Re-Index?",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            },
            text = {
                Text(
                    text = "Clears generated face profiles and re-scans your photos from scratch. Your Saved boards, bookmarks, and custom captions will NOT be deleted.",
                    color = IgTextSecondary,
                    fontSize = 13.5.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetConfirmDialog = false
                        onResetAndRescanTriggered()
                    }
                ) {
                    Text(
                        text = "Reset & Re-Scan",
                        color = Color(0xFFFF3040),
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmDialog = false }) {
                    Text("Cancel", color = Color.White)
                }
            }
        )
    }
}

@Composable
private fun SettingsTopBar(
    title: String,
    onBackClick: () -> Unit,
    backTestTag: String = "settings_subpage_back_button"
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .bounceClick { onBackClick() }
                    .testTag(backTestTag),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = title,
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        HorizontalDivider(color = Color(0xFF1F1F24), thickness = 0.5.dp)
    }
}

@Composable
private fun SettingsSearchBar(
    query: String,
    placeholder: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF1C1C20))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = Color(0xFF8E8E93),
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterStart
        ) {
            if (query.isEmpty()) {
                Text(
                    text = placeholder,
                    fontSize = 13.5.sp,
                    color = Color(0xFF8E8E93)
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = Color.White,
                    fontSize = 13.5.sp
                ),
                cursorBrush = SolidColor(Color(0xFF0095F6)),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (query.isNotEmpty()) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Clear",
                tint = Color(0xFF8E8E93),
                modifier = Modifier
                    .size(16.dp)
                    .clickable { onQueryChange("") }
            )
        }
    }
}

@Composable
private fun PersonCircleAvatar(
    person: PersonEntity?,
    size: androidx.compose.ui.unit.Dp
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(0xFF26262A))
            .border(1.dp, Color(0xFF121216), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (person != null &&
            person.avatarCropPath.isNotBlank() &&
            File(person.avatarCropPath).exists()
        ) {
            AsyncImage(
                model = File(person.avatarCropPath),
                contentDescription = person.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = Icons.Outlined.Person,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(size * 0.45f)
            )
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            color = IgTextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF121216))
                .border(0.5.dp, Color(0xFF222228), RoundedCornerShape(16.dp))
        ) {
            content()
        }
    }
}

@Composable
private fun SettingsRowItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                color = IgTextSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = IgTextSecondary
        )
    }
}

@Composable
private fun DiagnosticStatRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = IgTextSecondary,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            color = IgTextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun decodeEmbeddingBlob(blob: ByteArray): FloatArray? {
    val floats = FaceEmbeddingExtractor.bytesToFloatArray(blob)
    return if (floats.isNotEmpty()) floats else null
}

private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
    val len = minOf(a.size, b.size)
    if (len == 0) return 0f
    var dot = 0f
    var normA = 0f
    var normB = 0f
    for (i in 0 until len) {
        dot += a[i] * b[i]
        normA += a[i] * a[i]
        normB += b[i] * b[i]
    }
    val denom = sqrt(normA) * sqrt(normB)
    return if (denom > 1e-6f) (dot / denom).coerceIn(-1f, 1f) else 0f
}
