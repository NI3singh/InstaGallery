// InstaGallery v1.0
package com.example

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import com.example.data.local.AppDatabase
import com.example.ml.FaceClusterManager

class InstaGalleryApp : Application(), ImageLoaderFactory {

    val database: AppDatabase by lazy {
        AppDatabase.getInstance(this)
    }

    val faceClusterManager: FaceClusterManager by lazy {
        FaceClusterManager(this, database)
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(VideoFrameDecoder.Factory())
            }
            .crossfade(true)
            .build()
    }
}
