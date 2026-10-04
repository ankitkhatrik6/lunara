/**
 * Lunara Project (C) 2026
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See NOTICE for contributors
 */

package com.lunara.app.ui.screens.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import com.lunara.app.utils.BugReport
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
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
import com.lunara.app.utils.Updater
import com.lunara.app.ui.utils.backToMain
import com.lunara.app.utils.rememberEnumPreference
import com.lunara.app.utils.rememberPreference
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.fillMaxSize

private class SettingRow(
    val icon: Int,
    val title: String,
    val subtitle: String,
    val badge: Boolean = false,
    val onClick: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    latestVersionName: String,
) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val isAndroid12OrLater = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val hasAndroidAuto = remember {
        try {
            context.packageManager.getPackageInfo("com.google.android.projection.gearhead", 0)
            true
        } catch (e: Exception) {
            false
        }
    }
    val showChangelog = LocalChangelogState.current
    var showBugReport by remember { mutableStateOf(false) }
    // Newer, not merely different. An inequality also fires when this build is
    // ahead of the last published one — which is every development build, and
    // was every build at all while the version being compared was the release
    // headline rather than a version. The badge was permanently on.
    val hasUpdate = BuildConfig.UPDATER_AVAILABLE &&
        Updater.isUpdateAvailable(BuildConfig.VERSION_NAME, latestVersionName)

    // Appearance state for the quick toggles at the top of the page.
    val (darkMode, onDarkModeChange) = rememberEnumPreference(DarkModeKey, DarkMode.AUTO)
    val (dynamicTheme, onDynamicThemeChange) = rememberPreference(DynamicThemeKey, true)
    val (pureBlack, setPureBlack) = rememberPreference(PureBlackKey, true)
    val (_, setPureBlackMiniPlayer) = rememberPreference(PureBlackMiniPlayerKey, false)
    // Look & Feel turns pure black on for the mini player as well. The chip used to
    // skip that, which left a grey mini player under an otherwise black app.
    val onPureBlackChange: (Boolean) -> Unit = { enabled ->
        setPureBlack(enabled)
        setPureBlackMiniPlayer(enabled)
    }

    // Lunara's settings are the whole of it: the three appearance toggles drawn
    // as chips just below, then About, Changelog and Updater. There is nothing
    // else to configure, so there is nothing else on this page.
    val groups: List<Pair<String, List<SettingRow>>> = listOf(
        stringResource(R.string.settings_group_about) to buildList {
            add(SettingRow(R.drawable.info, stringResource(R.string.about), stringResource(R.string.hint_about), badge = hasUpdate) {
                navController.navigate("settings/about")
            })
            add(SettingRow(R.drawable.newspaper, stringResource(R.string.changelog), stringResource(R.string.hint_changelog)) {
                showChangelog.value = true
            })
            if (BuildConfig.UPDATER_AVAILABLE) {
                add(SettingRow(R.drawable.update, stringResource(R.string.updater), stringResource(R.string.hint_updater)) {
                    navController.navigate("settings/updater")
                })
            }
        },
    )

    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top)))
        Spacer(Modifier.height(8.dp))

        // No accounts in Lunara: a plain brand header instead of a profile chip.
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 8.dp),
        )
        Spacer(Modifier.height(4.dp))

        Spacer(Modifier.height(12.dp))
        QuickToggles(
            darkMode = darkMode,
            onDarkModeChange = onDarkModeChange,
            dynamicTheme = dynamicTheme,
            onDynamicThemeChange = onDynamicThemeChange,
            pureBlack = pureBlack,
            onPureBlackChange = onPureBlackChange,
        )

        var chipIndex = 0
        groups.forEach { (groupTitle, rows) ->
            Spacer(Modifier.height(18.dp))
            Text(
                text = groupTitle.uppercase(),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 6.dp, bottom = 8.dp),
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer),
            ) {
                rows.forEachIndexed { i, row ->
                    if (i > 0) {
                        Box(
                            Modifier
                                .padding(start = 68.dp)
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        )
                    }
                    LunaraSettingRow(row = row, colorIndex = chipIndex)
                    chipIndex++
                }
            }
        }

        if (hasUpdate) {
            Spacer(Modifier.height(18.dp))
            ReleaseNotesCard()
        }

        Spacer(Modifier.height(24.dp))
    }

    if (showBugReport) {
        AlertDialog(
            onDismissRequest = { showBugReport = false },
            icon = { Icon(painterResource(R.drawable.bug_report), null) },
            title = { Text(stringResource(R.string.report_problem)) },
            text = {
                Column {
                    Text(stringResource(R.string.report_problem_body))
                    Spacer(Modifier.height(14.dp))
                    // Shown rather than merely attached. Nothing about someone's
                    // device should leave without them having seen it first.
                    Text(
                        text = BugReport.details(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(18.dp))
                    // Email first: it is the only one of the three that asks
                    // nothing of somebody who just wants to say it is broken.
                    ReportChoice(stringResource(R.string.report_problem_email)) {
                        showBugReport = false
                        BugReport.email(context)
                    }
                    ReportChoice(stringResource(R.string.report_problem_open)) {
                        showBugReport = false
                        uriHandler.openUri(BugReport.issueUrl())
                    }
                    ReportChoice(stringResource(R.string.report_problem_copy)) {
                        showBugReport = false
                        BugReport.copyDetails(context)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showBugReport = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    TopAppBar(
        title = { Text(stringResource(R.string.settings)) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain,
            ) {
                Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
            }
        },
    )
}

/** Chip background/foreground derived from the app's dynamic theme. */
@Composable
private fun chipColorsAt(index: Int): Pair<Color, Color> {
    val cs = MaterialTheme.colorScheme
    return when (index % 3) {
        0 -> cs.primary to cs.onPrimary
        1 -> cs.secondary to cs.onSecondary
        else -> cs.tertiary to cs.onTertiary
    }
}

@Composable
private fun LunaraSettingRow(row: SettingRow, colorIndex: Int) {
    val (chipBg, chipInk) = chipColorsAt(colorIndex)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = row.onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(chipBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(row.icon),
                contentDescription = null,
                tint = chipInk,
                modifier = Modifier.size(21.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = row.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (row.badge) {
                    Spacer(Modifier.width(8.dp))
                    Badge()
                }
            }
            Text(
                text = row.subtitle,
                fontSize = 12.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            painter = painterResource(R.drawable.navigate_next),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
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
        // Theme mode cycles Auto -> On -> Off.
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
    val shape = RoundedCornerShape(16.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .clip(shape)
            .clickable(onClick = onClick)
            .background(
                if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                else MaterialTheme.colorScheme.surfaceContainer,
            )
            // A visible outline makes the active state unmistakable.
            .border(
                width = if (active) 1.5.dp else 0.dp,
                color = if (active) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = shape,
            )
            .padding(vertical = 12.dp, horizontal = 8.dp),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun openDefaultLinksSettings(context: Context) {
    try {
        val intent = Intent(
            Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS,
            "package:${context.packageName}".toUri(),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, R.string.open_app_settings_error, Toast.LENGTH_LONG).show()
    }
}


@Composable
private fun ReportChoice(
    label: String,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp),
    ) {
        Text(label, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
    }
}
