# CLAUDE.md

Persistent rules for Claude Code sessions working on this repository. Read
this before making any change. Before implementing or changing product
behavior, also read `PROJECT_SPEC.md`. Treat the current source code as the
final authority if this documentation and the implementation ever differ;
report the discrepancy before proceeding.

## Project identity

- Repository: `peteyagmin/A500-Teleprompter`
- Current development branch: `claude/android-teleprompter-app-1wfqsr`
- Current release name: **Droid Prompter** (`app_name` in `strings.xml`;
  renamed from the earlier working title "A500 Teleprompter"), package
  `com.a500teleprompter.app`. The repository name, branch name, and Java
  package are unrelated identifiers and were not renamed along with the
  app name — do not assume they track it.
- The name may change again; the app runs on Android tablets other than
  the Acer A500.
- Artwork under `artwork/` (`DroidPrompt-bg.png`, `DroidPrompt-trans.png`)
  is editable source material; `DroidPrompt-trans.png` is also copied
  (unmodified, only renamed to a valid resource identifier) to
  `app/src/main/res/drawable-nodpi/droid_prompter_logo.png`, referenced
  by the Settings screen's branding panel (`@drawable/
  droid_prompter_logo`) — the first Android resource to reference either
  artwork file. "DroidPrompt" (the artwork filenames, one word) and
  "Droid Prompter" (the app name, two words) are related but distinct
  strings; don't conflate them. The packaged launcher icon is unrelated
  and still lives under `app/src/main/res/mipmap-*/` and
  `drawable-nodpi/ic_launcher_foreground.png`.
- Licensed under GPL-3.0 (`LICENSE` at the repo root); the Settings
  screen's branding panel displays the same license notice.
- Purpose: a lightweight, offline teleprompter designed primarily to give
  older Android tablets a useful second life.
- Original target hardware: Acer Iconia Tab A500 running Android 4.0.
- Physically tested devices: Lenovo Smart Tab M8 (Android 10/API29), an ASUS Nexus 7 (2013,
  second generation, Android 6.0.1/API 23), and the Acer Iconia Tab A500
  itself — see `PROJECT_SPEC.md` section 26 for exactly what has and has
  not been confirmed on each. The "designed for an older Android
  version" first-launch warning is device/version-dependent: observed on
  the Lenovo, not on the Nexus 7. Don't generalize any one device's
  behavior to another.
- Physical Acer A500 testing has occurred (see `PROJECT_SPEC.md` section
  26) — don't claim more was confirmed on it than that section states,
  and don't claim any *future* A500 testing happened without it actually
  occurring again.

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
- `KEY_MIRROR` (Mirror enabled) and `KEY_MIRROR_MODE` are separate
  preferences — never conflate them. Mirror Mode defaults to Flip.
  "Flip and Rotate" means the existing horizontal mirror plus an
  absolute 180-degree rotation (`setRotation(180f)`), not a differently
  named vertical-flip mode (`scaleY = -1f`), even where the two would be
  mathematically equivalent. Presenter-facing content (`scriptText`,
  `tapInstructionText`, `countdownText`) always transforms together,
  identically, via the one centralized `applyMirror()`. Operator-facing
  controls and every menu (main controls, Settings, file browser, Script
  Editor, the pause menu, the end-of-script menu) remain upright and
  unmirrored regardless of Mirror or Mirror Mode. Mirror Off always
  resets all three presenter-facing views' `scaleX`/`scaleY`/`rotation`
  to the identity transform (`1f`/`1f`/`0f`), regardless of the saved
  Mirror Mode.
- Flip and Rotate's 180-degree rotation preserves the visual transform
  exactly (never redefine it to fix a scrolling bug) but requires
  **reverse logical scrolling**, active only when `isReverseScrollMode()`
  (`mirrorEnabled && MIRROR_MODE_FLIP_ROTATE.equals(mirrorMode)`) is
  true: because the rotation pivots about `scriptText`'s own center while
  `ScrollView` clips to an untransformed `scrollY` window, `scrollY = 0`
  renders the *end* of the script and `scrollY = maxScroll` renders the
  *beginning* under Flip and Rotate — the mirror image of Mirror
  Off/Flip. `getScriptStartScrollY()`/`getScriptEndScrollY()`/
  `moveTowardScriptEnd()`/`hasReachedScriptEnd()` are the sole place this
  is ever handled; no other method may compare `mirrorEnabled`/
  `mirrorMode` directly. Preview, Start Prompter/Restart, Resume, and
  end-detection (both Preview and full-screen) must all honor whichever
  direction is currently active — visual beginning is always `0`
  (Mirror Off/Flip) or `maxScroll` (Flip and Rotate); visual end is the
  opposite. A Mirror or Mirror Mode change that flips the effective
  direction while a script is loaded in `CONTROLS` must remap the
  preview's scroll progress to the equivalent position in the new
  direction (`remapScrollPositionForMirrorModeChange()`) rather than
  leaving it at the wrong end. Operator-facing controls stay upright and
  untransformed regardless of scroll direction.
- Flip and Rotate must clear the centered Tap to Start overlay with a
  visible, non-overlapping gap below it, at an opening position that
  should feel comparable to (or, given the choice, slightly lower than)
  normal and Flip modes' own ~77.5%-down opening; the final line settles
  at the same ~60%-down position as normal/Flip. In reverse mode, **top**
  padding governs the *opening* position and **bottom** padding governs
  the *end* position — physically confirmed, and structurally forced:
  the reverse start is `scrollY == maxScroll`, and `maxScroll =
  scriptTextHeight - viewportHeight`, so any change to bottom padding
  changes both terms by the same amount and cancels out of the opening
  position entirely (raising the reverse-mode bottom-padding fraction
  from 0.775 to 0.95 was physically tested and produced **no visible
  change** in the opening position at all). Do not assume bottom padding
  affects the reverse-mode opening, or that top padding affects the
  reverse-mode end — that "sides exchanged" framing was tried and
  disproved. Within reverse-mode top padding, **increasing** it moves
  the buffer to the top of the screen and the first line lower
  (matching normal/Flip); **decreasing** it moves the buffer to the
  bottom and the first line higher, clearing it above Tap to Start on
  the wrong side — this specific direction was also tried backwards once
  and physically disproved, so do not assume "less top padding = first
  line further down" without new physical confirmation. In
  `applyFullScreenScriptPadding()`, reverse-mode top padding is
  `REVERSE_START_TOP_FRACTION` (`0.60f`, a separately calibrated
  constant, not derived from `OPENING_SPACE_FRACTION`), and reverse-mode
  bottom padding is `(1 - END_SPACE_FRACTION)` = `0.6`.
  `OPENING_SPACE_FRACTION` (`0.775f`) and `END_SPACE_FRACTION` (`0.4f`)
  are unchanged and continue to govern only Mirror Off/Flip's own
  padding. This geometry was revised five times on physical-testing
  evidence (complement formula → sides-exchanged mapping →
  `REVERSE_OPENING_SPACE_FRACTION` calibration → top/bottom model with
  wrong direction → corrected direction), and `REVERSE_START_TOP_FRACTION
  = 0.60f` has since been physically confirmed correct for the core
  opening geometry (clears the Tap to Start overlay, comparable position
  to Mirror Off/Flip) — but not against the full combination matrix
  (both orientations, font-size extremes, script lengths, both
  alignments, Restart specifically), so still verify those independently
  before assuming they're covered, and treat any future physically
  observed on-screen result as authoritative over this value if it
  disagrees. Reverse mode's target scroll position
  (`maxScroll`) is also measurement-dependent in a way Mirror Off/Flip's
  `scrollTo(0, 0)` never is, so always reach it through
  `scrollToScriptStartReliably()` (not a direct `scrollTo()`), which
  self-corrects once if the padding-driven re-layout hadn't settled by
  the first measurement. This is a separate concern from which side
  governs which position — fixing one does not fix the other, as this
  project's history shows.
- Preview scrolling (`previewScrolling`) and full-screen scrolling
  (`UiState.SCROLLING`) must remain separate state, even though they
  share one `Handler`.
- Start Prompter (and Restart) always starts from the beginning of the
  loaded script (`scrollTo(0, 0)`, `atScriptEnd = false`), regardless of
  any prior preview or paused position.
- Every new Preview run (each press while `previewScrolling` is false)
  begins from the top of the script, regardless of where a previous
  Preview run stopped — Stop itself still preserves position; only a
  fresh Preview press resets it.
- Automatic scrolling (Preview and full-screen alike) must remain
  linear, constant-speed, and elapsed-time-based (`SystemClock.
  uptimeMillis()`), preserving fractional sub-pixel movement in a
  per-context accumulator rather than discarding it each tick. No
  physics-based acceleration/deceleration, overscroll, or bounce.
- Pauses, countdowns, and lifecycle interruptions (backgrounding,
  Settings, another Activity) must reset the relevant scrolling timing
  baseline before the next tick, so elapsed time spent outside active
  scrolling is never counted as scrolling time and cannot produce a
  large jump. See `PROJECT_SPEC.md` section 34.
- Control-screen preview uses minimal, uniform top padding
  (`SCRIPT_PADDING_DP`).
- Full-screen Prompter mode uses the existing 77.5% opening buffer
  (`OPENING_SPACE_FRACTION`), applied only on entry, never preserved by
  `switchScriptPaddingPreservingPosition()`.
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
- Full-screen scrolling must continue until the final script line
  reaches its trailing-space resting position (currently `END_SPACE_
  FRACTION = 0.4f`, a bit below the vertical center of the display).
  Only then may the app enter `END_OF_SCRIPT_WAITING`.
- Full-screen Prompter states (`WAITING_FOR_TAP`, `COUNTING_DOWN`,
  `SCROLLING`, `PAUSED_FULLSCREEN`, `END_OF_SCRIPT_WAITING`) must hide
  the Android status bar and navigation bar; `CONTROLS`,
  `FileBrowserActivity`, and `ScriptEditorActivity` must show normal
  system bars. Use `uiState` as the sole source of truth for this — no
  second fullscreen boolean — and keep all system-UI flag manipulation
  inside the dedicated helper methods (`enterPrompterSystemUi()` /
  `exitPrompterSystemUi()` / `reapplyPrompterSystemUiIfNeeded()`), never
  scattered across unrelated methods. Keep this separate from
  keep-screen-awake handling — they are different concerns.
- Full-screen padding (`OPENING_SPACE_FRACTION`/`END_SPACE_FRACTION`)
  must be measured from `scrollView.getHeight()` only after the system
  bars are hidden and that layout change has settled — hiding the bars
  changes the usable viewport height.
- The Settings gear (`btnSettings`) sits at the far right of the Font
  Size/Speed adjustment row, behind a second divider matching the first.
  It must stay enabled regardless of whether a script is loaded, and
  must never be mirrored.
- `MainActivity`, `SettingsActivity`, `FileBrowserActivity`, and
  `ScriptEditorActivity` all remain landscape. Prompter Orientation
  (Horizontal/Vertical) affects only full-screen Prompter mode
  (`WAITING_FOR_TAP` through `END_OF_SCRIPT_WAITING`), never `CONTROLS`
  or any other Activity — never follow the physical accelerometer.
- Vertical Prompter mode's full-screen padding must be measured only
  after both the portrait orientation switch and the hidden-system-bar
  layout have settled — the same "measure after settling" rule as
  system-bar padding above, just with orientation added to what must
  settle first. See `PendingTransition` in `PROJECT_SPEC.md` section 32
  for the mechanism.
- Text Alignment (`applyTextAlignment()`) touches only `scriptText`'s
  gravity, in both the control-screen preview and every full-screen
  Prompter state — never Tap to Start, the countdown, the pause/end
  menus, main controls, the file browser, or the Script Editor.
- Load Last Script on Launch only ever applies in `onCreate()`'s
  `savedInstanceState == null` branch (a true fresh launch) — never on
  Activity recreation, including the orientation-driven kind above.
- Defaults: Text Alignment = Left, Prompter Orientation = Horizontal,
  Load Last Script on Launch = On. Existing installations must keep
  their current behavior with no new preference ever set.

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
- A Settings screen (`SettingsActivity`) is implemented, opened via the
  main screen's gear icon (`btnSettings`), with four settings: Text
  Alignment, Prompter Orientation, Mirror Mode, and Load Last Script on
  Launch (keys `KEY_TEXT_ALIGNMENT`/`KEY_PROMPTER_ORIENTATION`/
  `KEY_MIRROR_MODE`/`KEY_LOAD_LAST_SCRIPT` in `AppConstants`). Every
  control saves immediately; there is no Settings-screen Save button,
  only Done. The screen is split left (the settings, in a `ScrollView`)
  / right (a branding panel: logo, app name, copyright, GPL-3.0 license
  notice — non-interactive). Mirror Mode's `RadioGroup` is horizontal
  (matching Text Alignment/Prompter Orientation), not vertical, and has
  no explanatory text under either option — both were removed. See
  `PROJECT_SPEC.md` sections 30–33, 35.
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
   pipeline — see `PROJECT_SPEC.md` section 25).
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
