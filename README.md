# Wakeup

A single-purpose Android app. At a time you configure, it:

1. Turns **Do Not Disturb off**
2. Takes the phone **out of silent/vibrate** so notifications make sound
3. Sets the **ring, notification, alarm, and media volumes to 75%**

Nothing else. Target: Android 17 (API 37), minimum Android 13 (API 33).

## Why the order matters

While Do Not Disturb is active, Android **silently ignores** ringer-mode and volume
changes made by apps. So DND is turned off first; the other two steps would
otherwise appear to succeed and do nothing. This is enforced in
`WakeupActions.apply()`.

## Permissions

| Permission | How it's granted | If missing |
|---|---|---|
| `ACCESS_NOTIFICATION_POLICY` | **Special access**, via Settings → Notifications → Do Not Disturb access. There is no runtime dialog; the app deep-links you there. | The app cannot turn DND off, and the volume changes get ignored too. This is the one permission that is genuinely required. |
| `SCHEDULE_EXACT_ALARM` | Settings → Apps → Special app access → Alarms & reminders (deep-linked from the app). | Falls back to `setAndAllowWhileIdle`, which can run several minutes late. |
| `POST_NOTIFICATIONS` | Normal runtime dialog. | The app still works; you just don't get the "ran at 07:00" confirmation. |
| `RECEIVE_BOOT_COMPLETED` | Automatic. | The schedule would be lost on reboot. |

The main screen shows the live state of all three grantable permissions and hides
each button once granted.

## Scheduling

- Uses `AlarmManager.setAlarmClock()`, which is exempt from Doze deferral and
  shows the system alarm icon — the right primitive for a time-critical,
  user-visible event.
- Repeat mode is configurable: **every day**, **weekdays only**, or **once**
  (which disarms itself after firing).
- The alarm is rebuilt from saved settings on boot, app update, and clock or
  timezone change (`BootReceiver`).
- Times are computed in local wall-clock time, so a DST shift keeps the alarm at
  the same displayed time instead of drifting an hour.

An alarm-clock app could instead declare `USE_EXACT_ALARM`, which is granted at
install and can't be revoked — but Google Play restricts that permission to apps
whose core purpose is alarms or calendars, so this app asks for the revocable one.

## UI

Jetpack Compose with Material 3, using the wallpaper-derived dynamic color
palette (guaranteed available at minSdk 33). There are no XML layouts; the only
XML theme left is the window background painted before Compose's first frame,
which is why it uses a platform parent instead of pulling in AppCompat or
Material Components.

**The Compose compiler plugin version must match AGP's built-in Kotlin version.**
AGP 9 compiles Kotlin itself, and the Compose compiler ships inside the Kotlin
repo, versioned with it. AGP 9.3.1 uses Kotlin 2.2.10, so
`org.jetbrains.kotlin.plugin.compose` is pinned to 2.2.10. Confirm with:

```bash
./gradlew buildEnvironment | grep kotlin-stdlib
```

Lint will report a newer plugin release (2.4.10) is available. That warning is
left in place deliberately — newer does build, but drifting off the documented
Kotlin pairing is not worth silencing a notice that is otherwise useful.

## Building

Toolchain: AGP 9.3.1, Gradle 9.7.1 (wrapper committed), JDK 21, SDK platform
`android-37.0` with build-tools 37.0.0.

This machine is already set up:

- SDK installed at `/opt/homebrew/share/android-commandlinetools`
  (`brew install --cask android-commandlinetools`), recorded in the
  git-ignored `local.properties`.
- JDK 21 pinned for this directory via `.mise.toml`. The machine's global
  default is JDK 18, which AGP 9 does not support.

```bash
./gradlew assembleDebug
./gradlew installDebug        # with a device connected via adb
```

> mise's shell hook only fires in interactive shells, so scripts and CI need
> `JAVA_HOME` passed explicitly:
>
> ```bash
> JAVA_HOME=$(mise where java@temurin-21.0.7+6.0.LTS) ./gradlew assembleDebug
> ```

Note that `compileSdk = 37` resolves to the `android-37.0` platform directory —
API 37 uses the minor-version naming scheme, and AGP handles the mapping.

### Verified

Build: `assembleDebug`, `assembleRelease`, and `lintDebug` all pass, with only
the one intentional warning described under **UI** above.

Runtime, on an Android 17 (API 37) emulator — starting from DND on, ringer
SILENT, and all volumes at minimum:

| | before | after | 75% target |
|---|---|---|---|
| zen mode | `ZEN_MODE_IMPORTANT_INTERRUPTIONS` | `ZEN_MODE_OFF` | — |
| ringer | `SILENT` | `NORMAL` | — |
| ring | 0 (muted) | 5 / 7 | 5 |
| notification | 0 (muted) | 5 / 7 | 5 |
| alarm | 1 | 6 / 7 | 6 |
| media | 1 | 11 / 15 | 11 |

