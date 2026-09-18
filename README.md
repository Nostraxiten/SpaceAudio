# SpaceAudio

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Platform" />
  <img src="https://img.shields.io/badge/Language-Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" alt="Language" />
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white" alt="Jetpack Compose" />
  <img src="https://img.shields.io/badge/Engine-Media3%20ExoPlayer-FF6F00?style=for-the-badge&logo=googleplay&logoColor=white" alt="Media3" />
  <img src="https://img.shields.io/badge/Python-Chaquopy%203.14-3776AB?style=for-the-badge&logo=python&logoColor=white" alt="Python" />
  <img src="https://img.shields.io/badge/Database-Room-009688?style=for-the-badge&logo=sqlite&logoColor=white" alt="Room" />
  <img src="https://img.shields.io/badge/Min%20SDK-26%20(Android%208.0)-lightgrey?style=for-the-badge" alt="Min SDK" />
  <img src="https://img.shields.io/badge/Target%20SDK-35%20(Android%2015)-blue?style=for-the-badge" alt="Target SDK" />
</p>

---

## Overview

SpaceAudio is a modern, offline-first music player application for Android. Built entirely with Kotlin, Jetpack Compose, and AndroidX Media3, it combines modular audio importing, multi-client YouTube extraction, automatic high-quality MP3 encoding via FFmpeg, local library management with Room, and continuous playback navigation.

---

## Key Features

### YouTube Audio Importer
* **Multi-Client Fallback Engine**: Uses a multi-tier client strategy (`ios`, `mweb`, `web_creator`, `android_creator`) to bypass HTTP 403 Forbidden restrictions and SABR challenges.
* **Dual-Redundant Pipeline**: Primary Python `yt-dlp` extractor with automatic secondary fallback to direct stream resolution.
* **Metadata Preview & Editing**: Live pre-download track inspection (Title, Channel, Duration, Artwork) with customizable Title and Artist tags.
* **Automatic MP3 Encoding**: Converts downloaded audio streams into high-quality `.mp3` files (VBR ~190 kbps) with embedded ID3v2.3 metadata.
* **Direct Audio URL Support**: Direct link importing for `.mp3`, `.m4a`, `.aac`, `.ogg`, `.flac`, and `.wav` formats.

### Playback Engine
* **AndroidX Media3 / ExoPlayer Integration**: Built on modern Android media components for high-efficiency audio playback.
* **Continuous Playback Navigation**:
  * **Sequential Mode**: Continuous loop cycling through the queue without boundary locks.
  * **Shuffle Mode**: Non-repeating random selection that avoids immediate track repetition, accompanied by a dynamic navigation history stack.
* **Lock Screen & Status Bar Controls**: `MediaSessionService` foreground playback with full media notification integration, interactive seekbar, and lock screen visibility.
* **Playback Speed Control**: Real-time adjustable speed scaling from 0.5x to 2.0x.

### Local Library & Room Database
* **Offline-First Storage**: Audio files are stored locally in the application's storage without requiring an active network connection during playback.
* **Dynamic `[NEW]` Badge**: Tracks imported within the last 5 minutes receive an active highlight indicator in the library.
* **Organized Organization**: Track grouping by folders, favorites, custom playlists, and play count tracking.

### UI & Aesthetics
* **Space-Themed Design**: Dark cosmic palette with neon cyan, laser pink, and nebula violet accents.
* **Interactive Components**: Glassmorphic cards, animated vinyl disc preview, dynamic waveform bars, and responsive bottom sheets.

---

## Architecture & Technology Stack

| Layer | Technologies / Libraries |
|---|---|
| **Architecture** | Clean Architecture, MVVM, Repository Pattern, Dependency Injection |
| **User Interface** | Jetpack Compose, Material 3, Coil Compose, Compose Animations |
| **Audio Engine** | AndroidX Media3 (ExoPlayer, MediaSession, MediaSessionService) |
| **Download Pipeline** | Chaquopy (Python 3.14), yt-dlp, FFmpegKit Audio, OkHttp 3 |
| **Local Persistence** | AndroidX Room (SQLite), DataStore Preferences |
| **Asynchronous & Concurrency** | Kotlin Coroutines, StateFlow, SharedFlow |

---

## Audio Download Pipeline

```
[User Input URL]
       │
       ▼
[AudioSourceManager]
       │
       ├──► Primary: YtDlpAudioDownloader (Python yt-dlp / iOS multi-client)
       │         │
       │         ├── [Success] ──► Raw Stream
       │         └── [Failure] ──► Fallback: YouTubeStreamExtractor (OkHttp Stream)
       │                                     │
       │                                     └──► Raw Stream
       ▼
[FFmpegKit Audio Encoder]
       │
       ├── Transcode: libmp3lame (VBR Q2)
       ├── Embed ID3v2.3: Title, Artist, Album, SpaceAudio Tags
       └── Save to App Storage (Downloads folder)
       │
       ▼
[Room Database] ──► TrackEntity Indexed with 5-min NEW Badge
```

---

## Project Structure

```
SpaceAudio/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/spaceaudio/app/
│   │   │   │   ├── core/
│   │   │   │   │   └── source/           # Audio source providers & downloaders
│   │   │   │   │       ├── direct/       # Direct URL provider
│   │   │   │   │       └── youtube/      # YouTube provider, parser & stream extractor
│   │   │   │   ├── data/
│   │   │   │   │   ├── local/            # Room Database, DAOs & Entities
│   │   │   │   │   ├── repository/       # Audio repository implementation
│   │   │   │   │   └── storage/          # Local file manager
│   │   │   │   ├── di/                   # App container & dependency graph
│   │   │   │   ├── player/               # Media3 PlayerController, State & Service
│   │   │   │   │   └── service/          # MediaSessionService implementation
│   │   │   │   └── ui/                   # Jetpack Compose Screens & Components
│   │   │   │       ├── components/       # Glassmorphic cards, buttons, waveform
│   │   │   │       ├── screens/          # Library, Player, Import, Folders, Playlists
│   │   │   │       └── theme/            # Colors, typography, shapes
│   │   │   ├── python/
│   │   │   │   └── youtube_downloader.py # Multi-client yt-dlp Python engine
│   │   │   └── res/                      # Drawables, layout resources, manifest
│   │   └── test/                         # Unit tests
│   └── build.gradle.kts                  # App-level build configuration
├── gradle/                               # Gradle wrapper and version catalogs
└── README.md                             # Project documentation
```

---

## Permissions

SpaceAudio requests the following permissions for audio playback, network fetching, and local storage:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.READ_MEDIA_AUDIO" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
```

---

## Building from Source

### Prerequisites
* Android Studio Ladybug (2024.2.1) or newer
* JDK 17
* Android SDK (API 35)
* Python 3.10+ (for Chaquopy build toolchain)

### Build Steps

1. Clone the repository:
```bash
git clone https://github.com/Nostraxiten/SpaceAudio.git
cd SpaceAudio
```

2. Open the project in Android Studio or compile using the Gradle wrapper:
```bash
./gradlew assembleDebug
```

3. Locate the output APK:
```
app/build/outputs/apk/debug/SpaceAudio.apk
```

---

## License

This project is licensed under the Apache License 2.0. See the LICENSE file for details.
