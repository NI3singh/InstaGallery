// InstaGallery v1.0
package com.example.ui.navigation

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilledExtendedIcons
import androidx.compose.material.icons.outlined.OutlinedExtendedIcons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import coil.compose.AsyncImage
import com.example.data.local.OnboardingPreferences
import com.example.data.repository.GalleryRepository
import com.example.ui.components.bounceClick
import com.example.ui.screens.detail.PostDetailScreen
import com.example.ui.screens.home.HomeFeedFilter
import com.example.ui.screens.home.HomeFeedScreen
import com.example.ui.screens.home.HomeViewModel
import com.example.ui.screens.onboarding.FaceScanLoadingScreen
import com.example.ui.screens.onboarding.OnboardingViewModel
import com.example.ui.screens.onboarding.ProfileSetupScreen
import com.example.ui.screens.people.PeopleDirectoryScreen
import com.example.ui.screens.people.PeopleViewModel
import com.example.ui.screens.permission.PermissionGateScreen
import com.example.ui.screens.permission.hasAnyMediaPermission
import com.example.ui.screens.profile.ProfileScreen
import com.example.ui.screens.profile.ProfileViewModel
import com.example.ui.screens.reels.ReelsScreen
import com.example.ui.screens.reels.ReelsViewModel
import com.example.ui.screens.saved.SavedCollectionsScreen
import com.example.ui.screens.settings.SettingsScreen
import com.example.ui.screens.story.StoryPlayerScreen
import com.example.ui.screens.story.StoryPlayerViewModel
import com.example.ui.theme.IgBlack
import com.example.ui.theme.IgTextSecondary
import java.io.File

