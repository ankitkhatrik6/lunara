# Lunara — Modern Android Music Player

Lunara is a production-quality, independently designed music streaming and local audio player for Android. Built with 100% Kotlin, Jetpack Compose, Material 3, AndroidX Media3 (ExoPlayer), Room Database, and Clean Architecture.

Lunara adheres to a dark-first aesthetic featuring a charcoal palette (`#0B0D0F`), vibrant electric teal accent (`#35D6C2`), and lavender highlights (`#8B7CFF`).

---

## Features

- **Audio Engine**: Powered by AndroidX Media3 ExoPlayer & MediaSessionService for gapless audio, audio attributes, wake lock, background playback, and system media notifications.
- **Throttle-Proof Streaming**: every `*.googlevideo.com` request goes out as a *closed* byte range, carrying the user agent of the client identity that minted that URL. YouTube throttles a range-less request to ~31 KB/s but serves the same URL at several MB/s with `Range: bytes=0-<size - 1>`, so the data source closes each request (the whole file when the size is known from InnerTube, transparently chained 1 MiB ranges otherwise), resumes a range the CDN cuts short, and lets a `403` reach the player instead of silently ending the track - so a failing URL fails over to the next resolved one in seconds.
- **Client-Aware Stream Resolution**: the resolver fails over across seven InnerTube client identities (two Oculus VR builds, iOS, Android, visionOS, the embedded web player and TVHTML5), remembers which one minted every URL it hands out, and deep-probes each identity with a range request ~60% into the file. YouTube answers `206` to the *first* range of a URL whose signature it will not honour to the end and `403` to every range after that - the exact reason playback used to start fine and then die mid track - so identities caught serving only a prefix are pushed to the back of the chain and the next attempt starts on one that streams whole files.
- **YouTube Music Streaming Source**: Powered by InnerTube / Piped extractor architecture matching Blazify and ViMusic. Searches songs, albums, and artists, resolving direct high-bitrate Opus (160kbps) and M4A audio streams on demand.
- **Multi-Provider Synced Lyrics**:
  - **Paxsenix / Apple Music Provider**: Fast duration-matched and word-synced lyrics with agent-tag parsing (`v1`, `v2`, `bg`).
  - **LRCLIB Provider**: Exact `.lrc` and fuzzy title/artist matching.
  - **KuGou & Binimum / LyricsPlus** fallbacks.
- **Instant Offline Downloads**: `DownloadRepository` streams a track's *progressive* audio (never an HLS playlist, which used to be saved under a song's name and rejected by the player as "unsupported audio format") straight into the app's private media folder (`filesDir/lunara_media`) with chunked I/O, closed byte ranges and throttled Room progress — no shared Downloads folder, no WorkManager scheduling delay — plus atomic temp-file persistence and validation: a playlist, stub or truncated file is discarded instead of being recorded as a finished download.
- **Offline-First Feed**: without a connection Home swaps the network rails for your downloads and the music already on the device, with a friendly banner instead of an error card.
- **Local Audio (`MediaStore`)**: Scans on-device audio files alongside streamed music with folder indexing.
- **Dynamic Queue Management**: Reorderable playback queue, radio mode, queue autogeneration, shuffle, and cycle repeat modes (Off, All, One).
- **Local Persistence**: Room Database storing favorites, listening history, playlists, downloads, and cached tracks without any tracking or accounts.
- **Branded Start-Up**: the launcher icon, the Android 12 splash screen and the in-app loading screen all use the Lunara logo; regenerate the derived assets from the master artwork with `python3 tools/generate_brand_assets.py logo.png`.

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
│       │   ├── java/com/lunara/app/
│       │   │   ├── LunaraApplication.kt
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
signed release APK + AAB and attaches them to a new GitHub Release. Pushes to `master`
run the same build without publishing, so every change is compile-checked:

```bash
git tag v1.0.0
git push origin v1.0.0
```

Download the `.apk` / `.aab` from the **Releases** page of the repository.

Repository secrets used to sign releases (already configured for this repository):

| Secret | Purpose |
| --- | --- |
| `RELEASE_KEYSTORE_BASE64` | Base64-encoded `.jks` / `.keystore` used to sign releases |
| `RELEASE_KEYSTORE_PASSWORD` | Keystore password |
| `RELEASE_KEY_ALIAS` | Key alias (default `lunara`) |
| `RELEASE_KEY_PASSWORD` | Key password |

> **Why this matters.** Android only replaces an installed app when the new APK is signed with the
> *same* key. A throwaway per-build key produces `INSTALL_FAILED_UPDATE_INCOMPATIBLE`
> ("package appears to be invalid" / "app not installed"), so the app has to be uninstalled first —
> losing its data. Keep the keystore safe: if it is ever lost, every user has to uninstall once more.
>
> A local backup of the keystore used by this repository lives at `keystore/lunara-release.jks`
> with its passwords in `keystore/keystore.properties` (both git-ignored). **Back that folder up.**
> Without the secret, CI falls back to `keystore/lunara-release.jks` if it is present, and only
> generates a throwaway key — with a warning — when neither exists.

### Option B — Android Studio / local Gradle

1. Open this repository root directory in **Android Studio (Ladybug / Koala or newer)**.
2. Let Gradle sync dependencies from the Version Catalog.
3. Select a device or emulator running **API 24+** (Android 7.0+).
4. Press **Run** (`Shift + F10`).

Command line:

```bash
gradle assembleDebug      # debug APK
gradle assembleRelease    # release APK (signed when LUNARA_KEYSTORE_* env vars are set)
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

Lunara does not collect telemetry, analytics, user identifiers, or advertising IDs. All user data (favorites, history, playlists, downloads) resides strictly on the local device. Lunara does not host third-party copyrighted content or bypass DRM.

---

## License

MIT License. See [LICENSE](LICENSE) for details.
