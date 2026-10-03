/**
 * Lunara Project (C) 2026
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See NOTICE for contributors
 */

package com.lunara.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.ForegroundServiceStartNotAllowedException
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import android.os.IBinder
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.util.Consumer
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.coroutineScope
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.toBitmap
import com.lunara.innertube.YouTube
import com.lunara.innertube.models.SongItem
import com.lunara.innertube.models.WatchEndpoint
import com.lunara.app.constants.AppBarHeight
import com.lunara.app.constants.AppLanguageKey
import com.lunara.app.constants.BetaUpdatesKey
import com.lunara.app.constants.CheckForUpdatesKey
import com.lunara.app.constants.DarkModeKey
import com.lunara.app.constants.DefaultOpenTabKey
import com.lunara.app.constants.DisableScreenshotKey
import com.lunara.app.constants.DynamicThemeKey
import com.lunara.app.constants.EnableHighRefreshRateKey
import com.lunara.app.constants.EnableLandscapeScalingKey
import androidx.datastore.preferences.core.edit
import com.lunara.app.constants.LastSeenVersionKey
import com.lunara.app.constants.UpdateDeclinedVersionKey
import com.lunara.app.constants.LyricsProviderOrderKey
import com.lunara.app.constants.MiniPlayerBottomSpacing
import com.lunara.app.constants.MiniPlayerHeight
import com.lunara.app.constants.NavigationBarAnimationSpec
import com.lunara.app.constants.NavigationBarHeight
import com.lunara.app.constants.PauseListenHistoryKey
import com.lunara.app.constants.PauseSearchHistoryKey
import com.lunara.app.constants.PreferredLyricsProvider
import com.lunara.app.constants.PreferredLyricsProviderKey
import com.lunara.app.constants.PureBlackKey
import com.lunara.app.constants.SYSTEM_DEFAULT
import com.lunara.app.constants.SelectedThemeColorKey
import com.lunara.app.constants.SimpMusicMigrationDoneKey
import com.lunara.app.constants.SlimNavBarHeight
import com.lunara.app.constants.SlimNavBarKey
import com.lunara.app.constants.StopMusicOnTaskClearKey
import com.lunara.app.constants.UpdateNotificationsEnabledKey
import com.lunara.app.constants.UseNewMiniPlayerDesignKey
import com.lunara.app.db.MusicDatabase
import com.lunara.app.db.entities.SearchHistory
import com.lunara.app.extensions.toEnum
import com.lunara.app.lyrics.LyricsProviderRegistry
import com.lunara.app.models.toMediaMetadata
import com.lunara.app.playback.DownloadUtil
import com.lunara.app.playback.MusicService
import com.lunara.app.playback.MusicService.MusicBinder
import com.lunara.app.playback.PlayerConnection
import com.lunara.app.playback.queues.YouTubeQueue
import androidx.compose.ui.platform.LocalUriHandler
import com.lunara.app.utils.StarPrompt
import com.lunara.app.constants.OnboardingCompletedKey
import com.lunara.app.ui.component.LunaraSnackbarHost
import com.lunara.app.ui.component.LunaraSplash
import com.lunara.app.ui.screens.OnboardingScreen
import com.lunara.app.ui.component.AppNavigationBar
import com.lunara.app.ui.component.AppNavigationRail
import com.lunara.app.ui.component.BottomSheetMenu
import com.lunara.app.ui.component.BottomSheetPage
import com.lunara.app.ui.component.LocalBottomSheetPageState
import com.lunara.app.ui.component.LocalMenuState
import com.lunara.app.ui.component.BottomSheetState
import com.lunara.app.ui.component.SpotifyImportDialog
import com.lunara.app.ui.component.rememberBottomSheetState
import com.lunara.app.ui.player.PLAYER_DESIGN_GALLERY_ROUTE
import com.lunara.app.ui.component.shimmer.ShimmerTheme
import com.lunara.app.ui.menu.YouTubeSongMenu
import com.lunara.app.ui.player.BottomSheetPlayer
import com.lunara.app.ui.screens.Screens
import com.lunara.app.ui.screens.navigationBuilder
import com.lunara.app.ui.component.UpdateDialog
import com.lunara.app.ui.screens.settings.ChangelogScreen
import com.lunara.app.ui.screens.settings.DarkMode
import com.lunara.app.ui.screens.settings.NavigationTab
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.lunara.app.ui.theme.LunaraGradientEnd
import com.lunara.app.ui.theme.LunaraThemeColor
import com.lunara.app.ui.theme.ColorSaver
import com.lunara.app.ui.theme.DefaultThemeColor
import com.lunara.app.ui.theme.LunaraTheme
import com.lunara.app.ui.theme.extractThemeColor
import com.lunara.app.ui.utils.appBarScrollBehavior
import com.lunara.app.ui.utils.resetHeightOffset
import com.lunara.app.utils.SearchRoutes
import com.lunara.app.utils.SyncUtils
import com.lunara.app.utils.ReleaseInfo
import androidx.activity.result.contract.ActivityResultContracts
import com.lunara.app.utils.Updater
import com.lunara.app.utils.dataStore
import com.lunara.app.utils.safeDataStoreEdit
import com.lunara.app.utils.get
import com.lunara.app.utils.rememberEnumPreference
import com.lunara.app.utils.rememberPreference
import com.lunara.app.utils.PlaylistLink
import com.lunara.app.utils.SharedPlaylistImport
import com.lunara.app.utils.reportException
import com.lunara.app.utils.setAppLocale
import com.lunara.app.widget.PlaylistWidgetReceiver
import com.valentinilk.shimmer.LocalShimmerTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.Locale
import javax.inject.Inject

