# Droid Prompter — Project Specification

This document describes the application as it exists in the current
source. See `CLAUDE.md` for the persistent rules derived from it.

**Documented as of commit `13abf34`** (branch
`claude/android-teleprompter-app-1wfqsr` at that time; the repository
owner renamed this branch to `droid-prompter` on GitHub afterward — see
section 2), updated for a bug fix to the
Mirror On + Flip and Rotate setting introduced at that commit (section
35): Flip and Rotate's 180-degree rotation was found to make Preview and
full-screen Prompter scrolling start at the end of the script and run
backward toward the beginning, since the rotation is a pure draw-time
transform that leaves `ScrollView`'s untransformed `scrollY` order
unaffected while visually inverting what that order renders. This round
adds an explicit **reverse logical scrolling** model, active only for
Mirror On + Flip and Rotate, so the presenter always experiences the
script running from its first line to its last regardless of Mirror
Mode — see section 35 for the full derivation and implementation. The
`13abf34` round's claim that "Mirror Mode required zero changes anywhere
in the scrolling/state-machine code" is corrected by this round; it was
true only in the narrow sense that `scrollY`'s own numeric mechanics
were untouched, not that no *direction*-aware logic was needed on top of
it. The commit that lands this update is necessarily newer than
`13abf34`; that commit's hash is reported separately at the end of the
task that made these changes, not inferred here.

**A further round** (commit `a0a3088` and newer) found a second,
separate bug via physical testing of the reverse-scrolling round above:
Mirror On + Flip and Rotate's opening buffer was missing — the first
script line rendered near the top of the viewport instead of at the same
~77.5%-down position Mirror Off/Flip use. Fixing it took two sub-rounds:
commit `08e3bbc` first diagnosed it as a measurement-timing gap and added
`scrollToScriptStartReliably()`, but physical testing of that fix found
the opening buffer still wrong; a following round found the actual cause
was the reverse-mode padding *mapping* itself — logical bottom padding,
not top, governs the visual opening space under Flip and Rotate's
180-degree rotation, so the two sides must be exchanged rather than
complemented. `scrollToScriptStartReliably()` was kept (it solves an
independent measurement-reliability problem) alongside the corrected
mapping. See "Reverse-mode full-screen padding" and "Physically
observed: missing reverse-mode opening buffer, and its fix" under
section 35 for the full history.

**A third sub-round** of the opening-buffer fix (commit `735acb0`) found
that the then-current mapping's reused `OPENING_SPACE_FRACTION` value
(`0.775`) was itself insufficient once applied to reverse mode's bottom
padding: the first line cleared the "near the top" symptom but still sat
behind the centered Tap to Start overlay. `REVERSE_OPENING_SPACE_FRACTION
= 0.95f`, calibrated separately against physical testing, replaced
`OPENING_SPACE_FRACTION` in that one branch.

**A fourth sub-round** (commit newer than `735acb0`) found, via physical
testing, that raising that bottom-padding fraction to `0.95` had **no
visible effect on the opening position at all** — disproving the
"logical bottom padding governs the reverse-mode opening" premise the
second and third sub-rounds shared, not merely its calibrated value.
Structurally: the reverse start is `scrollY == maxScroll`, and any
change to bottom padding changes `maxScroll` by the same amount, so the
two cancel exactly at that position. It is **top** padding, not bottom,
that actually governs the reverse-mode opening; bottom padding
correctly governs the reverse-mode *end* instead, where no such
cancellation occurs. The corrected model uses a new, independently
calibrated `REVERSE_START_TOP_FRACTION = 0.15f` for reverse-mode top
padding, and restores `(1 − END_SPACE_FRACTION)` = `0.6` for reverse-mode
bottom padding; `REVERSE_OPENING_SPACE_FRACTION` was removed as
unused. `OPENING_SPACE_FRACTION` itself remains unchanged throughout,
governing only Mirror Off/Flip's own top padding.

**A fifth sub-round** found, via physical testing of `0.15f`, that the
*direction* of the top-padding relationship was itself backwards: the
first line moved higher still — clear above the Tap to Start overlay,
with the buffer now sitting at the bottom of the screen instead of the
top — the opposite of the intended result (a buffer at the top, first
line lower on screen, matching normal/Flip mode). The actual, physically
confirmed relationship is that **increasing** reverse-mode top padding
moves the buffer to the top and the first line lower; decreasing it does
the opposite. `REVERSE_START_TOP_FRACTION` was corrected from `0.15f` to
`0.60f` accordingly — above round three's `0.4f` (itself already too
high), consistent with the stated preference for landing slightly too
low rather than too high. This also resolves what had been an open,
unexplained discrepancy: round one's `0.225f` (a *small* value) failing
with the buffer too far toward the bottom (line too high) is exactly
what the now-corrected direction predicts, not a separate mystery. See
"Reverse-mode full-screen padding" and "Physically observed: missing
reverse-mode opening buffer, and its fix" (round five) under section 35
for the full derivation.

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
- Renamed from "A500 Teleprompter" to **Droid Prompter** (`app_name` in
  `strings.xml`) — the project explicitly anticipates running on tablets
  other than the Acer A500, and the name was always documented as a
  working title subject to change. The Java package
  (`com.a500teleprompter.app`) is a separate identifier, not changed by
  this rename. The GitHub repository and the development branch *were*
  later renamed to match (`peteyagmin/Droid-Prompter`, `droid-prompter`
  — done by the repository owner directly on GitHub, not as part of the
  `app_name` rename commit) — see section 2.

## 2. Current repository and release status

