package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.example.data.local.AppDatabase
import com.example.data.media.MediaStoreScanner
import com.example.ui.navigation.InstaGalleryNavGraph
import com.example.ui.screens.permission.hasAnyMediaPermission
import com.example.ui.theme.InstaGalleryTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val scanner by lazy {
        MediaStoreScanner(this, AppDatabase.getInstance(this).mediaDao())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            InstaGalleryTheme {
                val navController = rememberNavController()
                InstaGalleryNavGraph(navController = navController)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (hasAnyMediaPermission(this)) {
            lifecycleScope.launch {
                scanner.scanAndReconcile()
            }
        }
    }
}
