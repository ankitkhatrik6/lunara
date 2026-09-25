package com.dhunya.app.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dhunya.app.domain.model.UserSettings
import com.dhunya.app.domain.repository.SettingsRepository
import com.dhunya.app.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val settings: StateFlow<UserSettings> = settingsRepository.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserSettings())

    fun toggleHighQuality(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.updateHighQuality(enabled) }
    }

    fun toggleAutoPlay(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.updateAutoPlay(enabled) }
    }

    fun toggleOfflineMode(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.updateOfflineMode(enabled) }
    }
}

@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsState()

    Scaffold(
        containerColor = DhunyaBackground,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = DhunyaTextPrimary
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.titleLarge,
                    color = DhunyaTextPrimary
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = "Playback",
                    style = MaterialTheme.typography.titleMedium,
                    color = DhunyaAccent,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "High Quality Audio", style = MaterialTheme.typography.titleMedium, color = DhunyaTextPrimary)
                        Text(text = "Stream at 320kbps when connected to Wi-Fi", style = MaterialTheme.typography.bodyMedium, color = DhunyaTextSecondary)
                    }
                    Switch(
                        checked = settings.highQualityAudio,
                        onCheckedChange = viewModel::toggleHighQuality,
                        colors = SwitchDefaults.colors(checkedThumbColor = DhunyaAccent, checkedTrackColor = DhunyaSurfaceElevated)
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Autoplay Similar Tracks", style = MaterialTheme.typography.titleMedium, color = DhunyaTextPrimary)
                        Text(text = "Keep music playing when current queue finishes", style = MaterialTheme.typography.bodyMedium, color = DhunyaTextSecondary)
                    }
                    Switch(
                        checked = settings.autoPlay,
                        onCheckedChange = viewModel::toggleAutoPlay,
                        colors = SwitchDefaults.colors(checkedThumbColor = DhunyaAccent, checkedTrackColor = DhunyaSurfaceElevated)
                    )
                }
            }

            item {
                Text(
                    text = "Storage & Downloads",
                    style = MaterialTheme.typography.titleMedium,
                    color = DhunyaAccent,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Offline Mode Only", style = MaterialTheme.typography.titleMedium, color = DhunyaTextPrimary)
                        Text(text = "Only play downloaded songs to save mobile data", style = MaterialTheme.typography.bodyMedium, color = DhunyaTextSecondary)
                    }
                    Switch(
                        checked = settings.offlineModeOnly,
                        onCheckedChange = viewModel::toggleOfflineMode,
                        colors = SwitchDefaults.colors(checkedThumbColor = DhunyaAccent, checkedTrackColor = DhunyaSurfaceElevated)
                    )
                }
            }

            item {
                Text(
                    text = "About Dhunya",
                    style = MaterialTheme.typography.titleMedium,
                    color = DhunyaAccent,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(text = "Dhunya Music Player v1.0.0", style = MaterialTheme.typography.titleMedium, color = DhunyaTextPrimary)
                    Text(text = "Engineered with Jetpack Compose, Material 3, AndroidX Media3 ExoPlayer, and Clean Architecture.", style = MaterialTheme.typography.bodyMedium, color = DhunyaTextSecondary)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = "Lyrics powered by LRCLIB API.", style = MaterialTheme.typography.labelSmall, color = DhunyaTextMuted)
                }
            }
        }
    }
}
