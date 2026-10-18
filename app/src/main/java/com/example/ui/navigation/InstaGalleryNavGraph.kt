package com.example.ui.navigation

import android.content.Context
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.example.data.repository.GalleryRepository
import com.example.ui.screens.detail.PostDetailScreen
import com.example.ui.screens.home.HomeFeedScreen
import com.example.ui.screens.home.HomeViewModel
import com.example.ui.screens.people.PeopleDirectoryScreen
import com.example.ui.screens.people.PeopleViewModel
import com.example.ui.screens.permission.PermissionGateScreen
import com.example.ui.screens.permission.hasAnyMediaPermission
import com.example.ui.screens.profile.ProfileScreen
import com.example.ui.screens.profile.ProfileViewModel
import com.example.ui.screens.reels.ReelsScreen
import com.example.ui.screens.reels.ReelsViewModel
import com.example.ui.theme.IgBlack
import com.example.ui.theme.IgBorderNav
import com.example.ui.theme.IgSurfacePill
import com.example.ui.theme.IgTextPrimary
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

    val repository = GalleryRepository(context)
    val allPeople by repository.allPeople.collectAsStateWithLifecycle(initialValue = emptyList())
    val topPersonAvatar = allPeople.firstOrNull()?.avatarCropPath ?: ""

    val showBottomBar = currentRoute != Screen.PermissionGate.route

    val startDestination = if (hasAnyMediaPermission(context)) {
        Screen.Home.route
    } else {
        Screen.PermissionGate.route
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                InstaBottomNavigationBar(
                    currentRoute = currentRoute,
                    topPersonAvatar = topPersonAvatar,
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
            composable(Screen.PermissionGate.route) {
                PermissionGateScreen(
                    onPermissionsGranted = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.PermissionGate.route) { inclusive = true }
                        }
                    }
                )
            }

            composable(Screen.Home.route) {
                val homeViewModel: HomeViewModel = viewModel()
                HomeFeedScreen(
                    viewModel = homeViewModel,
                    onPersonClick = { personId ->
                        navController.navigate(Screen.Profile.createRoute(personId))
                    }
                )
            }

            composable(Screen.Reels.route) {
                val reelsViewModel: ReelsViewModel = viewModel()
                ReelsScreen(
                    viewModel = reelsViewModel,
                    onViewProfilesClick = { personId ->
                        navController.navigate(Screen.Profile.createRoute(personId))
                    }
                )
            }

            composable(Screen.People.route) {
                val peopleViewModel: PeopleViewModel = viewModel()
                PeopleDirectoryScreen(
                    viewModel = peopleViewModel,
                    onPersonClick = { personId ->
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
                    }
                )
            }

            composable(
                route = Screen.PostDetail.route,
                arguments = listOf(
                    navArgument("personId") { type = NavType.LongType },
                    navArgument("mediaId") { type = NavType.LongType }
                )
            ) { backStackEntry ->
                val personId = backStackEntry.arguments?.getLong("personId") ?: -1L
                val initialMediaId = backStackEntry.arguments?.getLong("mediaId") ?: 0L

                val profileViewModel: ProfileViewModel = viewModel(
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
                val activeList = if (uiState.selectedTab == 0) uiState.photos else uiState.videos

                PostDetailScreen(
                    mediaList = activeList,
                    initialMediaId = initialMediaId,
                    onBackClick = { navController.popBackStack() },
                    onPersonClick = { clickedPersonId ->
                        navController.navigate(Screen.Profile.createRoute(clickedPersonId))
                    },
                    onToggleFavorite = { id, _ -> },
                    onToggleBookmark = { id, _ -> }
                )
            }
        }
    }
}

@Composable
fun InstaBottomNavigationBar(
    currentRoute: String?,
    topPersonAvatar: String,
    onNavigate: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(IgBlack)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            HorizontalDivider(color = IgBorderNav, thickness = 1.dp)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                // Tab 1: Home (Solid white house when active, stroke when inactive)
                val isHomeActive = (currentRoute == Screen.Home.route)
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clickable { onNavigate(Screen.Home.route) }
                        .testTag("nav_tab_home"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isHomeActive) Icons.Filled.Home else Icons.Outlined.Home,
                        contentDescription = "Home",
                        tint = if (isHomeActive) Color.White else Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(26.dp)
                    )
                }

                // Tab 2: Reels
                val isReelsActive = (currentRoute == Screen.Reels.route)
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clickable { onNavigate(Screen.Reels.route) }
                        .testTag("nav_tab_reels"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Movie,
                        contentDescription = "Reels",
                        tint = if (isReelsActive) Color.White else Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(26.dp)
                    )
                }

                // Tab 3: People / Profile Avatar
                val isPeopleActive = (currentRoute == Screen.People.route || currentRoute?.startsWith("profile") == true)
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clickable { onNavigate(Screen.People.route) }
                        .testTag("nav_tab_people"),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .border(
                                width = if (isPeopleActive) 2.dp else 1.dp,
                                color = if (isPeopleActive) Color.White else Color.White.copy(alpha = 0.6f),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (topPersonAvatar.isNotBlank() && File(topPersonAvatar).exists()) {
                            AsyncImage(
                                model = File(topPersonAvatar),
                                contentDescription = "People Profile",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.Person,
                                contentDescription = "People Profile",
                                tint = if (isPeopleActive) Color.White else IgTextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
