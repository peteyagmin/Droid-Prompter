# A500 Teleprompter — Project Specification

This document describes the application as it exists in the current
source. See `CLAUDE.md` for the persistent rules derived from it.

**Documented as of commit `66d459f`** (branch
`claude/android-teleprompter-app-1wfqsr`). The commit that adds this file
and `CLAUDE.md` is necessarily newer than `66d459f`; that commit's hash is
reported separately at the end of the task that created these files, not
inferred here.

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
  section 28 for the intended (not yet practiced) discipline.

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
  (`README.md`'s permissions section is stale — it mentions only
  `READ_EXTERNAL_STORAGE`; see the Discrepancies section at the end of
  this document.)
- Known modern-Android installation limitation: `targetSdkVersion 14` is
  far below what current Android versions expect. This project has not
  characterized or tested what warnings or restrictions this produces on
  a modern device — treat it as an open question if modern-device support
  is ever pursued, not as a resolved fact.

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

- **Enter Prompter** (`btn_start`) — starts the full-screen sequence
  (section 17).
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

Script preview: the same `scriptText` view used in full-screen mode,
inside `scrollView`, shown below the controls panel whenever
`uiState == CONTROLS`.

No other controls exist on this screen. In particular, there is no
"Return to Top" button and no Speed minus/plus button pair.

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
- Persistent field labels/hints (unchanged since prior rounds, must not
  be altered without explicit instruction): "Script Filename"
  (`script_filename_label`) / "Enter a filename" (`filename_hint`) for
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
4. The Cancel/Save row — a horizontal `LinearLayout`, positioned after
   `scriptBodyField` in document order (never above it):
   - `btnEditorCancel` — style `SecondaryControlButton` — "Cancel"
     (`btn_cancel`).
   - `btnEditorSave` — style `ControlButton`, with an explicit
     `android:textSize="@dimen/secondary_text_size"` override — "Save"
     (`btn_save`).

Confirmed facts:

- `scriptBodyField` is the weighted element that receives whatever
  vertical space remains after the (possibly collapsed)
  `titleAndFilenameSection`, the label row, and the button row are laid
  out.
- `scriptBodyField` is not nested in a `ScrollView` anywhere in this
  hierarchy.
- Cancel and Save appear below `scriptBodyField`, never above it.
- Cancel and Save currently share the same text size: both resolve to
  `@dimen/secondary_text_size` = **16sp** — Cancel inherits it from
  `SecondaryControlButton`'s own `textSize` entry; Save gets it via the
  explicit per-instance override on top of `ControlButton` (which still
  supplies Save's larger `minHeight`, padding, and background — those
  were deliberately left unchanged). Verified by resolving both
  attributes in the compiled APK, not just by reading the XML.
- Styles live in `app/src/main/res/values/styles.xml`
  (`ControlButton`: `minHeight=@dimen/control_button_height` (64dp),
  `textSize=@dimen/control_text_size` (20sp), `padding=12dp`; `Secondary
  ControlButton`: `minHeight=@dimen/secondary_button_height` (56dp),
  `textSize=@dimen/secondary_text_size` (16sp), `padding=10dp`; both have
  `textAllCaps=false` and no `textStyle`/`fontFamily`). Dimensions live in
  `app/src/main/res/values/dimens.xml`, including `editor_text_size`
  (20sp, `scriptBodyField`'s own text size — unrelated to the
  Cancel/Save mismatch that was fixed).

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
- Compact mode: `scriptBodyField.setOnFocusChangeListener(...)` calls
  `enterCompactMode()` whenever the body gains focus. `enterCompactMode()`
  sets `titleAndFilenameSection` to `GONE` and, only in
  `EDITOR_MODE_CREATE_NEW`, makes `btnEditFilename` `VISIBLE`. It is
  idempotent (`compactMode` boolean guard).
- Title and filename section collapsing when the body gains focus: as
  above — this is the entire mechanism; there is no separate "shrink"
  animation or measurement step beyond the `GONE` visibility change plus
  `scriptBodyField`'s own `layout_weight` claiming the freed space.
