package com.lunara.app.features.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lunara.app.R
import com.lunara.app.domain.model.ThemeMode
import com.lunara.app.domain.model.UserSettings
import com.lunara.app.domain.repository.SettingsRepository
import com.lunara.app.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the Updates row is currently showing. */
sealed interface UpdatesUiState {
    data object Idle : UpdatesUiState
    data object Checking : UpdatesUiState
    data object UpToDate : UpdatesUiState
    data object Failed : UpdatesUiState
    data class Available(
        val version: String,
        val notes: String,
        val downloadUrl: String
    ) : UpdatesUiState
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val updateChecker: UpdateChecker
) : ViewModel() {

    val settings: StateFlow<UserSettings> = settingsRepository.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserSettings())

    /** Installed version, e.g. `2.2.0`. */
    val currentVersion: String = updateChecker.currentVersion

    private val _updates = MutableStateFlow<UpdatesUiState>(UpdatesUiState.Idle)
    val updates: StateFlow<UpdatesUiState> = _updates.asStateFlow()

    fun toggleHighQuality(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.updateHighQuality(enabled) }
    }

    fun toggleAutoPlay(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.updateAutoPlay(enabled) }
    }

    fun toggleOfflineMode(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.updateOfflineMode(enabled) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.updateThemeMode(mode) }
    }

    fun checkForUpdates() {
        if (_updates.value == UpdatesUiState.Checking) return
        _updates.value = UpdatesUiState.Checking
        viewModelScope.launch {
            _updates.value = when (val result = updateChecker.check()) {
                is UpdateCheckResult.UpToDate -> UpdatesUiState.UpToDate
                is UpdateCheckResult.Failed -> UpdatesUiState.Failed
                is UpdateCheckResult.Available -> UpdatesUiState.Available(
                    version = result.version,
                    notes = result.notes,
                    downloadUrl = result.downloadUrl
                )
            }
        }
    }

    /** Clears a finished result so the row returns to its resting state. */
    fun dismissUpdateResult() {
        if (_updates.value != UpdatesUiState.Checking) _updates.value = UpdatesUiState.Idle
    }
}

