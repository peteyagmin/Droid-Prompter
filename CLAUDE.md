# CLAUDE.md

Persistent rules for Claude Code sessions working on this repository. Read
this before making any change. Before implementing or changing product
behavior, also read `PROJECT_SPEC.md`. Treat the current source code as the
final authority if this documentation and the implementation ever differ;
report the discrepancy before proceeding.

## Project identity

- Repository: `peteyagmin/A500-Teleprompter`
- Current development branch: `claude/android-teleprompter-app-1wfqsr`
- Current release name: **A500 Teleprompter** (`app_name` in
  `strings.xml`, package `com.a500teleprompter.app`)
- The name is a working title and may change; the app runs on Android
  tablets other than the Acer A500.
- Artwork under `artwork/` (`DroidPrompt-bg.png`, `DroidPrompt-trans.png`)
  is source material only. It is **not** referenced by any Android
  resource and does not establish "DroidPrompt" as the app name. The
  packaged launcher icon lives under `app/src/main/res/mipmap-*/` and
  `drawable-nodpi/ic_launcher_foreground.png`.
- Purpose: a lightweight, offline teleprompter designed primarily to give
  older Android tablets a useful second life.
- Original target hardware: Acer Iconia Tab A500 running Android 4.0.
- Physically tested devices: Lenovo Smart Tab M8 and an ASUS Nexus 7
  (2013, second generation, Android 6.0.1/API 23) — see `PROJECT_SPEC.md`
  section 25 for exactly what has and has not been confirmed on each.
  The "designed for an older Android version" first-launch warning is
  device/version-dependent: observed on the Lenovo, not on the Nexus 7.
  Don't generalize either device's behavior to other devices.
- Do not claim physical Acer A500 testing unless it has actually occurred.

## Mandatory compatibility requirements

- Keep `minSdkVersion` at 14.
- Keep `targetSdkVersion` at 14.
- Java only. Traditional Android XML Views only.
- No AndroidX, Kotlin, Jetpack Compose, Material Components,
  ConstraintLayout, or external libraries.
- No lambdas, no coroutines, no Storage Access Framework.
- No `INTERNET` permission.
- Preserve `READ_EXTERNAL_STORAGE` and `WRITE_EXTERNAL_STORAGE`.
- Preserve v1 APK signing (required for Android 4.0); v2 is also enabled
  and must stay enabled alongside it, not in place of it.
- Preserve the existing custom offline build process (`build.sh`).
- Check every new Android API against API level 14 before using it.

## Mandatory product and data-safety rules

- The app must work fully offline.
- The app must not require another file-manager application.
- Do not introduce behavior that risks losing a user's script.
- Preserve filename validation, empty-script validation, overwrite
  confirmation, and atomic temporary-file saving.
- Preserve `IOException`/`SecurityException` handling around file I/O.
- Preserve unsaved-changes protection (baseline-snapshot dirty tracking +
  Discard Changes dialog) on Cancel and Android Back alike.
- Preserve UTF-8 saving (script files are always written as UTF-8, even
  if the original file was read via the ISO-8859-1 fallback).
- Preserve editor state (text, selection, compact-mode, and focus target)
  through orientation changes.
- Script text, the countdown, and Tap to Start must mirror together via
  `applyMirror()`. Controls, buttons, the editor UI, and the file browser
  must remain unmirrored.
- Preview scrolling (`previewScrolling`) and full-screen scrolling
  (`UiState.SCROLLING`) must remain separate state, even though they
  share one `Handler`.
- Start Prompter (and Restart) always starts from the beginning of the
  loaded script (`scrollTo(0, 0)`, `atScriptEnd = false`), regardless of
  any prior preview or paused position.
- Control-screen preview uses minimal, uniform top padding
  (`SCRIPT_PADDING_DP`).
- Full-screen Prompter mode uses the existing 77.5% opening buffer
  (`OPENING_SPACE_FRACTION`), applied only on entry, never preserved by
  `switchTopPaddingPreservingPosition()`.
- Tapping the script (or Android Back) during `SCROLLING` pauses into
  `PAUSED_FULLSCREEN` and shows the full-screen pause menu (Resume /
  Restart / Back to Controls) — it does not return to the controls
  screen directly. Only `WAITING_FOR_TAP`'s Back behavior still exits
  full-screen mode directly; every other full-screen state's Back
  behavior goes through the pause-menu-aware paths in `onBackPressed()`.
