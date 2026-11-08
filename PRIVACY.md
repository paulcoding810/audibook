# AudiBook privacy policy

Effective November 8, 2026. Applies to AudiBook for Android (Google Play and GitHub builds) and desktop.

AudiBook has no accounts, analytics, ads or tracking. The developer receives no data about you or your books.

## What stays on your device

- The EPUB files you add, their covers, your reading progress and your settings.
- Voice models you download.

None of this is sent to the developer.

## When AudiBook uses the network

| Request | Sent to | Why |
| --- | --- | --- |
| Voice list and voice downloads | GitHub (`api.github.com`, `github.com`) | To show and download on-device voices from the [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) project |
| Update checks and update downloads (GitHub and desktop builds only, when you tap "Check for updates") | GitHub | To find and download a newer AudiBook release |

These requests include no information about your books. As with any web request, GitHub sees your IP address; see [GitHub's privacy statement](https://docs.github.com/en/site-policy/privacy-policies/github-general-privacy-statement). The Google Play build checks for updates by opening the Play Store instead.

## Speech engines

- **On-device AI voices** run entirely on your device.
- **Google Text-to-Speech** (Android) and the built-in voices on desktop are provided by your operating system. AudiBook prefers voices that work offline. If you choose a voice that needs the internet, your device's speech engine sends the text being read to its provider under that provider's own privacy policy (for Google, the [Google Privacy Policy](https://policies.google.com/privacy)).

## Backups

- On Android, the system's automatic backup can copy AudiBook's library database and settings (not books or voices) to your Google account. You control this in your device's backup settings.
- "Export library" writes a `.audibook` file only where you choose to save it.

## Permissions (Android)

- **Internet**: voice and update downloads described above.
- **Foreground service (media playback), notifications, wake lock**: keep reading aloud with the screen off and show playback controls; the wake lock also lets a shake resume playback for up to 5 minutes after the sleep timer stops it.
- **Install packages** (GitHub build only): install an update you chose to download.

## Children

AudiBook isn't directed at children and collects no personal information from anyone.

## Changes and contact

Changes to this policy are published in this file, in the AudiBook repository. Questions: open an issue at https://github.com/paulcoding810/audibook/issues.