@Composable
fun InstaGalleryNavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val repository = remember(context) { GalleryRepository(context) }
    val allPeople by repository.allPeople.collectAsStateWithLifecycle(initialValue = emptyList())
    val myProfilePersonId by OnboardingPreferences.myProfilePersonIdFlow.collectAsStateWithLifecycle()
    val myProfilePerson = remember(allPeople, myProfilePersonId) {
        allPeople.firstOrNull { it.personId == myProfilePersonId } ?: allPeople.firstOrNull()
    }
    val topPersonAvatar = myProfilePerson?.avatarCropPath ?: ""

    val onboardingViewModel: OnboardingViewModel = viewModel()
    val homeViewModel: HomeViewModel = viewModel()
    val homeUiState by homeViewModel.uiState.collectAsStateWithLifecycle()

    val showBottomBar = currentRoute != Screen.PermissionGate.route &&
        currentRoute != Screen.FaceScanning.route &&
        currentRoute != Screen.ProfileSetup.route &&
        currentRoute != Screen.Settings.route &&
        currentRoute?.startsWith("story_player") != true

    val startDestination = remember {
        when {
            !hasAnyMediaPermission(context) -> Screen.PermissionGate.route
            !OnboardingPreferences.isInitialScanDone(context) -> Screen.FaceScanning.route
            !OnboardingPreferences.isProfileSetupDone(context) -> Screen.ProfileSetup.route
            else -> Screen.Home.route
        }
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                InstaBottomNavigationBar(
                    currentRoute = currentRoute,
                    isSavedFilterActive = currentRoute == Screen.SavedCollections.route ||
                        (currentRoute == Screen.Home.route && homeUiState.isFilteringBookmarks),
                    topPersonAvatar = topPersonAvatar,
                    onHomeClick = {
                        homeViewModel.setFilter(HomeFeedFilter.ALL)
                        if (currentRoute != Screen.Home.route) {
                            navController.navigate(Screen.Home.route) {
                                popUpTo(Screen.Home.route) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    },
                    onSavedClick = {
                        if (currentRoute != Screen.SavedCollections.route) {
                            navController.navigate(Screen.SavedCollections.route) {
                                popUpTo(Screen.Home.route) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    },
                    onNavigate = { targetRoute ->
                        navController.navigate(targetRoute) {
                            popUpTo(Screen.Home.route) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        },
        containerColor = IgBlack,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(
                route = Screen.PermissionGate.route,
                enterTransition = {
                    fadeIn(tween(380)) + scaleIn(
                        initialScale = 0.94f,
                        animationSpec = tween(380, easing = FastOutSlowInEasing)
                    )
                },
                exitTransition = {
                    fadeOut(tween(280)) + scaleOut(
                        targetScale = 1.04f,
                        animationSpec = tween(280)
                    )
                }
            ) {
                PermissionGateScreen(
                    onboardingViewModel = onboardingViewModel,
                    onScanCompleted = {
                        navController.navigate(Screen.ProfileSetup.route) {
                            popUpTo(Screen.PermissionGate.route) { inclusive = true }
                        }
                    },
                    onPermissionsGranted = {
                        val nextRoute = when {
                            !OnboardingPreferences.isInitialScanDone(context) -> Screen.FaceScanning.route
                            !OnboardingPreferences.isProfileSetupDone(context) -> Screen.ProfileSetup.route
                            else -> Screen.Home.route
                        }
                        navController.navigate(nextRoute) {
                            popUpTo(Screen.PermissionGate.route) { inclusive = true }
                        }
                    }
                )
            }

            composable(
                route = Screen.FaceScanning.route,
                enterTransition = {
                    fadeIn(tween(380)) + scaleIn(
                        initialScale = 0.94f,
                        animationSpec = tween(380, easing = FastOutSlowInEasing)
                    )
                },
                exitTransition = {
                    fadeOut(tween(280)) + scaleOut(
                        targetScale = 1.04f,
                        animationSpec = tween(280)
                    )
                }
            ) {
                FaceScanLoadingScreen(
                    viewModel = onboardingViewModel,
                    onScanCompleted = {
                        navController.navigate(Screen.ProfileSetup.route) {
                            popUpTo(Screen.FaceScanning.route) { inclusive = true }
                        }
                    }
                )
            }

            composable(
                route = Screen.ProfileSetup.route,
                enterTransition = {
                    fadeIn(tween(380)) + scaleIn(
                        initialScale = 0.94f,
                        animationSpec = tween(380, easing = FastOutSlowInEasing)
                    )
                },
                exitTransition = {
                    fadeOut(tween(280)) + scaleOut(
                        targetScale = 1.04f,
                        animationSpec = tween(280)
                    )
                }
            ) {
                ProfileSetupScreen(
                    viewModel = onboardingViewModel,
                    onSetupCompleted = {
                        val popped = navController.popBackStack()
                        if (!popped) {
                            navController.navigate(Screen.Home.route) {
                                popUpTo(Screen.ProfileSetup.route) { inclusive = true }
                            }
                        }
                    }
                )
            }

            composable(
                route = Screen.Home.route,
                enterTransition = {
                    fadeIn(tween(380)) + scaleIn(
                        initialScale = 0.94f,
                        animationSpec = tween(380, easing = FastOutSlowInEasing)
                    )
                },
                exitTransition = {
                    fadeOut(tween(280)) + scaleOut(
                        targetScale = 1.04f,
                        animationSpec = tween(280)
                    )
                }
            ) {
                HomeFeedScreen(
                    viewModel = homeViewModel,
                    onPersonClick = { personId ->
                        navController.navigate(Screen.Profile.createRoute(personId))
                    },
                    onOpenStory = { personId ->
                        navController.navigate(Screen.StoryPlayer.createRoute(personId))
                    },
                    onReviewNewPeopleClick = {
                        onboardingViewModel.prepareForPostOnboardingReview()
                        navController.navigate(Screen.ProfileSetup.route)
                    },
                    onOpenSavedCollections = {
                        navController.navigate(Screen.SavedCollections.route) {
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable(Screen.SavedCollections.route) {
                SavedCollectionsScreen(
                    onBackClick = { navController.popBackStack() },
                    onPersonClick = { personId ->
                        navController.navigate(Screen.Profile.createRoute(personId))
                    }
                )
            }

            composable(Screen.Reels.route) {
                val reelsViewModel: ReelsViewModel = viewModel()
                ReelsScreen(
                    viewModel = reelsViewModel
                )
            }

            composable(Screen.People.route) {
                val peopleViewModel: PeopleViewModel = viewModel()
                PeopleDirectoryScreen(
                    viewModel = peopleViewModel,
                    onPersonClick = { personId ->
                        navController.navigate(Screen.Profile.createRoute(personId))
                    },
                    onOpenSetupClick = {
                        onboardingViewModel.prepareForPostOnboardingReview()
                        navController.navigate(Screen.ProfileSetup.route)
                    },
                    onRescanGalleryClick = {
                        onboardingViewModel.triggerFullRescan()
                        navController.navigate(Screen.FaceScanning.route)
                    },
                    onBackClick = { navController.popBackStack() },
                    onOpenSettingsClick = { navController.navigate(Screen.Settings.route) }
                )
            }

            composable(Screen.MyProfile.route) {
                val profileViewModel: ProfileViewModel = viewModel(
                    key = "my_profile_root",
                    factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                            return ProfileViewModel(
                                context.applicationContext as android.app.Application,
                                -2L
                            ) as T
                        }
                    }
                )
                val uiState by profileViewModel.uiState.collectAsStateWithLifecycle()
                val activePersonId = uiState.person?.personId ?: -1L

                ProfileScreen(
                    viewModel = profileViewModel,
                    onBackClick = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Home.route) { inclusive = false }
                            launchSingleTop = true
                        }
                    },
                    onMediaClick = { mediaId ->
                        navController.navigate(Screen.PostDetail.createRoute(activePersonId, mediaId))
                    },
                    onMergedToPerson = { mergedPersonId ->
                        navController.navigate(Screen.Profile.createRoute(mergedPersonId))
                    },
                    onPersonClick = { targetPersonId ->
                        navController.navigate(Screen.Profile.createRoute(targetPersonId))
                    },
                    onOpenStory = { targetPersonId ->
                        navController.navigate(Screen.StoryPlayer.createRoute(targetPersonId))
                    },
                    onOpenPeopleDirectory = {
                        navController.navigate(Screen.People.route)
                    },
                    onOpenSettings = {
                        navController.navigate(Screen.Settings.route)
                    }
                )
            }

            composable(Screen.Settings.route) {
                SettingsScreen(
                    onBackClick = { navController.popBackStack() },
                    onOpenProfileSetup = {
                        onboardingViewModel.prepareForPostOnboardingReview()
                        navController.navigate(Screen.ProfileSetup.route)
                    },
                    onResetAndRescanTriggered = {
                        onboardingViewModel.triggerFullRescan()
                        navController.navigate(Screen.FaceScanning.route) {
                            popUpTo(Screen.Home.route) { inclusive = false }
                        }
                    },
                    onNavigateToProfile = { personId ->
                        navController.navigate(Screen.Profile.createRoute(personId))
                    }
                )
            }

            composable(
                route = Screen.Profile.route,
                arguments = listOf(navArgument("personId") { type = NavType.LongType })
            ) { backStackEntry ->
                val personId = backStackEntry.arguments?.getLong("personId") ?: -1L
                val profileViewModel: ProfileViewModel = viewModel(
                    key = "profile_$personId",
                    factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                            return ProfileViewModel(
                                context.applicationContext as android.app.Application,
                                personId
                            ) as T
                        }
                    }
                )
                ProfileScreen(
                    viewModel = profileViewModel,
                    onBackClick = { navController.popBackStack() },
                    onMediaClick = { mediaId ->
                        navController.navigate(Screen.PostDetail.createRoute(personId, mediaId))
                    },
                    onMergedToPerson = { mergedPersonId ->
                        navController.navigate(Screen.Profile.createRoute(mergedPersonId)) {
                            popUpTo(Screen.Profile.route) { inclusive = true }
                        }
                    },
                    onPersonClick = { targetPersonId ->
                        navController.navigate(Screen.Profile.createRoute(targetPersonId))
                    },
                    onOpenStory = { targetPersonId ->
                        navController.navigate(Screen.StoryPlayer.createRoute(targetPersonId))
                    },
                    onOpenPeopleDirectory = {
                        navController.navigate(Screen.People.route)
                    },
                    onOpenSettings = {
                        navController.navigate(Screen.Settings.route)
                    }
                )
            }

            composable(
                route = Screen.PostDetail.route,
                arguments = listOf(
                    navArgument("personId") { type = NavType.LongType },
                    navArgument("initialMediaId") { type = NavType.LongType }
                )
            ) { backStackEntry ->
                val personId = backStackEntry.arguments?.getLong("personId") ?: -1L
                val initialMediaId = backStackEntry.arguments?.getLong("initialMediaId") ?: 0L

                val profileViewModel: ProfileViewModel = viewModel(
                    key = "post_detail_$personId",
                    factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                            return ProfileViewModel(
                                context.applicationContext as android.app.Application,
                                personId
                            ) as T
                        }
                    }
                )

                val uiState by profileViewModel.uiState.collectAsStateWithLifecycle()

                PostDetailScreen(
                    mediaList = uiState.photos,
                    initialMediaId = initialMediaId,
                    onBackClick = { navController.popBackStack() },
                    onPersonClick = { clickedPersonId ->
                        navController.navigate(Screen.Profile.createRoute(clickedPersonId))
                    },
                    onToggleFavorite = { id, current -> profileViewModel.toggleFavorite(id, current) },
                    onToggleBookmark = { id, current -> profileViewModel.toggleBookmark(id, current) },
                    onUpdateCaption = { id, newCaption -> profileViewModel.updateCaption(id, newCaption) },
                    allPeople = uiState.allPeople,
                    onTagPerson = { mediaId, targetPersonId ->
                        profileViewModel.tagPersonInMedia(mediaId, targetPersonId)
                    },
                    onUntagPerson = { mediaId, targetPersonId ->
                        profileViewModel.removePhotoFromPerson(mediaId, targetPersonId)
                    }
                )
            }

            composable(
                route = Screen.StoryPlayer.route,
                arguments = listOf(navArgument("personId") { type = NavType.LongType }),
                enterTransition = {
                    scaleIn(initialScale = 0.88f, animationSpec = tween(260, easing = FastOutSlowInEasing)) +
                        fadeIn(tween(260))
                },
                exitTransition = {
                    scaleOut(targetScale = 0.88f, animationSpec = tween(220)) +
                        fadeOut(tween(220))
                }
            ) { backStackEntry ->
                val personId = backStackEntry.arguments?.getLong("personId") ?: -1L
                val storyViewModel: StoryPlayerViewModel = viewModel(
                    key = "story_player_$personId",
                    factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                            return StoryPlayerViewModel(
                                context.applicationContext as android.app.Application,
                                personId
                            ) as T
                        }
                    }
                )

                StoryPlayerScreen(
                    viewModel = storyViewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToProfile = { pid ->
                        navController.navigate(Screen.Profile.createRoute(pid))
                    }
                )
            }
        }
    }
}

