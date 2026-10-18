# AudiBook

Android app that imports EPUBs and reads them aloud with Google TTS or on-device neural voices (sherpa-onnx), highlighting the sentence being spoken.

## Build & test

```sh
./gradlew :app:testFossDebugUnitTest   # JVM unit tests (parser, splitter, WAV decoder, update logic)
./gradlew :app:assembleFossDebug
./gradlew :app:installFossDebug
```

- Two product flavors (dimension `distribution`):
  - `foss` is the GitHub build. It has the in-app updater (`src/foss`: `GitHubAppUpdater`, `REQUEST_INSTALL_PACKAGES`, a FileProvider).
  - `gplay` is the Play build. It has no self-update code or install permission; "Check for updates" just opens the Play listing.
  - Each flavor binds `AppUpdater` in its own `core/di/FlavorModule.kt`. Never add self-update code or the install permission to `src/main`.
- AGP 9 with built-in Kotlin (no `kotlin-android` plugin). Compiled classes land in `app/build/intermediates/built_in_kotlinc/`, not `tmp/kotlin-classes`.
- Room uses KSP. Dependency versions live in `gradle/libs.versions.toml`.
- sherpa-onnx comes from JitPack (`com.github.k2-fsa.sherpa-onnx:sherpa-onnx`, AAR). The JitPack repo in `settings.gradle.kts` is restricted to that group.
- ABI splits produce one APK per ABI (~40–55 MB release, mostly sherpa-onnx native libs) plus a universal APK (~140 MB). Install the matching one, e.g. `app/build/outputs/apk/foss/debug/app-foss-arm64-v8a-debug.apk`.
- Release signing reads `key.properties` (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`) from the repo root; without it, release APKs are unsigned. Never commit `key.properties` or keystores.

## CI/CD (`.github/workflows`)

- `dev.yml`: on push/PR to `main`, runs unit tests and uploads the universal debug APK.
- `release.yml`: on a `v*.*` tag, builds signed per-ABI foss APKs and creates a draft GitHub release. Needs the repo secrets `KEY_PROPERTIES` (file contents, with `storeFile=release.keystore`) and `RELEASE_KEYSTORE_BASE64` (`base64 -i release.keystore`).
- `publish-playstore.yml`: manual run; builds the gplay release AAB and uploads it as a draft to the chosen Play track (internal/alpha/beta/production). Also needs `PLAY_STORE_JSON_KEY` (service account JSON). Bump `versionCode` in `app/build.gradle.kts` before each upload; Play rejects reused codes.

## Architecture

Single `app` module, Compose + Koin + Media3. Package `com.paulcoding.audibook`:

- `core/epub`: dependency-free EPUB parsing (`ZipFile` + Jsoup) into chapters → paragraphs → sentences. `SentenceSplitter` uses `java.text.BreakIterator`, not ICU, so it runs in JVM unit tests. Sentences are capped at 300 chars.
- `core/data`: Room `books` table (reading progress is stored as columns on it), DataStore `TtsSettings`, and `BookRepository` (import, parser + chapter LRU cache).
- `core/tts`: the `SpeechEngine` interface. Both engines render PCM ahead and play it through `playPipelined()` in `PcmPipeline.kt`.
  - `SystemTtsEngine`: Google TTS via `synthesizeToFile()`, then `WavReader`.
  - `SherpaTtsEngine`: sherpa-onnx `OfflineTts` on a single dedicated thread.
  - `ModelManager`: model catalog, download, and tar.bz2 extraction.
- `core/playback`:
  - `ReadAloudController`: the single source of truth for what's being read. Main-thread only.
  - `TtsPlayer`: a Media3 `SimpleBasePlayer` adapter.
  - `PlaybackService`: the `MediaSessionService`.
  - `AudioFocus`: audio focus and headphone-unplug ("becoming noisy") handling.
- `feature/{library,reader,settings}`: screens + ViewModels. Routes are typed in `core/ui/Routes.kt`; DI lives in `core/di/AppModule.kt` and `ViewModelModule.kt`.

## Non-obvious decisions (don't undo without reason)

- **Never use `TextToSpeech.speak()` for playback.** The framework only synthesizes ~500 ms ahead, which caused multi-second gaps between sentences on a Snapdragon 665 device. Pre-render with `synthesizeToFile()` and play the PCM yourself.
- **Audio must play from this app's process.** Android picks the media-button session by which UID is producing audio. If the TTS engine's process plays the audio, headset and media keys don't reach the app.
- **Automatic voice choice** prefers installed offline voices and ranks Google's `legacySetLanguageVoice` voices (e.g. `vi-VN-language`) last: they go network-first even though they report `isNetworkConnectionRequired = false`. If a network voice fails, the app falls back to an offline voice once; if none exists, it shows an actionable error.
- The manifest needs `<queries>` for `android.intent.action.TTS_SERVICE` (API 30+ package visibility).
- `ReadAloudController` guards against cancelled playback jobs changing state (`playJob !== self`). Keep that when touching play/seek/restart logic.
- Pause stops synthesis. Resume restarts from the current sentence.
- `ReadAloudController.pause(byUser)`: a pause the user asked for (UI, notification, headset) cancels the sleep timer; interruptions (audio focus loss, unplugged headphones, the timer itself, switching books, voice preview) pass `byUser = false` and keep it. With auto-start on, Play starts the timer using the saved duration.

## Device testing notes

- vivo devices block `logcat` for apps entirely. To measure timing there, write to a file in `filesDir` and read it with `adb shell run-as com.paulcoding.audibook cat files/<name>`.
- Inspect app state with `adb shell run-as com.paulcoding.audibook sqlite3 databases/audibook.db 'select title,chapterIndex,sentenceIndex from books'`.
- Galaxy Fold devices have multiple displays, so `screencap` fails; use `uiautomator dump` instead.
- In zsh, `ADB="adb -s X"; $ADB ...` does not word-split. Use a wrapper script or call `adb -s X` directly.

## Releasing

- Bump `versionName` and `versionCode` in `app/build.gradle.kts`, then tag `v<versionName>`. `release.yml` fails if the tag doesn't match `versionName`; otherwise the updater would keep offering the same version.
- `release.yml` creates a **draft** release, so publish it. GitHub's `/releases/latest`, which the updater uses, ignores drafts and pre-releases.
- Keep APK asset names ending in `-<abi>.apk` (or `-<abi>-release.apk`). `pickApk()` in `core/update/UpdateLogic.kt` picks the asset for the device's CPU type that way.
