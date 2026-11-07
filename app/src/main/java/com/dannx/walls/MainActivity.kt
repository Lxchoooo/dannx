package com.dannx.walls

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dannx.walls.data.ThemeMode
import com.dannx.walls.ui.AppRoot
import com.dannx.walls.ui.LocalWindowSizeClass
import com.dannx.walls.ui.theme.DannXTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels {
        MainViewModel.factory(application)
    }

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val startTab = intent?.getStringExtra(EXTRA_TAB) ?: ""
        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            val darkTheme = when (state.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            val windowSizeClass = calculateWindowSizeClass(this)
            DannXTheme(darkTheme = darkTheme, liteMode = state.liteMode) {
                CompositionLocalProvider(LocalWindowSizeClass provides windowSizeClass) {
                    AppRoot(viewModel = viewModel, startTab = startTab)
                }
            }
        }
    }

    companion object {
        const val EXTRA_TAB = "dannx.extra.TAB"
    }
}