- "Edit Filename" behavior in `EDITOR_MODE_CREATE_NEW`: tapping
  `btnEditFilename` calls `exitCompactModeForFilenameEdit()`, which
  restores `titleAndFilenameSection`, hides `btnEditFilename` again,
  refocuses `filenameField` with the cursor at the end of its existing
  text, and calls `showKeyboardFor()`/`ensureVisibleAboveKeyboard()`. It
  does not clear or alter either field's text and does not save.
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
- Keyboard-related limitations requiring hardware testing: the actual
  visual/behavioral result of compact mode, the "Edit Filename" control,
  the raw-`KEYCODE_ENTER` fallback, and `requestRectangleOnScreen()` on
  real hardware has not been confirmed since this mechanism was added —
  see section 25.

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
- Speed behavior: preview uses the same `computeScrollPixelsPerTick()`
  and `SCROLL_INTERVAL_MS` (50ms) as full-screen scrolling — one shared
  `scrollSpeed` value drives both.
- Current-position behavior: preview scrolls the same `scrollView` the
  controls-screen script preview is already showing; no separate scroll
  position is tracked.
- Controls remaining visible: yes — preview never hides `controlsPanel`
  (it only runs while `uiState == CONTROLS`).
- Conditions that stop Preview: the Preview/Stop button itself
  (`togglePreviewScrolling()`), reaching the end of the script
  (`performPreviewScrollTick()` when `maxScroll <= 0` or the tick would
  overshoot it), and every control that defensively calls
  `stopPreviewScrolling()` — Enter Prompter, New Script, Edit Script,
  Load Script, Exit, Back-out-of-full-screen, and `onPause()`.
- End-of-preview behavior: scrolls to exactly `maxScroll` then stops;
  does not loop, does not return to the top automatically.
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
- Speed calculation: `computeScrollPixelsPerTick()` —
  `dpPerTick = scrollSpeed * 0.5f`, converted to pixels via display
  density, floored at a minimum of 1px per tick, applied every
  `SCROLL_INTERVAL_MS` (50ms).
- How speed affects Preview and full-screen scrolling: identically —
  both call the same `computeScrollPixelsPerTick()` off the same
  `scrollSpeed` field.
- Persisted via `KEY_SCROLL_SPEED` (int) in `SharedPreferences`, clamped
  to `[MIN_SPEED, MAX_SPEED]` on load.
- There is no Speed minus/plus button pair anywhere in the source —
  see the "Known limitations and future ideas" section.

## 16. Mirror behavior

- `mirrorToggle` (`ToggleButton`) — "Mirror: On" / "Mirror: Off".
- Persisted via `KEY_MIRROR` (boolean) in `SharedPreferences`.
- `applyMirror()` sets `scaleX = -1f` (mirrored) or `1f` (normal) on
  exactly three views: `scriptText`, `countdownText`, and
  `tapInstructionText`. Nothing else is touched.
- Script mirroring: yes (`scriptText`).
- Countdown mirroring: yes (`countdownText`).
- Tap to Start mirroring: yes (`tapInstructionText`).
- Unmirrored: the controls panel and every button/label on it, the
  Script Editor Activity in its entirety, and the file browser Activity
  in its entirety — `applyMirror()` is only ever called from
  `MainActivity` and only touches the three views above.

## 17. Enter Prompter workflow

Exact implemented sequence, `enterFullScreenWaitingFromControls()`:

1. No-op unless `uiState == CONTROLS`.
2. If `scriptText`'s text is null/empty, show "Load a script before
   starting." (`no_script_loaded`) and stop. (In practice `btnStart` is
   disabled whenever nothing is loaded, and the placeholder text is
   never itself empty, so this is a defensive check rather than a
   reachable path through the normal UI.)
3. `scrollHandler.removeCallbacksAndMessages(null)`.
4. `stopPreviewScrolling()`.
5. `atScriptEnd = false`.
6. `uiState = WAITING_FOR_TAP`.
7. `scriptText.setKeepScreenOn(false)`.
8. `refreshStateVisibility()` — hides `controlsPanel`.
9. On the next layout pass: compute and apply the 77.5% opening padding
   (`OPENING_SPACE_FRACTION`, floored at `SCRIPT_PADDING_DP`), then on a
   second, nested layout pass, `scrollView.scrollTo(0, 0)`.