Every stream landed exactly on target. Note **alarm**: its range is 1..7, not
0..7, so taking 75% of the *usable* range yields 6 where a naive `max * 0.75`
would give 5 — which is why `targetVolume()` accounts for `getStreamMinVolume()`.

Both paths were exercised: the **Run now** button, and the real scheduled path
via `AlarmManager`. For the latter, `dumpsys alarm` confirmed
`tag=*walarm*:com.wakeup.app/.WakeupReceiver`, `window=0`,
`exactAllowReason=permission`, an `Alarm clock:` block (i.e. `setAlarmClock()`
was used, not `setExact()`), and listing under **Next wake from idle** — the
Doze exemption. On firing it re-armed for the next day rather than re-firing the
same day, and posted its confirmation notification. A clock change also
correctly triggered `BootReceiver` → re-sync via `ACTION_TIME_SET`.

The release APK is 22 MB because `isMinifyEnabled = false`. Compose benefits a
lot from R8; turning minification on would cut that substantially.

## Testing it

The **Run now (test)** button on the main screen applies the whole sequence
immediately, so you can verify behaviour without waiting for the scheduled time.
Put the phone in DND + silent first to see all three steps take effect.

To test the scheduled path end to end, set the time a minute or two ahead and
leave the screen. To watch it fire:

```bash
adb logcat -s WakeupActions:I Scheduler:I WakeupReceiver:I
```

### On the emulator

An API 37 AVD named `wakeup37` (Pixel 7, arm64) is already created:

```bash
$ANDROID_HOME/emulator/emulator -avd wakeup37 -gpu host &
```

Two of the three permissions cannot be tapped through from a script, so grant
them over adb:

```bash
adb shell pm grant com.wakeup.app android.permission.POST_NOTIFICATIONS
adb shell appops set com.wakeup.app SCHEDULE_EXACT_ALARM allow
# NB: `cmd notification allow_dnd <pkg>` silently does nothing — write the
# secure setting instead:
adb shell settings put secure enabled_notification_policy_access_packages com.wakeup.app
```

Set up the adverse state, then inspect the result:

```bash
adb shell cmd audio set-volume 2 0        # 2=ring 3=media 4=alarm 5=notification
adb shell cmd audio set-ringer-mode SILENT
adb shell cmd notification set_dnd priority

adb shell dumpsys audio | grep -A8 '^- STREAM_RING:'   # streamVolume: is the live value
adb shell settings get global zen_mode                 # 0 = off
```

Read volumes from `dumpsys audio` (`streamVolume:`), **not** from
`settings get system volume_ring` — the settings rows lag well behind the real
value. The `media volume` and `cmd media_session volume` commands are also dead
ends on this image; `cmd audio set-volume` is the one that works.

To make a scheduled run happen without waiting, jump the clock (needs `adb root`):

```bash
adb shell settings put global auto_time 0
adb shell date 082106592026.30      # MMDDhhmmCCYY.ss — just before 07:00
```

Remember `adb shell settings put global auto_time 1` afterwards.

## Caveats

- **Fixed-volume devices** (some tablets, Android Automotive) report
  `AudioManager.isVolumeFixed()`; volume changes are impossible there and the app
  reports that rather than failing silently.
- On many devices the **notification volume is aliased to the ring volume**, so
  those two sliders move together. The app writes both regardless.
- **Aggressive OEM battery management** (Xiaomi, Huawei, Samsung, OnePlus) can
  kill background alarms. If runs get missed, exempt the app from battery
  optimisation in system settings.
- 75% is `WakeupActions.VOLUME_PERCENT` — a one-line change if you want a
  different level.

## Layout

```
app/src/main/java/com/wakeup/app/
├── MainActivity.kt       Activity, UI state assembly, permission flows
├── WakeupScreen.kt       The Compose UI + time picker dialog
├── Theme.kt              Material 3 dynamic-color theme
├── WakeupActions.kt      The actual DND/ringer/volume work
├── Scheduler.kt          Arms, re-arms, cancels the alarm
├── WakeupReceiver.kt     Runs at the scheduled time, then re-arms
├── BootReceiver.kt       Rebuilds the schedule after reboot/time change
├── Notifier.kt           Post-run confirmation notification
└── Prefs.kt              Saved settings + RepeatMode
```

`MainActivity` reads a `WakeupUiState` snapshot from prefs plus live system
permission state, and re-reads it on `ON_RESUME` — necessary because the user
grants two of the three permissions in system Settings and returns.
