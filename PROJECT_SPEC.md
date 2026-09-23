# A500 Teleprompter — Project Specification

This document describes the application as it exists in the current
source. See `CLAUDE.md` for the persistent rules derived from it.

**Documented as of commit `76e0b30`** (branch
`claude/android-teleprompter-app-1wfqsr`), updated for a new Mirror Mode
Settings preference on top of it (section 35): the existing Mirror
On/Off toggle is unchanged, but now applies one of two transformations
— Flip (the pre-existing behavior, default) or Flip and Rotate (an
additional absolute 180-degree rotation, for mounting the tablet with
operator controls upright) — to `scriptText`/`countdownText`/
`tapInstructionText` via a rewritten, centralized `applyMirror()`.
Entirely a rendering-layer change: no scrolling/state-machine code was
touched. The commit that lands this update is necessarily newer than
`76e0b30`; that commit's hash is reported separately at the end of the
task that made these changes, not inferred here.

Throughout this document:

- **Physically tested** means the user reported running the built APK on
  real hardware and observing the described behavior.
- **Verified by inspection** means confirmed by reading the source and/or
  by inspecting the compiled APK (`aapt dump`, `zipalign`, `apksigner
  verify`), but not run on a device in this project's history.
- **Requires physical testing** means the behavior is implemented and
  compiles, but has not been run on real hardware.
- **Future idea** means not implemented; listed only to say so explicitly.

---

## 1. Project purpose and design goals

- A lightweight, offline teleprompter intended to give older Android
  tablets a useful second life rather than a general-purpose modern app.
- Fully offline: no accounts, no ads, no analytics, no cloud services, no
  network access of any kind (no `INTERNET` permission is requested).
- "A500 Teleprompter" is a working title; the project explicitly
  anticipates running on tablets other than the Acer A500 and the name
  may change.

## 2. Current repository and release status

