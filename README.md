# AdScope

AdScope is a local experimental Android app that watches Spotify media-session metadata. When Spotify exposes content as an ad, AdScope lowers the ad instantly and immediately starts a random local track from your library. When Spotify music returns, Spotify volume is restored immediately and only the local track fades out.

> Experimental project for personal testing and community collaboration. Not affiliated with Spotify.


## Repository status

This repository tracks the community-development version of AdScope. The current baseline is **1.3.7 FULL MUTE**, the first version validated successfully on phone playback and Bluetooth car audio. Changes to the core audio path should be conservative and reproducible.

## Current stable baseline

This English package is based on the stable **1.3.7 FULL MUTE** behavior that has already been validated on:

- phone speaker,
- wired / wireless headphones,
- and Bluetooth car audio.

## Main behavior

- No confirmation delay after an explicit ad metadata match.
- Spotify volume drops immediately when an ad is detected.
- The next local track is preloaded while AdScope is idle.
- No fade-in for the local track.
- Each ad block starts with a random local track from the beginning.
- Immediate repeat of the same track is avoided while the service stays active.
- The same local track continues across `Ad 1 of 3`, `Ad 2 of 3`, `Ad 3 of 3`, etc.
- When normal Spotify content returns, Spotify volume is restored immediately and only the local track fades out.

## Minimum library size

AdScope requires **at least 5 local tracks** before it can be enabled or tested.

## Features

- Explicit detection of `Ad X of Y` style Spotify metadata.
- Works with blocks of 2, 3, 4, or more ads.
- Secondary heuristics for incomplete metadata.
- Instant ad-volume change.
- Mandatory random playback from a local library.
- Independent ad volume and local music volume controls.
- One fade only: local music fade-out when Spotify returns.
- 10-second configuration test.
- Emergency volume restore from the app and from the notification.
- Recovery after unexpected service termination.
- Technical history and local statistics.
- No Internet permission.
- No Accessibility service.

## FULL MUTE mode

When **Ad volume = MUTE (0)**:

- Spotify `STREAM_MUSIC` is forced to `0`.
- Spotify is not paused.
- AdScope starts a random local track immediately.
- The local library is played through a separate stream so you can test whether Spotify keeps consuming the ad while fully muted.
- When Spotify returns to normal music, the original volume is restored immediately and only the local track fades out.

This mode performed especially well during testing and is the baseline for further work.

## Build requirements

- Android Studio
- JDK 17
- Android SDK matching the project configuration
- `minSdk = 26`
- `targetSdk = 36`

## Build and install

1. Open the project root in Android Studio.
2. Let Gradle sync finish.
3. Connect your Android phone or start an emulator.
4. Run the `app` configuration.

The debug APK is normally generated under:

```text
app/build/outputs/apk/debug/
```

## Typical usage

1. Grant notification-listener access to AdScope.
2. Select five or more local audio tracks.
3. Set the ad-volume level and the local-music level.
4. Optionally run the **10 s test**.
5. Enable AdScope.
6. Play Spotify normally.

## Safety and restore logic

AdScope stores the original and applied volume levels so they can be restored safely. The app also offers **Restore volume now** for manual recovery.

If the user changes a volume channel manually during an ad block, AdScope tries to respect that manual change instead of overwriting it.

## Diagnostics

The advanced section exposes current sessions, technical history, and a copyable diagnostic report. Useful fields include:

- app and package name,
- title / artist / album,
- duration,
- playback state,
- can seek / can skip next,
- ad score and classification,
- and detection reasons.

Diagnostics remain on the device unless the user explicitly copies and shares them.

## Notes

- “Instant” means no deliberate delay is introduced by the app; Android, storage, Bluetooth, or hardware can still add a few milliseconds.
- Audio-channel behavior may vary by manufacturer, headphones, Bluetooth stack, equalizers, or battery-saving policies.
- Future Spotify metadata changes may require detector updates.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). Anonymous diagnostics from different devices are especially useful.

## License

MIT