@Composable
fun InstaBottomNavigationBar(
    currentRoute: String?,
    isSavedFilterActive: Boolean,
    topPersonAvatar: String,
    onHomeClick: () -> Unit,
    onSavedClick: () -> Unit,
    onNavigate: (String) -> Unit
) {
    Surface(
        color = Color.Black,
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            HorizontalDivider(color = Color(0xFF1A1A1E), thickness = 0.5.dp)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Slot 1: Home (Screen.Home)
                val isHomeActive = (currentRoute == Screen.Home.route && !isSavedFilterActive)
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .bounceClick { onHomeClick() }
                        .testTag("nav_tab_home"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isHomeActive) FilledExtendedIcons.Home else OutlinedExtendedIcons.Home,
                        contentDescription = "Home",
                        tint = if (isHomeActive) Color.White else Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(25.dp)
                    )
                }

                // Slot 2: Reels (Screen.Reels)
                val isReelsActive = (currentRoute == Screen.Reels.route)
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .bounceClick { onNavigate(Screen.Reels.route) }
                        .testTag("nav_tab_reels"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isReelsActive) FilledExtendedIcons.Movie else OutlinedExtendedIcons.Movie,
                        contentDescription = "Reels",
                        tint = if (isReelsActive) Color.White else Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Slot 3: Saved / Favorites
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .bounceClick { onSavedClick() }
                        .testTag("nav_tab_saved"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isSavedFilterActive) FilledExtendedIcons.Bookmark else OutlinedExtendedIcons.BookmarkBorder,
                        contentDescription = "Saved",
                        tint = if (isSavedFilterActive) Color.White else Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(25.dp)
                    )
                }

                // Slot 4: My Profile & People (Screen.MyProfile)
                val isProfileActive = currentRoute == Screen.MyProfile.route ||
                    currentRoute == Screen.People.route ||
                    currentRoute == Screen.Settings.route ||
                    currentRoute?.startsWith("profile") == true
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .bounceClick { onNavigate(Screen.MyProfile.route) }
                        .testTag("nav_tab_people"),
                    contentAlignment = Alignment.Center
                ) {
                    val outerRingSize = if (isProfileActive) 34.dp else 27.dp
                    Box(
                        modifier = Modifier
                            .size(outerRingSize)
                            .clip(CircleShape)
                            .then(
                                if (isProfileActive) {
                                    Modifier
                                        .border(2.dp, Color.White, CircleShape)
                                        .padding(3.5.dp) // 2.dp ring + 1.5.dp black inner gap
                                } else {
                                    Modifier.border(1.dp, Color.White.copy(alpha = 0.45f), CircleShape)
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (topPersonAvatar.isNotBlank() && File(topPersonAvatar).exists()) {
                            AsyncImage(
                                model = File(topPersonAvatar),
                                contentDescription = "My Profile",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(27.dp)
                                    .clip(CircleShape)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(27.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF1A1A1E)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Person,
                                    contentDescription = "My Profile",
                                    tint = if (isProfileActive) Color.White else IgTextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