- Resume must run the same five-second countdown as a fresh Start
  Prompter tap, then continue scrolling from the exact paused position
  (not the top) — `beginActiveScrolling()`/`performScrollTick()` must
  never be changed to reset scroll position on their own.
- Reaching the end of a script must leave the final text unobstructed
  until the user taps. Only then should Restart and Back to Controls
  appear. Resume must not appear at the end.

## Current UI facts that must not be inferred incorrectly

- Speed currently uses a `SeekBar` (`speedSeekBar`), range 1–20. There is
  no minus/plus Speed control.
- Font Size uses decrement/increment buttons (`btnFontDecrease` /
  `btnFontIncrease`), range 14sp–96sp in 4sp steps.
- Do not document or implement a Speed minus/plus control unless
  explicitly requested in a future task.
- "Return to Top" does not exist in the current source.
- The main screen's primary button (`btnStart`) is now labeled "Start
  Prompter" (`btn_start`), not "Enter Prompter" — a label-only rename;
  its ID, click listener, and the method it calls
  (`enterFullScreenWaitingFromControls()`) were kept as-is.
- `MainActivity`'s `UiState` enum has six values, including
  `PAUSED_FULLSCREEN` (the full-screen pause menu) and
  `END_OF_SCRIPT_WAITING` (the unobstructed final-text wait before a tap
  reveals the reduced end-of-script menu) — these are deliberately
  distinct states, not one reused for both. See `PROJECT_SPEC.md`
  sections 17–22.
- New Script and Edit Script are implemented (`ScriptEditorActivity`,
  `EDITOR_MODE_CREATE_NEW` / `EDITOR_MODE_EDIT_EXISTING`).
- The Script Editor uses inline keyboard editing (`flagNoExtractUi` on
  both `EditText` fields) rather than the keyboard's fullscreen extract
  editor.
- The Script Editor has a focus-driven "compact mode" that collapses the
  title+filename section when `scriptBodyField` gains focus, with an
  "Edit Filename" control (CREATE_NEW mode only) to bring it back. See
  `PROJECT_SPEC.md` section 11.
- Save and Cancel in the Script Editor share one style,
  `EditorActionButton` (a standalone style, not an alias of
  `SecondaryControlButton`/`ControlButton`), so they're geometrically
  identical — same height, padding, and 16sp text. Do not give either
  button a per-instance override; if they ever need to look different
  again, change `EditorActionButton` itself or split it into two
  editor-specific styles, not ad hoc overrides on one button.
- `editorButtonRow` (the Cancel/Save row) is hidden (`GONE`) while
  `filenameField` has focus in CREATE_NEW mode, and shown once
  `scriptBodyField` has focus (compact mode). Don't assume it's always
  visible when editing the layout or the focus-change listeners.
- Inspect source for exact labels, dimensions, styles, and button
  placement rather than assuming any of the above stays byte-for-byte
  accurate over time.

## Required workflow

- Before implementing product changes, read `PROJECT_SPEC.md`.
- Inspect the current source rather than assuming a previously discussed
  change was implemented — conversation history and uploaded prompts may
  describe proposed behavior that was never actually built.
- Treat repository contents as the source of truth.
- Preserve unrelated user commits.
- Fetch and inspect remote changes before pushing; rebase rather than
  overwriting newer user work when appropriate.
- Build successfully before committing any APK-affecting change.
- Do not commit a broken APK-affecting change.
- For large tasks, save the full requirements in a repository or uploaded
  text file to reduce the risk of prompt truncation, and check the
  uploaded file's actual length before starting — a spec that ends
  mid-sentence has been truncated; ask for the full version rather than
  guessing the rest.
- Audit the completed implementation against the full requirements before
  finalizing.

## Build and verification

After any APK-affecting change:

1. Build the debug APK with `./build.sh` (the existing verified offline
   pipeline — see `PROJECT_SPEC.md` section 24).
2. Verify v1 and v2 signatures (`apksigner verify`).
3. Verify zip alignment (`zipalign -c`).
4. Confirm `minSdkVersion`/`targetSdkVersion` remain 14
   (`aapt dump badging`).
5. Confirm the `INTERNET` permission is absent.
6. Confirm `READ_EXTERNAL_STORAGE` and `WRITE_EXTERNAL_STORAGE` remain
   present.
7. Confirm the custom launcher icon remains packaged.
8. Confirm no prohibited frameworks/dependencies were introduced.
9. Inspect compiled resources or DEX when appropriate (e.g.
   `aapt dump xmltree`) rather than checking source only — source can say
   one thing and the compiled attribute another.
10. Report the APK path, APK size, branch, and final commit hash.
