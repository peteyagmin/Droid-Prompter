# A500 Teleprompter

A lightweight teleprompter app built to give older Android tablets a
useful second life — originally for the Acer Iconia Tab A500, targeting
Android 4.0 Ice Cream Sandwich (API level 14). Plain Java, plain Android
Views/XML layouts, no AndroidX, no Jetpack Compose, no Material
Components, no external dependencies. "A500 Teleprompter" is a working
title; the app runs on Android tablets other than the A500 too.

For a comprehensive, source-verified description of every implemented
behavior, see [`PROJECT_SPEC.md`](PROJECT_SPEC.md). For persistent rules
this project is developed under, see [`CLAUDE.md`](CLAUDE.md).

## Features

- Large, scrollable script display: white text on a black background.
- **Start Prompter** for the full-screen countdown-then-auto-scroll
  teleprompter mode; tapping the script (or Android Back) while it's
  scrolling pauses immediately, in place, and opens a full-screen pause
  menu with **Resume**, **Restart**, and **Back to Controls** — the app
  never silently drops back to the control screen mid-script. There is no
  separate Pause button and no Return to Top / Return to Beginning
  button; Restart (or Start Prompter again from the controls) is how you
  get back to the top.
- **Preview** / **Stop** on the control screen, to test auto-scrolling at
  the current speed without entering full-screen mode.
- Adjustable auto-scroll speed via a **Speed** `SeekBar`, with the
  current speed shown; the same speed setting drives both Preview and
  full-screen scrolling.
- Font size **A-** / **A+** controls, with the current size shown.
- **New Script** and **Edit Script**: a built-in full-screen plain-text
  editor for creating and revising scripts without leaving the app (see
  below).
- **Mirror** toggle that horizontally flips only the script text,
  countdown, and "Tap to Start" prompt (for teleprompter glass rigs) —
  buttons, labels, the editor, and the file browser are never mirrored.
- Screen stays awake only while actively auto-scrolling (full-screen or
  Preview); scrolling pauses automatically when the app is backgrounded.
- Settings (font size, scroll speed, mirror state, last file/directory)
  persist across launches via `SharedPreferences`.
- A built-in file browser (no third-party file manager or Storage Access
  Framework dependency) for choosing or saving a `.txt`/`.text` script.

## Permissions

This app requests exactly two permissions:

- **`READ_EXTERNAL_STORAGE`** — used to load plain-text scripts from
  shared storage via the built-in file browser.
- **`WRITE_EXTERNAL_STORAGE`** — used to create and edit scripts in the
  app's `Teleprompter` folder on shared storage.

No `INTERNET` permission is requested. The app is fully offline: no
accounts, no advertising, no analytics, no cloud synchronization, and no
network access of any kind.

Legacy external-storage permissions are not handled identically across
every Android release — runtime permission prompts, scoped storage, and
related behavior have all changed over the years since API 14. This app
targets the original, simpler API 14 permission model; if you run into
storage-access issues on a specific device or Android version, that
difference is the most likely place to look.

## Prompter Workflow

1. Load an existing script (**Load Script**), or create/edit one
   (**New Script** / **Edit Script**).
2. Adjust **Font Size** and **Speed** while viewing the control-screen
   preview.
3. Optionally press **Preview** to test automatic scrolling at the
   current speed without entering full-screen mode; press it again
   (now labeled **Stop**) to stop.
4. Press **Start Prompter** when ready.
5. Start Prompter stops Preview scrolling if it's running.
6. Start Prompter always returns the script to the very beginning,
   regardless of where Preview or a previous full-screen session left
   off.
7. The app switches to full-screen mode, using the existing opening
   buffer so the first line starts well down the screen rather than at
   the very top.
8. **Tap to Start** appears.
9. Tapping begins a five-second countdown.
10. Automatic scrolling begins once the countdown finishes.
11. Tapping the screen (or pressing Android Back) at any point while
    scrolling pauses immediately, in place, and opens the full-screen
    pause menu:

    - **Resume** — runs the same five-second countdown, then continues
      scrolling from exactly where it paused.
    - **Restart** — returns to the very top of the script and shows
      **Tap to Start** again, without leaving full-screen mode.
    - **Back to Controls** — leaves full-screen mode and returns to the
      control screen, preserving roughly the same passage in the
      control-screen preview.

    The pause menu blocks taps to the script underneath it, and is never
    mirrored even when Mirror is on.
12. There is no separate Pause button — pausing is a side effect of
    tapping the script (or Back) while it's scrolling.
13. There is no separate Return to Top or Return to Beginning button —
    Restart (from the pause menu) or Start Prompter again (from the
    controls) serve that role.