10. Shows "Tap to Start" (`tapInstructionText`, via
    `refreshStateVisibility()`'s `WAITING_FOR_TAP && !atScriptEnd` rule).
11. Waits for a tap (`handleScriptTap()` in `WAITING_FOR_TAP`) →
    `startCountdown()`.
12. Countdown: `COUNTDOWN_START_VALUE = 5`, 1000ms per tick
    (`performCountdownTick()`), `countdownText` visible via
    `refreshStateVisibility()`.
13. `beginActiveScrolling()` — `uiState = SCROLLING`,
    `scriptText.setKeepScreenOn(true)`, first scroll tick posted after
    `SCROLL_INTERVAL_MS`.

This deliberately bypasses `switchTopPaddingPreservingPosition()`'s
delta-preserving logic and always jumps straight to `scrollTo(0, 0)` —
Enter Prompter always restarts from the top regardless of any prior
preview or paused position.

## 18. UiState and state transitions

`private enum UiState { CONTROLS, WAITING_FOR_TAP, COUNTING_DOWN,
SCROLLING }` — exactly four values, declared in `MainActivity`.

Transitions (all routed through `handleScriptTap()` for tap-driven ones):

- `CONTROLS` → `WAITING_FOR_TAP`: Enter Prompter
  (`enterFullScreenWaitingFromControls()`).
- `WAITING_FOR_TAP` → `COUNTING_DOWN`: tap, only if `!atScriptEnd`
  (`startCountdown()`).
- `WAITING_FOR_TAP` → `CONTROLS`: tap, only if `atScriptEnd`
  (`exitFullScreenToControls()`).
- `COUNTING_DOWN` → `SCROLLING`: countdown reaching 0
  (`beginActiveScrolling()`), not tap-driven.
- `COUNTING_DOWN` → `WAITING_FOR_TAP`: tap
  (`cancelCountdownToWaiting()`).
- `SCROLLING` → `CONTROLS`: tap (`exitFullScreenToControls()`).
- `SCROLLING` → `WAITING_FOR_TAP` (with `atScriptEnd = true`): reaching
  the end of the script during a scroll tick
  (`stopScrollingAtEnd()`), not tap-driven.
- Any state → `CONTROLS`: `onPause()`, Exit button, Android Back when
  `uiState != CONTROLS`.

`previewScrolling` is a completely separate boolean; it is never read or
written by any of the `UiState` transition methods above, and none of
its own start/stop methods change `uiState`.

## 19. Full-screen scrolling and tap behavior

- `Handler`/`Runnable`: `scrollRunnable` (full-screen scroll ticks),
  `countdownRunnable`, and `previewScrollRunnable` all post to the same
  `scrollHandler`; only one of the three is ever actively scheduled for
  the full-screen/countdown pair at a time (they share
  `removeCallbacksAndMessages(null)` cleanup).
- Speed calculation: `computeScrollPixelsPerTick()` (section 15),
  invoked every `SCROLL_INTERVAL_MS` (50ms) via `performScrollTick()`.
- Start: `beginActiveScrolling()`, after the countdown reaches 0.
- Pause/tap-to-pause: a tap during `SCROLLING` calls
  `exitFullScreenToControls()` directly — there is no intermediate
  "paused, still full-screen" scrolling state; pausing and returning to
  controls are the same action.
- Resume: re-entering is only via Enter Prompter again, which always
  restarts from the top (section 17) — there is no "resume scrolling
  from where it paused" full-screen resume path. Returning to the
  controls-screen preview does preserve scroll position (see section 21).
- Return to controls: `exitFullScreenToControls()` — cancels pending
  callbacks, stops preview scrolling defensively, disables keep-screen-on,
  sets `uiState = CONTROLS`, refreshes visibility, and applies the
  minimal-padding switch with position preservation
  (`switchTopPaddingPreservingPosition`).
- Lifecycle cleanup: `onPause()` always calls
  `exitFullScreenToControls()` then `savePreferences()`; `onStop()` and
  `onDestroy()` both additionally call
  `scrollHandler.removeCallbacksAndMessages(null)` directly.
- Keep-screen-awake: `scriptText.setKeepScreenOn(true)` during
  `COUNTING_DOWN` and `SCROLLING` (and during preview scrolling,
  section 14), `false` at every other transition into those states'
  opposite.

## 20. End-of-script behavior

- Stopping at the end: `performScrollTick()` detects `maxScroll <= 0`
  (nothing to scroll) or that the next tick would reach/exceed
  `maxScroll`, snaps to exactly `maxScroll`, and calls
  `stopScrollingAtEnd()`.
- `atScriptEnd` behavior: set `true` by `stopScrollingAtEnd()`; reset to
  `false` by `enterFullScreenWaitingFromControls()` (every fresh Enter
  Prompter) and by `loadScriptFile()`/`applyNoScriptState()`.
- Tap to Start suppression: `refreshStateVisibility()`'s
  `showTapInstruction = uiState == WAITING_FOR_TAP && !atScriptEnd` — at
  the end of the script, `WAITING_FOR_TAP` is active but "Tap to Start"
  is not shown.
- Tapping at the end: `handleScriptTap()`'s `WAITING_FOR_TAP` case checks
  `atScriptEnd` — if true, goes straight to
  `exitFullScreenToControls()` instead of starting a countdown.
- Re-entering Prompter mode: only via Enter Prompter from the controls
  screen, which unconditionally resets `atScriptEnd = false` and scrolls
  to the top (section 17) — there is no "continue from the end" or
  "loop" behavior.

## 21. Preview versus full-screen padding

- Minimal preview/control top padding: `SCRIPT_PADDING_DP = 24f`, applied
  uniformly on all four sides whenever `uiState == CONTROLS`.
- Full-screen opening padding: `OPENING_SPACE_FRACTION = 0.775f` of the
  full-screen viewport height, floored at `SCRIPT_PADDING_DP`, computed
  and applied only inside `enterFullScreenWaitingFromControls()`.
- Centralized switching: `switchTopPaddingPreservingPosition()` is the
  single method used to change `scriptText`'s top padding while
  preserving apparent scroll position — but it is used **only** by
  `exitFullScreenToControls()` (returning to controls), never by
  `enterFullScreenWaitingFromControls()`.
- Scroll-position adjustment: if `scrollView.getScrollY() > 0` when the
  padding changes, the new scroll position is `previousScrollY +
  (newTopPaddingPx - oldTopPaddingPx)`, clamped to
  `[0, contentHeight - viewportHeight]`, applied after the padding
  change has been laid out (`scrollView.post(...)`). If already at
  scroll position 0, no adjustment is made — the new padding alone
  produces the correct look.
- Intentional reset when Enter Prompter is pressed:
  `enterFullScreenWaitingFromControls()` always forces
  `scrollTo(0, 0)` regardless of where the preview or a previous
  full-screen session left off (section 17) — this is a deliberate
  bypass of position preservation, not an oversight.
- Preservation when returning to preview after pausing: a tap during
  `SCROLLING` (or `WAITING_FOR_TAP`/`COUNTING_DOWN`) that calls
  `exitFullScreenToControls()` **does** go through
  `switchTopPaddingPreservingPosition()`, so the controls-screen preview
  lands on approximately the same passage of text that was on screen in
  full-screen mode.

## 22. Exit and Android Back behavior

- Exit (`btnExit`): `exitFullScreenToControls()` then `finish()` —
  always leaves the app from a clean controls-screen state first.
- Back in full-screen states (`uiState != CONTROLS`):
  `exitFullScreenToControls()` only — returns to controls, does **not**
  finish the Activity.
- Back in control state (`uiState == CONTROLS`): `finish()` — closes the
  app.
- Editor Back (`ScriptEditorActivity.onBackPressed()`):
  `handleCancelOrBack()` — dirty-check + Discard Changes dialog, same as
  the Cancel button (section 12).
- File browser Back: navigates up one directory if not already at the
  browser's start location or a detected storage root; otherwise
  `setResult(RESULT_CANCELED)` and closes.
- Callback cleanup: every path back to `CONTROLS` or out of the app goes
  through `exitFullScreenToControls()` (or the lifecycle methods'
  explicit `scrollHandler.removeCallbacksAndMessages(null)`), so no
  scroll/countdown/preview callback can keep firing after leaving the
  relevant screen.
- Preference preservation: `onPause()` calls `savePreferences()`
  unconditionally; nothing in the Back/Exit paths clears any saved
  preference.

## 23. SharedPreferences and persisted state

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

`savePreferences()` uses `SharedPreferences.Editor.commit()`
(synchronous), not `apply()`.

## 24. Build process

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

## 25. Hardware testing

### Physically tested on Lenovo Smart Tab M8

Reported by the user, based on running built APKs on that device:

- The fullscreen white keyboard "extract editor" previously appeared
  when the soft keyboard opened in the Script Editor; the
  `flagNoExtractUi` fix (present in current source, section 11) was
  confirmed by the user to prevent it.
- Before the compact-editing-mode fix existed, opening the keyboard in
  landscape in the Script Editor was confirmed to compress
  `scriptBodyField` to zero usable height, motivating the compact-mode
  work described in section 11.

**Not yet confirmed on hardware**: the compact-editing-mode mechanism
itself (title/filename collapse, "Edit Filename" control, the raw
`KEYCODE_ENTER` fallback, `requestRectangleOnScreen()`'s actual visual
effect) has not been reported as tested since it was added. Treat it as
implemented-and-compiling, not as hardware-verified.

### Acer Iconia Tab A500

No physical testing on an actual Acer Iconia Tab A500 has occurred
anywhere in this project's history. All A500-specific guidance
(`README.md`'s troubleshooting section, the original target-hardware
framing) is written for that device but remains unverified against it.
Everything not explicitly listed above as Lenovo-tested should be
considered to require A500 (or equivalent API 14 device/emulator)
testing, including: touch behavior of the tap-to-show/hide gesture over
the auto-scrolling `ScrollView`, real-world auto-scroll smoothness on
weaker hardware, the exact on-device rendering of the launcher icon and
button-background drawables, and whether the A500's microSD mount path
is among the legacy paths already checked (section 6).

### Modern Android

`targetSdkVersion 14` is far below what current Android versions expect;
this is expected to produce install-time warnings or restrictions on
modern devices, but the specifics have not been characterized or tested
in this project. This project is not redefined as a modern-Android
application by that fact — its compatibility target remains API 14.

## 26. Artwork and launcher icons

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

## 27. Known limitations and future ideas

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

## 28. Change and release discipline

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

## Discrepancies found while documenting

- `README.md`'s "Only permission requested: `READ_EXTERNAL_STORAGE`"
  (Compatibility notes section) is out of date: the current manifest
  also declares `WRITE_EXTERNAL_STORAGE`, required by the New
  Script/Edit Script save functionality added after that README section
  was last written. `README.md` was not modified as part of this
  documentation task (out of scope — source-code/resource changes were
  explicitly excluded), but a future task should update it.
- `README.md`'s Features list ("Start / Pause / Return to Beginning
  controls") describes an earlier control scheme than what current
  source implements (`btn_start` = "Enter Prompter", no separate
  Pause/Return-to-Beginning controls; tapping the script area serves the
  pause/resume/return role instead, per sections 17–20 above). Same
  out-of-scope note as above.
- No other discrepancies between the current source and this
  documentation were found; every behavior described above was
  confirmed directly against `app/src/main/java/...`,
  `app/src/main/res/...`, `AndroidManifest.xml`, `app/build.gradle`, and
  `build.sh` as checked out at commit `66d459f`, and, where noted, by
  inspecting the compiled APK's resolved attributes rather than the XML
  source alone.
