/**
 * Lunara Project (C) 2026
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See NOTICE for contributors
 *
 * Minimalist Spotify-Inspired Settings:
 * Flat surfaces, high-contrast typography, Spotify green accents.
 * No emojis, no sparkles, no gradients. Fast and smooth 60/120fps.
 */

package com.lunara.app.ui.screens.settings

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.lunara.app.BuildConfig
import com.lunara.app.LocalChangelogState
import com.lunara.app.LocalPlayerAwareWindowInsets
import com.lunara.app.R
import com.lunara.app.constants.DarkModeKey
import com.lunara.app.constants.DynamicThemeKey
import com.lunara.app.constants.PureBlackKey
import com.lunara.app.constants.PureBlackMiniPlayerKey
import com.lunara.app.ui.component.IconButton
import com.lunara.app.ui.component.ReleaseNotesCard
import com.lunara.app.ui.theme.SpotifyBlack
import com.lunara.app.ui.theme.SpotifyCardHover
import com.lunara.app.ui.theme.SpotifyCardSurface
import com.lunara.app.ui.theme.SpotifyDivider
import com.lunara.app.ui.theme.SpotifyElevatedSurface
import com.lunara.app.ui.theme.SpotifyGreen
import com.lunara.app.ui.theme.SpotifyTextMuted
import com.lunara.app.ui.theme.SpotifyTextPrimary
import com.lunara.app.ui.theme.SpotifyTextSecondary
import com.lunara.app.ui.utils.backToMain
import com.lunara.app.utils.Updater
import com.lunara.app.utils.rememberEnumPreference
import com.lunara.app.utils.rememberPreference

