// InstaGallery v1.0
package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.example.data.repository.GalleryRepository
import com.example.ui.navigation.InstaGalleryNavGraph
import com.example.ui.screens.permission.hasAnyMediaPermission
import com.example.ui.theme.InstaGalleryTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val galleryRepository by lazy {
        GalleryRepository(applicationContext)
    }

    private var lastResumeSyncTimestampMs: Long = 0L

    private val resumeSyncObserver = LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_RESUME) {
            if (!hasAnyMediaPermission(this)) return@LifecycleEventObserver
            val now = System.currentTimeMillis()
            if (now - lastResumeSyncTimestampMs >= 500L) {
                lastResumeSyncTimestampMs = now
                galleryRepository.startMediaStoreObserver()
                galleryRepository.scheduleMediaUriTriggerWorker()
                lifecycleScope.launch {
                    galleryRepository.syncOnAppResume()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        galleryRepository.startMediaStoreObserver()
        if (hasAnyMediaPermission(this)) {
            galleryRepository.scheduleMediaUriTriggerWorker()
        }
        lifecycle.addObserver(resumeSyncObserver)
        setContent {
            InstaGalleryTheme {
                val navController = rememberNavController()
                InstaGalleryNavGraph(navController = navController)
            }
        }
    }

    override fun onDestroy() {
        lifecycle.removeObserver(resumeSyncObserver)
        galleryRepository.stopMediaStoreObserver()
        super.onDestroy()
    }
}
