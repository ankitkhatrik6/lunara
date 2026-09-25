# Dhunya (धुँया) — Modern Android Music Player

Dhunya is a production-quality, independently designed music streaming and local audio player for Android. Built with 100% Kotlin, Jetpack Compose, Material 3, AndroidX Media3 (ExoPlayer), Room Database, and Clean Architecture.

Dhunya adheres to a dark-first aesthetic featuring a charcoal palette (`#0B0D0F`), vibrant electric teal accent (`#35D6C2`), and lavender highlights (`#8B7CFF`).

---

## Features

- **Audio Engine**: Powered by AndroidX Media3 ExoPlayer & MediaSessionService for gapless audio, audio attributes, wake lock, background playback, and system media notifications.
- **YouTube Music Streaming Source**: Powered by InnerTube / Piped extractor architecture matching Blazify and ViMusic. Searches songs, albums, and artists, resolving direct high-bitrate Opus (160kbps) and M4A audio streams on demand.
- **Multi-Provider Synced Lyrics**:
  - **Paxsenix / Apple Music Provider**: Fast duration-matched and word-synced lyrics with agent-tag parsing (`v1`, `v2`, `bg`).
  - **LRCLIB Provider**: Exact `.lrc` and fuzzy title/artist matching.
  - **KuGou & Binimum / LyricsPlus** fallbacks.
- **Offline Download Manager & WorkManager**: Robust background file downloader backed by `DownloadRepository` and `WorkManager` (`MusicDownloadWorker`) with foreground service notifications, retry logic, network constraints, atomic file persistence, Room DB tracking, and automatic offline lyrics caching.
- **Local Audio (`MediaStore`)**: Scans on-device audio files alongside streamed music with folder indexing.
- **Dynamic Queue Management**: Reorderable playback queue, radio mode, queue autogeneration, shuffle, and cycle repeat modes (Off, All, One).
- **Local Persistence**: Room Database storing favorites, listening history, playlists, downloads, and cached tracks without any tracking or accounts.

---

## Tech Stack & Architecture

- **Language**: Kotlin 2.0+
- **UI Framework**: Jetpack Compose with Material 3
- **Architecture**: Clean Architecture + MVVM + Unidirectional Data Flow
- **Dependency Injection**: Dagger Hilt
- **Playback Engine**: AndroidX Media3 ExoPlayer (`1.5.0`) + MediaSessionService
- **Networking**: Ktor Client (`3.0.1`) with OkHttp engine & kotlinx.serialization
- **Local Database**: Room (`2.6.1`) with Kotlin Flow
- **Key-Value Store**: AndroidX DataStore Preferences
- **Image Loading**: Coil Compose (`2.7.0`)
- **Build System**: Gradle Kotlin DSL with Version Catalogs (`libs.versions.toml`)

---

## Project Structure

```
.
├── .github/
│   └── workflows/
│       └── release.yml        # Builds signed APK/AAB and publishes a GitHub Release
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/dhunya/app/
│       │   │   ├── DhunyaApplication.kt
│       │   │   ├── MainActivity.kt
│       │   │   ├── core/          # Network client, formatters, result wrappers
│       │   │   ├── data/          # Room DB, DAOs, lyrics/music APIs, repositories, workers
│       │   │   ├── di/            # Hilt modules (App, Network, DB, Repos)
│       │   │   ├── domain/        # Models, repository contracts, use cases
│       │   │   ├── navigation/    # Compose Navigation graph & routes
│       │   │   ├── player/        # Media3 ExoPlayer, MediaSession, QueueManager
│       │   │   ├── features/      # Home, Search, Player, Lyrics, Library, Downloads, Settings
│       │   │   └── ui/            # Theme, colors, typography, shapes, components
│       │   └── res/               # strings, themes, backup & data-extraction rules
│       └── test/                  # JUnit & MockK unit tests
├── gradle/
│   └── libs.versions.toml         # Gradle Version Catalog
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── LICENSE
└── README.md
```

---

## Building the App

### Option A — GitHub Releases (recommended)

Every tag matching `v*` triggers the **Android Release** workflow, which builds a
signed release APK + AAB and attaches them to a new GitHub Release:

```bash
git tag v1.0.0
git push origin v1.0.0
```

Download the `.apk` / `.aab` from the **Releases** page of the repository.

Optional repository secrets (if omitted, an ephemeral CI keystore is used):

| Secret | Purpose |
| --- | --- |
| `RELEASE_KEYSTORE_BASE64` | Base64-encoded `.jks` / `.keystore` used to sign releases |
| `RELEASE_KEYSTORE_PASSWORD` | Keystore password |
| `RELEASE_KEY_ALIAS` | Key alias (default `dhunya`) |
| `RELEASE_KEY_PASSWORD` | Key password |

### Option B — Android Studio / local Gradle

1. Open this repository root directory in **Android Studio (Ladybug / Koala or newer)**.
2. Let Gradle sync dependencies from the Version Catalog.
3. Select a device or emulator running **API 24+** (Android 7.0+).
4. Press **Run** (`Shift + F10`).

Command line:

```bash
gradle assembleDebug      # debug APK
gradle assembleRelease    # release APK (signed when DHUNYA_KEYSTORE_* env vars are set)
gradle test               # unit tests
```

> The repository does not ship a Gradle wrapper; CI installs Gradle `8.9` automatically.
> Locally, use the Gradle version bundled with Android Studio (8.9+) or run `gradle wrapper` once to generate it.

---

## Testing

```bash
gradle test
```

Or right-click `app/src/test` in Android Studio and run tests.

---

## Privacy & Ethics

Dhunya does not collect telemetry, analytics, user identifiers, or advertising IDs. All user data (favorites, history, playlists, downloads) resides strictly on the local device. Dhunya does not host third-party copyrighted content or bypass DRM.

---

## License

MIT License. See [LICENSE](LICENSE) for details.
