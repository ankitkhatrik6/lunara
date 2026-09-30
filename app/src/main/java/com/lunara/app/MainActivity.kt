package com.lunara.app

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.lunara.app.core.state.SongStates
import com.lunara.app.features.splash.SplashGate
import com.lunara.app.navigation.LunaraApp
import com.lunara.app.player.MediaSessionService
import com.lunara.app.player.PlayerManager
import com.lunara.app.ui.theme.LunaraTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var playerManager: PlayerManager

    @Inject
    lateinit var songStates: SongStates

    /**
     * Connection to [MediaSessionService].
     *
     * Building the controller starts and binds the service, which is what creates the
     * MediaSession. Without it Android never shows the media notification, the quick settings
     * player or the lock screen / Bluetooth controls.
     */
    private var mediaController: MediaController? = null
    private var controllerReleased = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ensureNotificationPermission()
        connectToPlaybackService()
        setContent {
            LunaraTheme {
                // Branded start-up screen; the app composes underneath it so startup work overlaps
                // with the animation (see SplashGate).
                SplashGate {
                    LunaraApp(playerManager = playerManager, songStates = songStates)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // The service stops itself when the task is swiped away (that is what stops playback),
        // which also disconnects this controller. Reconnect so the notification works again.
        val controller = mediaController
        if (controller == null || !controller.isConnected) {
            controller?.release()
            mediaController = null
            controllerReleased = false
            connectToPlaybackService()
        }
    }

    override fun onDestroy() {
        controllerReleased = true
        mediaController?.release()
        mediaController = null
        super.onDestroy()
    }

    /**
     * Android 13+ silently drops notifications — including the media player — unless the user
     * granted `POST_NOTIFICATIONS`, so it is requested up front.
     */
    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                NOTIFICATION_PERMISSION_REQUEST
            )
        }
    }

    private fun connectToPlaybackService() {
        val token = SessionToken(this, ComponentName(this, MediaSessionService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        future.addListener(
            {
                val controller = runCatching { future.get() }.getOrNull()
                if (controller == null || controllerReleased) {
                    controller?.release()
                } else {
                    mediaController = controller
                }
            },
            ContextCompat.getMainExecutor(this)
        )
    }

    private companion object {
        const val NOTIFICATION_PERMISSION_REQUEST = 1001
    }
}
