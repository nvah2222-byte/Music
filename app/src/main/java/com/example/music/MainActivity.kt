package com.example.music

import android.content.ComponentName
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.music.data.SettingsStore
import com.example.music.ui.MainScreen
import com.example.music.ui.theme.MusicTheme
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors

class MainActivity : ComponentActivity() {
    private var controller by mutableStateOf<MediaController?>(null)
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var buildInProgress = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val settingsStore = SettingsStore(this)

        connectController()

        setContent {
            val settings by settingsStore.settings.collectAsState()
            MusicTheme(
                darkTheme = when (settings.darkMode) {
                    1 -> false
                    2 -> true
                    else -> isSystemInDarkTheme()
                },
                dynamicColor = settings.dynamicColor,
                accentHue = settings.accentHue
            ) {
                MainScreen(controller, settingsStore)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Сервис мог быть убит системой, пока приложение было свёрнуто, — переподключаемся.
        if (controller?.isConnected != true) connectController()
    }

    /** Подключаемся к PlaybackService; повторный вызов безопасен. */
    private fun connectController() {
        if (buildInProgress || controller?.isConnected == true) return
        buildInProgress = true
        controllerFuture?.let { MediaController.releaseFuture(it) }
        val sessionToken = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, sessionToken).buildAsync()
        controllerFuture = future
        future.addListener({
            buildInProgress = false
            controller = try {
                future.get()
            } catch (e: Exception) {
                null
            }
        }, MoreExecutors.directExecutor())
    }

    override fun onDestroy() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        controller = null
        super.onDestroy()
    }
}
