package com.pion.psremote

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.pion.psremote.core.ui.theme.PsRemoteTheme
import com.pion.psremote.navigation.AppNavHost

/**
 * Hosts the game picker and the demo it opens (LLM.md §7). Landscape is locked in the manifest (D1).
 *
 * Immersive: the system bars are hidden so the video and the controller own the whole screen, and come
 * back only transiently on a swipe from the edge. The screen stays on for as long as this is visible,
 * because a demo has long stretches of watching with no touch.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        setContent {
            PsRemoteTheme {
                AppNavHost()
            }
        }
    }
}
