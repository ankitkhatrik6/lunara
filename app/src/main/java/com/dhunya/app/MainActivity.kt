package com.dhunya.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.dhunya.app.navigation.DhunyaApp
import com.dhunya.app.player.PlayerManager
import com.dhunya.app.ui.theme.DhunyaTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var playerManager: PlayerManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DhunyaTheme {
                DhunyaApp(playerManager = playerManager)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
