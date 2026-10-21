package com.dannx.walls

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.dannx.walls.ui.AppRoot
import com.dannx.walls.ui.theme.DannXTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels {
        MainViewModel.factory(application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val startTab = intent?.getStringExtra(EXTRA_TAB) ?: ""
        setContent {
            DannXTheme {
                AppRoot(viewModel = viewModel, startTab = startTab)
            }
        }
    }

    companion object {
        const val EXTRA_TAB = "dannx.extra.TAB"
    }
}