- Branch: `claude/android-teleprompter-app-1wfqsr`.
- HEAD at documentation time: `66d459f` ("Fix Save button text size to
  match Cancel in script editor").
- Application label (`android:label` on `<application>` and
  `MainActivity`): `@string/app_name` = **A500 Teleprompter**.
- Package / `applicationId`: `com.a500teleprompter.app`.
- Latest known APK output path: `app/build/outputs/apk/debug/app-debug.apk`
  — produced by `build.sh`, **not** committed to the repository
  (`/app/build/` is in `.gitignore`). It must be rebuilt from source.
- `versionCode 1`, `versionName "1.0"` (`app/build.gradle`), unchanged
  since project inception. No beta/release designation, changelog, or
  git tag exists in the repository (`git tag` is currently empty) — see
  section 29 for the intended (not yet practiced) discipline.

## 3. Supported Android configuration

- `minSdkVersion 14`, `targetSdkVersion 14`, `compileSdkVersion 23`
  (`app/build.gradle`; `build.sh` passes `--min-sdk-version 14
  --target-sdk-version 14` to `aapt2 link` independently). The higher
  compile SDK exists only so modern build tooling can be used; no API
  above level 14 is called.
- Legacy Views/XML layouts and plain Java throughout; no AndroidX,
  Kotlin, Compose, Material Components, or ConstraintLayout anywhere in
  the source tree.
- Permissions declared in `AndroidManifest.xml`:
  `android.permission.READ_EXTERNAL_STORAGE` and
  `android.permission.WRITE_EXTERNAL_STORAGE`. No `INTERNET` permission.
- Modern-Android installation range: the intended normal-installation
  range is Android 4.0 through Android 13. Starting with Android 14,
  normal installation is blocked outright because this app's
  `targetSdkVersion` (14) is below the minimum Android 14 permits;
  `adb install --bypass-low-target-sdk-block` is an advanced,
  non-modernizing testing workaround for that specific block, not a
  supported install path. Within the 4.0–13 range, some devices show a
  one-time "designed for an older version of Android" warning on first
  launch and some don't — this is device- and Android-version-dependent,
  not a fixed cutoff; see section 26 for exactly what has been physically
  observed.

## 4. Main control-screen layout

Source: `app/src/main/res/layout/activity_main.xml`,
`MainActivity.java`. Labels quoted exactly from `strings.xml`.

Top file row (`btnOpenFile`, `btnNewScript`, `currentFileLabel`,
`btnEditScript`), left to right:

- **Load Script** (`btn_open_file`) — opens `FileBrowserActivity` for
  result.
- **New Script** (`btn_new_script`) — opens `ScriptEditorActivity` in
  `EDITOR_MODE_CREATE_NEW`.
- Loaded filename display (`currentFileLabel`) — "Loaded: %1$s"
  (`loaded_file_label`) with the current file's name, or "No file
  loaded" (`no_file_loaded`) when nothing is loaded. Single line,
  middle-ellipsized.
- **Edit Script** (`btn_edit_script`) — opens `ScriptEditorActivity` in
  `EDITOR_MODE_EDIT_EXISTING` with the currently loaded file path; shows
  "Load a script before editing." (`no_script_to_edit`) if pressed with
  nothing loaded (defensive — the button is disabled in that state, see
  section 5).

Primary action row (`btnStart`, `mirrorToggle`, `btnExit`):

- **Start Prompter** (`btn_start` — renamed from "Enter Prompter"; the
  ID `btnStart`, its click listener, and the method it calls,
  `enterFullScreenWaitingFromControls()`, were deliberately left
  unrenamed) — starts the full-screen sequence (section 17).
- **Mirror** toggle (`mirrorToggle`, a `ToggleButton`) — "Mirror: On" /
  "Mirror: Off" (`mirror_on` / `mirror_off`).
- **Exit** (`btn_exit`) — returns to controls, then finishes the
  Activity.

Font size + speed row:

- Font Size: static "Font Size" label, **A-** / **A+** buttons
  (`btn_font_decrease` / `btn_font_increase`), and a live numeric label
  (`fontSizeLabel`).
- A vertical divider, then Speed: "Speed:" prefix (`speed_prefix`), a
  live numeric label (`speedLabel`), a `SeekBar` (`speedSeekBar`), and
  **Preview** / **Stop** (`btn_preview` / `btn_stop_preview`,
  `btnPreview`, label toggles at runtime).
- A second vertical divider (identical width/color/margins to the
  first — see section 30), then the compact Settings gear
  (`btnSettings`, an `ImageButton`) — opens `SettingsActivity` (section
  30).

Script preview: the same `scriptText` view used in full-screen mode,
inside `scrollView`, shown below the controls panel whenever
`uiState == CONTROLS`.

No other controls exist on this screen. In particular, there is no
"Return to Top" button and no Speed minus/plus button pair.

**Button sizing**: `btnOpenFile`, `btnNewScript`, `btnEditScript`, and
`btnStart` all use `@style/SecondaryControlButton` (56dp `minHeight`,
16sp text, 10dp padding) — the same style as `mirrorToggle`, `btnExit`,
and `btnPreview` — so every rectangular button on this screen shares one
consistent physical size and typography. Start Prompter remains the
visually dominant action purely through its wider `layout_weight="3"`
(vs. `1` each for Mirror and Exit) and central placement, not through a
taller button or larger text. `@style/ControlButton` (64dp `minHeight`,
20sp text, 12dp padding) is no longer used anywhere on this screen —
only by the full-screen pause-menu buttons, the Script Editor's Cancel/
Save row is `EditorActionButton` instead (section 11), and the Settings/
file-browser screens have their own unrelated buttons.

## 5. Loaded-script-dependent button states

`updateLoadedScriptDependentControls()` is the single method that enables
or disables `btnStart`, `btnEditScript`, and `btnPreview`, based solely on
`loadedFilePath != null`. It is called after every change to
`loadedFilePath`: initial `onCreate()` restore, `loadScriptFile()`
success, and `applyNoScriptState()`.

- **Valid script loaded**: `btnStart`, `btnEditScript`, `btnPreview` all
  enabled.
- **No script loaded** (fresh install, or after `applyNoScriptState()`):
  all three disabled; `scriptText` shows the placeholder
  "Load a script to begin" (`default_script_placeholder`).
- **A saved new script is not loaded** (user answers "No" to "Would you
  like to load this new script now?" in `promptLoadNewlySavedScript()`):
  `applyNoScriptState()` runs — same as "no script loaded" above. The
  newly saved file exists on disk but is not the loaded script.
- **The previously loaded file becomes unavailable**: detected only via
  `ScriptEditorActivity` signaling `EXTRA_EDIT_FILE_UNAVAILABLE = true`
  on `RESULT_CANCELED` when Edit Script's file read fails; `MainActivity`
  responds with `applyNoScriptState()`. There is no independent
  "check the file still exists" step elsewhere (e.g. plain app resume
  does not re-validate `loadedFilePath`).

`btnOpenFile`, `btnNewScript`, `mirrorToggle`, and `btnExit` are never
disabled by loaded-script state.

## 6. Built-in file browser

Source: `FileBrowserActivity.java`, `StorageLocations.java`,
`activity_file_browser.xml`.

- Supported extensions: `.txt` and `.text`, matched case-insensitively
  (`isSupportedTextFile()`).
- Directory navigation: tapping a directory entry navigates into it;
  **Up** (`btnUp`) and Android Back navigate to the parent, disabled/
  redirected to cancel once at a detected storage root or the browser's
  start directory (`onBackPressed()`, `canNavigateUp()`).
- Storage roots (`StorageLocations.findReadableRoots()`):
  `Environment.getExternalStorageDirectory()` plus any of the legacy
  paths `/mnt/external_sd`, `/mnt/sdcard`, `/mnt/sdcard2`,
  `/storage/sdcard0`, `/storage/sdcard1`, `/Removable/MicroSD` that
  actually exist and are readable. **Storage** (`btnStorage`) is only
  shown when more than one root is found, and opens a chooser dialog
  listing their absolute paths.
- Teleprompter folder: `StorageLocations.getTeleprompterFolder()` =
  `<external storage>/Teleprompter`; created on `FileBrowserActivity`
  startup if missing (`ensureTeleprompterFolderExists()`). **Teleprompter
  Folder** (`btnTeleprompterFolder`) jumps there directly, or shows
  "The Teleprompter folder is not available." if it can't be read.
- Last-used directory: `resolveInitialDirectory()` reads
  `KEY_LAST_DIRECTORY` from `SharedPreferences` and opens there if it
  still exists, is a directory, and is readable; otherwise falls back to
  external storage root. Written on successful file selection
  (`confirmSelection()`) and when the user accepts loading a newly saved
  script (`promptLoadNewlySavedScript()`'s Yes branch, which points it at
  the Teleprompter folder).
- File-size limit: 2 MB (`TextFileReader.MAX_FILE_SIZE_BYTES`), enforced
  both by the browser before returning a selection and again by
  `TextFileReader.readTextFile()` itself.
- Encoding: files are read as raw bytes and decoded strict UTF-8 first;
  fallback encoding is ISO-8859-1 if UTF-8 decoding fails
  (`TextFileReader.decodeUtf8Strict()` /
  `decodeIsoLatin1()`). Windows (`\r\n`) and lone `\r` line endings are
  normalized to `\n`.
- Inaccessible-directory behavior: `File.listFiles()` returning `null`
  shows "This folder cannot be accessed."; an empty directory shows
  "This folder is empty."; a directory with only unsupported files shows
  "This folder contains no text files."
- Removable-storage behavior: only the legacy paths above are ever
  offered, and only when actually present and readable on the device;
  navigation never goes above the detected roots.

## 7. Script loading and no-script state

- Loading (`MainActivity.loadScriptFile(path)`): reads via
  `TextFileReader`; on failure, shows the reader's error message as a
  Toast and leaves the previous state untouched. On success:
  `exitFullScreenToControls()` is called first (always lands back on
  controls), `atScriptEnd` is reset to `false`, `scriptText` is set to
  the file's content, `loadedFilePath` is set, the filename label
  updates, `updateLoadedScriptDependentControls()` runs,
  `KEY_LAST_FILE_PATH` is persisted, and the scroll position is reset to
  the top on the next layout pass.
- Filename display: `updateLoadedFileLabel()` — "Loaded: <name>" or
  "No file loaded".
- Preview initialization: no separate step; the same `scrollView`/
  `scriptText` pair used for the full-screen prompter is what's visible
  under the controls panel whenever `uiState == CONTROLS`.
- Clearing loaded state: `applyNoScriptState()` — stops preview
  scrolling, nulls `loadedFilePath`, resets `atScriptEnd`, restores
  minimal uniform padding, sets the placeholder text, scrolls to the
  top, and calls `updateLoadedScriptDependentControls()`. Does not touch
  font size, speed, mirror, or last-directory preferences.
- Placeholder text: "Load a script to begin"
  (`default_script_placeholder`).
- Failure behavior: a Toast with the specific reader error (file not
  found, unreadable, empty, too large, unsupported encoding); the
  previously loaded script (if any) remains loaded and unchanged.
- Dependent button states: as in section 5.

## 8. New Script workflow

- **New Script** button opens `ScriptEditorActivity` with
  `EXTRA_EDITOR_MODE = EDITOR_MODE_CREATE_NEW` (no `EXTRA_EDIT_FILE_PATH`).
- `setUpCreateNew()`: title "New Script" (`title_new_script`),
  `filenameField` enabled and cleared, `scriptBodyField` cleared,
  baseline snapshot taken.
- Persistent field labels/hints (must not be altered without explicit
  instruction): "Script Filename" (`script_filename_label`) / "Enter a
  filename, then press Enter or Next" (`filename_hint`) for
  `filenameField`; "Script Text" (`script_text_label`) / "Type or paste
  your script here" (`script_body_hint`) for `scriptBodyField`.
- Validation on Save (`saveNew()`), in order: non-empty trimmed filename
  (`filename_required`), no invalid characters — `/ \ : * ? " < > |`
  (`filename_invalid_chars`), non-blank body after trim
  (`script_content_required`), external storage writable
  (`storage_unavailable`).
- Automatic `.txt` extension: `ensureTxtExtension()` appends `.txt`
  unless the trimmed name already ends in `.txt` or `.text`
  (case-insensitive).
- Unsafe-character handling: rejected outright with a Toast, not
  sanitized/stripped.
- Empty-content handling: rejected with a Toast; whitespace-only counts
  as empty (`isBlank()` trims first).
- Save folder: always `StorageLocations.getTeleprompterFolder()`,
  created via `mkdirs()` if missing.
- External-storage checks: `Environment.getExternalStorageState() ==
  MEDIA_MOUNTED` before attempting to write, and `folder.canWrite()`
  after ensuring the folder exists.
- Overwrite confirmation: if the target file already exists, an
  AlertDialog ("File Already Exists" / "A script with this name already
  exists. Replace it?") with **Replace** / **Cancel** gates the actual
  write; Cancel just dismisses, no state change.
- Atomic saving: `writeTextFileAtomically()` — write to `<name>.tmp` in
  the same directory via `OutputStreamWriter(..., "UTF-8")`, flush,
  close, then `File.renameTo(target)` only on success; the temp file is
  deleted on any failure and the original target is never touched until
  the rename succeeds.
- "New Script Saved" dialog: shown by `MainActivity`, not the editor —
  `promptLoadNewlySavedScript()` after `ScriptEditorActivity` returns
  `RESULT_OK` with `EXTRA_EDITOR_WAS_NEW = true`. Title "New Script
  Saved" (`new_script_saved_title`), message "Would you like to load
  this new script now?" (`new_script_saved_message`).
  - **Yes**: persists `KEY_LAST_DIRECTORY` = Teleprompter folder, then
    `loadScriptFile(path)`.
  - **No**: `applyNoScriptState()` — the file exists on disk but is left
    unloaded (section 5).

## 9. Edit Script workflow

- Availability: `btnEditScript` is enabled only when a script is loaded
  (section 5); its click handler also defensively re-checks
  `loadedFilePath == null` and shows "Load a script before editing."
  (`no_script_to_edit`) if somehow reached with nothing loaded.
- Opens `ScriptEditorActivity` with `EDITOR_MODE_EDIT_EXISTING` and
  `EXTRA_EDIT_FILE_PATH = loadedFilePath`.
- Existing-file checks: `setUpEditExisting()` reads the file via
  `TextFileReader.readTextFile()`. On failure: Toast with the reader's
  error, `setResult(RESULT_CANCELED, {EXTRA_EDIT_FILE_UNAVAILABLE: true})`,
  `finish()` — no editor UI is ever shown.
- Read-only filename: `filenameField` is populated with the existing
  file's name and `setEnabled(false)`; it cannot be changed in this mode,
  and the "Edit Filename" control is never shown here (section 11).
- Loading existing contents into `scriptBodyField`; baseline snapshot
  taken from both fields.
- Save (`saveExisting()`) and Cancel: Cancel goes through
  `handleCancelOrBack()` like CREATE_NEW (dirty-check + discard dialog).
  Save validates non-blank body and writable storage, then writes.
- Empty-content validation: same `script_content_required` Toast as
  CREATE_NEW; filename is not re-validated (it's fixed).
- Atomic replacement: same `writeTextFileAtomically()` against
  `existingFilePath` directly (no rename of the visible filename).
- Automatic reload: `MainActivity.onActivityResult()` for
  `RESULT_OK`+`wasNew == false` calls `loadScriptFile(savedPath)`
  immediately — no confirmation dialog — followed by a "Script saved"
  Toast (`script_saved_toast`).
- Unavailable-file handling: see the existing-file-checks bullet above;
  `MainActivity` responds to `EXTRA_EDIT_FILE_UNAVAILABLE` with
  `applyNoScriptState()`.
- Encoding conversion to UTF-8: yes — regardless of how the original file
  was decoded (UTF-8 or the ISO-8859-1 fallback on read), every save
  (`writeTextFileAtomically`) writes via `OutputStreamWriter(...,
  "UTF-8")`, so a legacy ISO-8859-1 file is transparently converted to
  UTF-8 the first time it's edited and saved.

## 10. Script Editor visual hierarchy

Source: `app/src/main/res/layout/activity_script_editor.xml`. Top to
bottom, exactly as it currently exists:

1. `titleAndFilenameSection` — a vertical `LinearLayout`, toggled
   `VISIBLE`/`GONE` as a unit by compact mode. Contains:
   - `editorTitleText` — "New Script" or "Edit Script" (`title_new_script`
     / `title_edit_script`), 22sp bold, `@color/accent_amber`.
   - `filenameLabel` — "Script Filename" (`script_filename_label`), 16sp.
   - `filenameField` — the filename `EditText`, `imeOptions
     ="flagNoExtractUi|actionNext"`, hint "Enter a filename"
     (`filename_hint`), background `@drawable/button_background`.
2. A horizontal row (always visible) containing:
   - `scriptBodyLabel` — "Script Text" (`script_text_label`), 16sp,
     `layout_weight="1"` so it fills the row.
   - `btnEditFilename` — the "Edit Filename" control
     (`btn_edit_filename`), a plain clickable/focusable `TextView`,
     `visibility="gone"` by default, `@color/accent_amber`, 14sp.
3. `scriptBodyField` — the script-body `EditText`. `layout_height="0dp"`
   with `layout_weight="1"`; it is the **only** weighted element in the
   whole layout tree besides the internal weighting inside row 2's own
   `LinearLayout`. It is a direct child of the root layout, **not**
   nested inside any `ScrollView`. `imeOptions="flagNoExtractUi"`, hint
   "Type or paste your script here" (`script_body_hint`).
4. `editorButtonRow` — the Cancel/Save row, a horizontal `LinearLayout`
   with `visibility="gone"` by default, positioned after `scriptBodyField`
   in document order (never above it):
   - `btnEditorCancel` — style `EditorActionButton` — "Cancel"
     (`btn_cancel`).
   - `btnEditorSave` — style `EditorActionButton`, no per-instance
     overrides — "Save" (`btn_save`).

Confirmed facts:

- `scriptBodyField` is the weighted element that receives whatever
  vertical space remains after the (possibly collapsed)
  `titleAndFilenameSection`, the label row, and `editorButtonRow` (when
  visible) are laid out.
- `scriptBodyField` is not nested in a `ScrollView` anywhere in this
  hierarchy.
- Cancel and Save appear below `scriptBodyField`, never above it.
- `editorButtonRow` is hidden (`GONE`, not `INVISIBLE`, so it never
  retains layout space) whenever `filenameField` has focus, and shown
  whenever `scriptBodyField` has focus (compact mode) — see section 11.
- Cancel and Save are geometrically identical, not just text-size
  matched: both use the single shared style `EditorActionButton` with no
  per-instance `android:textSize` (or any other) override on either
  button. Verified by resolving the compiled style bag in the built
  APK — `EditorActionButton`'s resolved bag (`textSize`, `textColor`,
  `background`, `padding`, `minHeight`, `textAllCaps`) is byte-identical
  to `SecondaryControlButton`'s, and both `btnEditorCancel` and
  `btnEditorSave` reference that one style resource ID with nothing else
  applied on top of it. `layout_width="0dp"` + `layout_weight="1"` on
  both buttons gives them an equal 50/50 share of the row regardless of
  which one carries the row's internal spacing margin (a LinearLayout
  weight distribution detail: the margin is subtracted from the shared
  pool before the 1:1 split, not added asymmetrically to one side).
- `ControlButton` was **not** modified by this — `EditorActionButton` is
  its own style (duplicated from, not aliasing, `SecondaryControlButton`)
  so the pause-menu buttons and every other `ControlButton`/
  `SecondaryControlButton` consumer elsewhere in the app is unaffected by
  editor-button changes, and vice versa. (Start Prompter, Load Script,
  New Script, and Edit Script all use `SecondaryControlButton` as of the
  main-screen button-sizing pass described in section 4 — not
  `ControlButton`, unlike when this paragraph was first written.)
- Styles live in `app/src/main/res/values/styles.xml`
  (`ControlButton`: `minHeight=@dimen/control_button_height` (64dp),
  `textSize=@dimen/control_text_size` (20sp), `padding=12dp`;
  `SecondaryControlButton` and `EditorActionButton`, identical to each
  other: `minHeight=@dimen/secondary_button_height` (56dp),
  `textSize=@dimen/secondary_text_size` (16sp), `padding=10dp`; all three
  styles have `textAllCaps=false` and no `textStyle`/`fontFamily`).
  Dimensions live in `app/src/main/res/values/dimens.xml`, including
  `editor_text_size` (20sp, `scriptBodyField`'s own text size — unrelated
  to the editor-button styling above).

## 11. Script Editor keyboard and compact-mode behavior

Source: `ScriptEditorActivity.java`, `AndroidManifest.xml`.

- `flagNoExtractUi` is set on both `filenameField` and `scriptBodyField`
  (combined with `actionNext` on `filenameField` only). This suppresses
  the keyboard's fullscreen white "extract" editor UI. Verified by
  decoding the compiled `imeOptions` attribute in the built APK
  (`0x10000005` on `filenameField`, `0x10000000` on `scriptBodyField`).
- `ScriptEditorActivity` declares
  `android:windowSoftInputMode="adjustResize"` in the manifest, so the
  Activity's window shrinks (rather than pans) when the keyboard opens —
  this is what makes `scriptBodyField`'s `layout_weight` mechanism
  relevant in the first place.
- Filename IME action: `filenameField.setOnEditorActionListener(...)`
  consumes `EditorInfo.IME_ACTION_NEXT`.
- Raw Enter fallback: `filenameField.setOnKeyListener(...)` separately
  consumes a raw `KeyEvent.KEYCODE_ENTER` on `ACTION_DOWN` with
  `getRepeatCount() == 0`, for keyboards that send Enter instead of
  firing the IME action.
- `advanceFromFilenameToBody()`: the single method both paths call. If
  `scriptBodyField` already has focus it's a no-op (guards against a
  keyboard delivering both an editor action and a raw key event for one
  keypress); otherwise it moves focus to `scriptBodyField`, moves the
  cursor to the end of its text, and calls `showKeyboardFor()`. It never
  inserts a newline and never triggers Save.
- Multiline Enter in `scriptBodyField`: unaffected — `scriptBodyField`
  has no `imeOptions` action set (only the `flagNoExtractUi` flag), which
  is what lets ordinary Enter presses continue to insert newlines while
  typing the script body.
- Filename-focused visibility: `filenameField.setOnFocusChangeListener(...)`
  calls `showFilenameSection()` whenever the field gains focus.
  `showFilenameSection()` sets `compactMode = false`, makes
  `titleAndFilenameSection` `VISIBLE`, and hides both `editorButtonRow`
  and `btnEditFilename` (`GONE`). Because a disabled `EditText` cannot
  take focus, this listener never fires in `EDITOR_MODE_EDIT_EXISTING`
  (where `filenameField` is disabled), so `editorButtonRow` there is
  controlled purely by compact mode below.
- Compact mode: `scriptBodyField.setOnFocusChangeListener(...)` calls
  `enterCompactMode()` whenever the body gains focus. `enterCompactMode()`
  sets `titleAndFilenameSection` to `GONE`, makes `editorButtonRow`
  `VISIBLE`, and, only in `EDITOR_MODE_CREATE_NEW`, makes
  `btnEditFilename` `VISIBLE`. It is idempotent (`compactMode` boolean
  guard). `editorButtonRow` is `GONE` (not `INVISIBLE`) by default in the
  XML, so it never occupies layout space while hidden.
- Title and filename section collapsing when the body gains focus: as
  above — this is the entire mechanism; there is no separate "shrink"
  animation or measurement step beyond the `GONE` visibility change plus
  `scriptBodyField`'s own `layout_weight` claiming the freed space.
- CREATE_NEW's very first focus: `applyInitialFocusAndCompactState()`
  explicitly calls `filenameField.requestFocus()` when there is no
  `savedInstanceState` and the mode isn't EDIT_EXISTING, so
  `showFilenameSection()` reliably fires before the first frame is
  shown — `editorButtonRow` starts hidden deterministically rather than
  by relying on Android's implicit "focus the first focusable view"
  behavior.
- "Edit Filename" behavior in `EDITOR_MODE_CREATE_NEW`: tapping
  `btnEditFilename` calls `exitCompactModeForFilenameEdit()`, which
  calls `filenameField.requestFocus()` (triggering `showFilenameSection()`
  as a side effect, the same way focusing `scriptBodyField` triggers
  `enterCompactMode()` as a side effect), moves the cursor to the end of
  the existing filename text, and calls `showKeyboardFor()`. It does not
  clear or alter either field's text and does not save.
- "Edit Filename" absence in `EDITOR_MODE_EDIT_EXISTING`:
  `enterCompactMode()` only shows `btnEditFilename` when `editorMode ==
  EDITOR_MODE_CREATE_NEW`; in EDIT_EXISTING it is left `GONE` for the
  entire Activity lifetime (there is nothing to edit — `filenameField` is
  disabled there, section 9).
- Initial focus in `EDITOR_MODE_EDIT_EXISTING`:
  `applyInitialFocusAndCompactState()` requests focus on `scriptBodyField`
  immediately when there is no `savedInstanceState`, which cascades into
  `enterCompactMode()` via the focus listener — the editor opens directly
  in compact mode with the body focused.
- `requestRectangleOnScreen()` behavior: `ensureVisibleAboveKeyboard()`
  calls `view.post(...)` then `view.requestRectangleOnScreen(new
  Rect(0, 0, view.getWidth(), view.getHeight()), true)` — an API
  14-compatible way to ask the scrolling/resizing machinery to keep the
  given field visible once the pending layout pass has settled.
- Explicit keyboard reopening/retention: `showKeyboardFor()` calls
  `InputMethodManager.showSoftInput(view, SHOW_IMPLICIT)` on a `post()`,
  used after both `advanceFromFilenameToBody()` and
  `exitCompactModeForFilenameEdit()`, so the keyboard staying open across
  a focus hand-off between the two `EditText`s does not rely solely on
  implicit platform behavior.
- Keyboard-related limitations requiring hardware testing: the prior
  version of compact mode (title/filename collapse, Edit Filename, the
  raw-`KEYCODE_ENTER` fallback) was physically tested on the Lenovo Smart
  Tab M8 and found fundamentally working, which is how the
  `editorButtonRow`-hiding and identical-button-size refinements
  described above came to be requested. Those specific refinements
  themselves have not yet been separately re-confirmed on hardware since
  being implemented — see section 26.

## 12. Script Editor state and safety

- Dirty-state tracking: `baselineFilename`/`baselineBody` snapshot the
  fields at open time and after every successful save
  (`updateBaseline()`); `hasUnsavedChanges()` compares current field text
  against that snapshot.
- Cancel behavior: `handleCancelOrBack()` — if dirty, shows the Discard
  Changes dialog; otherwise `setResult(RESULT_CANCELED)` and `finish()`
  immediately.
- Android Back behavior: `onBackPressed()` calls the same
  `handleCancelOrBack()` — Back and Cancel are identical.
- Discard Changes dialog: title "Discard Changes?"
  (`discard_changes_title`), message "Your unsaved changes will be lost."
  (`discard_changes_message`), non-cancelable (no dismiss via outside tap
  or system Back), **Discard** / **Keep Editing** buttons.
- Keep Editing: dismisses the dialog only; no other side effect.
- Orientation restoration: `onSaveInstanceState()` persists editor mode,
  existing file path, both fields' current text, the baseline snapshot,
  `scriptBodyField`'s selection, `filenameField`'s selection, the
  `compactMode` boolean, and whether `scriptBodyField` had focus.
  `restoreUnsavedState()` and `applyInitialFocusAndCompactState()`
  restore all of it on recreation.
- Restored focus: whichever field held focus (or `compactMode` being
  true) before rotation gets focus back; compact mode is re-entered via
  the normal focus-listener path rather than a separate code path.
- Restored cursor positions: implemented for both fields —
  `scriptBodyField`'s selection bounds and `filenameField`'s selection
  bounds are both saved and restored (clamped to the current text
  length before being applied).
- Filename and body state preservation: both fields' live text is saved
  and restored verbatim, independent of the baseline.
- File-size limit: 2 MB, enforced by `TextFileReader` when opening an
  existing file for editing (same as section 6); there is no separate
  size limit on what can be typed/pasted into `scriptBodyField` itself.
- `IOException` handling: `writeTextFileAtomically()` catches it around
  the write and reports failure (no partial/corrupt write reaches the
  target file).
- `SecurityException` handling: caught in the same place, same outcome.
- Temporary-file atomic save sequence: write `<name>.tmp` → flush → close
  → `renameTo(target)`; the temp file is deleted if the write fails or if
  the rename fails, and `target` is never opened for writing directly.
- Overwrite protection: the "File Already Exists" dialog in `saveNew()`
  (section 8); `saveExisting()` has no equivalent prompt because it is
  always overwriting the same file the user opened.
- UTF-8 behavior: as in section 9 — every save is UTF-8 regardless of the
  original file's encoding.

## 13. Activity result workflow

- `startActivityForResult()` usage: `MainActivity` uses two request
  codes — `REQUEST_CODE_BROWSE_FILE = 100` for `FileBrowserActivity`, and
  a single `REQUEST_CODE_EDIT_SCRIPT = 200` for `ScriptEditorActivity`,
  reused for **both** CREATE_NEW and EDIT_EXISTING. Outcomes within that
  one request code are distinguished entirely through Intent extras, not
  additional numeric codes.
- Result codes and extras from `ScriptEditorActivity`:
  - `RESULT_OK` + `EXTRA_SAVED_FILE_PATH` (the written file's absolute
    path) + `EXTRA_EDITOR_WAS_NEW` (`true` for a CREATE_NEW save,
    `false` for an EDIT_EXISTING save).
  - `RESULT_CANCELED`, no extras — plain Cancel/Back/Discard.
  - `RESULT_CANCELED` + `EXTRA_EDIT_FILE_UNAVAILABLE = true` — the file
    being edited could not be read when `EDIT_EXISTING` tried to open it.
- Create-new outcome: `MainActivity` reads `EXTRA_EDITOR_WAS_NEW == true`
  and calls `promptLoadNewlySavedScript()` (section 8).
- Edit-existing outcome: `EXTRA_EDITOR_WAS_NEW == false` triggers an
  immediate reload (`loadScriptFile()`) plus a "Script saved" Toast, no
  prompt.
- Cancellation: `resultCode != RESULT_OK` and no
  `EXTRA_EDIT_FILE_UNAVAILABLE` — `MainActivity` does nothing.
- Unavailable-file signaling: `EXTRA_EDIT_FILE_UNAVAILABLE == true` →
  `applyNoScriptState()`.
- `FileBrowserActivity` result: `RESULT_OK` + `EXTRA_FILE_PATH` →
  `loadScriptFile(path)`; otherwise no-op.

## 14. Preview scrolling

- Labels: **Preview** (`btn_preview`) / **Stop** (`btn_stop_preview`),
  toggled by `updatePreviewButtonLabel()`.
- `previewScrolling` is a plain boolean field, entirely separate from
  `UiState` — starting/stopping preview never changes `uiState`, which
  stays `CONTROLS` throughout.
- Shared `Handler`: `previewScrollRunnable` is posted on the same
  `scrollHandler` used for full-screen scrolling and the countdown, so
  the existing `scrollHandler.removeCallbacksAndMessages(null)` cleanup
  calls (lifecycle methods, every full-screen transition) also cancel a
  pending preview tick for free.
- Speed behavior: preview uses the same `computeScrollPixelsPerSecond()`
  and `SCROLL_INTERVAL_MS` as full-screen scrolling — one shared
  `scrollSpeed` value drives both. See section 34 for the elapsed-time
  model and its own `previewScrollRemainderPx`/
  `previewScrollLastTickUptimeMs` accumulator/baseline pair.
- **Every Preview press starts from the top.** `startPreviewScrolling()`
  removes any pending preview callback, clears `atScriptEnd`, reapplies
  the minimal control-screen padding (`applyScriptPadding`), sets
  `previewScrolling = true` synchronously (so a second rapid press is
  rejected by the method's own guard rather than queuing a duplicate
  start), then defers via `scrollView.post()` to `scrollTo(0, 0)`,
  reset the preview accumulator/baseline, and post the first tick — the
  deferred step re-checks `previewScrolling`/`uiState` first, so a Stop
  press landing in that brief window correctly cancels the pending
  start instead of restarting it. This replaced the previous behavior,
  where Preview resumed from wherever it had last stopped — on a short
  script already at its end, pressing Preview again (e.g. after
  changing Speed) used to appear to do nothing, since the ScrollView was
  already sitting at `maxScroll`.
- Current-position behavior *while running or after Stop*: preview
  scrolls the same `scrollView` the controls-screen script preview is
  already showing; Stop preserves exactly wherever scrolling was
  stopped — only a fresh Preview *press* resets to the top, not Stop.
- Controls remaining visible: yes — preview never hides `controlsPanel`
  (it only runs while `uiState == CONTROLS`).
- Conditions that stop Preview: the Preview/Stop button itself
  (`togglePreviewScrolling()`), reaching the end of the script
  (`performPreviewScrollTick()` when `maxScroll <= 0` or the next tick
  would reach or overshoot it), and every control that defensively calls
  `stopPreviewScrolling()` — Start Prompter, New Script, Edit Script,
  Load Script, Settings, Exit, Back-out-of-full-screen, and `onPause()`.
- End-of-preview behavior: scrolls to exactly `maxScroll` then stops;
  does not loop. Stopping at the end sets no flag that would prevent the
  *next* Preview press from resetting to the top — the reset-to-top
  behavior above is unconditional on every press, regardless of why
  Preview last stopped.
- Keep-screen-awake: `scriptText.setKeepScreenOn(true)` while preview is
  active, `false` again in `stopPreviewScrolling()`.

## 15. Font Size and Speed

- Font Size: `btnFontDecrease`/`btnFontIncrease` call
  `changeFontSize(±FONT_STEP_SP)`; `FONT_STEP_SP = 4f`,
  `MIN_FONT_SIZE_SP = 14f`, `MAX_FONT_SIZE_SP = 96f`,
  `DEFAULT_FONT_SIZE_SP = 32f`.
- Live preview: `applyFontSize()` calls
  `scriptText.setTextSize(COMPLEX_UNIT_SP, fontSizeSp)` and updates
  `fontSizeLabel` immediately on every change — no separate "apply"
  step.
- Persisted via `KEY_FONT_SIZE` (float) in `SharedPreferences`.
- Speed: `speedSeekBar`, range `MIN_SPEED = 1` to `MAX_SPEED = 20`
  (`SeekBar.setMax(MAX_SPEED - MIN_SPEED)`, progress offset by
  `MIN_SPEED`), `DEFAULT_SPEED = 5`.
- Speed calculation: `computeScrollPixelsPerSecond()` —
  `scrollSpeed * SCROLL_DP_PER_SECOND_PER_SPEED_UNIT * density`
  (`SCROLL_DP_PER_SECOND_PER_SPEED_UNIT = 10f`), i.e. `scrollSpeed * 10`
  dp/second, converted to pixels via display density. This is an
  elapsed-time rate, not a fixed per-tick distance — see section 34 for
  the full scrolling model and why this preserves the exact pre-existing
  nominal rate at every Speed setting.
- How speed affects Preview and full-screen scrolling: identically —
  both call the same `computeScrollPixelsPerSecond()` off the same
  `scrollSpeed` field, every tick, so a live change (e.g. dragging
  `speedSeekBar` during Preview) takes effect on the very next tick with
  no restart.
- Persisted via `KEY_SCROLL_SPEED` (int) in `SharedPreferences`, clamped
  to `[MIN_SPEED, MAX_SPEED]` on load.
- There is no Speed minus/plus button pair anywhere in the source —
  see the "Known limitations and future ideas" section.

## 16. Mirror behavior

- `mirrorToggle` (`ToggleButton`) — "Mirror: On" / "Mirror: Off". A
  simple On/Off toggle only — it does not cycle through Mirror Mode
  choices and never gained a third visible state or a longer label.
- Persisted via `KEY_MIRROR` (boolean) in `SharedPreferences`, entirely
  separate from the `KEY_MIRROR_MODE` preference (section 35) that
  determines *which* transformation Mirror On applies — toggling Mirror
  never reads or writes `KEY_MIRROR_MODE`, and changing Mirror Mode
  never reads or writes `KEY_MIRROR`.
- `applyMirror()` sets `scaleX`, `scaleY`, and `rotation` on exactly
  three views: `scriptText`, `countdownText`, and `tapInstructionText`,
  via the shared private helper `applyMirrorTransform(view, scaleX,
  scaleY, rotation)`. Nothing else is touched. See section 35 for the
  exact values per Mirror/Mirror Mode combination.
- Script mirroring: yes (`scriptText`).
- Countdown mirroring: yes (`countdownText`).
- Tap to Start mirroring: yes (`tapInstructionText`).
- Unmirrored: the controls panel and every button/label on it, the
  Script Editor Activity in its entirety, the file browser Activity in
  its entirety, `SettingsActivity` in its entirety, the Settings gear,
  and the full-screen pause menu/end-of-script menu — `applyMirror()` is
  only ever called from `MainActivity` and only touches the three views
  above.

## 17. Start Prompter workflow

Button label "Start Prompter" (`btn_start`, renamed from "Enter
Prompter"); ID `btnStart`, its click listener, and the method it calls,
`enterFullScreenWaitingFromControls()`, were deliberately left unrenamed
(the rename was label-only, per explicit instruction not to rename Java
methods or resource IDs just because a visible label changed).

Exact implemented sequence:

1. `enterFullScreenWaitingFromControls()`: no-op unless
   `uiState == CONTROLS`.
2. If `scriptText`'s text is null/empty, show "Load a script before
   starting." (`no_script_loaded`) and stop. (In practice `btnStart` is
   disabled whenever nothing is loaded, and the placeholder text is
   never itself empty, so this is a defensive check rather than a
   reachable path through the normal UI.)
3. `stopPreviewScrolling()`.
4. Delegates to `resetToWaitingFromTop()` (shared with Restart, see
   below): cancels pending callbacks, `atScriptEnd = false`,
   `uiState = WAITING_FOR_TAP`, `refreshStateVisibility()` (hides
   `controlsPanel`, keeps the screen awake — section 19).
5. On the next layout pass: compute and apply the 77.5% opening padding
   (`OPENING_SPACE_FRACTION`, floored at `SCRIPT_PADDING_DP`), then on a
   second, nested layout pass, `scrollView.scrollTo(0, 0)`.
6. Shows "Tap to Start" (`tapInstructionText`, via
   `refreshStateVisibility()`'s `WAITING_FOR_TAP && !atScriptEnd` rule).
7. Waits for a tap (`handleScriptTap()` in `WAITING_FOR_TAP`) →
   `startCountdown(CountdownOrigin.INITIAL_START)`.
8. Countdown: `COUNTDOWN_START_VALUE = 5`, 1000ms per tick
   (`performCountdownTick()`), `countdownText` visible via
   `refreshStateVisibility()`.
9. `beginActiveScrolling()` — `uiState = SCROLLING`, first scroll tick
   posted after `SCROLL_INTERVAL_MS`.

This deliberately bypasses `switchScriptPaddingPreservingPosition()`'s
delta-preserving logic and always jumps straight to `scrollTo(0, 0)` —
Start Prompter always restarts from the top regardless of any prior
preview or paused position.

**Restart** (the pause menu's Restart button, section 19) runs the
identical `resetToWaitingFromTop()` reset (steps 4–9 above, unchanged),
the only difference being it's reachable from `PAUSED_FULLSCREEN`
instead of `CONTROLS` and the app was already in full-screen mode
throughout, so there is no `controlsPanel` to hide and no
`stopPreviewScrolling()` call (Preview cannot be active from a
full-screen state — section 19).

## 18. UiState and state transitions

```java
private enum UiState {
    CONTROLS,
    WAITING_FOR_TAP,
    COUNTING_DOWN,
    SCROLLING,
    PAUSED_FULLSCREEN,
    END_OF_SCRIPT_WAITING
}
```
— six values, declared in `MainActivity`. A second enum,
`CountdownOrigin { INITIAL_START, RESUME }`, records which of two
different places a countdown was started from (`startCountdown(origin)`
takes it as a parameter and stores it in a `countdownOrigin` field), so
canceling it can return to the correct stationary state.

`END_OF_SCRIPT_WAITING` is deliberately distinct from `PAUSED_FULLSCREEN`:
reaching the end of the script leaves the final text fully visible and
unobstructed with no overlay at all, and only becomes `PAUSED_FULLSCREEN`
(showing the reduced Restart/Back to Controls menu) once the user taps —
see section 20.

Transitions (all routed through `handleScriptTap()` for tap-driven ones):

- `CONTROLS` → `WAITING_FOR_TAP`: Start Prompter
  (`enterFullScreenWaitingFromControls()` → `resetToWaitingFromTop()`).
- `WAITING_FOR_TAP` → `COUNTING_DOWN`: tap
  (`startCountdown(CountdownOrigin.INITIAL_START)`). `atScriptEnd` is
  always `false` here — reaching the end no longer leaves the app in
  `WAITING_FOR_TAP` (see `SCROLLING` → `END_OF_SCRIPT_WAITING` below), so
  this transition no longer branches on it.
- `COUNTING_DOWN` → `SCROLLING`: countdown reaching 0
  (`beginActiveScrolling()`), not tap-driven.
- `COUNTING_DOWN` → `WAITING_FOR_TAP` or `PAUSED_FULLSCREEN`: tap or
  Android Back (`cancelCountdown()`), branching on `countdownOrigin` —
  `INITIAL_START` returns to `WAITING_FOR_TAP`, `RESUME` returns to
  `PAUSED_FULLSCREEN`.
- `SCROLLING` → `PAUSED_FULLSCREEN`: a mid-script tap or Android Back
  (`pauseFullScreenScrolling()`) — stops the scroll callback and shows
  the full pause menu (Resume/Restart/Back to Controls) immediately,
  without touching `CONTROLS` or the scroll position. Replaces what used
  to be `SCROLLING` → `CONTROLS` directly.
- `SCROLLING` → `END_OF_SCRIPT_WAITING` (with `atScriptEnd = true`):
  reaching the end of the script during a scroll tick
  (`stopScrollingAtEnd()`), not tap-driven. No menu or overlay appears
  yet — see section 20.
- `END_OF_SCRIPT_WAITING` → `PAUSED_FULLSCREEN`: a tap
  (`showEndOfScriptMenu()`, deferred via `scrollView.post(...)` — section
  20) — reveals the reduced end-of-script menu (Restart/Back to
  Controls; Resume stays hidden since `atScriptEnd` is still `true`).
- `END_OF_SCRIPT_WAITING` → `CONTROLS`: Android Back
  (`exitFullScreenToControls()`) — goes directly to `CONTROLS`,
  deliberately skipping the end-of-script menu a tap would show.
- `PAUSED_FULLSCREEN` → `COUNTING_DOWN`: pressing Resume
  (`startCountdown(CountdownOrigin.RESUME)`); only offered when
  `!atScriptEnd` (section 19) — since `atScriptEnd` is only ever `true`
  by way of `END_OF_SCRIPT_WAITING` → `PAUSED_FULLSCREEN`, Resume can
  never appear in the state that used to be reached straight from
  end-of-script.
- `PAUSED_FULLSCREEN` → `WAITING_FOR_TAP`: pressing Restart
  (`restartFromPauseMenu()` → `resetToWaitingFromTop()`, identical to
  Start Prompter's reset — section 17). Works the same whether
  `PAUSED_FULLSCREEN` was reached from a mid-script pause or from the
  end-of-script menu.
- `PAUSED_FULLSCREEN` → `CONTROLS`: pressing Back to Controls, or
  Android Back while `PAUSED_FULLSCREEN` (`exitFullScreenToControls()`
  — the same method the Exit button and Back from `WAITING_FOR_TAP`/
  `END_OF_SCRIPT_WAITING` use).
- `WAITING_FOR_TAP` → `CONTROLS`: Android Back
  (`exitFullScreenToControls()`) — unchanged from before; `WAITING_FOR_TAP`
  is the other full-screen state (besides `END_OF_SCRIPT_WAITING`) whose
  Back behavior exits directly rather than pausing/showing a menu.
- Any state → `CONTROLS`: `onPause()`, Exit button
  (`exitFullScreenToControls()`).

`previewScrolling` is a completely separate boolean; it is never read or
written by any of the `UiState` transition methods above, and none of
its own start/stop methods change `uiState`.

## 19. Full-screen scrolling, tap behavior, and the pause menu

- `Handler`/`Runnable`: `scrollRunnable` (full-screen scroll ticks),
  `countdownRunnable`, and `previewScrollRunnable` all post to the same
  `scrollHandler`; only one of the three is ever actively scheduled for
  the full-screen/countdown pair at a time (they share
  `removeCallbacksAndMessages(null)` cleanup).
- Speed calculation: `computeScrollPixelsPerSecond()` (section 15),
  consulted every `SCROLL_INTERVAL_MS` via `performScrollTick()` — see
  section 34 for the elapsed-time model.
- Start: `beginActiveScrolling()`, after the countdown reaches 0. This is
  the sole entry point into `SCROLLING` regardless of `CountdownOrigin`
  (a fresh Start Prompter/Restart tap or Resume alike), and is where the
  full-screen timing baseline and fractional-pixel accumulator are reset
  — see section 34.
- **Pause menu layout**: `pauseMenuOverlay` (a `LinearLayout`,
  `activity_main.xml`) is the last child of `fullScreenContainer`
  (after `scrollView`, `countdownText`, `tapInstructionText`), so it
  draws on top of them; `visibility="gone"` by default,
  `clickable="true"` and `focusable="true"` so that, once visible, it
  intercepts every touch within `fullScreenContainer` — the script stays
  visible but non-interactive beneath it, and a tap on the scrim outside
  the buttons does nothing (no click listener on the overlay itself, it
  just consumes the touch). Background `@color/pause_menu_scrim`
  (`#CC000000`, 80% opaque black). Contains three `ControlButton`-styled
  buttons, centered, in document order `btnResume`, `btnRestart`,
  `btnBackToControls` — "Resume" / "Restart" / "Back to Controls"
  (`btn_resume` / `btn_restart` / `btn_back_to_controls`). None of the
  three, nor the overlay itself, is touched by `applyMirror()` — the
  pause menu is never mirrored, regardless of the Mirror setting.
- Manual pause/tap-to-pause: a tap during `SCROLLING` (or Android Back)
  calls `pauseFullScreenScrolling()`: cancels the pending scroll
  callback, sets `uiState = PAUSED_FULLSCREEN`, and calls
  `refreshStateVisibility()` — which shows `pauseMenuOverlay` (with
  Resume, Restart, and Back to Controls all visible) immediately and
  keeps `controlsPanel` hidden. The scroll position is never touched, so
  it is preserved exactly. `atScriptEnd` is not touched either (stays
  `false` for a mid-script pause). This is unconditional and unchanged
  by the end-of-script correction in section 20 below — a manual pause
  always shows the full three-button menu right away, with no delay or
  intermediate state.
- Resume: pressing **Resume** calls
  `startCountdown(CountdownOrigin.RESUME)` — the same countdown path as
  a fresh Start Prompter tap (`COUNTDOWN_START_VALUE = 5`,
  `performCountdownTick()`), just with `countdownOrigin` recorded as
  `RESUME`. Neither the countdown nor `beginActiveScrolling()` touches
  `scrollView`'s position, so scrolling resumes from exactly where it
  was paused; `beginActiveScrolling()` *does* reset the elapsed-time
  baseline (section 34), so the time spent paused and counting down is
  never counted as scrolling time — without that reset, the first tick
  after Resume would see an artificially huge elapsed gap and jump far
  past the paused position. Resume is hidden (`btnResume` `GONE`)
  whenever `atScriptEnd` is true — see section 20.
- Restart: pressing **Restart** calls `restartFromPauseMenu()`, which is
  a thin guard (`uiState == PAUSED_FULLSCREEN`) around the same
  `resetToWaitingFromTop()` Start Prompter uses (section 17) — resets to
  the top, `WAITING_FOR_TAP`, "Tap to Start" shown, no automatic
  countdown or scrolling.
- Back to Controls: pressing **Back to Controls** calls
  `exitFullScreenToControls()` directly — the exact same method the Exit
  button and `WAITING_FOR_TAP`'s Back behavior use (see below).
- Return to controls: `exitFullScreenToControls()` — cancels pending
  callbacks, stops preview scrolling defensively, sets
  `uiState = CONTROLS`, refreshes visibility (hides `pauseMenuOverlay`
  along with everything else full-screen), and applies the
  minimal-padding switch with position preservation
  (`switchScriptPaddingPreservingPosition`).
- Lifecycle cleanup: `onPause()` always calls
  `exitFullScreenToControls()` then `savePreferences()`; `onStop()` and
  `onDestroy()` both additionally call
  `scrollHandler.removeCallbacksAndMessages(null)` directly.
- Keep-screen-awake: centralized in `refreshStateVisibility()` as
  `scriptText.setKeepScreenOn(uiState != UiState.CONTROLS)` — every
  full-screen state (`WAITING_FOR_TAP`, `COUNTING_DOWN`, `SCROLLING`,
  `PAUSED_FULLSCREEN`, `END_OF_SCRIPT_WAITING`) keeps the screen awake,
  not just `COUNTING_DOWN`/`SCROLLING` as before either round of this
  feature; only `CONTROLS` clears it. This
  single line replaced several individual `setKeepScreenOn()` calls that
  used to be scattered across the transition methods. Preview's own
  keep-screen-awake handling (`startPreviewScrolling()`/
  `stopPreviewScrolling()`) is separate and untouched by this, since
  Preview only ever runs while `uiState` stays `CONTROLS`.
- Preview/full-screen exclusivity: `startPreviewScrolling()` requires
  `uiState == CONTROLS`, and every full-screen entry point
  (`enterFullScreenWaitingFromControls()`) stops Preview first — Restart,
  Resume, and Back to Controls all operate entirely within the
  full-screen state cluster and never pass back through `CONTROLS`
  themselves (except Back to Controls, which stops Preview defensively
  via `exitFullScreenToControls()` even though it's already known to be
  inactive) — so Preview can never overlap full-screen scrolling, the
  pause menu, or a countdown.

## 20. End-of-script behavior

Two distinct phases, deliberately kept as two different `UiState` values
rather than reusing `PAUSED_FULLSCREEN` for both:

**Phase 1 — `END_OF_SCRIPT_WAITING`: unobstructed final text.**

- Stopping at the end: `performScrollTick()` detects `maxScroll <= 0`
  (nothing to scroll) or that the next tick would reach/exceed
  `maxScroll`, snaps to exactly `maxScroll`, and calls
  `stopScrollingAtEnd()`. `performScrollTick()`'s own code is completely
  unchanged by the end-of-script centering work in section 21 below —
  `maxScroll = scriptText.getHeight() - scrollView.getHeight()` already
  measures the true bottom of all of `scriptText`'s content, including
  whatever bottom padding is currently applied, so growing that padding
  (to hold the trailing end space) is enough on its own to make
  `maxScroll` land later, with no changes to the detection logic itself.
  `END_OF_SCRIPT_WAITING` still begins only once `scrollView` actually
  reaches that true final scroll position — never merely because the
  last line first becomes visible.
- `stopScrollingAtEnd()` cancels the pending scroll callback, sets
  `atScriptEnd = true` and `uiState = END_OF_SCRIPT_WAITING`, and calls
  `refreshStateVisibility()`. It does **not** touch `scrollView`'s
  position (already snapped to `maxScroll` by the caller) and does not
  start any countdown.
- Why a separate state: reaching the bottom of the `ScrollView` doesn't
  mean the presenter has finished reading or speaking the final visible
  lines. `END_OF_SCRIPT_WAITING` shows no overlay of any kind —
  `refreshStateVisibility()`'s existing conditions are each phrased as
  "== the one state that should show this" (`controlsPanel` only for
  `CONTROLS`, `countdownText` only for `COUNTING_DOWN`,
  `tapInstructionText` only for `WAITING_FOR_TAP && !atScriptEnd`,
  `pauseMenuOverlay` only for `PAUSED_FULLSCREEN`), so a state matching
  none of them — as `END_OF_SCRIPT_WAITING` matches none — automatically
  hides all four, leaving only the full-screen script itself visible and
  unobstructed. No code change to `refreshStateVisibility()`'s body was
  needed to get this; it fell out of how the method was already written.
- Screen stays awake throughout, via `refreshStateVisibility()`'s
  `scriptText.setKeepScreenOn(uiState != UiState.CONTROLS)` — the
  presenter gets unlimited time to finish reading before tapping.
- `controlsPanel` stays hidden and the app remains in full-screen mode
  the entire time — there is no return to `CONTROLS` in this phase.

**Phase 2 — tap reveals the reduced end-of-script menu.**

- `handleScriptTap()`'s `END_OF_SCRIPT_WAITING` case calls
  `showEndOfScriptMenu()`, which (after re-confirming `uiState` is still
  `END_OF_SCRIPT_WAITING`) transitions to `uiState = PAUSED_FULLSCREEN`
  and calls `refreshStateVisibility()` — the exact same state a
  mid-script pause reaches, so it's governed by the exact same
  visibility rules: `pauseMenuOverlay` becomes visible, and because
  `atScriptEnd` is still `true`, `btnResume` stays `GONE` (per its
  `atScriptEnd ? GONE : VISIBLE` rule) while `btnRestart` and
  `btnBackToControls` (which have no such conditional) are shown — the
  reduced **Restart** / **Back to Controls** menu, with no Resume button
  that could do nothing (or immediately re-stop) if pressed.
- Double-activation safety: `showEndOfScriptMenu()`'s state change and
  `refreshStateVisibility()` call are wrapped in `scrollView.post(...)`
  (re-checking `uiState` once more inside the posted `Runnable`, in case
  anything changed in the meantime) rather than applied synchronously
  inside the tap handler. In practice a single tap's `ACTION_DOWN`→
  `ACTION_UP` sequence is already fully dispatched and finished by the
  time `GestureDetector.onSingleTapUp()` fires, so the newly-revealed
  buttons couldn't be hit by that same physical touch regardless; the
  `post()` is defense in depth on top of that, guaranteeing the reveal
  never lands mid-dispatch of the triggering gesture.
- Restart and Back to Controls, once the menu is showing: identical to
  the manual-pause versions (section 19) — `restartFromPauseMenu()` and
  `exitFullScreenToControls()` respectively — since both states are
  `PAUSED_FULLSCREEN` by that point. No separate code path exists for
  "restart from the end" versus "restart from a mid-script pause."
- `atScriptEnd` behavior: set `true` by `stopScrollingAtEnd()`; reset to
  `false` by `resetToWaitingFromTop()` (used by both Start Prompter and
  Restart) and by `loadScriptFile()`/`applyNoScriptState()`.

**Android Back differs from a tap** while in `END_OF_SCRIPT_WAITING`:
`onBackPressed()` calls `exitFullScreenToControls()` directly, going
straight to `CONTROLS` without ever showing the end-of-script menu —
unlike a tap, which reveals it first. This mirrors `WAITING_FOR_TAP`,
the only other state whose Back behavior exits directly rather than
pausing into a menu.

- Re-entering Prompter mode: only via Start Prompter (from `CONTROLS`) or
  Restart (from the end-of-script menu, once revealed), both of which
  unconditionally reset `atScriptEnd = false` and scroll to the top
  (section 17) — there is still no "continue from the end" or "loop"
  behavior.

## 21. Preview versus full-screen padding, and the end-of-script trailing space

`scriptText`'s padding is the entire mechanism behind both the opening
buffer and the end-of-script centering; there is no separate "trailing
space view" or scroll-range override anywhere. Two helper methods
centralize every change to it:

- `applyScriptPadding(topPaddingPx, bottomPaddingPx)` — the **only**
  place `scriptText.setPadding(...)` is ever called. Left/right always
  use `SCRIPT_PADDING_DP`; top and bottom are passed in explicitly by
  every caller, so neither is ever left implicit.
- `switchScriptPaddingPreservingPosition(newTopPaddingPx,
  newBottomPaddingPx, afterSettled)` — changes both paddings via
  `applyScriptPadding()` while preserving the apparent scroll position,
  used **only** by `exitFullScreenToControls()` (returning to controls,
  including via Back to Controls). Only the top-padding delta shifts the
  preserved position — `scrollY` is measured from the top of the
  content, so a bottom-padding change alone never moves any earlier
  content on screen — but the new bottom padding is applied in the same
  call, so the post-layout clamp below already sees the correct, updated
  maximum scroll range.

**Minimal control-screen padding** (`uiState == CONTROLS`): both top and
bottom set to `SCRIPT_PADDING_DP = 24f` via `applyScriptPadding()` —
unchanged from before this round, just now stated as an explicit bottom
value instead of an implicit side-padding coincidence. This is also
exactly what Preview/Stop scrolls within: Preview only ever runs while
`uiState == CONTROLS` (section 19), so `performPreviewScrollTick()`'s own
`maxScroll` calculation automatically reflects this minimal padding,
never the large full-screen trailing space below — no code change was
needed in either Preview method to keep this true.

**Full-screen opening padding (top)**: `OPENING_SPACE_FRACTION = 0.775f`
of the measured full-screen viewport height (`scrollView.getHeight()`),
floored at `SCRIPT_PADDING_DP`. Unchanged from before this round.

**Full-screen trailing end space (bottom)**: `END_SPACE_FRACTION = 0.4f`
of the same measured full-screen viewport height, floored at
`SCRIPT_PADDING_DP`. Both fractions are computed together, from the same
viewport-height measurement, inside `resetToWaitingFromTop()` (shared by
Start Prompter and Restart — section 17) and applied via
`applyScriptPadding()`. Because scrollable content height includes a
View's own padding, this bottom padding *is* the trailing space — no
blank lines or extra characters are ever added to `scriptText`'s actual
string content, and nothing about the loaded/saved script file changes.

*Why `0.4f` lowers the final line's resting position*: at true max
scroll, the bottom edge of `scrollView`'s content is flush with the
bottom edge of the viewport (`scrollY = maxScroll = contentHeight -
viewportHeight`). The text's own bottom edge (end of the last line,
before the trailing padding) sits at content-relative height `topPadding
+ textHeight`. Its on-screen position at that scroll position works out
to `viewportHeight - bottomPadding`, independent of `topPadding` and
`textHeight` entirely. Setting `bottomPadding = viewportHeight *
END_SPACE_FRACTION` therefore leaves the last line's bottom edge at
`viewportHeight * (1 - END_SPACE_FRACTION)`: at the prior `0.5f` this was
`viewportHeight * 0.5` (the vertical midpoint); at the current `0.4f` it
is `viewportHeight * 0.6` (60% of the way down — lower, i.e. closer to
the bottom edge, than before), regardless of font size, script length, or
device resolution. This physically lower resting position was requested
after testing on real hardware showed the prior `0.5f` value read as
slightly too high; the `0.4f` value itself is implemented and builds
correctly but, per this repository's own testing discipline, is not
claimed as physically verified until it too has actually been run on a
device — see section 26.

**Full-screen padding is measured only after the system bars are
hidden**: `resetToWaitingFromTop()` calls `enterPrompterSystemUi()`
(section 22) before the viewport height is ever read. Hiding the status
and navigation bars changes the usable viewport height, so measuring
`scrollView.getHeight()` before they are hidden would compute both
fractions against the wrong (larger, bars-still-visible) height. An
extra `scrollView.post()` layer is inserted specifically to let that
bar-hide's own layout pass settle before the existing height-measurement
`post()` runs — see section 22 for the full sequencing.

**Scroll-end detection** (section 20's first bullet): `performScrollTick()`
is unmodified; `scriptText.getHeight()` (which already includes the new
bottom padding in its measured height) makes `maxScroll` land later on
its own, so scrolling continues through the trailing space using the
existing tick loop and existing speed calculation, and
`stopScrollingAtEnd()` still fires from the same two call sites, only
later than before this round.

**Short scripts**: even a script that fits entirely within one screen
still has `maxScroll ≈ (OPENING_SPACE_FRACTION + END_SPACE_FRACTION − 1)
× viewportHeight + textHeight`, i.e. positive (~0.275 × viewportHeight
at minimum) purely from the two paddings, regardless of how short
`textHeight` is — so scrolling always has real distance to travel and
never stops immediately just because every line was already visible when
it began.

**Long scripts**: nothing about mid-script scrolling, speed, pause, or
Resume changes — the trailing space only ever affects where the *true*
maximum scroll position is, not how scrolling behaves before reaching it.

**Scroll-position adjustment** (in
`switchScriptPaddingPreservingPosition()`): if `scrollView.getScrollY() >
0` when the padding changes, the new scroll position is `previousScrollY
+ (newTopPaddingPx - oldTopPaddingPx)`, clamped to `[0, contentHeight -
viewportHeight]` — using the content height *after* both new paddings
have been applied — applied after the padding change has been laid out
(`scrollView.post(...)`). If already at scroll position 0, no adjustment
is made — the new padding alone produces the correct look.

**Intentional reset when Start Prompter or Restart is pressed**:
`resetToWaitingFromTop()` always forces `scrollTo(0, 0)` regardless of
where the preview or a previous full-screen session left off (section
17) — this is a deliberate bypass of position preservation, not an
oversight.

**Preservation when returning to controls after pausing**: Back to
Controls / Android Back from `PAUSED_FULLSCREEN`, Back from
`END_OF_SCRIPT_WAITING`, and Back from `WAITING_FOR_TAP`, all go through
`exitFullScreenToControls()`, which **does** go through
`switchScriptPaddingPreservingPosition()`, so the controls-screen preview
lands on approximately the same passage of text that was paused (or the
top of the script, for `WAITING_FOR_TAP`) in full-screen mode — including
from a position within the trailing end space, or from the fully
centered end-of-script position itself.

**Mirroring**: `applyMirror()` only ever sets `scaleX` on `scriptText`,
`countdownText`, and `tapInstructionText` (section 16) — it has no
interaction with padding or vertical layout at all, so horizontal
mirroring never affects any of the above.

**Pause and Resume through the trailing space**: `pauseFullScreenScrolling()`
and the Resume path (`startCountdown(CountdownOrigin.RESUME)` →
`beginActiveScrolling()`) never touch padding, so pausing partway through
the trailing space and resuming behaves exactly like pausing anywhere
else in the script (section 19) — `performScrollTick()` simply continues
its existing tick loop toward the same (unchanged) `maxScroll`.

## 22. Full-screen status and navigation bar hiding

Every full-screen Prompter `UiState` (`WAITING_FOR_TAP`, `COUNTING_DOWN`,
`SCROLLING`, `PAUSED_FULLSCREEN`, `END_OF_SCRIPT_WAITING`) hides the
Android status bar and navigation bar, leaving only script text, Tap to
Start, the countdown, the pause menu, or the end-of-script menu visible
against the black background. `CONTROLS`, `FileBrowserActivity`, and
`ScriptEditorActivity` all show normal Android system bars — this is a
runtime, per-window concern confined to `MainActivity`'s own Window, not
a manifest or theme change, so the other two activities (which share
`@style/AppTheme` at the Application level, unchanged by this work) are
automatically unaffected.

**`uiState` is the sole source of truth** — there is no second
"is full screen" boolean anywhere in `MainActivity`. Three centralized
helper methods are the only places system-UI flags are ever touched:

- `enterPrompterSystemUi()` — hides both bars. API 14–18 get the legacy
  combination: `View.SYSTEM_UI_FLAG_HIDE_NAVIGATION` (available since API
  14) plus the window's `WindowManager.LayoutParams.FLAG_FULLSCREEN`
  (hides the status bar), applied via `Window.addFlags()`/
  `View.setSystemUiVisibility()` at runtime, not by setting flags before
  `setContentView()`. API 19+ additionally get the guarded
  immersive-sticky set — `SYSTEM_UI_FLAG_FULLSCREEN`,
  `SYSTEM_UI_FLAG_HIDE_NAVIGATION`, `SYSTEM_UI_FLAG_IMMERSIVE_STICKY`,
  `SYSTEM_UI_FLAG_LAYOUT_STABLE`, `SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN`,
  `SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION` — wrapped in a
  `Build.VERSION.SDK_INT >= 19` guard, so a device below API 19 never
  even references the immersive-sticky constant at runtime. No AndroidX,
  no `WindowInsetsController`/`WindowInsetsCompat` — every flag used is a
  platform `View`/`Window` constant compiled against `compileSdkVersion
  23` but guarded to the API level that actually defines its behavior.
- `exitPrompterSystemUi()` — restores normal bars: clears
  `FLAG_FULLSCREEN` and sets `View.SYSTEM_UI_FLAG_VISIBLE` (value `0`,
  present since API 14). Called from `exitFullScreenToControls()` (the
  single funnel back to `CONTROLS` used by the Back to Controls button,
  the Exit button, and every `onBackPressed()` case that returns to
  `CONTROLS`) and from `onResume()`.
- `reapplyPrompterSystemUiIfNeeded()` — re-issues the hidden-bar flags
  only if `uiState != CONTROLS`, otherwise does nothing. Used in two
  places: at the end of `handleScriptTap()`, deferred via
  `scrollView.post()` so it runs strictly after whatever state
  transition that same tap triggered has already been dispatched (API
  14–18 can briefly reveal the navigation bar on any touch, with no
  callback to detect that other than re-asserting the flags after the
  fact); and from the new `onWindowFocusChanged(boolean hasFocus)`
  override, when `hasFocus` is `true` (the window regaining focus after
  the recents switcher, another app, or a system dialog/notification
  shade).

**Bars are hidden only once the app has committed to entering full
screen**: `resetToWaitingFromTop()` (section 17/21, shared by Start
Prompter and Restart) calls `enterPrompterSystemUi()` immediately after
setting `uiState = WAITING_FOR_TAP` and before any padding is measured.
Every other full-screen state is reached from `WAITING_FOR_TAP` or
`PAUSED_FULLSCREEN` (both already full-screen with bars already hidden —
see section 18's state diagram), so `resetToWaitingFromTop()` is the only
place bars need to be freshly hidden on entry.

**Padding is measured only after the bars are hidden and layout has
settled** (section 21's final subsection has the padding side of this;
this is the system-UI side): `resetToWaitingFromTop()`'s `scrollView.post()`
chain now has an extra layer — `enterPrompterSystemUi()` runs
synchronously, then one `scrollView.post()` is used purely to let that
bar-hide's own layout pass complete, and only the *nested* `post()`
inside it reads `scrollView.getHeight()` and computes the top/bottom
padding fractions. This is a single bounded extra frame, not a loop or
recursive re-posting — `resetToWaitingFromTop()` still runs to completion
in a fixed, small number of layout passes every time it's called.

**Restoration**: every path back to `CONTROLS` — the pause menu's Back to
Controls, the end-of-script menu's Back to Controls, Android Back from
`WAITING_FOR_TAP`/`PAUSED_FULLSCREEN`/`END_OF_SCRIPT_WAITING`, the Exit
button, and `onPause()` backgrounding the app — already funnels through
`exitFullScreenToControls()` (section 23), so adding
`exitPrompterSystemUi()` there was sufficient; no other method needed a
restoration call. `onResume()` additionally re-checks `uiState` on its
own (`exitPrompterSystemUi()` if `CONTROLS`, `enterPrompterSystemUi()`
otherwise) as a defensive measure, though in practice `onPause()` already
forces `uiState` to `CONTROLS` before the app can ever be backgrounded,
so the "still full-screen on resume" branch is not currently reachable —
it exists so `onResume()` stays correct if that invariant ever changes,
without introducing a second source of truth.

**Lifecycle**: `onPause()`/`onStop()`/`onDestroy()`'s existing callback
cleanup (`scrollHandler.removeCallbacksAndMessages(null)`) is unchanged;
none of the scrolling/countdown lifecycle was touched by this work.
Keep-screen-awake (`refreshStateVisibility()`, section 19) and
system-bar hiding are deliberately kept as separate, independently
callable concerns — neither method calls the other, and no single flag
represents both.

**Explicit constraints preserved**: this is ordinary, temporary,
per-Activity `Window`/`View` flag manipulation — not kiosk mode, lock
task mode, a device-owner API, root access, or an accessibility-service
trick — and it never permanently disables Android navigation. The user
retains whatever platform-supported way their Android version offers to
reveal the bars (an edge swipe on API 19+ with immersive-sticky, or a
tap revealing them on API 14–18 until `reapplyPrompterSystemUiIfNeeded()`
re-hides them on the next tap or focus event).

**Hardware verification status**: implemented and confirmed by source
inspection and a successful build only. Neither the legacy (API 14–18)
nor the guarded immersive-sticky (API 19+) behavior, nor the tap/focus
reapplication logic, has been run on physical hardware as of this
writing — see section 26 for what has and has not actually been tested
on a device. Do not treat a successful build as evidence this behaves
correctly on any real tablet.

## 23. Exit and Android Back behavior

- Exit (`btnExit`): `exitFullScreenToControls()` then `finish()` —
  always leaves the app from a clean controls-screen state first.
  Unchanged.
- Android Back, by `uiState` (`onBackPressed()`, a `switch` over
  `uiState`):
  - `SCROLLING` → `pauseFullScreenScrolling()`: pauses into
    `PAUSED_FULLSCREEN` and shows the pause menu, mirroring a
    script-area tap. Does **not** immediately return to `CONTROLS`.
  - `PAUSED_FULLSCREEN` → `exitFullScreenToControls()`: identical to
    pressing Back to Controls.
  - `END_OF_SCRIPT_WAITING` → `exitFullScreenToControls()`: goes directly
    to `CONTROLS` without first showing the end-of-script menu — unlike
    a tap in this state, which reveals it (section 20). Does not restart
    the script and does not display a countdown.
  - `COUNTING_DOWN` → `cancelCountdown()`: cancels the countdown and
    returns to `WAITING_FOR_TAP` (with "Tap to Start" shown) or
    `PAUSED_FULLSCREEN` (with the pause menu shown, paused position
    preserved), depending on `countdownOrigin`.
  - `WAITING_FOR_TAP` → `exitFullScreenToControls()`: exits full-screen
    mode directly to `CONTROLS` — the other full-screen state (besides
    `END_OF_SCRIPT_WAITING`) that behaves this way rather than
    pausing/showing a menu, unchanged from before the pause menu existed.
  - `CONTROLS` → `finish()`: closes the app. Unchanged.
- Editor Back (`ScriptEditorActivity.onBackPressed()`):
  `handleCancelOrBack()` — dirty-check + Discard Changes dialog, same as
  the Cancel button (section 12). Unaffected by any of the above.
- File browser Back: navigates up one directory if not already at the
  browser's start location or a detected storage root; otherwise
  `setResult(RESULT_CANCELED)` and closes. Unaffected.
- Callback cleanup: every path back to `CONTROLS`, into
  `PAUSED_FULLSCREEN`, or into `END_OF_SCRIPT_WAITING` cancels pending
  callbacks first (`exitFullScreenToControls()`,
  `pauseFullScreenScrolling()`, `cancelCountdown()`,
  `stopScrollingAtEnd()`, and the lifecycle methods' explicit
  `scrollHandler.removeCallbacksAndMessages(null)`), so no
  scroll/countdown/preview callback can keep firing after leaving the
  relevant state.
- Preference preservation: `onPause()` calls `savePreferences()`
  unconditionally; nothing in the Back/Exit/pause-menu paths clears any
  saved preference.

## 24. SharedPreferences and persisted state

Prefs name: `teleprompter_prefs` (`AppConstants.PREFS_NAME`). Keys
(`AppConstants`):

- `KEY_FONT_SIZE` (float) — current font size in sp. Written by
  `savePreferences()` (called from `changeFontSize()` and `onPause()`).
- `KEY_SCROLL_SPEED` (int) — current scroll speed. Written the same way,
  plus directly from the `SeekBar`'s `onProgressChanged` when
  `fromUser == true`.
- `KEY_MIRROR` (boolean) — mirror on/off. Written from the toggle's
  `onCheckedChanged` and from `onPause()`.
- `KEY_LAST_FILE_PATH` (string) — the loaded script's path; only written
  when `loadedFilePath != null` (never cleared to null in prefs by
  `applyNoScriptState()` — a stale value can remain in prefs until the
  next successful load, but `onCreate()`'s restore path nulls the
  in-memory field before attempting to reload it, per the fix described
  in section 5's "unavailable" case).
- `KEY_LAST_DIRECTORY` (string) — last browsed/relevant directory.
  Written by `FileBrowserActivity.confirmSelection()` (the directory the
  selected file was found in) and by
  `MainActivity.promptLoadNewlySavedScript()`'s Yes branch (the
  Teleprompter folder).
- `KEY_TEXT_ALIGNMENT` (string; `TEXT_ALIGNMENT_LEFT` = `"left"` or
  `TEXT_ALIGNMENT_CENTER` = `"center"`, default `TEXT_ALIGNMENT_LEFT`) —
  see section 31. Written only by `SettingsActivity`, immediately on
  each `RadioGroup` selection change; `MainActivity` only ever reads it.
- `KEY_PROMPTER_ORIENTATION` (string; `PROMPTER_ORIENTATION_HORIZONTAL`
  = `"horizontal"` or `PROMPTER_ORIENTATION_VERTICAL` = `"vertical"`,
  default `PROMPTER_ORIENTATION_HORIZONTAL`) — see section 32. Same
  write/read split as Text Alignment.
- `KEY_MIRROR_MODE` (string; `MIRROR_MODE_FLIP` = `"flip"` (default) or
  `MIRROR_MODE_FLIP_ROTATE` = `"flip_rotate"`) — see section 35. Same
  write/read split as the other Settings-owned keys; entirely separate
  from `KEY_MIRROR` (still written only by `mirrorToggle`'s listener and
  `onPause()`, above) — neither key is ever read or written alongside
  the other.
- `KEY_LOAD_LAST_SCRIPT` (boolean, default `true`) — see section 33.
  Same write/read split as the other Settings-owned keys.

`savePreferences()` uses `SharedPreferences.Editor.commit()`
(synchronous), not `apply()`; `SettingsActivity`'s three listeners each
do the same, independently, on their own `SharedPreferences.Editor`.
None of the three Settings-owned keys above is ever written by
`MainActivity.savePreferences()` — `MainActivity` only reads them (via
`loadDisplayAndStartupPreferences()`, called from `loadPreferences()` on
create and again, on its own, every `onResume()`), so there is nothing
for that method to reconcile for these three the way it does for font
size/speed/mirror/last file path.

## 25. Build process

- The repository is a normal Gradle/Android-Gradle-Plugin project
  (`build.gradle`, `app/build.gradle`, `settings.gradle`,
  `gradle.properties`) and would normally build with
  `./gradlew assembleDebug`.
- Network limitation encountered in this cloud environment: outbound
  access to `dl.google.com`/`maven.google.com` is blocked, so the Android
  Gradle Plugin itself cannot be downloaded and Gradle cannot be used
  here.
- Fallback: `build.sh` at the repository root drives the underlying
  tools directly, without Gradle/AGP:
  1. `aapt2 compile --dir app/src/main/res -o build/res-compiled/res.zip`
  2. `aapt2 link` against `android.jar` from apt's
     `android-sdk-platform-23` (installed from `archive.ubuntu.com`, not
     Google's servers) with `--min-sdk-version 14
     --target-sdk-version 14 --version-code 1 --version-name 1.0`,
     generating `R.java` and `build/apk/base.apk`.
  3. `javac --release 8` compiles all `.java` sources plus the generated
     `R.java` against `android.jar`.
  4. `dx --dex` converts the compiled classes to `classes.dex`.
  5. `classes.dex` is zipped into a copy of `base.apk`
     (`build/apk/unsigned.apk`).
  6. `zipalign -f -p 4` produces `build/apk/aligned.apk`.
  7. A debug keystore is generated once via `keytool` on first run
     (`build/keystore/debug.keystore`, alias `androiddebugkey`) and
     reused thereafter.
  8. `apksigner sign --min-sdk-version 14 --v1-signing-enabled true
     --v2-signing-enabled true --v3-signing-enabled false
     --v4-signing-enabled false` produces the final signed APK.
  9. `apksigner verify -v --min-sdk-version 14` confirms the signature.
- APK output path: `app/build/outputs/apk/debug/app-debug.apk` — the
  same conventional location Gradle would use, but produced by
  `build.sh` and not committed to the repository (`.gitignore` excludes
  both `/build/` and `/app/build/`).

## 26. Hardware testing

### Physically tested on Lenovo Smart Tab M8

Reported by the user, based on running built APKs on that device. Runs
Android 10 (API level 29).

- Normal sideloading works.
- Android displays a one-time warning on first launch that the
  application was designed for an older Android version; the app
  launches and functions normally once the warning is dismissed. See
  "Android version warning" below — this is device/version-dependent,
  not universal.
- Core teleprompter functionality has been physically tested.
- The built-in file browser has been physically tested.
- Preview scrolling has been physically tested.
- New Script and Edit Script have been physically tested.
- The compact Script Editor layout has been physically tested. The
  fullscreen white keyboard "extract editor" previously appeared when
  the soft keyboard opened in the Script Editor; the `flagNoExtractUi`
  fix (section 11) was confirmed to prevent it. Before the
  compact-editing-mode fix existed, opening the keyboard in landscape
  was confirmed to compress `scriptBodyField` to zero usable height,
  motivating that work. The specific editor-button refinements built in
  response to four issues found during that testing (`editorButtonRow`
  hiding while `filenameField` has focus, the two buttons' identical
  geometry via `EditorActionButton`, the updated filename hint) have not
  been separately re-tested since being implemented — treat those three
  specifically as implemented-and-compiled (compiled-APK-verified for
  the button geometry and hint text), not hardware-reverified.
- The full-screen pause menu has been physically tested and is
  functional. Resume, Restart, and Back to Controls have all been
  physically tested.
- The `END_OF_SCRIPT_WAITING` correction (unobstructed final text with
  no automatic overlay, revealed only by a tap) has been physically
  confirmed working on this device: scrolling stops, the final text
  stays unobstructed, no menu appears automatically, and tapping reveals
  Restart and Back to Controls.

- The lowered end-of-script resting position (`END_SPACE_FRACTION =
  0.4f`, section 21) and full-screen status/navigation bar hiding
  (section 22) have both been physically tested on this device and
  reported working as expected. Running API 29, this device exercises
  the guarded API 19+ immersive-sticky system-bar code path (section
  22), the same as the Nexus 7 below.

**Awaiting physical verification on the Lenovo**: the Settings screen
(section 30), Text Alignment (section 31), Prompter Orientation
(section 32), Load Last Script on Launch (section 33), and Mirror Mode
(section 35) have not been installed or tested on this device.

### ASUS Nexus 7 (2013, second generation)

Newly reported by the user this round, based on running the built APK on
this device. Identified as the 2013 second-generation Nexus 7 by its
rear-facing camera (the 2012 first generation has none) and its Android
version.

- Runs Android 6.0.1 "Marshmallow" (API level 23).
- Installs normally through sideloading and launches normally.
- Android 6.0.1 did **not** display the "designed for an older Android
  version" warning on this specific tested device — this observation is
  specific to this Nexus 7 and is not generalized to every Android 6
  device or manufacturer build (see "Android version warning" below).
- The main controls, built-in file browser, script loading, New Script,
  Edit Script, Preview scrolling, full-screen Prompter mode, the pause
  menu, and Resume/Restart/Back to Controls are all functional on this
  device.
- The `END_OF_SCRIPT_WAITING` correction has also been physically
  confirmed working on this device, same as the Lenovo above.
- The Nexus 7 has less usable screen space than the Lenovo, making it a
  useful compact-layout test in addition to the Lenovo.

- The same two refinements — the `0.4f` end-of-script resting position
  and full-screen status/navigation bar hiding — have also been
  physically tested on this device and reported working as expected.
  Running API 23, this device specifically exercises the guarded API 19+
  immersive-sticky system-bar code path (section 22).

**Awaiting physical verification on the Nexus 7**: same as the Lenovo
above — the Settings screen and its four settings, including Mirror
Mode, have not been installed or tested on this device. In particular,
Vertical Prompter Orientation on this device would exercise the API 19+
immersive-sticky system-bar path (section 22) during the
orientation-driven recreation (section 32) — an interaction not yet
exercised on any device.

### Acer Iconia Tab A500

Physically tested on an actual Acer Iconia Tab A500 as of this round —
the first confirmed test result for this device anywhere in this
project's history. The user reported running the built APK on the
device and confirmed both of this round's refinements work as expected:
the lowered end-of-script resting position (`END_SPACE_FRACTION =
0.4f`, section 21) and full-screen status/navigation bar hiding (section
22). Running (the original target) Android 4.0/API 14, this is the only
device in this project's history confirmed to exercise the legacy (API
14–18) system-bar hiding path specifically, rather than the guarded API
19+ immersive-sticky path — including a touch's effect on the navigation
bar and `reapplyPrompterSystemUiIfNeeded()`'s reapplication of it.

This confirmation covers those two specific refinements only. It is not
a confirmation of every other feature this document describes as
Lenovo- or Nexus-7-tested — nothing here states the A500 was used to
retest, for example, the built-in file browser, New/Edit Script, the
compact Script Editor layout, the pause menu, or Resume/Restart, and
none of that should be inferred from this entry. Do not claim confirmed
A500 behavior beyond what is stated here without a further report.
Still not confirmed on this device specifically: real-world auto-scroll
smoothness on weaker hardware, the exact on-device rendering of the
launcher icon and button-background drawables, and whether the A500's
microSD mount path is among the legacy paths already checked (section
6).

**Awaiting physical verification on the A500**: the Settings screen and
its four settings (sections 30–33, 35), including Mirror Mode, have not
been installed or tested on this device. Vertical Prompter Orientation
here would specifically
exercise the API 14–18 legacy system-bar path (section 22) together with
the orientation-recreation mechanism (section 32) — the combination this
project can least afford to get wrong unverified, since the A500 is the
original target hardware and the only device confirmed to run the
legacy path at all.

### Android version warning: device- and version-dependent, not universal

Some newer Android devices may display a one-time warning that the app
was designed for an older Android version. This warning was observed on
the Lenovo Smart Tab M8 but was **not** displayed on the tested ASUS
Nexus 7 running Android 6.0.1. Do not say that all Android 6 devices
suppress the warning, and do not say that every newer Android device
displays it — both tested devices are the only data points this project
has, and they disagree with each other. This is separate from, and does
not change:

- The intended normal-installation range, Android 4.0 through Android
  13 (not every version in that range has been physically tested — only
  the two devices above have).
- Starting with Android 14, normal installation is blocked outright
  because this app's `targetSdkVersion` (14) is below the minimum
  Android 14 permits — a stricter, separate restriction from the
  first-launch warning above, not bypassed by enabling "Install unknown
  apps." Android 14+ is outside this app's normal supported installation
  range (see `README.md`'s Android Compatibility section for the
  advanced `adb install --bypass-low-target-sdk-block` testing
  workaround).

`minSdkVersion`/`targetSdkVersion` remain 14; this project is not
redefined as a modern-Android application by any of the above.

## 27. Artwork and launcher icons

- Source artwork lives under `artwork/`: `DroidPrompt-bg.png` (solid
  background variant) and `DroidPrompt-trans.png` (transparent
  background variant). Neither file is referenced by any Android
  resource path — they are editable source material only, not packaged
  into the APK directly.
- The actual packaged launcher icon resources are under
  `app/src/main/res/mipmap-{hdpi,mdpi,xhdpi,xxhdpi,xxxhdpi}/
  ic_launcher.png` (legacy, pre-API-26 icon) and
  `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` (an adaptive icon
  for API 26+, referencing `drawable-nodpi/ic_launcher_foreground.png`
  as its foreground and `@color/background_black` as a solid-color
  background — not artwork-derived).
- These are two different things: editable source artwork (`artwork/`)
  versus packaged, resource-referenced app icon assets
  (`app/src/main/res/mipmap-*`, `drawable-nodpi/`). Do not move the
  packaged mipmap/drawable-nodpi resources into `artwork/`, and do not
  assume the reverse either.
- The official app name is established by `strings.xml`'s `app_name` =
  "A500 Teleprompter", used as `android:label` throughout the manifest.
  Nothing in the current source treats "DroidPrompt" as the application
  name; it only appears in the two artwork filenames.

## 28. Known limitations and future ideas

Not implemented — listed only to say so explicitly, per instruction not
to present these as current functionality:

- Replacing the Speed `SeekBar` with minus/plus buttons (Font Size
  already uses buttons; Speed does not, and no such Speed buttons exist
  in source).
- A separate modern-Android-targeted build (only one `build.gradle`
  target exists, `minSdkVersion`/`targetSdkVersion` 14).
- Changing the final product name away from "A500 Teleprompter" (it
  remains a working title, but no rename has been made in source).
- Hardware verification of the compact-editing-mode UI refinements
  described in section 11/25 — implemented and compiled, not yet
  confirmed on a physical device since being added.

## 29. Change and release discipline

Intended practice going forward (not yet practiced in this repository's
history — no tags exist yet, and there is no prior tagged known-good
release to point to):

- Tag known-good releases once a version has been verified (build +
  signature/zipalign checks, and ideally hardware testing) so there is a
  restorable checkpoint.
- Preserve a working APK build before substantial experiments — e.g. by
  tagging or branching before a large refactor, not by relying on
  `app/build/outputs/` alone (it is gitignored and gets overwritten by
  every `build.sh` run).
- Use focused, single-purpose commits (the existing commit history on
  this branch already follows this pattern — one behavioral change per
  commit).
- Keep source and behavior documentation synchronized: update
  `PROJECT_SPEC.md` whenever product behavior changes, not just when
  asked to.
- Keep `CLAUDE.md` concise — persistent rules only, not a history log.

---

## 30. Settings screen and the Settings gear icon

**Main-screen placement** (`app/src/main/res/layout/activity_main.xml`,
inside the Font Size/Speed adjustment row — section 4): the row now
reads `Font Size | Speed and Preview | Settings`, three sections
separated by two vertical dividers. The second divider is a plain
decorative `View`, not referenced by `MainActivity.java`, with the exact
same width (`@dimen/adjustment_divider_width`), color
(`@color/divider_color`), margins (12dp horizontal, `@dimen/
adjustment_divider_vertical_margin` vertical), `clickable="false"`, and
`focusable="false"` as the pre-existing first divider — a deliberate
copy, not a shared style, matching how the first divider was already
written inline. Neither divider carries an `android:id`.

**Row width allocation**: the Font Size group is `android:layout_width=
"wrap_content"` with no `layout_weight` — it takes exactly the width its
label/`A-`/value/`A+` need, so the first divider sits immediately after
`A+` at its intended 12dp margin, with no blank trailing gap. Only the
Speed group carries `layout_weight="1"` (changed from the original
`38`/`59` split between the two groups): as the row's one remaining
flexible-width sibling, it claims all the space left over after the
now-`wrap_content` Font Size group, both fixed-width dividers, and the
fixed 56dp Settings button are accounted for. Inside the Speed group,
`speedSeekBar` still carries its own `layout_weight="1"` to expand
within that group — this was already the mechanism that kept Preview
flush against the second divider with no gap, and is why only the Font
Size side needed fixing.

`btnSettings` is an `ImageButton`, not a full-size text button: no
visible "Settings" label appears on the main screen, only the icon.

- Size: `@dimen/secondary_button_height` (56dp) square — comfortably
  above the ~48dp minimum touch-target guideline, and matched to
  `btnPreview`'s height (`SecondaryControlButton`, also 56dp) so the
  gear reads as the same visual weight as Preview/Stop, not larger.
- Background: `@drawable/button_background` — the same selector
  (normal/pressed/disabled states) every other control button already
  uses, so pressed-state feedback matches the rest of the app for free.
- `android:padding="12dp"` inside the 56dp button leaves a 32dp visible
  icon, keeping the artwork clear of the button edges.
- `android:contentDescription="@string/settings_content_description"`
  (string value "Settings") — sourced from `strings.xml`, never
  hard-coded in Java or XML.
- No `setEnabled()` call anywhere ties `btnSettings` to
  `updateLoadedScriptDependentControls()` (unlike `btnStart`/
  `btnEditScript`/`btnPreview`) — the gear stays enabled whether or not
  a script is loaded, and is never touched by that method.
- `applyMirror()` never includes `btnSettings` in its `scaleX` list, so
  the icon is never mirrored regardless of the Mirror setting.

**Gear icon artwork** (`ic_settings.png`): a white, outlined 8-tooth gear
with a clearly visible open center hole, transparent background, no
text, high contrast against the app's black/dark-gray interface. A PNG,
not a vector drawable — API 14 has no `VectorDrawable` support without
an AndroidX compat shim, which is prohibited. Sourced from a
user-supplied 512×512 RGBA PNG (already square, artwork extending to
all four canvas edges with no built-in transparent padding of its own —
the button's existing 12dp `android:padding` supplies that instead, so
none was added to the source), downsampled with Pillow's `LANCZOS`
filter to each density's exact target size, preserving the original
artwork, proportions, and transparency unmodified — no stretching,
cropping, or redrawing. This replaced an earlier programmatically
generated gear (a solid silhouette with no center hole, which read as
less clearly gear-like at small sizes) from the previous round. Provided
at five density buckets, each a plain raster PNG at the same design (no
`-v4`/adaptive-icon complexity used for the launcher icon):

| Folder | Size |
| --- | --- |
| `drawable-mdpi/ic_settings.png` | 32×32 |
| `drawable-hdpi/ic_settings.png` | 48×48 |
| `drawable-xhdpi/ic_settings.png` | 64×64 |
| `drawable-xxhdpi/ic_settings.png` | 96×96 |
| `drawable-xxxhdpi/ic_settings.png` | 128×128 |

The existing launcher icon (`mipmap-*/ic_launcher.png`,
`mipmap-anydpi-v26/ic_launcher.xml`,
`drawable-nodpi/ic_launcher_foreground.png`) is untouched by this work.

**Tap behavior** (`btnSettings`'s `OnClickListener`, only ever reachable
while `uiState == CONTROLS` since it lives on `controlsPanel`):

1. `stopPreviewScrolling()` — stops Preview if running, removes the
   pending preview callback, clears `previewScrolling`, and restores the
   Preview/Stop button label. This is the same centralized method every
   other control that must not leave Preview running underneath it
   already calls (New Script, Edit Script, Load Script, Start Prompter,
   Exit) — Settings was simply added to that existing list, not given
   its own bespoke stop logic.
2. `startActivity(new Intent(MainActivity.this, SettingsActivity.class))`
   — a plain, non-`ForResult` launch. `MainActivity` never needs a
   result code from `SettingsActivity`: every setting saves to
   `SharedPreferences` immediately (see sections 31–33, 35), and
   `onResume()` already rereads all four on every return from any child
   Activity (see section 32's orientation discussion for why
   `onResume()`, not a dedicated `onActivityResult()` case, is the
   single reread point).

Nothing else changes: the loaded script, filename, scroll position,
font size, speed, mirror state, and `uiState` are all left exactly as
they were. `uiState` never leaves `CONTROLS` for this flow.

**`SettingsActivity`** (`app/src/main/java/com/a500teleprompter/app/
SettingsActivity.java`, layout `app/src/main/res/layout/
activity_settings.xml`): a plain `Activity`, declared
`android:screenOrientation="landscape"` in `AndroidManifest.xml` (like
`FileBrowserActivity`/`ScriptEditorActivity`), using the Application's
`@style/AppTheme` unchanged — normal Android status and navigation bars
throughout, since it never calls `enterPrompterSystemUi()` or any other
system-UI-hiding code. Structure, top to bottom:

- A "Settings" heading (`settings_title`, 28sp bold, white).
- **Text Alignment** section (section 31): an amber section label, then
  a horizontal `RadioGroup` (`textAlignmentGroup`) with two
  `RadioButton`s, `radioAlignLeft` ("Left") and `radioAlignCenter`
  ("Centered").
- A thin decorative divider (`divider_color`, 20dp top/bottom margin).
- **Prompter Orientation** section (section 32): the same section-label
  style, then `RadioGroup` `prompterOrientationGroup` with
  `radioOrientationHorizontal` ("Horizontal") and
  `radioOrientationVertical` ("Vertical").
- Another divider.
- **Mirror Mode** section (section 35): the same section-label style,
  then a *vertical* `RadioGroup` (`mirrorModeGroup`, unlike the two
  horizontal ones above) with `radioMirrorModeFlip` ("Flip") and
  `radioMirrorModeFlipRotate` ("Flip and Rotate"), each immediately
  followed by its own small gray explanatory `TextView`
  (`settings_mirror_mode_flip_description` /
  `settings_mirror_mode_flip_rotate_description`, 13sp,
  `@color/text_light_gray`, indented 36dp to sit under the option's own
  text rather than its radio circle) — plain `TextView`s interleaved
  inside the `RadioGroup`, which only manages the check-state of its
  `RadioButton` children and otherwise behaves as an ordinary vertical
  `LinearLayout`.
- Another divider.
- **Load Last Script on Launch** (section 33): a single `CheckBox`
  (`loadLastScriptCheckBox`), not a `RadioGroup` — simplest correct
  control for a plain on/off setting, per the task's own "CheckBox,
  toggle, or another clear control" allowance. `android.widget.Switch`
  was not used at all (never even considered a fallback need), avoiding
  the task's flagged API 14 appearance/compatibility risk entirely.
- All of the above sits inside a `ScrollView` (the footer button is
  outside it, always visible) as a defensive measure for narrower
  screens/higher font-scale settings — the three sections comfortably
  fit without scrolling on every device tested, but nothing breaks if
  they ever don't.
- A footer row below a full-width divider: a single **Done** button
  (`btnSettingsDone`, `@style/ControlButton`), centered, `finish()`es
  the Activity on click.

**Save-immediately behavior**: each control's own listener
(`RadioGroup.OnCheckedChangeListener` ×3,
`CompoundButton.OnCheckedChangeListener` ×1) writes straight to
`SharedPreferences` via `commit()` (synchronous, matching
`MainActivity.savePreferences()`'s existing convention) the instant the
selection changes — there is no separate "Save" action and therefore no
unsaved-changes state to protect. Android Back uses the platform's
default `Activity.onBackPressed()` behavior (not overridden) to finish
the Activity, which is safe for exactly the same reason: nothing is
ever buffered in memory waiting to be saved.

**Hardware status**: implemented and confirmed only by source inspection
and a successful build — see section 26 for what has and has not been
run on real hardware; nothing about the Settings screen itself has been
physically tested as of this writing.

## 31. Text Alignment

Setting key `KEY_TEXT_ALIGNMENT`, values `TEXT_ALIGNMENT_LEFT` (default)
or `TEXT_ALIGNMENT_CENTER` (section 24, section 30). Applied by a single
centralized method, `MainActivity.applyTextAlignment()`:

```java
private void applyTextAlignment() {
    int gravity = AppConstants.TEXT_ALIGNMENT_CENTER.equals(textAlignment)
            ? Gravity.CENTER_HORIZONTAL : Gravity.LEFT;
    scriptText.setGravity(gravity);
}
```

`Gravity.LEFT`, not `Gravity.START` — both have existed since API 1, but
`LEFT` is what the task explicitly asked for and avoids any ambiguity
with locale-aware start/end semantics this app has no other use for.

**Scope**: `scriptText` is the one `TextView` shared by the
control-screen preview and every full-screen Prompter state (`WAITING_
FOR_TAP` through `END_OF_SCRIPT_WAITING`) — there is no duplicate script
view, so setting its gravity once covers the preview and all five
full-screen states in one call. `applyTextAlignment()` touches nothing
but `scriptText`'s gravity: no padding, no scroll position, no script
content, no scrolling/countdown state. It is therefore safe to call
unconditionally from both `onCreate()` (right after `loadPreferences()`,
alongside `applyFontSize()`/`applyMirror()`) and every `onResume()`
(alongside the reread described in section 30), regardless of `uiState`
at the time.

Tap to Start, the countdown, the pause-menu and end-of-script-menu
buttons, the main controls, the filename display, the file browser, and
the Script Editor are never touched by this method — they use entirely
separate views, so Text Alignment cannot reach them by construction, not
by an extra exclusion check.

**Interaction with Mirror**: `applyMirror()` sets `scaleX` on
`scriptText`/`countdownText`/`tapInstructionText`; `applyTextAlignment()`
sets `scriptText`'s `gravity`. These are orthogonal View properties —
mirroring flips whatever is rendered, including centered text, with no
special-casing needed for either to work correctly with the other.

**Why alignment changes never disturb scrolling**: `Gravity` only
affects how each line's content is positioned *within* its own width
inside a `TextView` whose own `layout_width` is `match_parent` — it does
not change `scriptText`'s measured height, so it cannot move `maxScroll`,
does not trigger a `requestLayout()` that would invalidate an in-flight
scroll position, and needs no scroll-clamping logic of its own (unlike
padding or font-size changes, which do change height and are handled by
their own dedicated methods elsewhere in this document).

**Hardware status**: requires physical testing — see section 26.

## 32. Prompter Orientation (Horizontal/Vertical)

Setting key `KEY_PROMPTER_ORIENTATION`, values
`PROMPTER_ORIENTATION_HORIZONTAL` (default) or
`PROMPTER_ORIENTATION_VERTICAL` (section 24, section 30). Applies **only**
to full-screen Prompter mode (`WAITING_FOR_TAP` through
`END_OF_SCRIPT_WAITING`) — `CONTROLS`, `FileBrowserActivity`,
`ScriptEditorActivity`, and `SettingsActivity` are always landscape,
never following the setting or the physical accelerometer (every
`screenOrientation` in play throughout this app is one of `landscape`/
`portrait`, explicitly requested — never `sensor`/`unspecified` — so
physically rotating the tablet never changes what's on screen on its
own).

### Why this can recreate `MainActivity`

`MainActivity`'s `android:configChanges` in `AndroidManifest.xml`
remains `"keyboardHidden"` only — deliberately not extended with
`orientation|screenSize`. That means calling
`Activity.setRequestedOrientation()` to a value that actually differs
from the current configuration destroys and recreates the Activity (the
standard, undeclared-config-change Android behavior), rather than
MainActivity handling the change itself via `onConfigurationChanged()`.
This is intentional, not an oversight: it lets the rest of the app's
existing `onCreate()`/`onSaveInstanceState()`/`savedInstanceState`
machinery (already relied on for process-death recovery) double as the
orientation-recreation recovery path, with one addition
(`pendingTransition`, below) layered on top, rather than building a
second, parallel state-preservation mechanism via
`onConfigurationChanged()`.

### The two orientation-transition points

Exactly two methods ever call `setRequestedOrientation()`, and both are
the sole funnels for their respective direction of travel (section 17,
section 23):

- **`enterFullScreenWaitingFromControls()`** (`CONTROLS` →
  `WAITING_FOR_TAP`, i.e. Start Prompter) requests
  `ActivityInfo.SCREEN_ORIENTATION_PORTRAIT` if
  `prompterOrientationVertical` is true, or `SCREEN_ORIENTATION_
  LANDSCAPE` otherwise.
- **`exitFullScreenToControls()`** (any full-screen state → `CONTROLS`)
  always requests `SCREEN_ORIENTATION_LANDSCAPE`, regardless of the
  setting — the main controls must always be landscape.

`restartFromPauseMenu()` (Restart) calls `resetToWaitingFromTop()`
directly, bypassing both of the above — it runs from `PAUSED_FULLSCREEN`,
already in whichever orientation the current Prompter session is using,
so no orientation change (and no recreation risk) is ever involved.
`resetToWaitingFromTop()` itself needed **no changes at all** for
Vertical mode: it already measures `scrollView.getHeight()` freshly on
every call (section 21, section 22), so it automatically picks up
whatever the current orientation's viewport actually is.

### `PendingTransition` and the recreation-survival mechanism

A new three-value enum field, `pendingTransition` (`NONE` /
`ENTER_PROMPTER` / `EXIT_TO_CONTROLS`), is `MainActivity`'s only new
piece of state for this feature, persisted via `onSaveInstanceState()`
(as an `int` ordinal, key `STATE_PENDING_TRANSITION`) and restored in
`onCreate()`. Both transition points follow the same pattern:

1. Check whether the target orientation already matches
   `getResources().getConfiguration().orientation`
   (`orientationMatches()`) — this call happens *before*
   `setRequestedOrientation()`, capturing the pre-change state.
2. Set `pendingTransition` to the matching value.
3. Call `setRequestedOrientation(target)`.
4. If the orientation already matched (no recreation will occur), call
   the matching continuation (`continueEnterPrompter()` /
   `continueExitToControls()`) immediately, synchronously, right there.
   Otherwise, do nothing further — `onResume()` is guaranteed to run
   again once the recreation (`onPause→onSaveInstanceState→onStop→
   onDestroy→onCreate→onStart→onResume`) completes, and checks
   `pendingTransition` as its first order of business.

`onResume()`:

```java
loadDisplayAndStartupPreferences();
applyTextAlignment();
if (pendingTransition == PendingTransition.ENTER_PROMPTER) {
    continueEnterPrompter();
} else if (pendingTransition == PendingTransition.EXIT_TO_CONTROLS) {
    continueExitToControls();
} else if (uiState == UiState.CONTROLS) {
    exitPrompterSystemUi();
} else {
    enterPrompterSystemUi();
}
```

Both continuation methods reset `pendingTransition` to `NONE` as their
first line, so a transition is consumed exactly once no matter which of
the two paths (synchronous or recreation-driven) triggers it — this is
the mechanism that satisfies "avoid orientation loops among `onCreate()`/
`onResume()`/`setRequestedOrientation()`."

`continueEnterPrompter()` also explicitly resets `pendingRestoreScrollY`
to `-1` before calling `resetToWaitingFromTop()`: any scroll position
saved from the CONTROLS preview (via the pre-existing
`STATE_SCROLL_Y`/`pendingRestoreScrollY` mechanism) is irrelevant here,
since Start Prompter always begins at the top regardless (section 17) —
discarding it explicitly avoids a pointless, momentary incorrect scroll
that `resetToWaitingFromTop()`'s own `scrollTo(0, 0)` would immediately
overwrite anyway.

`continueExitToControls()` branches on whether `pendingRestoreScrollY`
is still `-1`:

- Still `-1` (a same-instance exit — Horizontal mode, or any exit that
  did not need a recreation): behaves exactly as before this feature,
  calling `switchScriptPaddingPreservingPosition()` (section 21), whose
  in-memory scroll-delta math is valid because nothing was destroyed.
- `>= 0` (a recreation-driven exit — Vertical mode): that in-memory math
  has nothing valid to measure from (the fresh `scrollView` starts at
  `scrollY == 0`), so it applies minimal padding directly via
  `applyScriptPadding()` instead, and leaves the actual scroll
  restoration to the pre-existing, `savedInstanceState`-driven
  `pendingRestoreScrollY` block later in the same `onResume()` call,
  which scrolls to the exact saved position once the padding change (and
  the now-landscape layout) has settled.

### Avoiding a main-controls flash during Vertical entry

`onCreate()`, when `savedInstanceState != null` and the restored
`pendingTransition != NONE`, calls `controlsPanel.setVisibility(View.
GONE)` immediately — before `setContentView()`'s default-visible
`controlsPanel` could ever actually be drawn to the screen. This covers
both directions (entering Vertical mode should never flash the landscape
controls in portrait; returning to controls should not flash them before
system bars are restored) with one line, since a fresh layout inflation
already defaults every other full-screen-only view (`countdownText`,
`tapInstructionText`, `pauseMenuOverlay`) to `visibility="gone"` in the
XML itself.

One residual, acknowledged imperfection: for the few frames between a
recreation completing and its continuation actually running (in
`onResume()`), the system status/navigation bars briefly show their
normal (non-hidden) state, since a fresh `Window` does not inherit the
previous instance's `setSystemUiVisibility()` flags. This is an inherent
consequence of Activity recreation on API 14 without `configChanges`
suppression, not something practically avoidable within this
architecture — noted here rather than glossed over.

### Reachability of other Activities from Vertical mode

`FileBrowserActivity`, `ScriptEditorActivity`, and `SettingsActivity` are
only ever launched from buttons on `controlsPanel`, which is only ever
visible while `uiState == CONTROLS` — and `CONTROLS` is always landscape
regardless of the Prompter Orientation setting. There is therefore no
path by which any of those three Activities could ever be launched while
MainActivity is in portrait; their own `android:screenOrientation=
"landscape"` manifest declarations are never actually put to the test
against a portrait caller.

### Known limitation

The full-screen pause menu (`pauseMenuOverlay`'s three buttons, section
19) and the end-of-script menu use the same `wrap_content`/`gravity=
"center"` layout in both orientations — functionally correct in
portrait, but not given a bespoke narrower-width redesign; on a
sufficiently narrow portrait width the three `minWidth="160dp"` buttons
could in principle crowd each other. Every device this project has
physically tested (section 26) has ample portrait width for this to be
a non-issue, but it has not been exercised on real hardware as of this
writing (see below).

**Hardware status**: requires physical testing — see section 26. Neither
Horizontal (a no-op orientation-wise, but not yet retested with the
Settings screen present at all) nor Vertical (which exercises the entire
recreation mechanism above) has been run on a device yet.

## 33. Load Last Script on Launch

Setting key `KEY_LOAD_LAST_SCRIPT` (boolean, default `true` — preserves
the app's pre-existing startup behavior for any installation that never
opens Settings). Applied only in `onCreate()`'s fresh-launch branch
(`savedInstanceState == null`) — the exact branch that already existed
for "restore the last loaded script," now additionally gated on this
preference:

```java
if (savedInstanceState != null) {
    ... recreation-restore path, unchanged by this setting ...
} else if (loadedFilePath != null && loadLastScriptOnLaunch) {
    String pathToRestore = loadedFilePath;
    loadedFilePath = null;
    loadScriptFile(pathToRestore);
} else {
    loadedFilePath = null;
    updateLoadedFileLabel();
}
```

**On (default)**: unchanged from every prior round of this project —
`loadScriptFile()` (with its existing `TextFileReader` validation:
non-empty path, file exists, is a regular file, is readable, is within
the 2 MB size limit, and decodes as UTF-8 or the ISO-8859-1 fallback)
attempts to reload the remembered script. Success shows it normally
(text, filename, `Start Prompter`/`Edit Script`/`Preview` enabled,
scrolled to the top). Failure shows the existing `Toast` error and
leaves `loadedFilePath` at `null` (it was already cleared to `null`
before the load attempt, and `loadScriptFile()` only ever sets it back
on success) — i.e. the no-script state, exactly matching the "handle a
deleted/unavailable script safely" requirement, with no new code needed
for that part.

**Off**: the fresh-launch branch falls through to the new `else`,
setting `loadedFilePath = null` **in memory only** and calling
`updateLoadedFileLabel()`. Since `savePreferences()` only ever writes
`KEY_LAST_FILE_PATH` when `loadedFilePath != null` (section 24, an
existing, unchanged guard), clearing the in-memory field here never
touches the stored preference — the remembered path, and the file
itself, and the last-used browser directory (`KEY_LAST_DIRECTORY`), are
all left exactly as they were. `scriptText` keeps the XML default
placeholder text (`default_script_placeholder`, "Load a script to
begin"), and `updateLoadedScriptDependentControls()` (called
unconditionally right after this whole branch, as before) disables
`btnStart`/`btnEditScript`/`btnPreview` since `loadedFilePath` is now
`null` — `btnOpenFile`/`btnNewScript`/`btnSettings`/`mirrorToggle`/
`btnExit` are never touched by that method and remain enabled exactly as
they already were for the ordinary no-script state (section 5, section
7).

**Mid-session changes have no effect on the active session**: toggling
this setting in `SettingsActivity` only ever writes the preference —
nothing in `MainActivity` reacts to it except the fresh-launch branch
above, so switching it Off does not unload whatever script happens to be
active right now, and switching it back On does not reload or replace
it either. This falls out of the architecture rather than needing an
explicit guard: nothing but `onCreate()`'s one `savedInstanceState ==
null` branch ever reads `loadLastScriptOnLaunch`.

**Recreation is not a fresh launch**: the branch above is gated on
`savedInstanceState == null`, the same signal this project has used
since before this feature existed to distinguish a genuine fresh launch
from any Activity recreation (a Vertical-orientation-triggered one,
section 32, or any other Android-initiated one, e.g. process death and
restore) — an orientation-driven recreation while a script is actively
loaded therefore never re-evaluates or re-applies this setting, and
never re-attempts (or skips) loading anything.

**Hardware status**: requires physical testing — see section 26.

## 34. Elapsed-time scrolling model and fractional-pixel accumulation

Both `performScrollTick()` (full-screen) and `performPreviewScrollTick()`
(Preview) were rewritten to compute movement from actual elapsed time
rather than assuming a fixed distance per tick, to fix visible
choppiness — particularly at slow Speed settings — from the previous
tick-based model.

**Previous model**: `SCROLL_INTERVAL_MS = 50` (50ms), and each tick
moved a fixed `computeScrollPixelsPerTick()` amount —
`Math.max(1, Math.round(scrollSpeed * 0.5f * density))`. This assumed
every `Handler.postDelayed()` callback fired at exactly 50ms, which a
`Handler` never guarantees; it also **discarded** any fractional pixel
distance every single tick (`Math.round()`, with no carry-forward), and
**floored** the per-tick distance at 1px (`Math.max(1, ...)`) whenever
the true nominal amount was less than half a pixel — both distortions
were most visible at slow Speed settings and on lower-density screens,
where they produced periodic stalls (rounding away a partial pixel
entirely) or an artificially inflated effective rate (the 1px floor
moving more than the nominal rate called for).

**Current model**: `SCROLL_INTERVAL_MS = 16` (~60 updates/second,
matching the task's explicit target) is only a *target* poll interval —
the actual gap between ticks is never assumed to equal it. Each tick:

```java
long now = SystemClock.uptimeMillis();
long elapsedMs = now - <context>LastTickUptimeMs;
<context>LastTickUptimeMs = now;
...
<context>RemainderPx += computeScrollPixelsPerSecond() * elapsedMs / 1000f;
int wholePixels = (int) <context>RemainderPx;
if (wholePixels > 0) {
    <context>RemainderPx -= wholePixels;
    // ... apply wholePixels to scrollView, clamped to maxScroll ...
}
scrollHandler.postDelayed(<runnable>, SCROLL_INTERVAL_MS);
```

`SystemClock.uptimeMillis()` — a monotonic clock that excludes deep
sleep and is immune to wall-clock/timezone changes, available since API
1 — measures the *real* elapsed time since the previous tick, whatever
it actually was. Intended movement (`pixels per second × elapsed
seconds`) accumulates into a float remainder; only the whole-pixel part
is ever applied to the `ScrollView` (`scrollTo()`, still a single
direct, predictable call — never `smoothScrollTo()`/`smoothScrollBy()`,
which would stack overlapping animations across ticks), and the leftover
fraction — always in `[0, 1)` after each tick — carries into the next
tick rather than being discarded. This means slow-speed sub-pixel
motion accumulates smoothly across several ticks until it crosses a
whole pixel, instead of either stalling (old rounding) or being forced
up to a full pixel every tick regardless of the true rate (old floor).
No 1px-per-tick floor exists in the new model at all — it is
unnecessary, since a slow rate simply accumulates over more ticks before
its next whole-pixel move, rather than needing an artificial minimum.

**`computeScrollPixelsPerSecond()`** (section 15): `scrollSpeed *
SCROLL_DP_PER_SECOND_PER_SPEED_UNIT * density`, where
`SCROLL_DP_PER_SECOND_PER_SPEED_UNIT = 10f`. This is an exact algebraic
restatement of the old nominal rate, not a redefinition: the old formula
moved `scrollSpeed * 0.5` dp per assumed-50ms tick, i.e. `scrollSpeed *
0.5 * (1000 / 50) = scrollSpeed * 10` dp/second — the same dp/second
figure this constant now names directly. Every existing Speed setting
(1–20) therefore keeps its same approximate real-world rate; what
changed is *how* that rate is realized frame-to-frame (continuous,
elapsed-time-accurate, fractional-pixel-preserving), not the rate
itself. Both Preview and full-screen scrolling call this one shared
method every tick, off the one shared `scrollSpeed` field, so dragging
`speedSeekBar` during an active Preview run takes effect on the very
next tick — no restart, exactly as before.

**Separate accumulators and baselines**, since Preview and full-screen
scrolling can each be active at different times with completely
independent histories:

| Context | Accumulator | Baseline |
| --- | --- | --- |
| Full-screen Prompter | `fullScreenScrollRemainderPx` | `fullScreenScrollLastTickUptimeMs` |
| Control-screen Preview | `previewScrollRemainderPx` | `previewScrollLastTickUptimeMs` |

**Baseline/accumulator reset conditions** — both are reset together,
always in pairs, only when a *new* scrolling run actually begins:

- Full-screen: inside `beginActiveScrolling()`, the sole entry point
  into `SCROLLING` reached after every countdown regardless of
  `CountdownOrigin` — covering both a brand-new Start Prompter/Restart
  run and a Resume run identically. This is also what keeps countdown
  and pause time from ever being counted as scrolling time (section 19)
  — the baseline is stamped fresh only once scrolling actually resumes,
  never inherited from before the countdown began.
- Preview: inside the deferred `scrollView.post()` Runnable at the end
  of `startPreviewScrolling()` (section 14), immediately before the
  first tick is posted — paired with the `scrollTo(0, 0)` reset that
  refinement adds, so a fresh Preview run's very first tick measures
  elapsed time from the moment scrolling actually starts, not from
  whenever the previous run happened to stop.
- Neither is reset merely because Speed changed — `speedSeekBar`'s
  `onProgressChanged` only ever writes `scrollSpeed` and saves
  preferences; it never touches either accumulator or baseline, so an
  in-progress run's accumulated fractional state survives a Speed change
  untouched, exactly as the accumulated whole-pixel position always has.

**Lifecycle safety**: every existing
`scrollHandler.removeCallbacksAndMessages(null)` /
`scrollHandler.removeCallbacks(previewScrollRunnable)` cleanup call
(manual pause, Stop, Restart, Back to Controls, Settings, Load/New/Edit
Script, Exit, `onPause()`/`onStop()`/`onDestroy()`) is unchanged by this
work — it still guarantees at most one full-screen tick and at most one
preview tick are ever pending, and that the two can never overlap
(section 19's "Preview/full-screen exclusivity" bullet, unchanged).
Because a stale baseline is only ever consulted by a tick, and every
lifecycle interruption above cancels any pending tick, a stale baseline
can never produce a large jump on its own — the next *actual* scrolling
run always re-stamps its baseline fresh at one of the two reset points
above before its first tick runs. Returning from the background is
covered the same way it always was (section 19): `onPause()`
unconditionally routes through `exitFullScreenToControls()` unless a
deliberate `pendingTransition` is already in flight (section 32), so the
app can never resume mid-scroll with a stale baseline — resuming full
prompter scrolling afterward always goes back through Start Prompter,
which resets fresh via `beginActiveScrolling()`.

**End-of-script precision preserved**: both tick methods still clamp
the proposed new position to `maxScroll` and call
`stopScrollingAtEnd()`/`stopPreviewScrolling()` **exactly once** — the
`if (wholePixels > 0) { ...; if (newY >= maxScroll) { snap and stop; } }`
structure is unchanged in shape from the old per-tick version, just
computing `wholePixels` differently. Once either method transitions out
of its active state (`SCROLLING`→`END_OF_SCRIPT_WAITING`, or
`previewScrolling = false`), its own top-of-method guard prevents any
further tick from running, so overshoot or a duplicate end-detection
call is not possible. `OPENING_SPACE_FRACTION`, `END_SPACE_FRACTION`,
and the final resting position (sections 21–22) are entirely untouched
— this refinement changes only how the scroll position advances tick to
tick, never where the maximum scroll position itself is.

**Deliberately not done**: elapsed time per tick is never capped or
smoothed. A rare long stall (e.g. a GC pause) produces one larger
catch-up jump on the next tick rather than the scroll permanently
falling behind real time — consistent with "do not assume every
callback occurs exactly on schedule" and "preserve the same approximate
real-world rate." No physics-based acceleration/deceleration, overscroll,
or bounce was added; motion remains strictly linear and constant-speed
at whatever rate `computeScrollPixelsPerSecond()` currently returns.

**Old-hardware performance**: no new object allocation occurs per tick
— `scrollRunnable`/`previewScrollRunnable` are the same reused `Runnable`
instances as before, `SystemClock.uptimeMillis()` returns a primitive
`long`, and the accumulator math uses only primitive `float`/`int`
arithmetic. No `requestLayout()` is called from either tick method
(`scrollTo()` alone does not trigger one). No new thread is introduced —
both tick methods still run exclusively on the main UI thread via the
existing `scrollHandler`.

**Hardware status**: requires physical testing — see section 26. The
perceived smoothness improvement, in particular, cannot be confirmed by
source inspection or a successful build alone.

## 35. Mirror Mode

Setting key `KEY_MIRROR_MODE`, values `MIRROR_MODE_FLIP` (default) or
`MIRROR_MODE_FLIP_ROTATE` (section 24, section 30). Determines *which*
transformation the pre-existing Mirror On/Off toggle applies while it is
On — it is a completely separate preference from `KEY_MIRROR` (section
16): toggling Mirror never touches Mirror Mode, and changing Mirror Mode
never touches Mirror's own On/Off state or its saved preference.

### The main-screen Mirror button is unchanged

`mirrorToggle` keeps its existing ID, click listener, On/Off behavior,
and displayed labels ("Mirror: On"/"Mirror: Off") — nothing about it
changed. It does not display the selected Mirror Mode, does not cycle
through Mirror Mode choices, and there is no second Mirror button and no
separate Rotate button anywhere on the main screen. Mirror Mode
configuration lives exclusively in `SettingsActivity` (section 30).

### Centralized transform: `applyMirror()`

```java
private void applyMirror() {
    float scaleX = mirrorEnabled ? -1f : 1f;
    float scaleY = 1f;
    float rotation = (mirrorEnabled && AppConstants.MIRROR_MODE_FLIP_ROTATE.equals(mirrorMode))
            ? 180f : 0f;
    applyMirrorTransform(scriptText, scaleX, scaleY, rotation);
    applyMirrorTransform(countdownText, scaleX, scaleY, rotation);
    applyMirrorTransform(tapInstructionText, scaleX, scaleY, rotation);
}

private static void applyMirrorTransform(View view, float scaleX, float scaleY, float rotation) {
    view.setScaleX(scaleX);
    view.setScaleY(scaleY);
    view.setRotation(rotation);
}
```

This is the single place `mirrorEnabled` and `mirrorMode` are ever
turned into concrete `scaleX`/`scaleY`/`rotation` values, and the single
place those values are ever applied — every call recomputes and
reassigns all three properties on all three presenter-facing views from
scratch (never a relative `rotationBy()`-style increment), so no call
site anywhere in `MainActivity` computes its own transform. The three
per-state outcomes:

| State | `scaleX` | `scaleY` | `rotation` |
| --- | --- | --- | --- |
| Mirror Off (any Mirror Mode) | `1.0f` | `1.0f` | `0.0f` |
| Mirror On + Flip | `-1.0f` | `1.0f` | `0.0f` |
| Mirror On + Flip and Rotate | `-1.0f` | `1.0f` | `180.0f` |

Flip and Rotate is implemented exactly as the horizontal flip (`Flip`)
composed with an *additional*, absolute 180-degree rotation — not as a
differently named "vertical flip" (`scaleY = -1f`), even though a
horizontal flip plus a 180-degree rotation is mathematically equivalent
to a vertical-only flip for a symmetric transform. The setting is
implemented and documented as "Flip and Rotate" throughout, matching
the actual physical teleprompter mounting it represents (operator
controls upright, presenter content correctly oriented once reflected)
rather than an abstract, less meaningful "vertical flip" label.

### Presenter-facing vs. operator-facing

Presenter-facing (transformed together, always identically, by the one
`applyMirror()` call): `scriptText`, `countdownText`,
`tapInstructionText`. Confirmed by inspection that these are the only
three views `applyMirror()` (or any method in `MainActivity`) ever
calls `setScaleX()`/`setScaleY()`/`setRotation()` on.

Operator-facing (never transformed, confirmed never referenced by
`applyMirror()` or `applyMirrorTransform()`): `controlsPanel` and every
control on it (Load Script, New Script, the filename display, Edit
Script, Start Prompter, Mirror, Exit, Font Size controls, the Speed
`SeekBar`, Preview/Stop, the Settings gear), `pauseMenuOverlay` and its
three buttons (Resume, Restart, Back to Controls), the end-of-script
menu (the same `pauseMenuOverlay`, reused — section 20), all of
`SettingsActivity`, all of `FileBrowserActivity`, and all of
`ScriptEditorActivity`.

### No explicit rotation pivot

Neither `setPivotX()` nor `setPivotY()` is ever called anywhere in this
class. `View`'s own default pivot is already the center of its
(post-measurement) bounds — true since `View.setRotation()`/
`setScaleX()`/`setScaleY()` were introduced in API 11, so this default
has always applied at this project's `minSdkVersion 14` floor — and it
recomputes automatically whenever the view's measured size changes
(e.g. a Font Size change), since nothing here ever freezes it. This is
exactly the centered pivot a 180-degree rotation needs, so no explicit
post-layout pivot-setting code was necessary.

### Why this can never clip or affect scrolling

A 180-degree rotation, and a horizontal flip, are each individually
bounds-preserving: rotating a rectangle 180 degrees about its own
center produces a rectangle of the identical width and height in the
identical position, and mirroring about a vertical center axis does
the same — so composing the two never enlarges a transformed view's
apparent footprint beyond its own untransformed bounds, and content
can never clip *because of* this transform (only content that would
already have been clipped without it could still be).

More fundamentally, `setScaleX()`/`setScaleY()`/`setRotation()` are
draw-time Canvas matrix transforms applied by the `View` framework —
they never affect that `View`'s measured/laid-out width, height, or
position, which is what `ScrollView.scrollTo()`, `getScrollY()`, and
every `maxScroll` calculation in this codebase (sections 14, 19, 34)
actually operate on. Mirror Mode therefore required **zero** changes
anywhere in the scrolling/state-machine code: `performScrollTick()`,
`performPreviewScrollTick()`, `beginActiveScrolling()`,
`resetToWaitingFromTop()`, `stopScrollingAtEnd()`, and every
`maxScroll`/`scrollY` calculation are byte-for-byte unchanged by this
feature. The rendered *pixels* may appear flipped and/or rotated on the
physical screen, but `scrollY` always still counts up from `0` toward
the same `maxScroll` it always did, in the same direction, against the
same untransformed layout coordinates.

### Interaction with Text Alignment

`Gravity` (section 31) positions each line of text *within*
`scriptText`'s own untransformed layout bounds, before any Canvas
transform is applied; `applyMirror()`'s `scaleX`/`scaleY`/`rotation`
then transform that already-gravity-resolved content as a single unit.
The two operate in entirely different, non-interacting stages of
rendering — changing Mirror Mode never reads or writes
`KEY_TEXT_ALIGNMENT`, and changing Text Alignment never reads or writes
`KEY_MIRROR_MODE` — so every combination (Left/Centered × Off/Flip/Flip
and Rotate) is automatically correct with no combination-specific code
anywhere.

### Interaction with Prompter Orientation

Entirely independent (section 32): Mirror Mode never calls
`setRequestedOrientation()`, never sets `pendingTransition`, and is
never consulted by `enterFullScreenWaitingFromControls()`/
`exitFullScreenToControls()`. The Horizontal/Vertical choices themselves
are untouched — same two options, same preference key, same defaults.
Because `applyMirror()`'s transform is relative to each view's own
(post-layout) bounds and center pivot regardless of the Activity's
actual current orientation, both mirror modes work identically whether
`scrollView`'s measured dimensions currently reflect landscape or
portrait — no orientation-conditional branch exists or is needed in
`applyMirror()`.

### Preview

`scriptText` is the same view in the control-screen preview and every
full-screen Prompter state (section 14, section 21) — `applyMirror()`
doesn't distinguish between them, so the preview reflects the active
Mirror/Mirror Mode combination immediately, the same as it already did
for the pre-existing Flip-only behavior. Preview's own elapsed-time
scrolling loop (section 34) reads/writes `scrollView`'s untransformed
scroll position exactly as before — unaffected by whatever rotation or
flip is currently applied to what's drawn on screen, per the "why this
can never clip or affect scrolling" reasoning above.

### Settings-return refresh

`onResume()` now calls `loadDisplayAndStartupPreferences()` (which
rereads `mirrorMode` among the other three Settings-owned preferences)
followed immediately by `applyTextAlignment()` and `applyMirror()` — so
returning from `SettingsActivity` with a newly selected Mirror Mode
reapplies it right away if Mirror is currently On, and is a harmless
no-op (still computes the identity transform) if Mirror is currently
Off. This is the same reread point already used for Text Alignment,
Prompter Orientation, and Load Last Script on Launch (sections 31–33) —
no dedicated `onActivityResult()` handling was added for Settings.
`applyMirror()` touches only `scaleX`/`scaleY`/`rotation` on the three
presenter-facing views, so this reread can never touch the loaded
script, scroll position, Preview's running/stopped state, or `uiState`
— confirmed by inspection that `applyMirror()`/`applyMirrorTransform()`
call nothing else.

### Persistence and upgrade defaults

`KEY_MIRROR_MODE` defaults to `MIRROR_MODE_FLIP` wherever it is read
(`loadDisplayAndStartupPreferences()` in `MainActivity`,
`SettingsActivity.onCreate()`'s initial `RadioButton` selection) — a
missing/absent preference value always safely resolves to Flip, which
is the exact pre-existing (and only) Mirror behavior this project had
before this feature existed. An existing installation upgrading to this
version therefore sees byte-for-byte identical Mirror behavior with no
migration, no database, and no reset of `KEY_MIRROR` or any other
existing preference — `KEY_MIRROR_MODE` is purely additive.

**Hardware status**: requires physical testing — see section 26. Verify
in particular the six alignment/mode combinations and the six
orientation/mode combinations this feature spans, on real hardware —
none of the visual outcomes here can be confirmed by source inspection
or a successful build alone.

---

## Discrepancies found while documenting

Historical note: an earlier pass of this document found `README.md` out
of date in two ways — it claimed `READ_EXTERNAL_STORAGE` was the only
permission (missing `WRITE_EXTERNAL_STORAGE`), and its Features list
still described a "Start / Pause / Return to Beginning" control scheme
predating "Enter Prompter" (itself since renamed to "Start Prompter" in
this round). Both were fixed in a subsequent `README.md`-focused task and
again kept current with this round's pause-menu changes.

This round also found section 3's "Known modern-Android installation
limitation" note stale — it still claimed the Android 14+ install block
and device-warning behavior were "not characterized," when `README.md`'s
Android Compatibility section had already documented both in an earlier
round, and this round adds physical confirmation from two devices. Fixed
in place (section 3) rather than left to accumulate further.

No other outstanding discrepancies between the current source and this
documentation are known as of this round; every behavior described above
was confirmed directly against `app/src/main/java/...`,
`app/src/main/res/...`, `AndroidManifest.xml`, `app/build.gradle`, and
`build.sh` as checked out at commit `d37883d`, and, where noted, by
inspecting the compiled APK/DEX rather than the source alone.
