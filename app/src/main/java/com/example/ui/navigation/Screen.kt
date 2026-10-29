// InstaGallery v1.0
package com.example.ui.navigation

sealed class Screen(val route: String) {
    data object PermissionGate : Screen("permission_gate")
    data object FaceScanning : Screen("face_scanning")
    data object ProfileSetup : Screen("profile_setup")
    data object Home : Screen("home_feed")
    data object Reels : Screen("reels")
    data object People : Screen("people_directory")
    data object Profile : Screen("profile/{personId}") {
        fun createRoute(personId: Long): String = "profile/$personId"
    }
    data object PostDetail : Screen("post_detail/{personId}/{initialMediaId}") {
        fun createRoute(personId: Long, initialMediaId: Long): String = "post_detail/$personId/$initialMediaId"
    }
    data object StoryPlayer : Screen("story_player/{personId}") {
        fun createRoute(personId: Long): String = "story_player/$personId"
    }
    data object SavedCollections : Screen("saved_collections")
    data object MyProfile : Screen("my_profile")
    data object Settings : Screen("settings")
}