@Suppress("DEPRECATION", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE")
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    companion object {
        private const val ACTION_SEARCH = "com.lunara.app.action.SEARCH"
        private const val ACTION_LIBRARY = "com.lunara.app.action.LIBRARY"
        private const val ACTION_SHUFFLE_LIKED = "com.lunara.app.action.SHUFFLE_LIKED"
        const val ACTION_RECOGNITION = "com.lunara.app.action.RECOGNITION"
        const val ACTION_OPEN_WIDGET_TARGET = "com.lunara.app.action.OPEN_WIDGET_TARGET"
        const val EXTRA_AUTO_START_RECOGNITION = "auto_start_recognition"
        const val EXTRA_WIDGET_TARGET_TYPE = "widget_target_type"
        const val EXTRA_WIDGET_TARGET_ID = "widget_target_id"
    }

    @Inject
    lateinit var database: MusicDatabase

    @Inject
    lateinit var downloadUtil: DownloadUtil

    @Inject
    lateinit var syncUtils: SyncUtils

    @Inject
    lateinit var listenTogetherManager: com.lunara.app.listentogether.ListenTogetherManager

    private lateinit var navController: NavHostController
    private var pendingIntent: Intent? = null

    /** A playlist somebody shared, waiting to be kept or turned down. */
    private var sharedPlaylist by mutableStateOf<PlaylistLink.Shared?>(null)
    private var latestVersionName by mutableStateOf(BuildConfig.VERSION_NAME)

    // Keep PlayerConnection as regular property - NOT mutableStateOf to prevent UI recomposition
    // when it becomes null during onStop. Only update the snapshot for Compose when needed.
    private var playerConnection: PlayerConnection? = null

    // This is the snapshot we pass to Compose - changes here trigger recomposition
    private var playerConnectionSnapshot by mutableStateOf<PlayerConnection?>(null)

    private var isServiceBound = false

    private val serviceConnection =
        object : ServiceConnection {
            override fun onServiceConnected(
                name: ComponentName?,
                service: IBinder?,
            ) {
                if (service is MusicBinder) {
                    playerConnection = PlayerConnection(this@MainActivity, service, database, lifecycleScope)
                    playerConnectionSnapshot = playerConnection
                    listenTogetherManager.setPlayerConnection(playerConnection)
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                // Disconnect Listen Together manager
                listenTogetherManager.setPlayerConnection(null)
                playerConnection?.dispose()
                // DO NOT null out playerConnection here - keep it for when service reconnects
                // DO NOT update playerConnectionSnapshot - this is the key to preventing recomposition
            }
        }

    private fun safeUnbindService(source: String) {
        if (!isServiceBound) return
        try {
            unbindService(serviceConnection)
        } catch (e: IllegalArgumentException) {
            Timber.tag("MainActivity").w(e, "Service was not bound when attempting to unbind in $source")
        } finally {
            isServiceBound = false
            listenTogetherManager.setPlayerConnection(null)
            playerConnection?.dispose()
            // DO NOT null out playerConnection here - keep it for reconnection
            // DO NOT update playerConnectionSnapshot - this prevents UI recomposition
        }
    }

    override fun onStart() {
        super.onStart()
        // Request notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1000)
            }
        }

        // Start the playback service explicitly once so it can outlive binding.
        // Re-issuing startForegroundService() while an existing service instance is already
        // running can trigger "did not then call startForeground" on some Android 9 devices
        // when the framework expects a fresh foreground promotion for that start request.
        if (!MusicService.isRunning) {
            val serviceIntent = Intent(this, MusicService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    ContextCompat.startForegroundService(this, serviceIntent)
                } else {
                    startService(serviceIntent)
                }
            } catch (e: ForegroundServiceStartNotAllowedException) {
                Timber.w(e, "Cannot start foreground service from background")
            } catch (e: IllegalStateException) {
                Timber.w(e, "Failed to start foreground service")
            }
        }

        // Bind to service - if already bound, this is a no-op but ensures we stay connected
        if (!isServiceBound) {
            bindService(
                Intent(this, MusicService::class.java),
                serviceConnection,
                BIND_AUTO_CREATE,
            )
            isServiceBound = true
        }
    }

    override fun onStop() {
        // Keep the service binding, PlayerConnection and Listen Together wiring alive while
        // the Activity is backgrounded. The MusicService is a foreground service and keeps
        // running, so the host must keep reporting playback state to the LT server; detaching
        // the player listener here used to break LT for any host that wasn't staring at the
        // app the whole session. Full teardown happens in onDestroy() via safeUnbindService().
        super.onStop()
    }

    override fun onDestroy() {
        if (isFinishing) {
            listenTogetherManager.disconnect()
        }
        super.onDestroy()
        // Use effective playing state so Cast (local player paused, remote playing) is included.
        val stopServiceOnClear =
            dataStore.get(StopMusicOnTaskClearKey, false) &&
                playerConnection?.isEffectivelyPlaying?.value == true &&
                isFinishing

        // Full cleanup - only on actual destroy
        playerConnection?.dispose()
        playerConnection = null
        playerConnectionSnapshot = null

        // Unbind before stopService: a started+bound service does not stop until all clients unbind.
        safeUnbindService("onDestroy()")

        if (stopServiceOnClear) {
            stopService(Intent(this, MusicService::class.java))
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (::navController.isInitialized) {
            handleShuffleShortcutIntent(intent)
            handleWidgetTargetIntent(intent, navController)
            handleDeepLinkIntent(intent, navController)
        } else {
            pendingIntent = intent
        }
    }

    @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        // The launcher theme carries the branded splash window background; swap back
        // to the normal theme now that we're about to draw the real UI.
        setTheme(R.style.Theme_Lunara)
        super.onCreate(savedInstanceState)
        // Tell the service before onStart starts it, so it doesn't load the old queue for a link
        // that is about to replace it.
        if (savedInstanceState == null && isPlayLink(intent)) {
            MusicService.markOpenedForLink()
        }
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_LTR
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Initialize Listen Together manager
        listenTogetherManager.initialize()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            val locale =
                dataStore[AppLanguageKey]
                    ?.takeUnless { it == SYSTEM_DEFAULT }
                    ?.let { Locale.forLanguageTag(it) }
                    ?: Locale.getDefault()
            setAppLocale(this, locale)
        }

        lifecycleScope.launch {
            dataStore.data
                .map { it[DisableScreenshotKey] ?: false }
                .distinctUntilChanged()
                .collectLatest {
                    if (it) {
                        window.setFlags(
                            WindowManager.LayoutParams.FLAG_SECURE,
                            WindowManager.LayoutParams.FLAG_SECURE,
                        )
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }
                }
        }

        // Defer migration and version tracking to avoid blocking first frame
        lifecycleScope.launch(Dispatchers.IO) {
            val preferences = dataStore.data.first()
            val currentVersion = BuildConfig.VERSION_NAME

            // SimpMusic Removal Migration
            if (preferences[SimpMusicMigrationDoneKey] != true) {
                safeDataStoreEdit { settings ->
                    val currentOrder = settings[LyricsProviderOrderKey] ?: ""
                    if (currentOrder.contains("SimpMusic")) {
                        val orderList =
                            currentOrder
                                .split(",")
                                .map { it.trim() }
                                .filter { it.isNotBlank() && it != "SimpMusic" }
                                .toMutableList()
                        if (orderList.isEmpty()) {
                            settings[LyricsProviderOrderKey] = ""
                        } else {
                            settings[LyricsProviderOrderKey] = orderList.joinToString(",")
                        }
                    }
                    if (settings[PreferredLyricsProviderKey] == "SIMPMUSIC") {
                        settings[PreferredLyricsProviderKey] = PreferredLyricsProvider.LRCLIB.name
                    }
                    settings[SimpMusicMigrationDoneKey] = true
                    settings[LastSeenVersionKey] = currentVersion
                }
            }
        }

        lifecycleScope.launch(Dispatchers.IO) {
            safeDataStoreEdit { settings ->
                settings[LastSeenVersionKey] = BuildConfig.VERSION_NAME
            }
        }

        setContent {
            // Startup is staged so the splash never animates against a busy UI
            // thread. Composing the app alongside it starved the animation to
            // ~9fps, which read as stuttering because wall-clock tweens jump
            // rather than slow down when frames are dropped. Instead: animate the
            // splash alone, then compose the app behind it while it is still
            // fully opaque, so the startup jank happens out of sight.
            var splashVisible by rememberSaveable { mutableStateOf(true) }
            var appComposed by rememberSaveable { mutableStateOf(false) }

            if (appComposed) {
                LunaraApp(
                    latestVersionName = latestVersionName,
                    onLatestVersionNameChange = { latestVersionName = it },
                    playerConnection = playerConnectionSnapshot,
                    database = database,
                    downloadUtil = downloadUtil,
                    syncUtils = syncUtils,
                )
            }

            LunaraSplash(
                visible = splashVisible,
                onIntroFinished = { appComposed = true },
            )

            LaunchedEffect(appComposed) {
                if (!appComposed) return@LaunchedEffect
                // Wait for the app to actually put frames up before uncovering it;
                // a fixed delay would either cut in early or idle for no reason.
                repeat(3) { withFrameNanos { } }
                splashVisible = false
            }

            // First run: introduce what the app does before dropping into it.
            val (onboardingCompleted, setOnboardingCompleted) =
                rememberPreference(OnboardingCompletedKey, defaultValue = false)
            if (!onboardingCompleted && !splashVisible) {
                OnboardingScreen(onFinish = { setOnboardingCompleted(true) })
            }
        }
    }

    @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun LunaraApp(
        latestVersionName: String,
        onLatestVersionNameChange: (String) -> Unit,
        playerConnection: PlayerConnection?,
        database: MusicDatabase,
        downloadUtil: DownloadUtil,
        syncUtils: SyncUtils,
    ) {
        val checkForUpdates by rememberPreference(CheckForUpdatesKey, defaultValue = true)

        // The release being offered in the app, if any. Declared here because the
        // check below is what fills it in.
        val offeredRelease = remember { mutableStateOf<ReleaseInfo?>(null) }

        if (BuildConfig.UPDATER_AVAILABLE) {
            LaunchedEffect(checkForUpdates) {
                if (checkForUpdates) {
                    withContext(Dispatchers.IO) {
                        val updatesEnabled = dataStore.get(CheckForUpdatesKey, true)
                        val notifEnabled = dataStore.get(UpdateNotificationsEnabledKey, true)
                        if (!updatesEnabled) return@withContext

                        Updater.checkForUpdate(
                            includeBetas = dataStore.get(BetaUpdatesKey, false),
                        ).onSuccess { (releaseInfo, hasUpdate) ->
                            if (releaseInfo != null) {
                                onLatestVersionNameChange(releaseInfo.versionName)
                                // Offer it in the app, once. A notification can be
                                // swiped away before it is read, and on a device
                                // that never granted notifications it is never seen
                                // at all — but somebody who has just opened the app
                                // is, by definition, looking at it.
                                if (hasUpdate &&
                                    dataStore.get(UpdateDeclinedVersionKey, "") != releaseInfo.tagName
                                ) {
                                    offeredRelease.value = releaseInfo
                                }
                                if (hasUpdate && notifEnabled) {
                                    val downloadUrl = Updater.getDownloadUrlForCurrentVariant(releaseInfo)
                                    if (downloadUrl != null) {
                                        val intent = Intent(Intent.ACTION_VIEW, downloadUrl.toUri())

                                        val flags =
                                            PendingIntent.FLAG_UPDATE_CURRENT or
                                                (PendingIntent.FLAG_IMMUTABLE)
                                        val pending = PendingIntent.getActivity(this@MainActivity, 1001, intent, flags)

                                        val notif =
                                            NotificationCompat
                                                .Builder(this@MainActivity, "updates")
                                                .setSmallIcon(R.drawable.update)
                                                .setContentTitle(getString(R.string.update_available_title))
                                                .setContentText(releaseInfo.versionName)
                                                .setContentIntent(pending)
                                                .setAutoCancel(true)
                                                .build()

                                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                                            ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) ==
                                            PackageManager.PERMISSION_GRANTED
                                        ) {
                                            NotificationManagerCompat.from(this@MainActivity).notify(1001, notif)
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    onLatestVersionNameChange(BuildConfig.VERSION_NAME)
                }
            }
        }

        val enableDynamicTheme by rememberPreference(DynamicThemeKey, defaultValue = true)
        val enableHighRefreshRate by rememberPreference(EnableHighRefreshRateKey, defaultValue = true)

        LaunchedEffect(enableHighRefreshRate) {
            val window = this@MainActivity.window
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val layoutParams = window.attributes
                if (enableHighRefreshRate) {
                    layoutParams.preferredDisplayModeId = 0
                } else {
                    val modes = window.windowManager.defaultDisplay.supportedModes
                    val mode60 =
                        modes.firstOrNull { kotlin.math.abs(it.refreshRate - 60f) < 1f }
                            ?: modes.minByOrNull { kotlin.math.abs(it.refreshRate - 60f) }

                    if (mode60 != null) {
                        layoutParams.preferredDisplayModeId = mode60.modeId
                    }
                }
                window.attributes = layoutParams
            } else {
                val params = window.attributes
                if (enableHighRefreshRate) {
                    params.preferredRefreshRate = 0f
                } else {
                    params.preferredRefreshRate = 60f
                }
                window.attributes = params
            }
        }

        val darkTheme by rememberEnumPreference(DarkModeKey, defaultValue = DarkMode.AUTO)
        val isSystemInDarkTheme = isSystemInDarkTheme()
        val useDarkTheme =
            remember(darkTheme, isSystemInDarkTheme) {
                if (darkTheme == DarkMode.AUTO) isSystemInDarkTheme else darkTheme == DarkMode.ON
            }

        LaunchedEffect(useDarkTheme) {
            setSystemBarAppearance(useDarkTheme)
        }

        val enableLandscapeScaling by rememberPreference(EnableLandscapeScalingKey, defaultValue = false)
        val pureBlackEnabled by rememberPreference(PureBlackKey, defaultValue = true)
        val pureBlack =
            remember(pureBlackEnabled, useDarkTheme) {
                pureBlackEnabled && useDarkTheme
            }

        val (selectedThemeColorInt) = rememberPreference(SelectedThemeColorKey, defaultValue = LunaraThemeColor.toArgb())
        val selectedThemeColor = Color(selectedThemeColorInt)

        val showChangelog = rememberSaveable { mutableStateOf(false) }

        var themeColor by rememberSaveable(stateSaver = ColorSaver) {
            mutableStateOf(selectedThemeColor)
        }

        val themeColorCache = remember { mutableMapOf<String, Color>() }

        LaunchedEffect(selectedThemeColor) {
            if (!enableDynamicTheme) {
                themeColor = selectedThemeColor
            }
        }

        LaunchedEffect(playerConnection, enableDynamicTheme, selectedThemeColor) {
            val playerConnection = playerConnection
            if (!enableDynamicTheme || playerConnection == null) {
                themeColor = selectedThemeColor
                return@LaunchedEffect
            }

            playerConnection.service.currentMediaMetadata
                .distinctUntilChanged { old, new -> old?.id == new?.id }
                .collectLatest { song ->
                    if (song?.thumbnailUrl != null) {
                        val cached = themeColorCache[song.thumbnailUrl]
                        if (cached != null) {
                            withFrameNanos { }
                            themeColor = cached
                            return@collectLatest
                        }
                        withContext(Dispatchers.IO) {
                            try {
                                val result =
                                    imageLoader.execute(
                                        ImageRequest
                                            .Builder(this@MainActivity)
                                            .data(song.thumbnailUrl)
                                            .allowHardware(false)
                                            .memoryCachePolicy(CachePolicy.ENABLED)
                                            .diskCachePolicy(CachePolicy.ENABLED)
                                            .networkCachePolicy(CachePolicy.ENABLED)
                                            .crossfade(false)
                                            .build(),
                                    )
                                val extractedColor = result.image?.toBitmap()?.extractThemeColor() ?: selectedThemeColor
                                themeColorCache[song.thumbnailUrl] = extractedColor
                                withFrameNanos { }
                                themeColor = extractedColor
                            } catch (e: Exception) {
                                withFrameNanos { }
                                themeColor = selectedThemeColor
                            }
                        }
                    } else {
                        themeColor = selectedThemeColor
                    }
                }
        }

        LunaraTheme(
            darkTheme = useDarkTheme,
            pureBlack = pureBlack,
            themeColor = themeColor,
        ) {
            val currentDensity = LocalDensity.current
            val windowInfo = LocalWindowInfo.current
            val containerSize = windowInfo.containerDpSize
            val smallestDimensionDp = minOf(containerSize.width, containerSize.height)

            val densityScale = remember(smallestDimensionDp, enableLandscapeScaling) {
                if (enableLandscapeScaling) {
                    when {
                        smallestDimensionDp >= 840.dp -> 1.15f
                        smallestDimensionDp >= 720.dp -> 1.1f
                        smallestDimensionDp >= 600.dp -> 1.05f
                        else -> 1.0f
                    }
                } else {
                    1.0f
                }
            }
            val scaledDensity: Density = remember(currentDensity, densityScale) {
                Density(
                    density = currentDensity.density * densityScale,
                    fontScale = currentDensity.fontScale,
                )
            }

            CompositionLocalProvider(LocalDensity provides scaledDensity) {
            BoxWithConstraints(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(if (pureBlack) Color.Black else MaterialTheme.colorScheme.surface),
            ) {
                val density = LocalDensity.current
                val configuration = LocalWindowInfo.current
                val cutoutInsets = WindowInsets.displayCutout
                val windowsInsets = WindowInsets.systemBars
                val bottomInset = with(density) { windowsInsets.getBottom(density).toDp() }
                val bottomInsetDp = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()

                val navController = rememberNavController()

                LaunchedEffect(Unit) {
                    val lastSeenVersion = dataStore.data.first()[LastSeenVersionKey] ?: ""
                    val currentVersion = BuildConfig.VERSION_NAME
                    if (lastSeenVersion != currentVersion) {
                        showChangelog.value = true
                    }
                }

                // Asked at most three times over about six weeks and then never
                // again. Not while anything is playing: interrupting music to
                // ask a favour is worse than not asking.
                var showStarPrompt by remember { mutableStateOf(false) }
                val starUriHandler = LocalUriHandler.current
                val starScope = rememberCoroutineScope()
                LaunchedEffect(Unit) {
                    val playing = playerConnectionSnapshot?.player?.isPlaying == true
                    if (!playing && StarPrompt.onOpened(this@MainActivity)) {
                        StarPrompt.onShown(this@MainActivity)
                        showStarPrompt = true
                    }
                }

                if (showStarPrompt) {
                    AlertDialog(
                        onDismissRequest = { showStarPrompt = false },
                        icon = { Icon(painterResource(R.drawable.star), null) },
                        title = { Text(stringResource(R.string.star_prompt_title)) },
                        text = { Text(stringResource(R.string.star_prompt_body)) },
                        confirmButton = {
                            TextButton(onClick = {
                                showStarPrompt = false
                                starScope.launch { StarPrompt.stop(this@MainActivity) }
                                starUriHandler.openUri(StarPrompt.REPO)
                            }) { Text(stringResource(R.string.star_prompt_yes)) }
                        },
                        dismissButton = {
                            Row {
                                TextButton(onClick = {
                                    showStarPrompt = false
                                    starScope.launch { StarPrompt.stop(this@MainActivity) }
                                }) { Text(stringResource(R.string.star_prompt_never)) }
                                TextButton(onClick = { showStarPrompt = false }) {
                                    Text(stringResource(R.string.star_prompt_later))
                                }
                            }
                        },
                    )
                }

                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val (previousTab, setPreviousTab) = rememberSaveable { mutableStateOf("home") }

                val navigationItems = remember { Screens.MainScreens }
                val routeIndexMap = remember(navigationItems) {
                    navigationItems.mapIndexed { i, s -> s.route to i }.toMap()
                }
                val (slimNav) = rememberPreference(SlimNavBarKey, defaultValue = false)
                val (useNewMiniPlayerDesign) = rememberPreference(UseNewMiniPlayerDesignKey, defaultValue = true)
                val (defaultOpenTabInt) = rememberPreference(DefaultOpenTabKey, defaultValue = NavigationTab.HOME.name)
                val defaultOpenTab = remember(defaultOpenTabInt) {
                    try {
                        NavigationTab.valueOf(defaultOpenTabInt)
                    } catch (_: IllegalArgumentException) {
                        NavigationTab.HOME
                    }
                }
                val tabOpenedFromShortcut =
                    remember {
                        when (intent?.action) {
                            ACTION_SEARCH -> NavigationTab.SEARCH
                            ACTION_LIBRARY -> NavigationTab.LIBRARY
                            else -> null
                        }
                    }

                val topLevelScreens =
                    remember {
                        listOf(
                            Screens.Home.route,
                            Screens.Library.route,
                            Screens.Yours.route,
                            "settings",
                        )
                    }

                val (query, onQueryChange) =
                    rememberSaveable(stateSaver = TextFieldValue.Saver) {
                        mutableStateOf(TextFieldValue())
                    }

                val onSearch: (String) -> Unit =
                    remember {
                        { searchQuery ->
                            if (searchQuery.isNotEmpty()) {
                                navController.navigate(SearchRoutes.resultRoute(searchQuery))

                                if (dataStore[PauseSearchHistoryKey] != true) {
                                    lifecycleScope.launch(Dispatchers.IO) {
                                        runCatching {
                                            database.insert(SearchHistory(query = searchQuery))
                                        }.onFailure { throwable ->
                                            Timber
                                                .tag("MainActivity")
                                                .w(throwable, "Failed to save search history for query: %s", searchQuery)
                                        }
                                    }
                                }
                            }
                        }
                    }

                val currentRoute by remember {
                    derivedStateOf { navBackStackEntry?.destination?.route }
                }

                val inSearchScreen by remember {
                    derivedStateOf { currentRoute?.startsWith("search/") == true }
                }
                val navigationItemRoutes =
                    remember(navigationItems) {
                        navigationItems.map { it.route }.toSet()
                    }

                val shouldShowNavigationBar =
                    remember(currentRoute, navigationItemRoutes) {
                        currentRoute == null ||
                            navigationItemRoutes.contains(currentRoute) ||
                            currentRoute!!.startsWith("search/")
                    }

                val isLandscape = configuration.containerDpSize.width > configuration.containerDpSize.height
                val isTablet = configuration.containerDpSize.width >= 600.dp

                val showRail = (isLandscape || isTablet) && !inSearchScreen

                val navPadding =
                    if (shouldShowNavigationBar && !showRail) {
                        if (slimNav) SlimNavBarHeight else NavigationBarHeight
                    } else {
                        0.dp
                    }

                val navigationBarHeight by animateDpAsState(
                    targetValue = if (shouldShowNavigationBar && !showRail) NavigationBarHeight else 0.dp,
                    animationSpec = NavigationBarAnimationSpec,
                    label = "navBarHeight",
                )

                val playerBottomSheetState =
                    rememberBottomSheetState(
                        dismissedBound = 0.dp,
                        collapsedBound =
                            bottomInset +
                                (if (!showRail && shouldShowNavigationBar) navPadding else 0.dp) +
                                (if (useNewMiniPlayerDesign) MiniPlayerBottomSpacing else 0.dp) +
                                MiniPlayerHeight,
                        expandedBound = maxHeight,
                    )

                val playerReadyState =
                    playerConnection?.service?.isPlayerReady?.collectAsStateWithLifecycle()
                        ?: remember { mutableStateOf(false) }
                val playerReady by playerReadyState
                val activePlayerConnection = if (playerReady) playerConnection else null

                // Home draws its own in-content header, so it gets no app-bar padding
                val isHomeRoute = navBackStackEntry?.destination?.route == Screens.Home.route
                val playerAwareWindowInsets =
                    remember(
                        bottomInset,
                        shouldShowNavigationBar,
                        playerBottomSheetState.isDismissed,
                        showRail,
                        isHomeRoute,
                        navPadding,
                        useNewMiniPlayerDesign,
                    ) {
                        // Has to match the player sheet's collapsedBound exactly,
                        // or the last row of every list ends up under the mini
                        // player. It was short by the spacing the new design
                        // leaves beneath itself, and used the full navigation
                        // bar height where the sheet uses the slim one — so the
                        // two disagreed in both directions at once.
                        var bottom = bottomInset
                        if (shouldShowNavigationBar && !showRail) {
                            bottom += navPadding
                        }
                        if (!playerBottomSheetState.isDismissed) {
                            bottom += MiniPlayerHeight
                            if (useNewMiniPlayerDesign) bottom += MiniPlayerBottomSpacing
                        }
                        windowsInsets
                            .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)
                            .add(WindowInsets(top = if (isHomeRoute) 0.dp else AppBarHeight, bottom = bottom))
                    }
                appBarScrollBehavior(
                    canScroll = {
                        !inSearchScreen &&
                            (playerBottomSheetState.isCollapsed || playerBottomSheetState.isDismissed)
                    },
                )

                val topAppBarScrollBehavior =
                    appBarScrollBehavior(
                        canScroll = {
                            !inSearchScreen &&
                                (playerBottomSheetState.isCollapsed || playerBottomSheetState.isDismissed)
                        },
                    )

                // Navigation tracking
                LaunchedEffect(navBackStackEntry) {
                    if (inSearchScreen) {
                        val searchQuery =
                            SearchRoutes.decodeQuery(
                                navBackStackEntry?.arguments?.getString("query").orEmpty(),
                            )
                        onQueryChange(
                            TextFieldValue(
                                searchQuery,
                                TextRange(searchQuery.length),
                            ),
                        )
                    } else if (navigationItems.fastAny { it.route == navBackStackEntry?.destination?.route }) {
                        onQueryChange(TextFieldValue())
                    }

                    // Reset scroll behavior for main navigation items
                    if (navigationItems.fastAny { it.route == navBackStackEntry?.destination?.route }) {
                        if (navigationItems.fastAny { it.route == previousTab }) {
                            topAppBarScrollBehavior.state.resetHeightOffset()
                        }
                    }

                    topAppBarScrollBehavior.state.resetHeightOffset()

                    // Collapse player when navigating to equalizer
                    if (navBackStackEntry?.destination?.route == "equalizer" &&
                        playerBottomSheetState.isExpanded
                    ) {
                        playerBottomSheetState.collapseSoft()
                    }

                    // Track previous tab for animations
                    navController.currentBackStackEntry?.destination?.route?.let {
                        setPreviousTab(it)
                    }
                }

                LaunchedEffect(activePlayerConnection) {
                    val player = runCatching { activePlayerConnection?.player }.getOrNull()
                    if (player?.currentMediaItem == null) {
                        if (!playerBottomSheetState.isDismissed) {
                            playerBottomSheetState.dismiss()
                        }
                        return@LaunchedEffect
                    }

                    if (playerBottomSheetState.isDismissed) {
                        playerBottomSheetState.collapseSoft()
                    }
                }

                DisposableEffect(activePlayerConnection, playerBottomSheetState) {
                    val player = runCatching { activePlayerConnection?.player }.getOrNull()
                        ?: return@DisposableEffect onDispose { }
                    val listener =
                        object : Player.Listener {
                            override fun onMediaItemTransition(
                                mediaItem: MediaItem?,
                                reason: Int,
                            ) {
                                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED &&
                                    mediaItem != null &&
                                    playerBottomSheetState.isDismissed
                                ) {
                                    playerBottomSheetState.collapseSoft()
                                }
                            }
                        }
                    player.addListener(listener)
                    onDispose {
                        player.removeListener(listener)
                    }
                }

                var shouldShowTopBar by rememberSaveable { mutableStateOf(false) }

                LaunchedEffect(navBackStackEntry) {
                    val currentRoute = navBackStackEntry?.destination?.route
                    // Home renders its own Lunara header inside the content
                    shouldShowTopBar = currentRoute in topLevelScreens &&
                        currentRoute != "settings" &&
                        currentRoute != Screens.Home.route
                }

                val coroutineScope = rememberCoroutineScope()
                var sharedSong: SongItem? by remember {
                    mutableStateOf(null)
                }
                val snackbarHostState = remember { SnackbarHostState() }

                LaunchedEffect(Unit) {
                    if (pendingIntent != null) {
                        handleShuffleShortcutIntent(pendingIntent!!)
                        handleWidgetTargetIntent(pendingIntent!!, navController)
                        handleRecognitionIntent(pendingIntent!!, navController)
                        handleDeepLinkIntent(pendingIntent!!, navController)
                        pendingIntent = null
                    } else {
                        handleShuffleShortcutIntent(intent)
                        handleWidgetTargetIntent(intent, navController)
                        handleRecognitionIntent(intent, navController)
                        handleDeepLinkIntent(intent, navController)
                    }
                }

                DisposableEffect(Unit) {
                    val listener =
                        Consumer<Intent> { intent ->
                            handleShuffleShortcutIntent(intent)
                            handleWidgetTargetIntent(intent, navController)
                            handleRecognitionIntent(intent, navController)
                            handleDeepLinkIntent(intent, navController)
                        }

                    addOnNewIntentListener(listener)
                    onDispose { removeOnNewIntentListener(listener) }
                }

                val currentTitleRes =
                    remember(navBackStackEntry) {
                        when (navBackStackEntry?.destination?.route) {
                            Screens.Home.route -> R.string.home
                            Screens.Search.route -> R.string.search
                            Screens.Library.route -> R.string.filter_library
                            Screens.Yours.route -> R.string.yours
                            else -> null
                        }
                    }

                var showSpotifyImportDialog by remember { mutableStateOf(false) }

                val pauseListenHistory by rememberPreference(PauseListenHistoryKey, defaultValue = false)
                val eventCount by database.eventCount().collectAsStateWithLifecycle(initialValue = 0)
                val showHistoryButton =
                    remember(pauseListenHistory, eventCount) {
                        !(pauseListenHistory && eventCount == 0)
                    }

                val baseBg = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainer

                CompositionLocalProvider(
                    LocalDatabase provides database,
                    LocalNavController provides navController,
                    LocalContentColor provides if (pureBlack) Color.White else contentColorFor(MaterialTheme.colorScheme.surface),
                    LocalPlayerConnection provides playerConnection,
                    LocalPlayerBottomSheetState provides playerBottomSheetState,
                    LocalPlayerAwareWindowInsets provides playerAwareWindowInsets,
                    LocalDownloadUtil provides downloadUtil,
                    LocalShimmerTheme provides ShimmerTheme,
                    LocalSyncUtils provides syncUtils,
                    LocalListenTogetherManager provides listenTogetherManager,
                    LocalChangelogState provides showChangelog,
                ) {
                    if (showChangelog.value) {
                        ChangelogScreen(onDismiss = { showChangelog.value = false })
                    }

                    sharedPlaylist?.let { shared ->
                        val scope = rememberCoroutineScope()
                        var saving by remember(shared) { mutableStateOf(false) }
                        AlertDialog(
                            onDismissRequest = { if (!saving) sharedPlaylist = null },
                            title = { Text(stringResource(R.string.shared_playlist_title)) },
                            text = {
                                Text(
                                    pluralStringResource(
                                        R.plurals.shared_playlist_body,
                                        shared.songIds.size,
                                        shared.name,
                                        shared.songIds.size,
                                    ),
                                )
                            },
                            confirmButton = {
                                TextButton(
                                    enabled = !saving,
                                    onClick = {
                                        saving = true
                                        scope.launch {
                                            val outcome = SharedPlaylistImport.save(shared, database)
                                            saving = false
                                            sharedPlaylist = null
                                            outcome
                                                .onSuccess {
                                                    Toast.makeText(
                                                        this@MainActivity,
                                                        getString(R.string.shared_playlist_kept, it.saved, it.total),
                                                        Toast.LENGTH_SHORT,
                                                    ).show()
                                                    navController.navigate("local_playlist/${it.playlistId}")
                                                }.onFailure {
                                                    Toast.makeText(
                                                        this@MainActivity,
                                                        R.string.shared_playlist_failed,
                                                        Toast.LENGTH_SHORT,
                                                    ).show()
                                                }
                                        }
                                    },
                                ) {
                                    Text(stringResource(R.string.save))
                                }
                            },
                            dismissButton = {
                                TextButton(enabled = !saving, onClick = { sharedPlaylist = null }) {
                                    Text(stringResource(R.string.cancel))
                                }
                            },
                        )
                    }

                    offeredRelease.value?.let { release ->
                        val scope = rememberCoroutineScope()
                        UpdateDialog(
                            release = release,
                            onDismiss = {
                                offeredRelease.value = null
                                scope.launch {
                                    dataStore.edit { it[UpdateDeclinedVersionKey] = release.tagName }
                                }
                            },
                        )
                    }

                    Scaffold(
                        snackbarHost = { LunaraSnackbarHost(snackbarHostState) },
                        topBar = {
                            AnimatedVisibility(
                                visible = shouldShowTopBar,
                                enter = fadeIn(animationSpec = tween(durationMillis = 300)),
                                exit = fadeOut(animationSpec = tween(durationMillis = 200)),
                            ) {
                                Row {
                                    TopAppBar(
                                        title = {
                                            if (navBackStackEntry?.destination?.route == Screens.Home.route) {
                                                // Lunara gradient wordmark on the home screen
                                                Text(
                                                    text = "Lunara",
                                                    style = MaterialTheme.typography.titleLarge.copy(
                                                        fontWeight = FontWeight.Bold,
                                                        letterSpacing = 0.5.sp,
                                                        brush = Brush.linearGradient(
                                                            colors = listOf(LunaraThemeColor, LunaraGradientEnd),
                                                        ),
                                                    ),
                                                )
                                            } else {
                                                Text(
                                                    text = currentTitleRes?.let { stringResource(it) } ?: "",
                                                    style = MaterialTheme.typography.titleLarge,
                                                )
                                            }
                                        },
                                        actions = {
                                            // In the library, bringing a playlist over is the
                                            // thing people come here to do, so it gets the
                                            // top-bar slot there.
                                            if (currentRoute == Screens.Library.route) {
                                                TextButton(
                                                    onClick = { showSpotifyImportDialog = true },
                                                    shape = CircleShape,
                                                    // A quiet capsule behind it, so it reads as
                                                    // something to press rather than a label.
                                                    colors =
                                                        ButtonDefaults.textButtonColors(
                                                            containerColor =
                                                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f),
                                                        ),
                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                                    modifier = Modifier.height(34.dp),
                                                ) {
                                                    Icon(
                                                        painter = painterResource(R.drawable.spotify),
                                                        contentDescription = null,
                                                        tint = Color.Unspecified,
                                                        modifier = Modifier.size(18.dp),
                                                    )
                                                    Spacer(Modifier.width(6.dp))
                                                    Text(
                                                        text = stringResource(R.string.import_spotify),
                                                        style = MaterialTheme.typography.labelLarge,
                                                    )
                                                }
                                            }
                                        },
                                        scrollBehavior = topAppBarScrollBehavior,
                                        colors =
                                            TopAppBarDefaults.topAppBarColors(
                                                containerColor = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainer,
                                                scrolledContainerColor = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainer,
                                                titleContentColor = MaterialTheme.colorScheme.onSurface,
                                                actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                            ),
                                        modifier =
                                            Modifier.windowInsetsPadding(
                                                if (showRail) {
                                                    WindowInsets(left = NavigationBarHeight)
                                                        .add(cutoutInsets.only(WindowInsetsSides.Start))
                                                } else {
                                                    cutoutInsets.only(WindowInsetsSides.Start + WindowInsetsSides.End)
                                                },
                                            ),
                                    )
                                }
                            }
                        },
                        bottomBar = {
                            val currentBackStackEntry = navController.currentBackStackEntry // reads reactively outside remember

                            val onNavItemClick: (Screens, Boolean) -> Unit =
                                remember(
                                    navController,
                                    coroutineScope,
                                    topAppBarScrollBehavior,
                                    playerBottomSheetState,
                                    currentBackStackEntry,
                                ) {
                                    { screen: Screens, isSelected: Boolean ->
                                        if (playerBottomSheetState.isExpanded) {
                                            playerBottomSheetState.collapseSoft()
                                        }
                                        if (isSelected) {
                                            val targetEntry =
                                                try {
                                                    val route = navController.currentBackStackEntry?.destination?.route
                                                    if (route == SearchRoutes.ROUTE || route == "search_input") {
                                                        // For search screens, use search_input entry
                                                        navController.getBackStackEntry("search_input")
                                                    } else {
                                                        // For other screens, use current entry
                                                        navController.currentBackStackEntry
                                                    }
                                                } catch (e: Exception) {
                                                    null
                                                }

                                            // Use appropriate key based on screen type
                                            if (screen == Screens.Search) {
                                                val current = targetEntry?.savedStateHandle?.get<Int>("scrollToTopCount") ?: 0
                                                targetEntry?.savedStateHandle?.set("scrollToTopCount", current + 1)
                                            } else {
                                                targetEntry?.savedStateHandle?.set("scrollToTop", true)
                                            }

                                            coroutineScope.launch {
                                                topAppBarScrollBehavior.state.resetHeightOffset()
                                            }
                                        } else {
                                            navController.navigate(screen.route) {
                                                popUpTo(navController.graph.startDestinationId) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        }
                                    }
                                }

                            val onSearchLongClick: () -> Unit =
                                remember(navController) {
                                    {
                                        navController.navigate("recognition") {
                                            launchSingleTop = true
                                        }
                                    }
                                }

                            // Pre-calculate values for graphicsLayer to avoid reading state during composition
                            val navBarTotalHeight = bottomInset + NavigationBarHeight

                            if (!showRail && currentRoute != "wrapped") {
                                Box {
                                    if (activePlayerConnection != null) {
                                        BottomSheetPlayer(
                                            state = playerBottomSheetState,
                                            navController = navController,
                                            pureBlack = pureBlack,
                                        )
                                    }

                                    AppNavigationBar(
                                        navigationItems = navigationItems,
                                        currentRoute = currentRoute,
                                        onItemClick = onNavItemClick,
                                        pureBlack = pureBlack,
                                        slimNav = slimNav,
                                        onSearchLongClick = onSearchLongClick,
                                        modifier =
                                            Modifier
                                                .align(Alignment.BottomCenter)
                                                .height(bottomInset + navPadding)
                                                // Use graphicsLayer instead of offset to avoid recomposition
                                                // graphicsLayer runs during draw phase, not composition phase
                                                .graphicsLayer {
                                                    val navBarHeightPx = navigationBarHeight.toPx()
                                                    val totalHeightPx = navBarTotalHeight.toPx()

                                                    translationY =
                                                        if (navBarHeightPx == 0f) {
                                                            totalHeightPx
                                                        } else {
                                                            // Read progress only during draw phase
                                                            val progress = playerBottomSheetState.progress.coerceIn(0f, 1f)
                                                            val slideOffset = totalHeightPx * progress
                                                            val hideOffset =
                                                                totalHeightPx * (1 - navBarHeightPx / NavigationBarHeight.toPx())
                                                            slideOffset + hideOffset
                                                        }
                                                },
                                    )

                                    Box(
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .align(Alignment.BottomCenter)
                                                .height(bottomInsetDp)
                                                // Use graphicsLayer for background color changes
                                                .graphicsLayer {
                                                    val progress = playerBottomSheetState.progress
                                                    alpha =
                                                        if (progress > 0f ||
                                                            (useNewMiniPlayerDesign && !shouldShowNavigationBar)
                                                        ) {
                                                            0f
                                                        } else {
                                                            1f
                                                        }
                                                }.background(baseBg),
                                    )
                                }
                            } else {
                                if (currentRoute != "wrapped") {
                                    if (activePlayerConnection != null) {
                                        BottomSheetPlayer(
                                            state = playerBottomSheetState,
                                            navController = navController,
                                            pureBlack = pureBlack,
                                        )
                                    }
                                }

                                Box(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .align(Alignment.BottomCenter)
                                            .height(bottomInsetDp)
                                            // Use graphicsLayer for background color changes
                                            .graphicsLayer {
                                                val progress = playerBottomSheetState.progress
                                                alpha =
                                                    if (progress > 0f || (useNewMiniPlayerDesign && !shouldShowNavigationBar)) 0f else 1f
                                            }.background(baseBg),
                                )
                            }
                        },
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
                    ) {
                        Row(Modifier.fillMaxSize()) {
                            val onRailItemClick: (Screens, Boolean) -> Unit =
                                remember(navController, coroutineScope, topAppBarScrollBehavior, playerBottomSheetState) {
                                    { screen: Screens, isSelected: Boolean ->
                                        if (playerBottomSheetState.isExpanded) {
                                            playerBottomSheetState.collapseSoft()
                                        }

                                        if (isSelected) {
                                            navController.currentBackStackEntry?.savedStateHandle?.set("scrollToTop", true)
                                            coroutineScope.launch {
                                                topAppBarScrollBehavior.state.resetHeightOffset()
                                            }
                                        } else {
                                            navController.navigate(screen.route) {
                                                popUpTo(navController.graph.startDestinationId) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        }
                                    }
                                }

                            val onRailSearchLongClick: () -> Unit =
                                remember(navController) {
                                    {
                                        navController.navigate("recognition") {
                                            launchSingleTop = true
                                        }
                                    }
                                }

                            if (showRail && currentRoute != "wrapped") {
                                AppNavigationRail(
                                    navigationItems = navigationItems,
                                    currentRoute = currentRoute,
                                    onItemClick = onRailItemClick,
                                    pureBlack = pureBlack,
                                    onSearchLongClick = onRailSearchLongClick,
                                )
                            }
                            Box(Modifier.weight(1f)) {
                                // NavHost with animations (Material 3 Expressive style)
                                NavHost(
                                    navController = navController,
                                    startDestination =
                                        when (tabOpenedFromShortcut ?: defaultOpenTab) {
                                            NavigationTab.HOME -> Screens.Home
                                            NavigationTab.LIBRARY -> Screens.Library
                                            NavigationTab.SEARCH -> Screens.Search
                                        }.route,
                                    enterTransition = {
                                        // Opened from the full player, the theme gallery is there at once
                                        // under the player, which then fades away over it (see
                                        // BottomSheetPlayer); sliding it in would show this page for a moment.
                                        if (targetState.destination.route == PLAYER_DESIGN_GALLERY_ROUTE &&
                                            playerBottomSheetState.isExpanded
                                        ) {
                                            return@NavHost EnterTransition.None
                                        }
                                        val currentRouteIndex = routeIndexMap[targetState.destination.route] ?: -1
                                        val previousRouteIndex = routeIndexMap[initialState.destination.route] ?: -1

                                        if (currentRouteIndex == -1 || currentRouteIndex > previousRouteIndex) {
                                            slideInHorizontally { it / 8 } + fadeIn(tween(200))
                                        } else {
                                            slideInHorizontally { -it / 8 } + fadeIn(tween(200))
                                        }
                                    },
                                    exitTransition = {
                                        if (targetState.destination.route == PLAYER_DESIGN_GALLERY_ROUTE &&
                                            playerBottomSheetState.isExpanded
                                        ) {
                                            return@NavHost ExitTransition.None
                                        }
                                        val currentRouteIndex = routeIndexMap[initialState.destination.route] ?: -1
                                        val targetRouteIndex = routeIndexMap[targetState.destination.route] ?: -1

                                        if (targetRouteIndex == -1 || targetRouteIndex > currentRouteIndex) {
                                            slideOutHorizontally { -it / 8 } + fadeOut(tween(200))
                                        } else {
                                            slideOutHorizontally { it / 8 } + fadeOut(tween(200))
                                        }
                                    },
                                    popEnterTransition = {
                                        val currentRouteIndex = routeIndexMap[targetState.destination.route] ?: -1
                                        val previousRouteIndex = routeIndexMap[initialState.destination.route] ?: -1

                                        if (previousRouteIndex != -1 && previousRouteIndex < currentRouteIndex) {
                                            slideInHorizontally { it / 8 } + fadeIn(tween(200))
                                        } else {
                                            slideInHorizontally { -it / 8 } + fadeIn(tween(200))
                                        }
                                    },
                                    popExitTransition = {
                                        val currentRouteIndex = routeIndexMap[initialState.destination.route] ?: -1
                                        val targetRouteIndex = routeIndexMap[targetState.destination.route] ?: -1

                                        if (currentRouteIndex != -1 && currentRouteIndex < targetRouteIndex) {
                                            slideOutHorizontally { -it / 8 } + fadeOut(tween(200))
                                        } else {
                                            slideOutHorizontally { it / 8 } + fadeOut(tween(200))
                                        }
                                    },
                                    modifier = Modifier.nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
                                ) {
                                    navigationBuilder(
                                        navController = navController,
                                        scrollBehavior = topAppBarScrollBehavior,
                                        latestVersionName = latestVersionName,
                                        activity = this@MainActivity,
                                        snackbarHostState = snackbarHostState,
                                    )
                                }
                            }
                        }
                    }

                    BottomSheetMenu(
                        state = LocalMenuState.current,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )

                    BottomSheetPage(
                        state = LocalBottomSheetPageState.current,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )

                    if (showSpotifyImportDialog) {
                        SpotifyImportDialog(
                            onDismiss = { showSpotifyImportDialog = false },
                            onImported = { playlistId ->
                                showSpotifyImportDialog = false
                                navController.navigate("local_playlist/$playlistId")
                            },
                        )
                    }

                    sharedSong?.let { song ->
                        playerConnection?.let {
                            Dialog(
                                onDismissRequest = { sharedSong = null },
                                properties = DialogProperties(usePlatformDefaultWidth = false),
                            ) {
                                Surface(
                                    modifier = Modifier.padding(24.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    color = AlertDialogDefaults.containerColor,
                                    tonalElevation = AlertDialogDefaults.TonalElevation,
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        YouTubeSongMenu(
                                            song = song,
                                            onDismiss = { sharedSong = null },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            }
        }
    }

    /**
     * Handles the ACTION_RECOGNITION intent sent from the Music Recognizer Widget.
     * Always navigates to the recognition screen to show the result.
     */
    private fun handleRecognitionIntent(
        intent: Intent,
        navController: NavHostController,
    ) {
        if (intent.action != ACTION_RECOGNITION) return
        val autoStart = intent.getBooleanExtra(EXTRA_AUTO_START_RECOGNITION, false)
        intent.action = null
        intent.removeExtra(EXTRA_AUTO_START_RECOGNITION)
        navController.navigate(if (autoStart) "recognition?autoStart=true" else "recognition") {
            launchSingleTop = true
        }
    }

    private sealed class WidgetTargetRoute(val route: String) {
        data class LocalPlaylist(val id: String) : WidgetTargetRoute("local_playlist/$id")
        data class OnlinePlaylist(val id: String) : WidgetTargetRoute("online_playlist/$id")
        data object LikedSongs : WidgetTargetRoute("auto_playlist/liked")
        data object DownloadedSongs : WidgetTargetRoute("auto_playlist/downloaded")
        data class TopSongs(val limit: String) : WidgetTargetRoute("top_playlist/$limit")
    }

    // Launcher shortcut "Shuffle": plays Liked songs shuffled straight away, the same
    // way the playlist widget starts a playlist.
    private fun handleShuffleShortcutIntent(intent: Intent) {
        if (intent.action != ACTION_SHUFFLE_LIKED) return
        intent.action = null
        val serviceIntent = Intent(this, MusicService::class.java).apply {
            action = PlaylistWidgetReceiver.ACTION_PLAY_TARGET
            putExtra(PlaylistWidgetReceiver.EXTRA_TARGET_TYPE, PlaylistWidgetReceiver.TARGET_TYPE_LIKED)
            putExtra(PlaylistWidgetReceiver.EXTRA_SHUFFLE, true)
        }
        runCatching { startService(serviceIntent) }
    }

    private fun handleWidgetTargetIntent(
        intent: Intent,
        navController: NavHostController,
    ) {
        if (intent.action != ACTION_OPEN_WIDGET_TARGET) return

        val targetType = intent.getStringExtra(EXTRA_WIDGET_TARGET_TYPE)
        val targetId = intent.getStringExtra(EXTRA_WIDGET_TARGET_ID)
        intent.action = null
        intent.removeExtra(EXTRA_WIDGET_TARGET_TYPE)
        intent.removeExtra(EXTRA_WIDGET_TARGET_ID)

        val normalizedTargetId = targetId?.takeIf { it.isNotBlank() }

        val targetRoute = when (targetType) {
            PlaylistWidgetReceiver.TARGET_TYPE_LOCAL ->
                normalizedTargetId?.let { WidgetTargetRoute.LocalPlaylist(it) }

            PlaylistWidgetReceiver.TARGET_TYPE_ONLINE ->
                normalizedTargetId?.let { WidgetTargetRoute.OnlinePlaylist(it) }

            PlaylistWidgetReceiver.TARGET_TYPE_LIKED ->
                WidgetTargetRoute.LikedSongs

            PlaylistWidgetReceiver.TARGET_TYPE_DOWNLOADED ->
                WidgetTargetRoute.DownloadedSongs

            PlaylistWidgetReceiver.TARGET_TYPE_TOP ->
                WidgetTargetRoute.TopSongs(normalizedTargetId ?: "50")

            else -> null
        } ?: return

        navController.navigate(targetRoute.route)
    }

    /**
     * Whether this intent is a link that starts something playing straight away: a song, or a
     * playlist opened from a watch link. Mirrors the branches of [handleDeepLinkIntent] that
     * call playQueue, so the service can skip loading the old queue for it.
     */
    private fun isPlayLink(intent: Intent?): Boolean {
        val uri = intent?.data ?: intent?.extras?.getString(Intent.EXTRA_TEXT)?.toUri() ?: return false
        val path = uri.pathSegments.firstOrNull()
        return when {
            uri.pathSegments.any { it.equals("listen", ignoreCase = true) } -> false
            path in setOf("playlist", "browse", "channel", "c", "search") -> false
            path == "watch" -> uri.getQueryParameter("v") != null || uri.getQueryParameter("list") != null
            uri.host == "youtu.be" -> path != null
            else -> uri.getQueryParameter("list") != null
        }
    }

    private fun handleDeepLinkIntent(
        intent: Intent,
        navController: NavHostController,
    ) {
        val uri = intent.data ?: intent.extras?.getString(Intent.EXTRA_TEXT)?.toUri() ?: return
        intent.data = null
        intent.removeExtra(Intent.EXTRA_TEXT)
        val coroutineScope = lifecycle.coroutineScope

        PlaylistLink.parse(uri)?.let { shared ->
            sharedPlaylist = shared
            return
        }

        when (val path = uri.pathSegments.firstOrNull()) {
            "playlist" -> {
                uri.getQueryParameter("list")?.let { playlistId ->
                    if (playlistId.startsWith("OLAK5uy_")) {
                        coroutineScope.launch(Dispatchers.IO) {
                            YouTube
                                .albumSongs(playlistId)
                                .onSuccess { songs ->
                                    songs.firstOrNull()?.album?.id?.let { browseId ->
                                        withContext(Dispatchers.Main) {
                                            navController.navigate("album/$browseId")
                                        }
                                    }
                                }.onFailure { reportException(it) }
                        }
                    } else {
                        navController.navigate("online_playlist/$playlistId")
                    }
                }
            }

            "browse" -> {
                uri.lastPathSegment?.let { browseId ->
                    navController.navigate("album/$browseId")
                }
            }

            "channel", "c" -> {
                uri.lastPathSegment?.let { artistId ->
                    navController.navigate("artist/$artistId")
                }
            }

            "search" -> {
                uri.getQueryParameter("q")?.let {
                    navController.navigate(SearchRoutes.resultRoute(it))
                }
            }

            else -> {
                val videoId =
                    when {
                        path == "watch" -> uri.getQueryParameter("v")
                        uri.host == "youtu.be" -> uri.pathSegments.firstOrNull()
                        else -> null
                    }

                val playlistId = uri.getQueryParameter("list")

                if (videoId != null) {
                    coroutineScope.launch(Dispatchers.IO) {
                        // The lookup is only for the title and artwork that show while the song
                        // loads. It comes back empty for some videos and mixes, and the link still
                        // names a song, so the id from the link is what the player is given —
                        // handing it an empty one left the app showing a song it could never play.
                        val first =
                            YouTube
                                .queue(listOf(videoId), playlistId)
                                .getOrElse {
                                    reportException(it)
                                    null
                                }?.firstOrNull()
                        withContext(Dispatchers.Main) {
                            playerConnection?.playQueue(
                                YouTubeQueue(
                                    WatchEndpoint(videoId = first?.id ?: videoId, playlistId = playlistId),
                                    first?.toMediaMetadata(),
                                ),
                            )
                        }
                    }
                } else if (playlistId != null) {
                    coroutineScope.launch(Dispatchers.IO) {
                        YouTube
                            .queue(null, playlistId)
                            .onSuccess { queue ->
                                val firstItem = queue.firstOrNull()
                                withContext(Dispatchers.Main) {
                                    playerConnection?.playQueue(
                                        YouTubeQueue(
                                            WatchEndpoint(videoId = firstItem?.id, playlistId = playlistId),
                                            firstItem?.toMediaMetadata(),
                                        ),
                                    )
                                }
                            }.onFailure {
                                reportException(it)
                            }
                    }
                }
            }
        }
    }

    @SuppressLint("ObsoleteSdkInt")
    private fun setSystemBarAppearance(isDark: Boolean) {
        WindowCompat.getInsetsController(window, window.decorView.rootView).apply {
            isAppearanceLightStatusBars = !isDark
            isAppearanceLightNavigationBars = !isDark
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            window.statusBarColor = (if (isDark) Color.Transparent else Color.Black.copy(alpha = 0.2f)).toArgb()
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            window.navigationBarColor = (if (isDark) Color.Transparent else Color.Black.copy(alpha = 0.2f)).toArgb()
        }
    }
}

val LocalDatabase = staticCompositionLocalOf<MusicDatabase> { error("No database provided") }
val LocalNavController = staticCompositionLocalOf<NavController> { error("No NavController provided") }
// Defaults to null rather than throwing: the type is nullable and every reader
// already null-checks. Previews shown before the player exists (onboarding, the
// Look & Feel phone frame) live outside the provider and must not crash.
val LocalPlayerConnection = staticCompositionLocalOf<PlayerConnection?> { null }
val LocalPlayerBottomSheetState = staticCompositionLocalOf<BottomSheetState?> { null }
val LocalPlayerAwareWindowInsets = compositionLocalOf<WindowInsets> { error("No WindowInsets provided") }
val LocalDownloadUtil = staticCompositionLocalOf<DownloadUtil> { error("No DownloadUtil provided") }
val LocalSyncUtils = staticCompositionLocalOf<SyncUtils> { error("No SyncUtils provided") }
val LocalListenTogetherManager = staticCompositionLocalOf<com.lunara.app.listentogether.ListenTogetherManager?> { null }
val LocalChangelogState = staticCompositionLocalOf<MutableState<Boolean>> { error("No LocalChangelogState provided") }
val LocalIsPlayerExpanded = compositionLocalOf { false }