@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsState()
    val updates by viewModel.updates.collectAsState()
    val context = LocalContext.current
    var showChangelog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = LunaraBackground,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = stringResource(R.string.settings_back),
                        tint = LunaraTextPrimary
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.settings_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = LunaraTextPrimary
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { SettingsSectionTitle(stringResource(R.string.settings_appearance)) }
            item {
                SettingsCard {
                    Text(
                        text = stringResource(R.string.settings_theme),
                        style = MaterialTheme.typography.titleMedium,
                        color = LunaraTextPrimary
                    )
                    Text(
                        text = stringResource(R.string.settings_theme_summary),
                        style = MaterialTheme.typography.bodyMedium,
                        color = LunaraTextSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    ThemeSelector(
                        selected = settings.themeMode,
                        onSelect = viewModel::setThemeMode
                    )
                }
            }

            item { SettingsSectionTitle(stringResource(R.string.settings_playback)) }
            item {
                SettingsCard {
                    SwitchRow(
                        title = stringResource(R.string.settings_high_quality),
                        summary = stringResource(R.string.settings_high_quality_summary),
                        checked = settings.highQualityAudio,
                        onCheckedChange = viewModel::toggleHighQuality
                    )
                    SettingsDivider()
                    SwitchRow(
                        title = stringResource(R.string.settings_autoplay),
                        summary = stringResource(R.string.settings_autoplay_summary),
                        checked = settings.autoPlay,
                        onCheckedChange = viewModel::toggleAutoPlay
                    )
                }
            }

            item { SettingsSectionTitle(stringResource(R.string.settings_storage)) }
            item {
                SettingsCard {
                    SwitchRow(
                        title = stringResource(R.string.settings_offline_mode),
                        summary = stringResource(R.string.settings_offline_mode_summary),
                        checked = settings.offlineModeOnly,
                        onCheckedChange = viewModel::toggleOfflineMode
                    )
                }
            }

            item { SettingsSectionTitle(stringResource(R.string.settings_about)) }
            item {
                SettingsCard {
                    AboutRow(version = viewModel.currentVersion)
                    SettingsDivider()
                    ActionRow(
                        icon = Icons.Default.History,
                        title = stringResource(R.string.settings_changelog),
                        summary = stringResource(R.string.settings_changelog_summary),
                        onClick = { showChangelog = true }
                    )
                }
            }

            item { SettingsSectionTitle(stringResource(R.string.settings_updates)) }
            item {
                SettingsCard {
                    UpdatesRow(
                        state = updates,
                        onCheck = viewModel::checkForUpdates,
                        onDismiss = viewModel::dismissUpdateResult,
                        onDownload = { url -> openRelease(context, url) }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }

    if (showChangelog) {
        ChangelogDialog(onDismiss = { showChangelog = false })
    }

    (updates as? UpdatesUiState.Available)?.let { available ->
        UpdateAvailableDialog(
            state = available,
            onDownload = {
                openRelease(context, available.downloadUrl)
                viewModel.dismissUpdateResult()
            },
            onDismiss = viewModel::dismissUpdateResult
        )
    }
}

/** Opens a release page or APK link in whatever app the user prefers for links. */
private fun openRelease(context: Context, url: String) {
    if (url.isBlank()) return
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

@Composable
private fun SettingsSectionTitle(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = LunaraTextMuted,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 14.dp, start = 4.dp, bottom = 2.dp)
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = LunaraSurface,
        border = BorderStroke(1.dp, LunaraBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 14.dp),
        thickness = 1.dp,
        color = LunaraBorder
    )
}

@Composable
private fun SwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = LunaraTextPrimary
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = LunaraTextSecondary
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = LunaraBackground,
                checkedTrackColor = LunaraAccent,
                uncheckedThumbColor = LunaraTextSecondary,
                uncheckedTrackColor = LunaraSurfaceElevated,
                uncheckedBorderColor = LunaraBorder
            )
        )
    }
}

@Composable
private fun ThemeSelector(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ThemeMode.values().forEach { mode ->
            FilterChip(
                selected = mode == selected,
                onClick = { onSelect(mode) },
                label = { Text(text = stringResource(mode.labelRes())) },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = LunaraSurfaceElevated,
                    labelColor = LunaraTextSecondary,
                    selectedContainerColor = LunaraAccent,
                    selectedLabelColor = LunaraBackground
                )
            )
        }
    }
}

private fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.settings_theme_system
    ThemeMode.LIGHT -> R.string.settings_theme_light
    ThemeMode.DARK -> R.string.settings_theme_dark
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    title: String,
    summary: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = LunaraSurface,
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = LunaraAccent)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = LunaraTextPrimary
                )
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = LunaraTextSecondary
                )
            }
        }
    }
}

@Composable
private fun AboutRow(version: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleLarge,
            color = LunaraTextPrimary,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = stringResource(R.string.settings_version, version.ifEmpty { "-" }),
            style = MaterialTheme.typography.bodyMedium,
            color = LunaraTextSecondary
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.settings_engine),
            style = MaterialTheme.typography.bodyMedium,
            color = LunaraTextMuted
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.settings_lyrics_credit),
            style = MaterialTheme.typography.bodyMedium,
            color = LunaraTextMuted
        )
    }
}

