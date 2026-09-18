# SpaceAudio 

Modern, offline-first Android music player with modular audio source import (YouTube, Direct Audio URLs), Room local persistence, and Jetpack Compose cosmic design.

## Features

- **Primary Audio Source (YouTube)**:
  - Input and validate YouTube URLs (`watch`, `youtu.be`, `shorts`, `m.youtube.com`).
  - Pre-download metadata inspection (Title, Author/Channel, Duration, Thumbnail).
  - Custom track renaming before download.
  - Automatic download and save to local `Downloads/` directory.
  - 100% offline local playback (no ongoing streaming).
- **Direct Audio URLs**:
  - Support for direct `.mp3`, `.m4a`, `.aac`, `.ogg`, `.flac`, `.wav` links.
- **AudioSourceProvider Architecture**:
  - Extensible, fully decoupled provider contract (`canHandle`, `normalizeUrl`, `analyze`, `obtainAudio`).
- **Room Database**:
  - Full local track indexing, custom folders, favorites, playlists, play counts.
  - **Dynamic 5-Minute `[NEW]` Badge**: Highlights freshly imported songs with a neon cyan pulse for 5 minutes (`isNewUntilTimestamp`).
- **Media3 / ExoPlayer Offline Engine**:
  - Foreground playback with rich notification controls.
  - Smart unrepeated Shuffle, Repeat modes (OFF, ALL, ONE), speed control, and live queue management.
- **Cosmic UI (Jetpack Compose)**:
  - Deep space aesthetic with neon cyan & nebula violet accents.
  - Animated spinning vinyl, interactive waveform bars, and persistent Mini-Player.
