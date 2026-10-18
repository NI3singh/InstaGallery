package com.example.ui.navigation

sealed class Screen(val route: String) {
    data object PermissionGate : Screen("permission_gate")
    data object Home : Screen("home_feed")
    data object Reels : Screen("reels")
    data object People : Screen("people_directory")
    data object Profile : Screen("profile/{personId}") {
        fun createRoute(personId: Long): String = "profile/$personId"
    }
    data object PostDetail : Screen("post_detail/{personId}/{mediaId}") {
        fun createRoute(personId: Long, mediaId: Long): String = "post_detail/$personId/$mediaId"
    }
}
