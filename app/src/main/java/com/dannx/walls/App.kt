package com.dannx.walls

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory

class App : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .crossfade(false)
            .bitmapFactoryMaxParallelism(4)
            .build()
}