14. Reaching the very end of the script also pauses into the full-screen
    menu, but with Resume hidden (there's nothing left to resume) —
    only Restart and Back to Controls are offered.
15. Preview scrolling (control screen) and full-screen Prompter
    scrolling are always independent — starting or stopping one never
    starts, stops, or otherwise affects the other, even though both use
    the same Speed setting.

## Script Creation and Editing

### New Script

- Opens the built-in full-screen plain-text editor.
- You supply a filename and the script text.
- `.txt` is added automatically if you don't type an extension.
- Empty scripts cannot be saved.
- Filenames with unsafe characters (`/ \ : * ? " < > |`) are rejected,
  not silently modified.
- New scripts are saved into the app's `Teleprompter` folder on shared
  storage.
- Saving over an existing filename asks for confirmation before
  replacing it.
- New scripts are always saved as UTF-8.
- After saving, the app asks whether to load the new script now.

### Edit Script

- Available whenever a valid script is currently loaded.
- The filename is read-only while editing an existing script.
- Edits are validated and saved safely (see Editor Workflow below).
- After saving, the edited script is automatically reloaded — no extra
  confirmation step.
- Saves use temporary-file atomic replacement, so a failed save can
  never corrupt or lose the original file.
- Cancel and the Android Back button both protect unsaved changes with
  a confirmation prompt.
- All of this happens fully offline, on-device.

### Editor Workflow

- **Script Filename** and **Script Text** are separate, clearly labeled
  fields.
- In New Script mode, pressing Next (or Enter, on keyboards that send
  Enter instead of a Next action) after typing a filename moves focus
  straight to Script Text.
- The editor uses a compact layout while you're typing the script: the
  title and filename section collapse out of the way so the script text
  area gets the most room above the on-screen keyboard.
- An **Edit Filename** control (shown only in New Script mode) brings
  the filename section back if you need to correct it, without losing
  anything already typed in the script text.
- **Cancel** and **Save** always appear below the script text area.
- The script-text field supports ordinary multiline plain text,
  including pasted content.
- Save and Cancel use matching text size.
- The editor avoids the keyboard's fullscreen "extract editor" UI where
  the device's keyboard respects the platform flag requesting that —
  behavior can still vary by keyboard app and manufacturer build, so
  this isn't guaranteed identical on every device.

## Built-in file browser

- Opens at `Environment.getExternalStorageDirectory()` by default, or
  the last successfully used directory if it still exists and is
  readable.
- Shows the full current path, lists directories before files, and
  sorts both alphabetically (case-insensitive).
- Shows only directories and `.txt`/`.text` files.
- `Up` button and the Android Back button navigate to the parent
  directory; Back at the browser's starting location closes the
  browser.
- A `Teleprompter Folder` shortcut jumps to the app's own
  `Teleprompter` folder on shared storage, which it attempts to create
  on first use.
- Checks common legacy removable-storage paths (`/mnt/external_sd`,
  `/mnt/sdcard`, `/mnt/sdcard2`, `/storage/sdcard0`, `/storage/sdcard1`,
  `/Removable/MicroSD`) and only offers ones that actually exist and are
  readable on the device; a `Storage` button lets you switch between
  them when more than one is found. Navigation never goes above these
  detected roots.
- Files are read as UTF-8 first; if that fails to decode, the browser
  falls back to ISO-8859-1 (for older plain-text files). Windows
  (`\r\n`) and Unix (`\n`) line endings are both handled.
- A 2 MB file-size cap avoids loading huge files into memory, with a
  clear message if a file is too large.
- Friendly messages are shown for empty folders, folders with no text
  files, unreadable folders, and files that cannot be opened.

## Project layout

```
A500-Teleprompter/
├── build.gradle                  (root)
├── settings.gradle
├── gradle.properties
├── build.sh                      (manual/offline build fallback, see below)
├── CLAUDE.md                     (persistent contributor rules)
├── PROJECT_SPEC.md               (comprehensive, source-verified behavior spec)
├── artwork/                      (editable source launcher-icon artwork)
├── app/
│   ├── build.gradle
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/a500teleprompter/app/
│       │   ├── MainActivity.java
│       │   ├── ScriptEditorActivity.java
│       │   ├── FileBrowserActivity.java
│       │   ├── FileListAdapter.java
│       │   ├── FileEntry.java
│       │   ├── StorageLocations.java
│       │   ├── TextFileReader.java
│       │   └── AppConstants.java
│       └── res/
│           ├── layout/
│           ├── values/ (strings, colors, dimens, styles)
│           ├── drawable/
│           └── mipmap-*/ (packaged launcher icon)
└── README.md
```

## Android Compatibility

- Android 4.0 "Ice Cream Sandwich" corresponds to API level 14.
- `minSdkVersion` is 14, making Android 4.0 the minimum Android release
  this app supports.
- `targetSdkVersion` remains 14, intentionally, for compatibility with
  the legacy devices this project targets.
- The intended normal-installation range is **Android 4.0 through
  Android 13**. Not every version in that range has been physically
  tested — see Hardware Testing below for exactly what has.
- On newer Android tablets within that supported range, installing this
  APK may show a one-time warning that the app was designed for an older
  version of Android. This warning has been physically observed on a
  Lenovo Smart Tab M8; after dismissing it, the app launches and runs
  normally there.
- **Starting with Android 14, normal installation is blocked outright**:
  Android 14 refuses to install an APK whose `targetSdkVersion` is below
  the minimum it permits, and this app's `targetSdkVersion` (14) is well
  under that floor. Enabling "Install unknown apps" does **not** bypass
  this block — it's a separate, stricter restriction. Android 14 and
  newer are not part of this app's normal supported installation range.

### Advanced: installing on Android 14+ for testing

Android's platform tools expose a developer-only bypass for this exact
block:

```
adb install --bypass-low-target-sdk-block app/build/outputs/apk/debug/app-debug.apk
```

This is an advanced testing workaround, not a supported installation
path:

- It does not modernize the APK — `minSdkVersion`/`targetSdkVersion`
  stay 14.
- It does not guarantee the app will behave correctly on a modern
  Android version.
- It is not how the app is intended to be normally installed.
- This project's primary purpose is repurposing **older** Android
  hardware, not running on the newest Android releases.

### Technical compatibility notes

- `compileSdkVersion 23` (a higher compile SDK is only used so the build
  tooling itself is modern; no API above level 14 is called anywhere in
  the app).
- No AndroidX, no Jetpack Compose, no Material Components, no Kotlin, no
  lambda expressions, no coroutines/LiveData/ViewModel.
- `View.setScaleX()` (used for the mirror effect) was added in API 11,
  so it is safe to call unconditionally on a minSdk-14 app.
- `android:textAllCaps` (used in the button style) was added in exactly
  API 14, matching the app's minimum.
- Layout files use paired `marginLeft`/`marginStart` (and
  `marginRight`/`marginEnd`) attributes so margins work correctly both
  before and after API 17, where `marginStart`/`marginEnd` were
  introduced.

## Building the APK

### Normal path: Gradle + Android Gradle Plugin

On a machine with normal internet access (or in Android Studio):

```
./gradlew assembleDebug
```

This downloads the Android Gradle Plugin and build tooling from Google's
Maven repository the first time it runs. The output APK will be at:

```
app/build/outputs/apk/debug/app-debug.apk
```

### Fallback path: `build.sh`

Some environments (including the one this project was originally
developed in) block outbound access to `dl.google.com` /
`maven.google.com` (the only source for the Android Gradle Plugin and
the Android SDK Manager's platform archives), so `./gradlew assembleDebug`
cannot run there. Ubuntu's package archive, however, ships a genuine
(non-stub) Android SDK Platform 23 — including `android.jar` — plus
`aapt2`, `dx`, `zipalign` and `apksigner`, all pulled from
`archive.ubuntu.com` rather than Google's servers:

```
apt-get install android-sdk-platform-23 android-sdk-build-tools \
    android-framework-res aapt aidl zipalign apksigner dalvik-exchange
```

`build.sh` at the project root drives those tools directly — `aapt2
compile`/`link`, `javac --release 8`, `dx --dex`, `zipalign`, and
`apksigner sign` (with both v1 and v2 signing enabled, required for
Android 4.0) — to produce the exact same kind of debug APK Gradle would,
without needing AGP. Run it with:

```
./build.sh
```

It writes the finished APK to the same conventional location:

```
app/build/outputs/apk/debug/app-debug.apk
```

`compileSdkVersion 23` is used only because that is the newest platform
available through Ubuntu's archive; the app still only calls APIs
available at level 14, and `minSdkVersion`/`targetSdkVersion` remain 14.
See `PROJECT_SPEC.md` section 24 for the full step-by-step pipeline.

## Installing on a supported (Android 4.0–13) tablet

1. Enable "Unknown sources" / "Install unknown apps" on the tablet.
2. Connect the tablet via USB and enable USB debugging if you want to
   use `adb`, or copy the APK to the tablet's storage and install it
   from a file manager.
3. Install with `adb`:

   ```
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

4. On a device where Android shows a one-time "designed for an older
   version of Android" warning, dismiss it and continue — this is
   expected (see Android Compatibility above).

For installing on Android 14 or newer, see the advanced testing
workaround under Android Compatibility — it is not part of this normal
installation path.

## Hardware Testing

### Lenovo Smart Tab M8

Physically tested. Confirmed on this device:

- Normal sideloading works (subject to the one-time legacy-app warning
  described above).
- Core teleprompter functionality: loading a script, starting the
  Prompter, the countdown, auto-scrolling, Exit.
- Built-in file browsing.
- Preview scrolling on the control screen.
- New Script and Edit Script (creating and saving a script, editing and
  re-saving an existing one).
- Script-editor keyboard and compact-layout behavior have been evaluated
  during development. The most recent compact-editor refinements have
  not been separately re-confirmed as fully verified since their latest
  commit — see `PROJECT_SPEC.md` section 25 for exactly what is and
  isn't confirmed as of any given commit.

**Not yet tested on any device**: the full-screen pause menu introduced
in this round (tapping/Back pausing into a menu instead of returning
directly to the controls, Resume, Restart, Back to Controls, the reduced
end-of-script menu, and the screen staying awake through the "Tap to
Start" wait) — verified only by a successful build and inspecting the
compiled layout/resources so far. See `PROJECT_SPEC.md` section 25.

### Acer Iconia Tab A500

The A500 running Android 4.0 (API 14) is this project's original target
device. Physical testing on an actual A500 is still pending. In
particular, none of the following has been confirmed on real A500
hardware:

- Touch behavior of the tap-to-show/hide gesture over the
  auto-scrolling script view.
- Real-world auto-scroll smoothness on the A500's actual (comparatively
  weak) hardware.
- Exact on-device rendering of the launcher icon and button-background
  drawables.
- Whether the A500's particular microSD mount path is one of the legacy
  paths already checked (see Built-in file browser above).

Treat any A500-specific claim elsewhere in this document as intent, not
confirmed behavior, until this section is updated to say otherwise.

## Troubleshooting

**Installation blocked outright on Android 14 or newer**
This is expected — see Android Compatibility above. It is a separate,
stricter restriction from the "older SDK" warning seen on Android
versions within the normal 4.0–13 supported range, and "Install unknown
apps" does not bypass it.

**`INSTALL_FAILED_OLDER_SDK`**
The APK's `minSdkVersion` doesn't match — confirm you're installing the
APK built from this project (its manifest declares `minSdkVersion="14"`,
`targetSdkVersion="14"`, which any Android 4.0+ device satisfies). If you
rebuilt the project and changed `minSdkVersion`/`targetSdkVersion` in
`app/build.gradle`, put them back to 14.

**Installation blocked by device settings**
Enable "Unknown sources" / "Install unknown apps" on the tablet before
installing an APK that didn't come from an app store.

**Device not appearing in `adb devices`**
- Confirm USB debugging is enabled (the exact settings path varies by
  Android version and manufacturer).
- Try a different USB cable/port; some cables are charge-only.
- Run `adb kill-server && adb start-server` and reconnect.
- On the host machine, make sure a udev rule / driver for the tablet's
  USB vendor ID is installed (Windows may need a vendor-specific ADB
  driver).

**Storage permission errors ("cannot be accessed" / save failures)**
- Confirm both `READ_EXTERNAL_STORAGE` and `WRITE_EXTERNAL_STORAGE` were
  granted at install time (on API 14 these are granted automatically at
  install, not at runtime — if either is missing, reinstall the APK).
  On a newer Android version installed via the advanced bypass, storage
  permission behavior may differ from what API 14 expects (see
  Permissions above) — this has not been characterized here.
- Check that the folder isn't on a path the OS itself restricts (e.g.
  other apps' private directories); the browser only lists what
  `File.listFiles()` can actually read, and saving only works where
  `File.canWrite()` succeeds.

**Removable microSD card not appearing**
The app only offers a storage location if the OS actually reports one of
the known legacy microSD paths (`/mnt/external_sd`, `/mnt/sdcard`,
`/mnt/sdcard2`, `/storage/sdcard0`, `/storage/sdcard1`,
`/Removable/MicroSD`) as present. Check what your microSD is actually
mounted as with a shell (`adb shell mount` or `adb shell ls /mnt`) —
vendors are inconsistent about this — and if it's a location not in that
list, let us know so it can be added.

**Text files not being listed**
- Only files ending in `.txt` or `.text` are shown, matched
  case-insensitively.
- Files over 2 MB are intentionally excluded with an on-screen message;
  split very large scripts into smaller files.
- If the containing folder itself reports as unreadable (permissions),
  the browser shows a friendly error rather than a crash — check the
  folder's permissions with a shell if this happens.