- Branch: `droid-prompter` — renamed by the repository owner on GitHub
  from this branch's original name, `claude/android-teleprompter-app-
  1wfqsr`; the old name no longer exists on the remote, and every local
  clone's branch and upstream tracking must be renamed to match (`git
  branch -m <old> droid-prompter` then re-pointing the upstream), not
  left pointing at a deleted remote ref.
- HEAD at documentation time: `66d459f` ("Fix Save button text size to
  match Cancel in script editor") — stale; many commits have landed
  since without this line being updated every round. Treat `git log`
  as authoritative over this line specifically.
- Application label (`android:label` on `<application>` and
  `MainActivity`): `@string/app_name` = **Droid Prompter**.
- Package / `applicationId`: `com.a500teleprompter.app` — unchanged by
  the app-name rename above; renaming the Java package is a separate,
  much more invasive change (affects APK signing/upgrade identity,
  `SharedPreferences` file naming, and every source file's package
  declaration) that was not requested and was not made.
- Latest known APK output path: `app/build/outputs/apk/debug/app-debug.apk`
  — produced by `build.sh`, **not** committed to the repository
  (`/app/build/` is in `.gitignore`). It must be rebuilt from source.
- `versionCode 1`, `versionName "1.0"` (`app/build.gradle`), unchanged
  since project inception. A `v1.0.0` tag now exists in the repository,
  pointing at commit `4d9495c` (the "Replace Settings screen's branding
  panel with an About popup dialog" commit) — created outside this
  session, not by any task reported here. This does not necessarily
  mean `versionCode`/`versionName` were bumped to match (they were not,
  as of this writing); don't assume the Gradle version fields track git
  tags automatically, or that a tag's presence means a corresponding
  Gradle version change was made. No changelog exists. See section 29
  for the intended discipline, now partially practiced (a tag exists)
  rather than not at all.

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
  background variant). Both are 1254×1254 PNGs (the `-trans` variant is
  RGBA with an alpha channel; `-bg` is opaque RGB). Neither original
  file is referenced by any Android resource path directly — they
  remain editable source material, not packaged into the APK as-is.
- The packaged **launcher icon** resources are, unchanged by this round,
  under `app/src/main/res/mipmap-{hdpi,mdpi,xhdpi,xxhdpi,xxxhdpi}/
  ic_launcher.png` (legacy, pre-API-26 icon) and
  `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` (an adaptive icon
  for API 26+, referencing `drawable-nodpi/ic_launcher_foreground.png`
  as its foreground and `@color/background_black` as a solid-color
  background — not artwork-derived).
- `app/src/main/res/drawable-nodpi/droid_prompter_logo.png` is a direct,
  unmodified copy of `artwork/DroidPrompt-trans.png`, renamed to a valid
  Android resource identifier (resource names cannot contain uppercase
  letters or hyphens). It is referenced by the Settings screen's
  **About dialog** (`dialog_about.xml`, section 30) as `@drawable/
  droid_prompter_logo` — the **first** Android resource path to
  reference either artwork file. (An earlier round placed this image
  directly on the Settings screen itself, in a split-screen branding
  panel; that layout was reverted in favor of the About dialog — see
  section 30 for the current design and that history.) `drawable-nodpi`
  (not a density bucket) means Android never rescales it
  per-screen-density; the `ImageView` itself scales it to fit its
  layout area via `adjustViewBounds`/`scaleType="fitCenter"`.
- These remain three different things: editable source artwork
  (`artwork/`), packaged app-icon assets (`app/src/main/res/mipmap-*`,
  `drawable-nodpi/ic_launcher_foreground.png`), and the packaged About-
  dialog image (`drawable-nodpi/droid_prompter_logo.png`). Do not move
  the packaged mipmap/drawable-nodpi resources into `artwork/`, and do
  not assume the reverse either — `artwork/`'s own files are untouched
  by any of this; only a renamed *copy* was added under `res/`.
- The official app name is established by `strings.xml`'s `app_name` =
  **"Droid Prompter"** (renamed from "A500 Teleprompter"; see section
  1), used as `android:label` throughout the manifest and now also
  echoed as the About dialog's heading text (section 30). Note the
  exact spelling difference from the artwork filenames: the app name is
  "Droid Prompter" (two words, with "-er"); `DroidPrompt-bg.png`/
  `DroidPrompt-trans.png` spell it "DroidPrompt" (one word, no "-er") —
  these are related but not identical strings, and nothing in source
  conflates them.

## 28. Known limitations and future ideas

Not implemented — listed only to say so explicitly, per instruction not
to present these as current functionality:

- Replacing the Speed `SeekBar` with minus/plus buttons (Font Size
  already uses buttons; Speed does not, and no such Speed buttons exist
  in source).
- A separate modern-Android-targeted build (only one `build.gradle`
  target exists, `minSdkVersion`/`targetSdkVersion` 14).
- Hardware verification of the compact-editing-mode UI refinements
  described in section 11/25 — implemented and compiled, not yet
  confirmed on a physical device since being added.

## 29. Change and release discipline

Intended practice going forward. A `v1.0.0` tag now exists (section 2),
created outside this session's own tasks, so this is no longer entirely
unpracticed — but it is the only tag, there is no changelog, and no
other part of the discipline below has been demonstrated yet:

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
system-UI-hiding code.

A left/right split was tried in an earlier round of this work and
**reverted** — the person found it visually unsatisfying — in favor of
the design below: a single-column settings list, unchanged in width
from before either round, plus a separate **About** dialog reachable
from a new footer button. Structure, top to bottom:

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
  then — as of this round — a *horizontal* `RadioGroup`
  (`mirrorModeGroup`, matching Text Alignment/Prompter Orientation's
  layout exactly, including the same `paddingRight`/`paddingEnd`
  "24dp" on the first `RadioButton`) with `radioMirrorModeFlip`
  ("Flip") and `radioMirrorModeFlipRotate` ("Flip and Rotate"). The
  explanatory `TextView`s that used to sit under each option
  (`settings_mirror_mode_flip_description` = "Standard teleprompter-
  glass display", `settings_mirror_mode_flip_rotate_description` =
  "Use when the tablet is mounted with the controls upright") were
  removed from the layout, and both strings were deleted from
  `strings.xml` entirely as now-unused resources — this section no
  longer differs in layout shape from the two sections above it.
- Another divider.
- **Load Last Script on Launch** (section 33): a single `CheckBox`
  (`loadLastScriptCheckBox`), not a `RadioGroup` — simplest correct
  control for a plain on/off setting, per the task's own "CheckBox,
  toggle, or another clear control" allowance. `android.widget.Switch`
  was not used at all (never even considered a fallback need), avoiding
  the task's flagged API 14 appearance/compatibility risk entirely.
- All of the above sits inside a `ScrollView` (the footer row is
  outside it, always visible) as a defensive measure for narrower
  screens/higher font-scale settings — the four sections comfortably
  fit without scrolling on every device tested, but nothing breaks if
  they ever don't.
- A footer row below a full-width divider, now with **two** buttons
  side by side (both `@style/ControlButton`, both `minWidth="200dp"`):
  **About** (`btnAbout`, 16dp trailing margin) then **Done**
  (`btnSettingsDone`, unchanged — still `finish()`es the Activity).

**About dialog** (`btnAbout`'s `OnClickListener`, `SettingsActivity.
showAboutDialog()`): a plain `AlertDialog` built with
`setView(inflate(R.layout.dialog_about))`, `setCancelable(true)`, and a
single `setPositiveButton(R.string.btn_close, null)` — dismissible via
Close, Back, or tapping outside, since there is nothing to save or lose
here (unlike every other dialog in this app, which all guard a real
decision). `dialog_about.xml` is a self-contained vertical
`LinearLayout` (`gravity="center"`, `background="@color/
background_black"` so its content reads correctly regardless of the
platform `AlertDialog` theme's own chrome) containing:

- An `ImageView` (`android:src="@drawable/droid_prompter_logo"`,
  `adjustViewBounds="true"`, `scaleType="fitCenter"`,
  `layout_width="200dp"`) — a fixed width (unlike the reverted split's
  weighted-column sizing) appropriate for a popup rather than a full
  screen region; the source is a square 1254×1254 PNG, so height scales
  proportionally with no stretching or cropping. See section 27 for the
  underlying resource (`drawable-nodpi/droid_prompter_logo.png`, a
  renamed copy of `artwork/DroidPrompt-trans.png`).
- A `TextView` showing `@string/app_name` ("Droid Prompter", 20sp,
  bold, white) directly below the image — reusing the same string
  resource the app's label and title bar use, rather than a duplicated
  literal, so a future rename only has to change one place.
- A `TextView` showing `settings_branding_copyright` ("Copyright ©
  2026 Pete Yagmin", 14sp, `@color/text_light_gray`).
- A `TextView` showing `settings_branding_license` ("Licensed under the
  GNU General Public License v3.0 (GPL-3.0)", 13sp,
  `@color/text_light_gray`, `layout_width="match_parent"` with
  `gravity="center"` so it wraps and stays centered rather than
  overflowing the dialog's width). The repository's root `LICENSE`
  file contains the full GPL-3.0 text this notice refers to.

Nothing in the dialog is interactive beyond dismissing it — no click
listeners on the image or text, no preferences read or written by
`showAboutDialog()`.

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
run on real hardware; nothing about the Settings screen itself, the
Mirror Mode layout change, the About button, or the About dialog has
been physically tested as of this writing. In particular, verify on
real hardware that the dialog's logo renders at a reasonable size (not
distorted, not so small it's illegible) within a 200dp-wide `ImageView`,
that the license text wraps without clipping at the dialog's actual
on-device width, and that the dialog is legible against whatever the
platform `AlertDialog` theme's own chrome looks like on that device
(the explicit black background on `dialog_about.xml`'s root should make
this moot, but has not been confirmed visually).

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

### Why this can never clip, and why it *does* affect logical scroll direction

A 180-degree rotation, and a horizontal flip, are each individually
bounds-preserving: rotating a rectangle 180 degrees about its own
center produces a rectangle of the identical width and height in the
identical position, and mirroring about a vertical center axis does
the same — so composing the two never enlarges a transformed view's
apparent footprint beyond its own untransformed bounds, and content
can never clip *because of* this transform (only content that would
already have been clipped without it could still be).

`setScaleX()`/`setScaleY()`/`setRotation()` are draw-time Canvas matrix
transforms applied by the `View` framework — they never affect that
`View`'s measured/laid-out width, height, or position, so
`ScrollView.scrollTo()`/`getScrollY()` and every `maxScroll` calculation
in this codebase (sections 14, 19, 34) keep operating on exactly the
same untransformed coordinates regardless of what Mirror Mode is
active. In that narrow sense `performScrollTick()`,
`performPreviewScrollTick()`, `beginActiveScrolling()`, and every
`maxScroll` calculation's *numeric mechanics* are unchanged by Mirror
Mode, and an earlier version of this document took that to mean no
scrolling code needed to change at all. That conclusion was wrong.

Flip and Rotate's 180-degree rotation pivots about `scriptText`'s own
center — the center of the *entire* content height, not the viewport.
`ScrollView` clips to a fixed viewport-sized window of untransformed
content coordinates `[scrollY, scrollY + viewportHeight]`, and the
rotation reflects the *whole* content (height `H`) about its center
before that window is drawn: a point at untransformed content position
`p` draws at position `H − p`. Working through what that means for what
appears inside the clip window at a given `scrollY` (see the box below)
shows that `scrollY = 0` renders the **end** of the script, and
`scrollY = maxScroll` renders the **beginning** — the exact opposite of
Mirror Off/Flip — and that content *within* the visible window also
reads bottom-to-top of the untransformed layout instead of top-to-bottom.
This is the root cause of the bug this round fixes: before this round,
Preview and full-screen Prompter mode always started at `scrollY = 0`
and increased `scrollY` toward `maxScroll`, which is correct for Mirror
Off/Flip but under Flip and Rotate started the presenter at the visual
**end** of the script and scrolled **backward** toward the beginning.

**Derivation** (content height `H` = `scriptText.getHeight()`,
viewport height `V` = `scrollView.getHeight()`, `maxScroll = H − V`):
the visible window in *canvas* coordinates is fixed at
`[scrollY, scrollY + V]` regardless of rotation (the `ScrollView` clip
is applied before the child's own rotation matrix is concatenated). A
content point at untransformed position `q` draws at canvas position
`H − q`. Solving `H − q ∈ [scrollY, scrollY + V]` for `q` gives the
untransformed content range actually visible: `q ∈ [H − scrollY − V,
H − scrollY]`. At `scrollY = 0` this is `[H − V, H]` — the final `V`
pixels of content, i.e. the end of the script. At `scrollY = maxScroll
= H − V` this is `[0, V]` — the first `V` pixels, i.e. the beginning.
Within that visible window, a point at `q` draws at relative (on-screen,
0 = top of viewport) position `(H − q) − scrollY`, which *decreases* as
`q` increases — content reads bottom-to-top of its own untransformed
layout, i.e. vertically flipped, exactly as the "Flip and Rotate ≡
horizontal flip + Y-flip" mathematical equivalence noted above predicts.

### Reverse logical scrolling (Flip and Rotate)

Fixing the rendering transform itself was explicitly out of scope (it
is correct and must not change — see the transform table above); the
fix instead layers an explicit **reverse logical scrolling** model on
top of the unchanged `scrollY` mechanics, active only when
`isReverseScrollMode()` is true:

```java
private boolean isReverseScrollMode() {
    return mirrorEnabled && AppConstants.MIRROR_MODE_FLIP_ROTATE.equals(mirrorMode);
}
```

| | Mirror Off / Mirror On + Flip | Mirror On + Flip and Rotate |
| --- | --- | --- |
| Visual beginning (`getScriptStartScrollY()`) | `0` | `maxScroll` |
| Visual end (`getScriptEndScrollY()`) | `maxScroll` | `0` |
| Automatic scrolling direction | `scrollY` increases | `scrollY` decreases |
| Reaching the end means | `scrollY >= maxScroll` | `scrollY <= 0` |

Five centralized helpers are the *only* place this condition is ever
consulted — no other method compares `mirrorEnabled`/`mirrorMode`
directly:

- `isReverseScrollMode()` — the condition itself.
- `getScriptStartScrollY(maxScroll)` / `getScriptEndScrollY(maxScroll)`
  — the mode-appropriate beginning/end `scrollY`.
- `moveTowardScriptEnd(currentScrollY, wholePixels, maxScroll)` — adds
  `wholePixels` to `scrollY` normally, subtracts it in reverse mode,
  clamping to `[0, maxScroll]` either way. `wholePixels` itself is
  always the non-negative whole-pixel distance produced by the
  elapsed-time/fractional-accumulator model (section 34) — that model,
  and the accumulator's sign, are entirely untouched; only the
  direction the resulting distance is *applied* to `scrollY` changes.
- `hasReachedScriptEnd(scrollY, maxScroll)` — `scrollY ==
  getScriptEndScrollY(maxScroll)`, always exact since
  `moveTowardScriptEnd()`'s clamp guarantees an overshoot lands exactly
  on `0` or `maxScroll`.

`performScrollTick()` and `performPreviewScrollTick()` both now compute
`newY` via `moveTowardScriptEnd()` and check `hasReachedScriptEnd()`
instead of a hardcoded `newY >= maxScroll`/`scrollTo(..., maxScroll)`
comparison — the only change to either method's shape; the rest
(elapsed-time measurement, the fractional accumulator, the single
`scrollTo()` call, the single end-detection call) is byte-for-byte the
same structure section 34 describes.

### Reverse-mode full-screen padding

`OPENING_SPACE_FRACTION` (`0.775f`) and `END_SPACE_FRACTION` (`0.4f`)
position the first/last line at a comfortable on-screen height in
Mirror Off/Flip by padding `scriptText` proportionally to the measured
full-screen viewport height (section 21). Flip and Rotate cannot reuse
these fractions unmodified — reaching the correct reverse-mode mapping
took multiple rounds of physical testing, documented in full under
"Physically observed" below. The current, physically validated model:

**Top padding governs the reverse-mode opening; bottom padding governs
the reverse-mode end.** The reverse visual start is `scrollY ==
maxScroll`, where `maxScroll = scriptTextHeight − viewportHeight`. Any
change to reverse-mode *bottom* padding changes `scriptTextHeight` and
`maxScroll` by the exact same amount, and those two changes cancel in
the position actually reached — so bottom padding has **no effect** on
where the first line visually lands at the reverse start (confirmed
directly: raising it from `0.775` to `0.95` produced no visible change
at all — commit `735acb0`). *Top* padding is not subject to that
cancellation, and physical testing confirms it is what actually moves
the opening position — but in the **opposite direction** from naive
top-down intuition: **increasing** reverse-mode top padding moves the
first line **further down** the screen; decreasing it moves the line
**up**. (This is the reverse of Mirror Off/Flip's own top padding, where
more padding also means further down, but by a completely different,
non-analogous mechanism — Mirror Off/Flip reaches its opening by
scrolling *down* from `scrollY == 0`, while Flip and Rotate reaches its
opening by scrolling *up* to `scrollY == maxScroll`.) The reverse visual
end, by contrast, is `scrollY == 0`, where no such cancellation occurs
for bottom padding — so bottom padding is what correctly governs the
ending position instead.

- Reverse-mode top padding (opening): `viewportHeight ×
  REVERSE_START_TOP_FRACTION` = `0.60 × viewportHeight` — a separately
  calibrated constant, not derived from `OPENING_SPACE_FRACTION`.
- Reverse-mode bottom padding (ending): `viewportHeight × (1.0 −
  END_SPACE_FRACTION)` = `0.6 × viewportHeight`.

These two fractions are numerically equal (both `0.6`) but govern
different, unrelated things — the top fraction the opening, the bottom
fraction the ending — and are computed and applied independently in
source; this is a coincidence of calibration, not a shared constant, and
must not be merged into one. Both fractions, and the mode selection, are
centralized in one method:

```java
private void applyFullScreenScriptPadding(int fullScreenHeightPx) {
    int sidePaddingPx = dpToPx(SCRIPT_PADDING_DP);
    float topFraction = isReverseScrollMode() ? REVERSE_START_TOP_FRACTION : OPENING_SPACE_FRACTION;
    float bottomFraction = isReverseScrollMode() ? (1.0f - END_SPACE_FRACTION) : END_SPACE_FRACTION;
    int newTopPaddingPx = Math.max(sidePaddingPx, Math.round(fullScreenHeightPx * topFraction));
    int newBottomPaddingPx = Math.max(sidePaddingPx, Math.round(fullScreenHeightPx * bottomFraction));
    applyScriptPadding(newTopPaddingPx, newBottomPaddingPx);
}
```

**Why `REVERSE_START_TOP_FRACTION` is `0.60`, and why the direction was
initially assumed backwards.** The prior round of this document reasoned
algebraically that *decreasing* top padding should move the first line
*further down* (treating the relationship as `screenPosition =
viewportHeight − topPadding`), and set `REVERSE_START_TOP_FRACTION =
0.15` on that basis — deliberately below the `0.225` that reasoning
implied would give an exact 77.5%-down match, as a margin against
landing too high. **Physical testing found the opposite of the
predicted direction**: at `0.15`, the first line moved *higher still*,
fully clear of the Tap to Start overlay on the wrong side — worse than
the prior `0.4` value, which had at least reached (but not cleared) the
overlay. This directly disproves the `viewportHeight − topPadding`
relationship the prior round assumed; the actual, physically observed
relationship is that increasing top padding moves the line down. `0.60`
was chosen — above the already-too-high `0.4` — specifically to move the
line further down, per the same too-low-over-too-high preference as
every prior round of this calibration.

This also resolves the "open question" a still-earlier round of this
document left unexplained: round one (commit `a0a3088`) used reverse-mode
top padding `1 − OPENING_SPACE_FRACTION = 0.225` — a *small* value — and
physical testing found the opening position too high. Under the
now-corrected direction (small top padding → line moves up), that result
is exactly what the corrected model predicts, not a mystery — round
one's failure was never a sign the cancellation-based side-selection
(top governs opening) was wrong, only that the specific formula and its
implied direction were.

`resetToWaitingFromTop()` (Start Prompter and Restart's shared full-screen
entry point, section 17) is the sole caller, called only after the
system bars are hidden and that layout has settled (section 22) — the
same "measure after settling" requirement as before, unchanged.
Preview's own padding (minimal, uniform `SCRIPT_PADDING_DP` on all
sides, section 14) needs no direction-aware variant — it doesn't use
the opening/end fractions at all, only the *starting position* within
that minimal padding differs by direction (below).

### Preview start/end in reverse mode

Every Preview press still resets to the *visual* beginning, preserving
the existing "reset to top" behavior (section 14). `startPreviewScrolling()`
applies minimal padding, waits for layout via `scrollView.post()`, then
calls `scrollToScriptStartReliably()` (below) instead of a hardcoded
`scrollTo(0, 0)` — landing at `0` in Mirror Off/Flip (unchanged), or the
reliably-measured `maxScroll` in Flip and Rotate; Preview's accumulator/
baseline reset and first-tick post are deferred to that call's
`afterPositioned` callback so they only run once the position is
confirmed. `performPreviewScrollTick()`
uses `moveTowardScriptEnd()`/`hasReachedScriptEnd()` as above: Preview
reaches its end at `scrollY >= maxScroll` (normal/Flip) or `scrollY <= 0`
(Flip and Rotate), then `stopPreviewScrolling()` runs exactly once,
relabeling Stop back to Preview and leaving the script at its visual
final position. A script freshly loaded via `loadScriptFile()` is
likewise positioned at `getScriptStartScrollY(computeMaxScroll())`
rather than a hardcoded `scrollTo(0, 0)`, so the initial (non-scrolling)
control-screen preview also opens on the correct visual beginning under
Flip and Rotate.

### Full-screen start/end in reverse mode

Start Prompter/Restart (`resetToWaitingFromTop()`) always begins at the
script's first line regardless of Mirror Mode. Mirror Off/Flip: enter
orientation → hide bars → wait for layout → apply normal opening/
trailing padding → wait for layout → `scrollTo(0, 0)` →
`WAITING_FOR_TAP` — unchanged. Flip and Rotate: the same sequence, but
`applyFullScreenScriptPadding()` applies the reverse-mode fractions
above, and the final step scrolls to the reverse-mode `maxScroll` via
`scrollToScriptStartReliably()` (below) rather than a direct
`scrollTo()`. Neither branch starts a countdown or scrolling
automatically; both still require a Tap to Start. Full-screen scrolling
reaches its end via the same `hasReachedScriptEnd()` check as Preview
(`scrollY <= 0` in reverse mode), routing through the existing
`stopScrollingAtEnd()` → `END_OF_SCRIPT_WAITING` path exactly once
either way — the final text stays unobstructed until a tap reveals the
reduced Restart/Back to Controls menu (section 20), unchanged.

### Physically observed: missing reverse-mode opening buffer, and its fix

Physical hardware testing of the reverse-scrolling fix above (direction,
start/end, Pause/Resume) confirmed it correct, but found a *second*,
separate bug: in Mirror On + Flip and Rotate, the first script line was
appearing near the **top** of the full-screen viewport instead of at the
same ~77.5%-down opening position Mirror Off/Flip use — the intended
opening buffer was effectively missing. Getting this right took two
rounds; both are recorded here since the first round's diagnosis turned
out to be incomplete.

**Round one (commit `08e3bbc`) — measurement timing, found insufficient.**
The reverse-mode padding fractions in place at the time (`1 −
OPENING_SPACE_FRACTION` for top, `1 − END_SPACE_FRACTION` for bottom)
were algebraically self-consistent with a specific model of how
`ScrollView`'s clip window composes with the child's rotation matrix,
so that round's diagnosis was that `maxScroll` (`computeMaxScroll()` =
`scriptText.getHeight() − scrollView.getHeight()`) was simply being read
before `applyFullScreenScriptPadding()`'s own effect on `scriptText`'s
measured height had settled — plausible, since Mirror Off/Flip's target
(`scrollTo(0, 0)`) never depends on that measurement at all, which is
consistent with only the reverse-mode opening position being affected.
That round added `scrollToScriptStartReliably(afterPositioned)`:
scrolls once using whatever `computeMaxScroll()` reports immediately,
waits one further settled frame, recomputes `maxScroll`, and re-scrolls
only if the recomputed value differs — a single bounded correction pass,
never a loop, wired into both of this codebase's script-start call sites
(`resetToWaitingFromTop()` and Preview's `startPreviewScrolling()`).
**Physical testing of that fix found the opening buffer still wrong** —
ruling out measurement-timing staleness as the (sole) cause and showing
the complement-based padding formula itself needed to be reconsidered,
not just measured more reliably.

**Round two (this round) — the padding-side mapping was inverted.**
Re-examining Flip and Rotate's rendering: because the 180-degree
rotation flips the content vertically within `ScrollView`'s clip window,
logical **bottom** padding — not top — becomes the visual opening space
at the reverse start position (`scrollY == maxScroll`), and logical
**top** padding becomes the visual trailing space at the reverse end
(`scrollY == 0`). The two sides are **exchanged**, not complemented.
Corrected side-exchange mapping (see "Reverse-mode full-screen padding"
above for the current method):

| | Normal/Flip | Flip and Rotate (round two, sides exchanged) | Flip and Rotate (round one, wrong) |
| --- | --- | --- | --- |
| Top padding | `OPENING_SPACE_FRACTION` | `END_SPACE_FRACTION` | `1 − OPENING_SPACE_FRACTION` |
| Bottom padding | `END_SPACE_FRACTION` | `OPENING_SPACE_FRACTION` | `1 − END_SPACE_FRACTION` |

This is a genuine geometry correction, not a re-tuning of the
measurement-reliability fix — `scrollToScriptStartReliably()` from round
one was kept as-is, since it solves an orthogonal problem (whether the
*correct* padding's effect on `maxScroll` has actually settled by the
time it's read) independent of which padding mapping is correct, and it
remains a harmless no-op whenever the first measurement is already
accurate.

**Round three (a further round) — the exchanged fraction's value needed
its own calibration.** Round two exchanged *which side* governs the
reverse-mode opening space (bottom padding, not top) but reused
`OPENING_SPACE_FRACTION`'s existing value (`0.775`) for that bottom
padding. Physical testing found this insufficient: the first line
cleared the "near the top" symptom round two fixed, but still sat behind
the centered Tap to Start overlay rather than at the same comfortable
opening position Mirror Off/Flip achieve. `OPENING_SPACE_FRACTION` and a
reverse-mode opening fraction drive visually different things (see
"Reverse-mode full-screen padding" above), so there was no reason to
expect the same numeric value to work for both once exchanged onto the
opposite side. `REVERSE_OPENING_SPACE_FRACTION = 0.95f` was introduced
as a value calibrated separately against the physical result, replacing
`OPENING_SPACE_FRACTION` in the bottom-padding branch only —
`OPENING_SPACE_FRACTION` itself is untouched and still governs Mirror
Off/Flip's own top padding.

| | Normal/Flip | Flip and Rotate (round three) | Flip and Rotate (round two) |
| --- | --- | --- | --- |
| Top padding | `OPENING_SPACE_FRACTION` (0.775) | `END_SPACE_FRACTION` (0.4) | `END_SPACE_FRACTION` (0.4) |
| Bottom padding | `END_SPACE_FRACTION` (0.4) | `REVERSE_OPENING_SPACE_FRACTION` (0.95) | `OPENING_SPACE_FRACTION` (0.775) |

**Round four (a further round) — round two's "sides exchanged" framing
was itself wrong; corrected to "top governs opening, bottom governs
end."** Round three's calibration (raising the reverse-mode bottom
padding fraction to `0.95`, on the theory that bottom padding governs
the reverse opening space) was physically tested and **produced no
visible change at all** — the first line remained behind the Tap to
Start overlay exactly as before. This result disproves round two's
premise directly, and has a structural explanation: the reverse visual
start is `scrollY == maxScroll`, and `maxScroll = scriptTextHeight −
viewportHeight`. Any increase to bottom padding increases
`scriptTextHeight` and `maxScroll` by the same amount, so the two
changes cancel exactly at `scrollY == maxScroll` and the visible opening
position cannot move no matter how large bottom padding is made. Top
padding is not subject to this cancellation — it is what actually
governs the opening position, and round two's own top-padding choice
(`END_SPACE_FRACTION` = `0.4`) is why the line landed "near the vertical
center, behind Tap to Start" rather than near the top *or* at the
correct 77.5%-down position: `0.4` is neither.

The reverse visual *end* (`scrollY == 0`) has no analogous cancellation
for bottom padding — round two's and three's shared choice of top
padding = `END_SPACE_FRACTION` for the *end* was actually a coincidence
of the "sides exchanged" framing, not a correct application of it; what
actually governs the end position, by the same reasoning, is bottom
padding, and `(1 − END_SPACE_FRACTION)` = `0.6` for bottom padding is
what places the final line at the intended ~60%-down resting height
(independently re-derived and consistent with round one's original
value for the same purpose, which — for the ending position only —
appears to have been correct all along).

Corrected model (see "Reverse-mode full-screen padding" above for the
current method and values):

| | Normal/Flip | Flip and Rotate (round four, current) | Flip and Rotate (round three, wrong) |
| --- | --- | --- | --- |
| Top padding (opening) | `OPENING_SPACE_FRACTION` (0.775) | `REVERSE_START_TOP_FRACTION` (0.15) | `END_SPACE_FRACTION` (0.4) |
| Bottom padding (end) | `END_SPACE_FRACTION` (0.4) | `1 − END_SPACE_FRACTION` (0.6) | `REVERSE_OPENING_SPACE_FRACTION` (0.95) |

`REVERSE_OPENING_SPACE_FRACTION` was removed (it is no longer used
anywhere); `REVERSE_START_TOP_FRACTION` replaces it as the sole
reverse-mode-specific constant. `scrollToScriptStartReliably()` remains
unchanged across all four rounds — round four's physical test (bottom
padding raised, no visible effect) is itself further evidence that
measurement timing was never the remaining issue, since a genuinely
stale measurement would have produced *some* visible movement, not
none.

**Round four's own open question, flagged but unresolved at the time**:
round one (commit `a0a3088`) used reverse-mode top padding `0.225`, and
physical testing at the time found the opening position "near the top,"
not the exact 77.5%-down match round four's own reasoning (`screenPosition
= viewportHeight − topPadding`) predicted it should give. Round four set
`0.15` deliberately lower than `0.225` specifically because of that
unresolved discrepancy — without realizing the discrepancy meant the
*direction* of its own formula was backwards, not just its calibrated
value. Round five (below) found exactly that.

**Round five (a further round) — the direction itself was backwards; top
padding decreases move the opening position *up*, not down.** Physical
testing of round four's `0.15` value found the first line moved *higher
still* — fully clear of the Tap to Start overlay on the wrong (upper)
side — worse than round three's `0.4`, which had at least reached (not
cleared) the overlay. This result is the opposite of what round four's
`viewportHeight − topPadding` formula predicted for a *decrease* in top
padding, and directly disproves that formula's direction: the actual,
physically observed relationship is that **increasing** reverse-mode top
padding moves the first line **further down**, and decreasing it moves
the line **up**. `REVERSE_START_TOP_FRACTION` was changed from `0.15` to
`0.60` — above round three's already-too-high `0.4` — specifically to
move the line further down, consistent with the same too-low-over-
too-high preference every round of this calibration has stated.

This also finally resolves round four's own flagged open question:
round one's `0.225` (a *small* value, smaller than round three's `0.4`)
failing with the line "near the top" is exactly what the now-corrected
direction predicts — a small top-padding value moves the line up, not
down. Round one's failure was never evidence against "top padding
governs the opening" (round four's core, structurally-derived
conclusion, still correct); it was only ever evidence against the
specific `viewportHeight − topPadding` *formula*, which both round one
and round four's `0.225`/`0.15` choices shared. The corrected model:

| | Normal/Flip | Flip and Rotate (round five, current) | Flip and Rotate (round four, wrong direction) |
| --- | --- | --- | --- |
| Top padding (opening) | `OPENING_SPACE_FRACTION` (0.775) | `REVERSE_START_TOP_FRACTION` (0.60) | `REVERSE_START_TOP_FRACTION` (0.15) |
| Bottom padding (end) | `END_SPACE_FRACTION` (0.4) | `1 − END_SPACE_FRACTION` (0.6) | `1 − END_SPACE_FRACTION` (0.6) — unchanged |

`REVERSE_START_TOP_FRACTION`'s value changed; its role (governs only the
reverse-mode opening) and reverse-mode bottom padding (unchanged across
rounds four and five, still governs only the end) did not.
`scrollToScriptStartReliably()` remains untouched across all five
rounds — this was a value-and-direction calibration, not a measurement-
timing change.

`loadScriptFile()`'s own (CONTROLS-preview, not full-screen) use of
`getScriptStartScrollY(computeMaxScroll())` was left as a direct call,
unchanged across all five rounds — not reported affected by any of
them, and a lower-stakes context (a freshly loaded script's initial
preview position, not an opening buffer the presenter reads from).

### Pause and Resume in reverse mode

Unchanged in shape, direction-correct by construction: pausing
(`pauseFullScreenScrolling()`) stops at the exact current `scrollY` and
never touches it. Resume runs the same five-second countdown as a fresh
start, then `beginActiveScrolling()` resets the elapsed-time baseline
and fractional accumulator (never the `scrollY` position) and posts the
first tick — which then calls `moveTowardScriptEnd()`, continuing to
*decrease* `scrollY` in reverse mode from the exact paused position,
since `isReverseScrollMode()` is read fresh on every tick rather than
being baked in when scrolling began.

### Restart in reverse mode

`restartFromPauseMenu()` calls the same `resetToWaitingFromTop()` Start
Prompter uses, so it inherits the same direction-aware padding and start
position described above with no separate code path — Restart from
Flip and Rotate lands back on `getScriptStartScrollY(maxScroll)` =
`maxScroll` after reapplying reverse-mode padding, exactly like a fresh
Start Prompter tap.

### Back to Controls in reverse mode

`switchScriptPaddingPreservingPosition()` (used only by
`exitFullScreenToControls()`/`continueExitToControls()`, section 21)
preserves the approximate passage on screen across the full-screen ↔
CONTROLS padding change. In Mirror Off/Flip, `scrollY` is measured from
the top of the content in the same order it renders, so only the
**top**-padding delta needs to be added to the previous `scrollY`
(unchanged from before this round). In reverse mode the rendered order
is inverted, so the position-preserving delta comes from the **bottom**-
padding change instead — derived the same way (solving for the `scrollY`
that keeps the same on-screen relative position as both `H` and the
top/bottom padding change) rather than reused blindly from the
forward-mode formula. Both branches then clamp to `[0, maxScroll]` once
the padding change has settled; a prior position already at that
direction's visual start reduces correctly under the clamp alone, so no
separate "already at start" fast path is needed (an earlier version of
this method had one, keyed off a hardcoded `scrollY <= 0`, which would
have been wrong in reverse mode where `scrollY <= 0` means the visual
*end*, not the start — removed as part of this fix).

### Mode-switch progress mapping

`scriptText` is shared by CONTROLS' preview and every full-screen state,
and it is mirrored/rotated in CONTROLS too — so the control-screen
preview's own `scrollY` is also reverse-mode-aware, and a Mirror/Mirror
Mode change reachable while CONTROLS is showing (Settings' Mirror Mode
radio group, or the main screen's own Mirror On/Off toggle) can flip the
effective scroll direction out from under an already-positioned preview.
`remapScrollPositionForMirrorModeChange(directionChanged)` handles both:

```java
private void remapScrollPositionForMirrorModeChange(boolean directionChanged) {
    if (!directionChanged) return;
    int maxScroll = computeMaxScroll();
    if (maxScroll <= 0) return;
    int currentScrollY = scrollView.getScrollY();
    float progress = (float) currentScrollY / (float) maxScroll;
    int mappedScrollY = Math.round(maxScroll * (1.0f - progress));
    scrollView.scrollTo(scrollView.getScrollX(), mappedScrollY);
}
```

`progress` (0.0 at the *old* direction's visual start, 1.0 at its visual
end) maps symmetrically onto the equivalent progress under the *new*
direction — the same formula works for either direction of switch,
since the mapping is its own inverse. A no-op whenever the direction
didn't actually change, or the script is too short to scroll
(`maxScroll <= 0`). Two call sites, both capturing `isReverseScrollMode()`
before reapplying `mirrorMode`/`mirrorEnabled` and comparing against it
after:

- `onResume()`, guarded by `uiState == CONTROLS` — covers returning from
  `SettingsActivity` with a newly selected Mirror Mode while Mirror is
  On (a Mirror Mode change alone never affects direction while Mirror is
  Off, since `isReverseScrollMode()` is `false` either way).
- `mirrorToggle`'s `OnCheckedChangeListener` — an On/Off change can also
  flip the effective direction, whenever the saved Mirror Mode is Flip
  and Rotate.

Neither call site starts or stops Preview — a script mid-Preview when
the mode changes keeps running, just continuing from the remapped
position in the new direction on its very next tick, since
`performPreviewScrollTick()` re-reads `isReverseScrollMode()` fresh each
tick rather than caching it.

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
`applyMirror()`. Reverse scrolling (this round) reads the same
orientation-agnostic `scrollView`/`scriptText` measurements as every
other `maxScroll` calculation, and `resetToWaitingFromTop()` only reads
them after the portrait-orientation switch and the hidden-system-bar
layout have both settled (section 32's "measure after settling" rule,
unchanged) — so reverse-mode start/end positions and padding are
equally correct in Horizontal and Vertical Prompter Orientation, with no
orientation-conditional branch needed in the reverse-scrolling helpers
either.

### Preview

`scriptText` is the same view in the control-screen preview and every
full-screen Prompter state (section 14, section 21) — `applyMirror()`
doesn't distinguish between them, so the preview reflects the active
Mirror/Mirror Mode combination immediately, the same as it already did
for the pre-existing Flip-only behavior. Preview's own elapsed-time
scrolling loop (section 34) reads/writes `scrollView`'s untransformed
`scrollY` exactly as before — unaffected by whatever rotation or flip is
currently applied to what's drawn on screen — but, as of this round,
*which* `scrollY` counts as Preview's start/end, and which direction a
tick moves it, both go through the same `isReverseScrollMode()`-aware
helpers full-screen scrolling uses (see "Reverse logical scrolling"
above), since the two share `scrollView`/`scriptText` and are equally
subject to the rendering inversion Flip and Rotate produces.

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
`applyMirror()` itself still touches only `scaleX`/`scaleY`/`rotation`
on the three presenter-facing views and nothing else. As of this round,
`onResume()` additionally captures `isReverseScrollMode()` immediately
before this reread and compares it against the value immediately after,
calling `remapScrollPositionForMirrorModeChange()` (guarded by
`uiState == CONTROLS`) when they differ — see "Mode-switch progress
mapping" above — so a Mirror Mode change that flips the effective
direction repositions the CONTROLS preview to the equivalent passage in
the new direction rather than leaving it at what became the wrong end.
This still never touches the loaded script, `uiState`, or Preview's
running/stopped state (a running Preview simply continues from the
remapped position on its next tick).

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
or a successful build alone. This round's reverse-scrolling fix
additionally requires physical confirmation that, under Mirror On +
Flip and Rotate specifically: Preview and Start Prompter both visually
begin at the script's first line and end at its last; automatic
scrolling visually moves in the correct (forward-reading) direction at
every Speed setting; Resume continues in the correct direction from the
exact paused position; and a Mirror Mode or Mirror On/Off change made
from CONTROLS repositions the preview to the correct end rather than
leaving it reversed. None of this can be confirmed by source inspection
or a successful build alone.

The reverse-mode opening buffer specifically took five physical-testing
rounds to reach its current form (commits `08e3bbc`, `3ec4c30`,
`735acb0`, `4a07446`, and this round's `REVERSE_START_TOP_FRACTION`
direction correction) — a measurement-timing fix that testing found
insufficient, a side-exchange padding mapping whose *direction* testing
confirmed but whose reused `OPENING_SPACE_FRACTION` value testing then
found insufficient, a bottom-padding increase that testing found had
**no visible effect at all** (disproving the side-exchange framing
itself), a corrected "top governs opening, bottom governs end" model
whose *value* testing then found moved the line the **wrong way**
(higher, not lower), and finally a correction to the direction itself.
That history is itself the caution: **the build succeeding, and even an
algebraic derivation checking out, has repeatedly not predicted the
actual on-screen result for this specific geometry** — round four's
formula was structurally well-reasoned (bottom padding's cancellation at
`scrollY == maxScroll` is real and round three's null result proved it)
and its *direction* was still backwards.

**Physically confirmed**: `REVERSE_START_TOP_FRACTION = 0.60f` was
tested on real hardware and reported correct — the user's own words:
"Perfect. You nailed it that time." Under Mirror On + Flip and Rotate,
the first line now clears the centered Tap to Start overlay with a
visible, non-overlapping gap below it, at an opening position matching
the intent of this five-round calibration; the final-line resting
position (reverse-mode bottom padding = `1 − END_SPACE_FRACTION` =
`0.6`, unchanged since round four) was not separately called out as
wrong, consistent with it still matching Mirror Off/Flip. This is the
first round of the opening-buffer saga to receive an affirmative
physical result rather than a further correction, closing it out for
now. The exhaustive combination matrix this section otherwise calls for
— both Prompter Orientations, small/large font sizes, short/long
scripts, Left/Centered alignment, Start Prompter vs. Restart
specifically — was not itemized in that confirmation, so treat the core
geometry (opening position, buffer placement, clearing the overlay) as
physically verified and the remaining combinations as still only
"requires physical testing" per this section's earlier list, not
separately confirmed. Given this geometry's five-round track record
before finally landing correctly, a further report of it being wrong in
some *other* combination (a different orientation, an extreme font
size, a very short script) should be treated as plausible and
investigated on its own terms, not assumed impossible just because the
core geometry is now confirmed.

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

A later round (the reverse-scrolling fix at the top of section 35) found
that this document's own claim — "Mirror Mode therefore required zero
changes anywhere in the scrolling/state-machine code" — was itself a
discrepancy: true only of `scrollY`'s raw numeric mechanics, not of
whether direction-aware logic was needed on top of them. Corrected in
place in section 35 rather than left standing.

A still later round found that the reverse-scrolling fix's own
"algebraic derivation" for the reverse-mode padding fractions, while
mathematically self-consistent on its own terms, had not accounted for
measurement-timing reliability on physical hardware — physical testing
found the resulting opening position wrong in practice despite the
formula checking out on paper. That round's own fix
(`scrollToScriptStartReliably()`) was itself then found insufficient by
a further physical test, which is the clearer discrepancy: this
document had asserted the padding *formula* was correct and only its
measurement timing was at fault, when in fact the formula's premise —
which padding side governs the visual opening space under a 180-degree
rotation — was inverted. Both the formula (top/bottom exchanged rather
than complemented) and this document were corrected in section 35 under
"Reverse-mode full-screen padding" and "Physically observed: missing
reverse-mode opening buffer, and its fix," rather than left standing. A
reminder that "verified by inspection" and "physically tested" (as this
document defines both terms above) are not interchangeable, even for a
derivation checked algebraically more than once — and that a plausible
alternative diagnosis (measurement timing) should not be accepted as
confirmed until the fix built on it is itself physically re-tested.

A yet further round found that even the "sides exchanged" correction
above was itself wrong in its premise, not just its calibrated value:
this document had asserted logical bottom padding governs the
reverse-mode opening position, when physical testing (raising that
bottom padding from `0.775` to `0.95` with no visible effect) proved it
does not — `maxScroll`'s own definition cancels any change to bottom
padding at the reverse start position, a structural fact this document
had not identified despite two prior rounds of padding-formula
correction. It is top padding that governs the opening, and bottom
padding that correctly governs the reverse-mode end. Corrected in
section 35 under "Reverse-mode full-screen padding" and "Physically
observed" (round four) rather than left standing.

A sixth round found round four's own correction was right about *which
side* governs the reverse-mode opening (top padding) but wrong about
*which direction* — this document asserted decreasing top padding moves
the first line down, when physical testing of that exact change (`0.15f`)
moved it up instead, clear above the Tap to Start overlay, with the
buffer landing at the bottom of the screen rather than the top. Corrected
to `REVERSE_START_TOP_FRACTION = 0.60f` (round five) in section 35.
This project's Flip-and-Rotate padding geometry has now been revised
five times on physical-testing evidence; each revision looked internally
consistent at the time it was written, and two of the five (rounds three
and four) were disproven not by a flaw in their stated reasoning but by
a physical result the reasoning had not anticipated. Treat any *further*
physical test result on this specific geometry as capable of overturning
the current model too, rather than assuming round five has necessarily
settled it.

A later round found this document's Settings-screen section (30)
describing a left/right split with a branding panel on the right — not
a correctness bug like the ones above, but a design the person who
requested it turned out not to like once built, and asked to have
replaced with an About dialog instead. Recorded here because it is the
same underlying lesson as the Flip-and-Rotate padding saga in spirit,
even though the previous design was not factually wrong: a round
documented as "done" on successful build and source inspection can still
need to be undone once it is actually seen. Section 30 was rewritten in
place for the current About-dialog design; the left/right split is kept
only as a brief historical note there and in section 27, not described
as current behavior.