@Composable
private fun UpdatesRow(
    state: UpdatesUiState,
    onCheck: () -> Unit,
    onDismiss: () -> Unit,
    onDownload: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = Icons.Default.Update, contentDescription = null, tint = LunaraAccent)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_updates),
                    style = MaterialTheme.typography.titleMedium,
                    color = LunaraTextPrimary
                )
                Text(
                    text = stringResource(R.string.settings_updates_summary),
                    style = MaterialTheme.typography.bodyMedium,
                    color = LunaraTextSecondary
                )
            }
        }

        when (state) {
            is UpdatesUiState.Available -> {
                Spacer(modifier = Modifier.height(14.dp))
                StatusLine(
                    icon = Icons.Default.Download,
                    tint = LunaraAccent,
                    text = stringResource(R.string.settings_update_available, state.version)
                )
            }

            UpdatesUiState.Checking -> {
                Spacer(modifier = Modifier.height(14.dp))
                StatusLine(
                    icon = null,
                    tint = LunaraTextSecondary,
                    text = stringResource(R.string.settings_update_checking),
                    showProgress = true
                )
            }

            UpdatesUiState.UpToDate -> {
                Spacer(modifier = Modifier.height(14.dp))
                StatusLine(
                    icon = Icons.Default.CheckCircle,
                    tint = LunaraSuccess,
                    text = stringResource(R.string.settings_update_up_to_date)
                )
            }

            UpdatesUiState.Failed -> {
                Spacer(modifier = Modifier.height(14.dp))
                StatusLine(
                    icon = Icons.Default.ErrorOutline,
                    tint = LunaraError,
                    text = stringResource(R.string.settings_update_failed)
                )
            }

            UpdatesUiState.Idle -> Unit
        }

        Spacer(modifier = Modifier.height(14.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onCheck,
                enabled = state != UpdatesUiState.Checking,
                colors = ButtonDefaults.buttonColors(
                    containerColor = LunaraAccent,
                    contentColor = LunaraBackground
                )
            ) {
                Text(text = stringResource(R.string.settings_check_updates))
            }

            if (state is UpdatesUiState.Available) {
                OutlinedButton(onClick = { onDownload(state.downloadUrl) }) {
                    Text(text = stringResource(R.string.settings_update_download))
                }
            }

            if (state == UpdatesUiState.UpToDate || state == UpdatesUiState.Failed) {
                TextButton(onClick = onDismiss) {
                    Text(text = stringResource(R.string.common_ok), color = LunaraTextSecondary)
                }
            }
        }
    }
}

@Composable
private fun StatusLine(
    icon: ImageVector?,
    tint: Color,
    text: String,
    showProgress: Boolean = false
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (showProgress) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = LunaraAccent,
                strokeWidth = 2.dp
            )
        } else if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = tint)
    }
}

@Composable
private fun UpdateAvailableDialog(
    state: UpdatesUiState.Available,
    onDownload: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = LunaraSurface,
        title = {
            Text(
                text = stringResource(R.string.settings_update_available, state.version),
                color = LunaraTextPrimary
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 260.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                if (state.notes.isBlank()) {
                    Text(
                        text = stringResource(R.string.settings_updates_summary),
                        style = MaterialTheme.typography.bodyMedium,
                        color = LunaraTextSecondary
                    )
                } else {
                    Text(
                        text = stringResource(R.string.settings_release_notes),
                        style = MaterialTheme.typography.titleSmall,
                        color = LunaraAccent
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = state.notes,
                        style = MaterialTheme.typography.bodyMedium,
                        color = LunaraTextSecondary
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDownload,
                colors = ButtonDefaults.buttonColors(
                    containerColor = LunaraAccent,
                    contentColor = LunaraBackground
                )
            ) {
                Text(text = stringResource(R.string.settings_update_download))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.settings_update_later),
                    color = LunaraTextSecondary
                )
            }
        }
    )
}

@Composable
private fun ChangelogDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = LunaraSurface,
        title = {
            Text(
                text = stringResource(R.string.settings_changelog),
                color = LunaraTextPrimary
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                LunaraChangelog.forEachIndexed { index, release ->
                    if (index > 0) Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "v${release.version} - ${release.headline}",
                        style = MaterialTheme.typography.titleMedium,
                        color = LunaraAccent
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    release.highlights.forEach { highlight ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "\u2022",
                                style = MaterialTheme.typography.bodyMedium,
                                color = LunaraTextMuted
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = highlight,
                                style = MaterialTheme.typography.bodyMedium,
                                color = LunaraTextSecondary
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.common_close), color = LunaraAccent)
            }
        }
    )
}





