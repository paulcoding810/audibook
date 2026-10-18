![Build](https://github.com/paulcoding810/audibook/actions/workflows/dev.yml/badge.svg?branch=main)
![GitHub release](https://img.shields.io/github/v/release/paulcoding810/audibook?include_prereleases)

# AudiBook

AudiBook is an Android app that turns your EPUB books into audiobooks. It reads aloud with Google Text-to-Speech or with neural voices that run fully on your phone, and highlights each sentence as it's spoken.

## Features

- **Import any EPUB** (EPUB 2 and 3): the cover, title, author and table of contents are picked up automatically.
- **Read or listen in one screen**:
  - While playing, the text follows the current sentence.
  - While paused, it's a plain reader. Tap any sentence to start reading aloud from there.
- **Seamless playback**: sentences are rendered ahead of time, so there are no gaps between sentences or chapters, even on slower phones.
- **Two kinds of voices**:
  - **Google Text-to-Speech**: many languages. Offline voices are preferred automatically, with a fallback if an online voice fails.
  - **On-device AI voices**: [Piper](https://github.com/rhasspy/piper), Kitten and [Kokoro](https://huggingface.co/hexgrad/Kokoro-82M) via [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx). They are downloaded in the app and run offline. Each is labelled **Light** (keeps up on any phone) or **Heavy** (more natural, needs a fast phone and more battery).
- **Background listening**: playback continues with the screen off, with notification, lock-screen and headset controls. It pauses for calls and when headphones are unplugged.
- **Sleep timer**: 5–120 minutes. It can start automatically whenever you press Play, and remembers your last choice.
- **Reading controls**: speed, pitch (Google voices), and chapter or sentence skipping.
- **Picks up where you left off**: progress is saved per sentence, and the app reopens your last book.
- **Light and dark theme**, with Material You colours.

## Download

- **GitHub**: grab the APK for your phone from [Releases](https://github.com/paulcoding810/audibook/releases). Most phones need `arm64-v8a`; if unsure, use `universal`. This build can update itself: Settings → Check for updates.
- **Google Play**: coming soon.

Requires Android 8.0 (API 26) or newer.

## Voices

| Voice | Engine | Size | Weight |
|---|---|---|---|
| Google Text-to-Speech | System TTS | (installed with the phone) | Light |
| Piper (Amy, Lessac, Ryan, Alba, LibriTTS-R, Vietnamese) | sherpa-onnx | ~20 MB each | Light |
| Kitten Nano (8 voices) | sherpa-onnx | 31 MB | Light |
| Kokoro (11 voices / 103 multilingual voices) | sherpa-onnx | 103–147 MB | Heavy |

To get more Google voices, open Settings → Install more voices. Download the offline voice for your book's language for the most reliable playback.

## Built with

- Kotlin, Jetpack Compose, Material 3
- Koin, Room, DataStore, Navigation Compose
- Media3 `MediaSessionService` for background playback and media controls
- [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) for on-device neural TTS
- Jsoup for EPUB parsing, Ktor for downloads, Coil for covers

## Building

```sh
./gradlew :app:installFossDebug        # GitHub build on a connected device
./gradlew :app:testFossDebugUnitTest   # unit tests
```

There are two product flavors:
- `foss`: the GitHub build, with the in-app updater.
- `gplay`: the Google Play build, without self-update code.

Builds are split per CPU type; install the APK that matches your device from `app/build/outputs/apk/foss/debug/`.

Release builds are signed when a `key.properties` file (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`) exists in the project root. Otherwise they come out unsigned.

### Releasing

1. Bump `versionName` and `versionCode` in `app/build.gradle.kts`.
2. Tag `v<versionName>` and push. CI builds signed APKs and creates a draft release.
3. Publish the draft. The in-app updater only sees published releases.

## Contributing

Contributions are welcome! Please feel free to open an issue or submit a Pull Request.
