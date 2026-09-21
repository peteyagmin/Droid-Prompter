# A500 Teleprompter

A lightweight teleprompter app built for the Acer Iconia Tab A500, targeting
Android 4.0 Ice Cream Sandwich (API level 14). Plain Java, plain Android
Views/XML layouts, no AndroidX, no Jetpack Compose, no Material Components,
no external dependencies.

## Features

- Large, scrollable script display: white text on a black background.
- Start / Pause / Return to Beginning controls.
- Adjustable auto-scroll speed via a SeekBar, with the current speed shown.
- Font size +/- controls, with the current size shown.
- Mirror toggle that horizontally flips **only** the script text (for
  teleprompter glass rigs) — buttons and labels are never mirrored.
- Screen stays awake only while actively auto-scrolling; scrolling pauses
  automatically when the app is backgrounded.
- Settings (font size, scroll speed, mirror state, last file path) persist
  across launches via `SharedPreferences`.
- Controls can be hidden by tapping the script area while running, and are
  shown again with another tap.
- A built-in file browser (no third-party file manager or Storage Access
  Framework dependency) for choosing a `.txt`/`.text` script.

## Built-in file browser

- Opens at `Environment.getExternalStorageDirectory()` by default, or the
  last successfully used directory if it still exists and is readable.
- Shows the full current path, lists directories before files, and sorts
  both alphabetically (case-insensitive).
- Shows only directories and `.txt`/`.text` files.
- `Up` button and the Android Back button navigate to the parent directory;
  Back at the browser's starting location closes the browser.
- A `Teleprompter Folder` shortcut jumps to `/sdcard/Teleprompter`, which the
  app attempts to create on first launch.
- Checks common legacy removable-storage paths (`/mnt/external_sd`,
  `/mnt/sdcard`, `/mnt/sdcard2`, `/storage/sdcard0`, `/storage/sdcard1`,
  `/Removable/MicroSD`) and only offers ones that actually exist and are
  readable on the device; a `Storage` button lets you switch between them
  when more than one is found. Navigation never goes above these detected
  roots.
- Files are read as UTF-8 first; if that fails to decode, the browser falls
  back to ISO-8859-1 (for older plain-text files). Windows (`\r\n`) and Unix
  (`\n`) line endings are both handled.
- A 2 MB file-size cap avoids loading huge files into memory, with a clear
  message if a file is too large.
- Friendly messages are shown for empty folders, folders with no text
  files, unreadable folders, and files that cannot be opened.

## Project layout

```
A500-Teleprompter/
├── build.gradle                  (root)
├── settings.gradle
├── gradle.properties
├── build.sh                      (manual/offline build fallback, see below)
├── app/
│   ├── build.gradle
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/a500teleprompter/app/
│       │   ├── MainActivity.java
│       │   ├── FileBrowserActivity.java
│       │   ├── FileListAdapter.java
│       │   ├── FileEntry.java
│       │   ├── StorageLocations.java
│       │   ├── TextFileReader.java
│       │   └── AppConstants.java
│       └── res/
│           ├── layout/
│           ├── values/ (strings, colors, dimens, styles)
│           └── drawable/
└── README.md
```

## Compatibility notes

- `minSdkVersion 14`, `targetSdkVersion 14`, `compileSdkVersion 23` (a
  higher compile SDK is only used so the build tooling itself is modern;
  no API above level 14 is called anywhere in the app).
- No AndroidX, no Jetpack Compose, no Material Components, no Kotlin, no
  lambda expressions, no coroutines/LiveData/ViewModel.
- Only permission requested: `READ_EXTERNAL_STORAGE`. No `INTERNET`
  permission, no analytics, no accounts, no background network activity.
- `View.setScaleX()` (used for the mirror effect) was added in API 11, so it
  is safe to call unconditionally on a minSdk-14 app.
- `android:textAllCaps` (used in the button style) was added in exactly API
  14, matching the app's minimum.
- Layout files use paired `marginLeft`/`marginStart` (and
  `marginRight`/`marginEnd`) attributes so margins work correctly both
  before and after API 17, where `marginStart`/`marginEnd` were introduced.

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

### Fallback path: `build.sh` (used to build this project in this session)