private class SettingRow(
    val icon: Int,
    val title: String,
    val subtitle: String,
    val accent: Color = SpotifyGreen,
    val badge: Boolean = false,
    val onClick: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    latestVersionName: String,
) {
    val showChangelog = LocalChangelogState.current
    val hasUpdate = BuildConfig.UPDATER_AVAILABLE &&
        Updater.isUpdateAvailable(BuildConfig.VERSION_NAME, latestVersionName)
    var query by rememberSaveable { mutableStateOf("") }

    val (darkMode, onDarkModeChange) = rememberEnumPreference(DarkModeKey, DarkMode.AUTO)
    val (dynamicTheme, onDynamicThemeChange) = rememberPreference(DynamicThemeKey, true)
    val (pureBlack, setPureBlack) = rememberPreference(PureBlackKey, false)
    val (_, setPureBlackMiniPlayer) = rememberPreference(PureBlackMiniPlayerKey, false)
    val onPureBlackChange: (Boolean) -> Unit = { enabled ->
        setPureBlack(enabled)
        setPureBlackMiniPlayer(enabled)
    }

    val groups: List<Pair<String, List<SettingRow>>> = listOf(
        stringResource(R.string.settings_group_personalize) to listOf(
            SettingRow(
                icon = R.drawable.palette,
                title = stringResource(R.string.appearance),
                subtitle = stringResource(R.string.hint_appearance),
            ) {
                navController.navigate("settings/appearance")
            },
            SettingRow(
                icon = R.drawable.contrast,
                title = stringResource(R.string.look_and_feel),
                subtitle = stringResource(R.string.look_and_feel_desc),
            ) {
                navController.navigate("settings/appearance/look_and_feel")
            },
        ),
        stringResource(R.string.storage) to listOf(
            SettingRow(
                icon = R.drawable.storage,
                title = stringResource(R.string.storage),
                subtitle = stringResource(R.string.hint_storage),
            ) {
                navController.navigate("settings/storage")
            },
            SettingRow(
                icon = R.drawable.security,
                title = stringResource(R.string.privacy),
                subtitle = stringResource(R.string.privacy),
            ) {
                navController.navigate("settings/privacy")
            },
        ),
        stringResource(R.string.settings_group_about) to buildList {
            add(
                SettingRow(
                    icon = R.drawable.info,
                    title = stringResource(R.string.about),
                    subtitle = stringResource(R.string.hint_about),
                    badge = hasUpdate,
                ) {
                    navController.navigate("settings/about")
                },
            )
            add(
                SettingRow(
                    icon = R.drawable.newspaper,
                    title = stringResource(R.string.changelog),
                    subtitle = stringResource(R.string.hint_changelog),
                ) {
                    showChangelog.value = true
                },
            )
        },
    )

    Column(
        Modifier
            .fillMaxSize()
            .background(SpotifyBlack)
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top)))
        Spacer(Modifier.height(56.dp))

        // Minimal Spotify Brand Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(SpotifyElevatedSurface)
                .clickable { navController.navigate("settings/about") }
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(SpotifyCardSurface),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.lunara_logo),
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.app_name),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = SpotifyTextPrimary,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "v${BuildConfig.VERSION_NAME}",
                    fontSize = 13.sp,
                    color = SpotifyTextSecondary,
                )
            }
            Icon(
                painter = painterResource(R.drawable.navigate_next),
                contentDescription = null,
                tint = SpotifyTextSecondary,
                modifier = Modifier.size(22.dp),
            )
        }

        Spacer(Modifier.height(14.dp))

        SettingsSearchField(query = query, onQueryChange = { query = it })

        if (query.isBlank()) {
            Spacer(Modifier.height(18.dp))
            Text(
                text = "DISPLAY & THEME",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                color = SpotifyTextSecondary,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
            )
            QuickToggles(
                darkMode = darkMode,
                onDarkModeChange = onDarkModeChange,
                dynamicTheme = dynamicTheme,
                onDynamicThemeChange = onDynamicThemeChange,
                pureBlack = pureBlack,
                onPureBlackChange = onPureBlackChange,
            )
        }

        val q = query.trim()
        groups.forEach { (groupTitle, rows) ->
            val filtered = if (q.isEmpty()) rows else rows.filter {
                it.title.contains(q, ignoreCase = true) || it.subtitle.contains(q, ignoreCase = true)
            }
            if (filtered.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                Text(
                    text = groupTitle.uppercase(),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    color = SpotifyTextSecondary,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(SpotifyElevatedSurface),
                ) {
                    filtered.forEachIndexed { i, row ->
                        if (i > 0) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 58.dp),
                                thickness = 0.5.dp,
                                color = SpotifyDivider,
                            )
                        }
                        SpotifySettingRow(row = row)
                    }
                }
            }
        }

        if (hasUpdate) {
            Spacer(Modifier.height(18.dp))
            ReleaseNotesCard()
        }

        Spacer(Modifier.height(36.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTopAppBar(
    navController: NavController,
) {
    TopAppBar(
        title = {
            Text(
                text = stringResource(R.string.settings),
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = SpotifyTextPrimary,
            )
        },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain,
            ) {
                Icon(painterResource(R.drawable.arrow_back), contentDescription = null, tint = SpotifyTextPrimary)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = SpotifyBlack,
            titleContentColor = SpotifyTextPrimary,
        ),
    )
}

@Composable
private fun SpotifySettingRow(row: SettingRow) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = row.onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(SpotifyCardSurface),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(row.icon),
                contentDescription = null,
                tint = SpotifyGreen,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = row.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = SpotifyTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (row.badge) {
                    Spacer(Modifier.width(8.dp))
                    Badge(containerColor = SpotifyGreen)
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = row.subtitle,
                fontSize = 13.sp,
                color = SpotifyTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            painter = painterResource(R.drawable.navigate_next),
            contentDescription = null,
            tint = SpotifyTextSecondary,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun QuickToggles(
    darkMode: DarkMode,
    onDarkModeChange: (DarkMode) -> Unit,
    dynamicTheme: Boolean,
    onDynamicThemeChange: (Boolean) -> Unit,
    pureBlack: Boolean,
    onPureBlackChange: (Boolean) -> Unit,
) {
    val (modeIcon, modeLabel) = when (darkMode) {
        DarkMode.AUTO -> R.drawable.contrast to stringResource(R.string.quick_theme_auto)
        DarkMode.ON -> R.drawable.bedtime to stringResource(R.string.quick_theme_dark)
        DarkMode.OFF -> R.drawable.contrast to stringResource(R.string.quick_theme_light)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        QuickChip(
            icon = modeIcon,
            label = modeLabel,
            active = darkMode != DarkMode.AUTO,
            modifier = Modifier.weight(1f),
        ) {
            onDarkModeChange(
                when (darkMode) {
                    DarkMode.AUTO -> DarkMode.ON
                    DarkMode.ON -> DarkMode.OFF
                    DarkMode.OFF -> DarkMode.AUTO
                },
            )
        }
        QuickChip(
            icon = R.drawable.palette,
            label = stringResource(R.string.quick_dynamic_color),
            active = dynamicTheme,
            modifier = Modifier.weight(1f),
        ) { onDynamicThemeChange(!dynamicTheme) }
        QuickChip(
            icon = R.drawable.bedtime,
            label = stringResource(R.string.pure_black),
            active = pureBlack,
            modifier = Modifier.weight(1f),
        ) { onPureBlackChange(!pureBlack) }
    }
}

@Composable
private fun QuickChip(
    icon: Int,
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .clip(shape)
            .clickable(onClick = onClick)
            .background(if (active) SpotifyCardHover else SpotifyElevatedSurface)
            .border(
                width = if (active) 1.dp else 0.dp,
                color = if (active) SpotifyGreen else Color.Transparent,
                shape = shape,
            )
            .padding(vertical = 12.dp, horizontal = 8.dp),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = if (active) SpotifyGreen else SpotifyTextSecondary,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            color = if (active) Color.White else SpotifyTextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SettingsSearchField(query: String, onQueryChange: (String) -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(SpotifyElevatedSurface)
            .padding(horizontal = 14.dp, vertical = 11.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.search),
            contentDescription = null,
            tint = SpotifyTextSecondary,
            modifier = Modifier.size(18.dp),
        )
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = TextStyle(
                color = SpotifyTextPrimary,
                fontSize = 14.sp,
            ),
            cursorBrush = SolidColor(SpotifyGreen),
            modifier = Modifier.weight(1f),
            decorationBox = { innerTextField ->
                if (query.isEmpty()) {
                    Text(
                        text = stringResource(R.string.search_settings),
                        style = TextStyle(
                            color = SpotifyTextMuted,
                            fontSize = 14.sp,
                        ),
                    )
                }
                innerTextField()
            },
        )
        if (query.isNotEmpty()) {
            Icon(
                painter = painterResource(R.drawable.close),
                contentDescription = null,
                tint = SpotifyTextSecondary,
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .clickable { onQueryChange("") },
            )
        }
    }
}
