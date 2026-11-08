# Google Play Console answers

Draft answers for the declarations Play asks for before the `gplay` build can be published. Check each against the current Play Console wording before submitting.

## Privacy policy URL

https://github.com/paulcoding810/audibook/blob/main/PRIVACY.md

## Foreground service declaration

Required because `targetSdk` is 36 and `PlaybackService` declares `android:foregroundServiceType="mediaPlayback"`.

- **Type:** Media playback.
- **What the user sees:** reading an EPUB aloud. Playback starts only when the user taps Play, continues with the screen off or the app in the background, and shows a media notification with play/pause and chapter controls.
- **Why it can't be deferred or interrupted:** it's continuous audio the user is listening to; stopping when the app leaves the foreground would end the listening session.
- **Video:** Play asks for a short screen recording. Record: open a book → Play → lock the screen → audio continues → controls in the notification and on the lock screen.

## Data safety

- **Data collected:** none. AudiBook has no accounts, analytics, ads or crash reporting, and sends nothing about the user or their books to the developer.
- **Data shared:** none.
- **Network use to disclose in the policy (not "collection"):** voice list and downloads from GitHub; Google's online voices, if the user picks one, are processed by the system speech engine under Google's policy.
- **Encryption in transit:** yes (all requests use HTTPS).
- **Data deletion:** all data is local; uninstalling or "Clear storage" deletes it.

## Content rating

Reference/education-style tool with no user-generated content shared with others, no ads, no purchases. The books' content is supplied by the user.

## Target audience

Not designed for children (13+ or 18+ as you prefer); no child-directed content.