The sandbox this project was developed in blocks all outbound access to
`dl.google.com` / `maven.google.com` (the only source for the Android
Gradle Plugin and the Android SDK Manager's platform archives), so
`./gradlew assembleDebug` cannot run there. Ubuntu's package archive,
however, ships a genuine (non-stub) Android SDK Platform 23 — including
`android.jar` — plus `aapt2`, `dx`, `zipalign` and `apksigner`, all pulled
from `archive.ubuntu.com` rather than Google's servers:

```
apt-get install android-sdk-platform-23 android-sdk-build-tools \
    android-framework-res aapt aidl zipalign apksigner dalvik-exchange
```

`build.sh` at the project root drives those tools directly — `aapt2
compile`/`link`, `javac --release 8`, `dx --dex`, `zipalign`, and
`apksigner sign` — to produce the exact same kind of debug APK Gradle
would, without needing AGP. It was used to build and verify this project's
APK in this session. Run it with:

```
./build.sh
```

It writes the finished APK to the same conventional location:

```
app/build/outputs/apk/debug/app-debug.apk
```

`compileSdkVersion 23` is used only because that is the newest platform
available through Ubuntu's archive; the app still only calls APIs available
at level 14, and `minSdkVersion`/`targetSdkVersion` remain 14.

## Installing on the Acer Iconia Tab A500

1. Enable "Unknown sources" on the tablet: **Settings → Security →
   Unknown sources**.
2. Connect the tablet via USB and enable USB debugging if you want to use
   `adb`, or copy the APK to the tablet's storage and install it from a
   file manager.
3. Install with `adb`:

   ```
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

## Troubleshooting

**`INSTALL_FAILED_OLDER_SDK`**
The APK's `minSdkVersion` doesn't match — confirm you're installing the APK
built from this project (its manifest declares `minSdkVersion="14"`,
`targetSdkVersion="14"`, which the A500 running Android 4.0 satisfies). If
you rebuilt the project and changed `minSdkVersion`/`targetSdkVersion` in
`app/build.gradle`, put them back to 14.

**Installation blocked by device settings**
Enable **Settings → Security → Unknown sources** on the tablet before
installing an APK that didn't come from the Play Store.

**Device not appearing in `adb devices`**
- Confirm USB debugging is enabled: **Settings → Applications →
  Development → USB debugging** (the exact path varies on ICS).
- Try a different USB cable/port; some cables are charge-only.
- Run `adb kill-server && adb start-server` and reconnect.
- On the host machine, make sure a udev rule / driver for the A500's USB
  vendor ID is installed (Windows needs Acer's ADB driver).

**Storage permission errors ("cannot be accessed" messages in the browser)**
- Confirm `READ_EXTERNAL_STORAGE` was granted at install time (on API 14
  this is granted automatically at install, not at runtime — if it's
  missing, reinstall the APK).
- Check that the folder isn't on a path the OS itself restricts (e.g. other
  apps' private directories); the browser only lists what
  `File.listFiles()` can actually read.

**Removable microSD card not appearing**
The app only offers a storage location if the OS actually reports one of
the known legacy microSD paths (`/mnt/external_sd`, `/mnt/sdcard`,
`/mnt/sdcard2`, `/storage/sdcard0`, `/storage/sdcard1`,
`/Removable/MicroSD`) as present. On the A500, check what your microSD is
actually mounted as with a shell (`adb shell mount` or `adb shell ls
/mnt`) — vendors are inconsistent about this path — and if it's a location
not in that list, tell us and we can add it.

**Text files not being listed**
- Only files ending in `.txt` (or optionally `.text`) are shown, matched
  case-insensitively.
- Files over 2 MB are intentionally excluded with an on-screen message;
  split very large scripts into smaller files.
- If the containing folder itself reports as unreadable (permissions), the
  browser shows a friendly error rather than a crash — check the folder's
  permissions with a shell if this happens.

## What could not be fully verified without the physical A500 or an API 14 emulator

This session's environment could not download an Android emulator system
image or install one (the same network restriction that blocked the
Android Gradle Plugin also blocks emulator image downloads), so the app
could not be run and interactively tested here. Everything below was
verified by static review and by successfully compiling, dexing, resource-
linking, and signing the APK against a real API 23 platform jar/AAPT2 (see
`build.sh`), but should still be smoke-tested on real hardware or an API 14
emulator:

- Actual touch behavior of the tap-to-show/hide-controls gesture over the
  auto-scrolling `ScrollView`.
- Real-world auto-scroll smoothness/CPU usage on the A500's actual (fairly
  weak, Tegra 2) hardware — the speed-to-pixel mapping in
  `MainActivity.computeScrollPixelsPerTick()` may need tuning by ear/eye.
- Whether the A500's particular microSD mount path is one of the legacy
  paths already checked (see the microSD troubleshooting note above).
- Exact on-device rendering of the shape-drawable app icon and button
  backgrounds (built and verified only via resource compilation, not a
  rendered screenshot).
