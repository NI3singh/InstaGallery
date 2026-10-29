// InstaGallery v2.0
package com.example.ui.screens.settings

import android.widget.Toast
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.local.HeartTheme
import com.example.data.local.OnboardingPreferences
import com.example.data.local.entity.PersonEntity
import com.example.data.repository.GalleryRepository
import com.example.ui.components.HeartThemePickerSheet
import com.example.ui.components.InstaCardTemplate
import com.example.ui.components.bounceClick
import com.example.ui.screens.people.MultiMergeFaceGridSheet
import com.example.ui.screens.profile.ChooseMyProfileSheet
import com.example.ui.screens.profile.EditProfileModalSheet
import com.example.ui.theme.IgBlack
import com.example.ui.theme.IgBlue
import com.example.ui.theme.IgRed
import com.example.ui.theme.IgTextPrimary
import com.example.ui.theme.IgTextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
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

    var faceCropBytes by remember { mutableLongStateOf(0L) }
    var isRescanningNow by remember { mutableStateOf(false) }
    var isRepairingDb by remember { mutableStateOf(false) }

    var showChooseMyProfileSheet by remember { mutableStateOf(false) }
    var showEditProfileSheet by remember { mutableStateOf(false) }
    var showMultiMergeSheet by remember { mutableStateOf(false) }
    var showManageHiddenSheet by remember { mutableStateOf(false) }
    var showHeartThemeSheet by remember { mutableStateOf(false) }
    var showCropModeSheet by remember { mutableStateOf(false) }
    var showInstaCardTemplateSheet by remember { mutableStateOf(false) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }

    LaunchedEffect(allPeopleIncludeHidden.size, processedPhotosCount) {
        faceCropBytes = withContext(Dispatchers.IO) {
            val dir = File(context.filesDir, "face_crops")
            if (dir.exists()) {
                dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            } else {
                0L
            }
        }
    }

    val cropStorageMbText = remember(faceCropBytes) {
        val mb = faceCropBytes.toDouble() / (1024.0 * 1024.0)
        String.format(Locale.US, "%.1f MB", mb)
    }

    val currentTemplateLabel = remember(defaultInstaCardTemplateId) {
        when (defaultInstaCardTemplateId) {
            InstaCardTemplate.CHAMELEON_GLASS.name -> "Ambient Glass"
            InstaCardTemplate.POLAROID_STAMP.name -> "Polaroid EXIF"
            else -> "OLED Dark"
        }
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(IgBlack)
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("settings_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }

                    Text(
                        text = "Settings and activity",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
                HorizontalDivider(color = Color(0xFF1C1C1E), thickness = 0.5.dp)
            }
        },
        containerColor = IgBlack,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("settings_lazy_column"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // SECTION 1: Your Profile & Identity
            item {
                SettingsSectionCard(title = "Your profile & identity") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showChooseMyProfileSheet = true }
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                            .testTag("settings_primary_profile_row"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF26262A))
                                .border(1.5.dp, IgBlue, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (resolvedMyPerson != null &&
                                resolvedMyPerson.avatarCropPath.isNotBlank() &&
                                File(resolvedMyPerson.avatarCropPath).exists()
                            ) {
                                AsyncImage(
                                    model = File(resolvedMyPerson.avatarCropPath),
                                    contentDescription = resolvedMyPerson.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Outlined.Person,
                                    contentDescription = "Primary Profile",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Primary Profile (@Me)",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (resolvedMyPerson != null) {
                                    "@${resolvedMyPerson.name.removePrefix("@")} • ${resolvedMyPerson.faceCount} photos"
                                } else {
                                    "Tap to select which face is yours"
                                },
                                color = IgTextSecondary,
                                fontSize = 12.sp
                            )
                        }

                        Surface(
                            color = Color(0xFF26262A),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "Change",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }

                    HorizontalDivider(color = Color(0xFF1C1C1E), thickness = 0.5.dp)

                    SettingsRowItem(
                        icon = Icons.Outlined.Edit,
                        title = "Edit Profile Handle & Bio",
                        subtitle = customProfileBio.ifBlank { "Add a personal bio for your profile" },
                        onClick = { showEditProfileSheet = true },
                        testTag = "settings_edit_profile_row"
                    )
                }
            }

            // SECTION 2: Face Engine & People Tools
            item {
                SettingsSectionCard(title = "Face engine & people tools") {
                    SettingsRowItem(
                        icon = Icons.Outlined.Face,
                        title = "Name & Review Faces",
                        subtitle = "Batch review and name detected people",
                        onClick = onOpenProfileSetup,
                        testTag = "settings_name_faces_row"
                    )

                    HorizontalDivider(color = Color(0xFF1C1C1E), thickness = 0.5.dp)

                    SettingsRowItem(
                        icon = Icons.Outlined.GroupAdd,
                        title = "Merge Duplicate Profiles",
                        subtitle = "Select 2 or more face clusters to combine",
                        onClick = { showMultiMergeSheet = true },
                        testTag = "settings_merge_duplicates_row"
                    )

                    HorizontalDivider(color = Color(0xFF1C1C1E), thickness = 0.5.dp)

                    SettingsRowItem(
                        icon = Icons.Outlined.Shield,
                        title = "Manage Hidden / @Other Profiles",
                        subtitle = if (hiddenPeople.isEmpty()) {
                            "No profiles currently hidden in @Other"
                        } else {
                            "${hiddenPeople.size} hidden profiles • Tap to restore"
                        },
                        onClick = { showManageHiddenSheet = true },
                        testTag = "settings_manage_hidden_row"
                    )

                    HorizontalDivider(color = Color(0xFF1C1C1E), thickness = 0.5.dp)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isRescanningNow) {
                                isRescanningNow = true
                                Toast
                                    .makeText(context, "Gallery sync & face scan started", Toast.LENGTH_SHORT)
                                    .show()
                                scope.launch {
                                    repository.syncOnAppResume()
                                    repository.enqueueFaceIndexingWorker()
                                    isRescanningNow = false
                                }
                            }
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
                                text = "Re-Scan Device Gallery Now",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Sync new camera captures & index faces immediately",
                                color = IgTextSecondary,
                                fontSize = 12.sp
                            )
                        }
                        if (isRescanningNow) {
                            CircularProgressIndicator(
                                color = IgBlue,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(20.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                contentDescription = null,
                                tint = IgTextSecondary
                            )
                        }
                    }
                }
            }

            // SECTION 3: Feed, Stories & Appearance
            item {
                SettingsSectionCard(title = "Feed, stories & appearance") {
                    // 1. Heart Reaction Color
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showHeartThemeSheet = true }
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                            .testTag("settings_heart_theme_row"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = null,
                            tint = heartTheme.primaryColor,
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
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(heartTheme.primaryColor)
                                .border(1.5.dp, Color.White.copy(alpha = 0.7f), CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                            contentDescription = null,
                            tint = IgTextSecondary
                        )
                    }

                    HorizontalDivider(color = Color(0xFF1C1C1E), thickness = 0.5.dp)

                    // 2. Default Photo Aspect Ratio
                    SettingsRowItem(
                        icon = Icons.Outlined.Photo,
                        title = "Default Photo Aspect Ratio",
                        subtitle = if (defaultCropMode) "Instagram 4:5 Crop" else "Original Uncropped (Fit)",
                        onClick = { showCropModeSheet = true },
                        testTag = "settings_aspect_ratio_row"
                    )

                    HorizontalDivider(color = Color(0xFF1C1C1E), thickness = 0.5.dp)

                    // 3. Hide @Other Photos from Home Feed
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                OnboardingPreferences.setHideOtherFromHomeFeed(context, !hideOtherFromHomeFeed)
                            }
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
                                text = "Hide @Other Photos from Home Feed",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Only show photos containing recognized people in Home",
                                color = IgTextSecondary,
                                fontSize = 12.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Switch(
                            checked = hideOtherFromHomeFeed,
                            onCheckedChange = { checked ->
                                OnboardingPreferences.setHideOtherFromHomeFeed(context, checked)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = IgBlue,
                                uncheckedThumbColor = Color(0xFFA1A1AA),
                                uncheckedTrackColor = Color(0xFF26262A)
                            )
                        )
                    }

                    HorizontalDivider(color = Color(0xFF1C1C1E), thickness = 0.5.dp)

                    // 4. Story Player Slide Duration
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.Tune,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Story Player Slide Duration",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Auto-advance speed per story photo",
                                    color = IgTextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(3, 5, 8).forEach { seconds ->
                                    val selected = storyDurationSeconds == seconds
                                    Box(
                                        modifier = Modifier
                                            .height(32.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (selected) IgBlue else Color(0xFF26262A))
                                            .bounceClick {
                                                OnboardingPreferences.setStoryDurationSeconds(context, seconds)
                                            }
                                            .padding(horizontal = 12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "${seconds}s",
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = Color(0xFF1C1C1E), thickness = 0.5.dp)

                    // 5. Default InstaCard Snapshot Style
                    SettingsRowItem(
                        icon = Icons.Outlined.AutoFixHigh,
                        title = "Default InstaCard Snapshot Style",
                        subtitle = currentTemplateLabel,
                        onClick = { showInstaCardTemplateSheet = true },
                        testTag = "settings_instacard_style_row"
                    )
                }
            }

            // SECTION 4: On-Device Database & Storage Diagnostics
            item {
                SettingsSectionCard(title = "On-device database & storage diagnostics") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        DiagnosticStatRow(
                            label = "Indexed Photos",
                            value = "$processedPhotosCount / $totalPhotosCount"
                        )
                        DiagnosticStatRow(
                            label = "Reels Videos",
                            value = "${videosWithAuthors.size}"
                        )
                        DiagnosticStatRow(
                            label = "Active People Profiles",
                            value = "${visiblePeople.size} active • ${hiddenPeople.size} in @Other"
                        )
                        DiagnosticStatRow(
                            label = "Saved Collections",
                            value = "${collections.size} boards"
                        )
                        DiagnosticStatRow(
                            label = "Face Crop Storage",
                            value = cropStorageMbText
                        )
                    }

                    HorizontalDivider(color = Color(0xFF1C1C1E), thickness = 0.5.dp)

                    // Repair & Sync Profile Photo Counts
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isRepairingDb) {
                                isRepairingDb = true
                                scope.launch {
                                    repository.repairAndSyncDatabaseCounts()
                                    isRepairingDb = false
                                    Toast
                                        .makeText(
                                            context,
                                            "Profile photo counts & face avatars synced",
                                            Toast.LENGTH_SHORT
                                        )
                                        .show()
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                            .testTag("settings_repair_db_row"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Build,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Repair & Sync Profile Photo Counts",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Reconcile SQLite counts & fix missing face avatars",
                                color = IgTextSecondary,
                                fontSize = 12.sp
                            )
                        }
                        if (isRepairingDb) {
                            CircularProgressIndicator(
                                color = IgBlue,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(20.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                contentDescription = null,
                                tint = IgTextSecondary
                            )
                        }
                    }

                    HorizontalDivider(color = Color(0xFF1C1C1E), thickness = 0.5.dp)

                    // Danger Zone: Reset Face Clusters & Re-Scan From Scratch
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showResetConfirmDialog = true }
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                            .testTag("settings_reset_clusters_row"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = null,
                            tint = IgRed,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Reset Face Clusters & Re-Scan From Scratch",
                                color = IgRed,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Clears all face groupings and rebuilds from device photos",
                                color = IgTextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    // 1. Choose My Profile Sheet
    if (showChooseMyProfileSheet) {
        ChooseMyProfileSheet(
            people = visiblePeople,
            currentMyProfileId = resolvedMyPerson?.personId ?: -1L,
            onSelectPerson = { chosenPerson ->
                OnboardingPreferences.setMyProfilePersonId(context, chosenPerson.personId)
                showChooseMyProfileSheet = false
            },
            onDismiss = { showChooseMyProfileSheet = false }
        )
    }

    // 2. Edit Profile Handle & Bio Sheet
    if (showEditProfileSheet && resolvedMyPerson != null) {
        val personFaces by remember(resolvedMyPerson.personId) {
            repository.getFacesForPerson(resolvedMyPerson.personId)
        }.collectAsStateWithLifecycle(initialValue = emptyList())

        EditProfileModalSheet(
            currentName = resolvedMyPerson.name.removePrefix("@"),
            currentBio = customProfileBio,
            avatarCropPath = resolvedMyPerson.avatarCropPath,
            faces = personFaces,
            onSave = { newHandle, newBio ->
                scope.launch {
                    if (newHandle.isNotBlank()) {
                        repository.renamePerson(resolvedMyPerson.personId, newHandle)
                    }
                    OnboardingPreferences.setCustomProfileBio(
                        context = context,
                        personId = resolvedMyPerson.personId,
                        bio = newBio
                    )
                }
                showEditProfileSheet = false
            },
            onSelectAvatarCrop = { cropPath ->
                scope.launch {
                    repository.updateAvatar(resolvedMyPerson.personId, cropPath)
                }
            },
            onChangeMeProfileClick = {
                showEditProfileSheet = false
                showChooseMyProfileSheet = true
            },
            onDismiss = { showEditProfileSheet = false }
        )
    }

    // 3. Multi-Merge Face Grid Sheet
    if (showMultiMergeSheet) {
        MultiMergeFaceGridSheet(
            people = visiblePeople,
            initialSelectedId = null,
            onMergeSelected = { selectedIds, targetName ->
                val targetId = selectedIds.firstOrNull() ?: return@MultiMergeFaceGridSheet
                val sourceIds = selectedIds.drop(1)
                scope.launch {
                    repository.mergeProfiles(
                        targetId = targetId,
                        sourceIds = sourceIds,
                        finalName = targetName
                    )
                    Toast
                        .makeText(context, "Merged into @$targetName", Toast.LENGTH_SHORT)
                        .show()
                }
                showMultiMergeSheet = false
            },
            onDismiss = { showMultiMergeSheet = false }
        )
    }

    // 4. Manage Hidden / @Other Profiles Sheet
    if (showManageHiddenSheet) {
        ManageHiddenProfilesSheet(
            hiddenPeople = hiddenPeople,
            onRestorePerson = { personId ->
                scope.launch {
                    repository.restorePersonFromOtherSql(personId)
                    Toast.makeText(context, "Profile restored from @Other", Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { showManageHiddenSheet = false }
        )
    }

    // 5. Heart Theme Picker Sheet
    if (showHeartThemeSheet) {
        HeartThemePickerSheet(
            currentTheme = heartTheme,
            onSelectTheme = { theme: HeartTheme ->
                OnboardingPreferences.setHeartTheme(context, theme)
            },
            onDismiss = { showHeartThemeSheet = false }
        )
    }

    // 6. Default Photo Aspect Ratio Sheet
    if (showCropModeSheet) {
        DefaultAspectRatioSheet(
            currentCropMode = defaultCropMode,
            onSelectCropMode = { cropMode ->
                OnboardingPreferences.setDefaultCropMode(context, cropMode)
                showCropModeSheet = false
            },
            onDismiss = { showCropModeSheet = false }
        )
    }

    // 7. Default InstaCard Template Sheet
    if (showInstaCardTemplateSheet) {
        DefaultInstaCardTemplateSheet(
            currentTemplateId = defaultInstaCardTemplateId,
            onSelectTemplate = { templateId ->
                OnboardingPreferences.setDefaultInstaCardTemplate(context, templateId)
                showInstaCardTemplateSheet = false
            },
            onDismiss = { showInstaCardTemplateSheet = false }
        )
    }

    // 8. Reset Confirmation Dialog
    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            containerColor = Color(0xFF1C1C1E),
            title = {
                Text(
                    text = "Reset all face clusters?",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "This will clear all named profiles and face groupings, then re-scan your gallery photos from scratch. Your actual device photos and Saved Collections will not be deleted.",
                    color = IgTextSecondary,
                    fontSize = 14.sp
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
                        color = IgRed,
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
        Surface(
            color = Color(0xFF121215),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                content()
            }
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
            fontSize = 13.sp
        )
        Text(
            text = value,
            color = IgTextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManageHiddenProfilesSheet(
    hiddenPeople: List<PersonEntity>,
    onRestorePerson: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF18181C),
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = "Hidden / @Other Profiles",
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Restore faces moved to @Other back into your main People Directory",
                color = IgTextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
            )

            if (hiddenPeople.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 36.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No profiles are currently hidden in @Other.",
                        color = IgTextSecondary,
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(hiddenPeople, key = { it.personId }) { person ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF222226))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF2C2C30)),
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
                                        tint = IgTextSecondary
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "@${person.name.removePrefix("@")}",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "${person.faceCount} photos",
                                    color = IgTextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .height(34.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(IgBlue)
                                    .bounceClick { onRestorePerson(person.personId) }
                                    .padding(horizontal = 14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Restore",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DefaultAspectRatioSheet(
    currentCropMode: Boolean,
    onSelectCropMode: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF18181C),
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Default Photo Aspect Ratio",
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            listOf(
                false to ("Original Uncropped (Fit)" to "Preserves full portrait/landscape framing without cropping"),
                true to ("Instagram 4:5 Crop" to "Fills feed cards with edge-to-edge 4:5 framing")
            ).forEach { (isCrop, info) ->
                val selected = currentCropMode == isCrop
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) Color(0xFF26262A) else Color.Transparent)
                        .clickable { onSelectCropMode(isCrop) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = info.first,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = info.second,
                            color = IgTextSecondary,
                            fontSize = 12.sp
                        )
                    }
                    if (selected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = IgBlue
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DefaultInstaCardTemplateSheet(
    currentTemplateId: String,
    onSelectTemplate: (String) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF18181C),
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Default InstaCard Snapshot Style",
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            listOf(
                InstaCardTemplate.OLED_DARK.name to ("OLED Dark Post" to "Pure black Instagram card with action row & likes"),
                InstaCardTemplate.CHAMELEON_GLASS.name to ("Chameleon Ambient Glass" to "Dynamic color palette extracted from your photo"),
                InstaCardTemplate.POLAROID_STAMP.name to ("Minimal Polaroid EXIF" to "Clean white gallery frame with camera metadata stamp")
            ).forEach { (id, info) ->
                val selected = currentTemplateId == id
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) Color(0xFF26262A) else Color.Transparent)
                        .clickable { onSelectTemplate(id) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = info.first,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = info.second,
                            color = IgTextSecondary,
                            fontSize = 12.sp
                        )
                    }
                    if (selected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = IgBlue
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
