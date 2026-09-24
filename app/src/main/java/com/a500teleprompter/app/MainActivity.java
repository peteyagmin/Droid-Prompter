package com.a500teleprompter.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import java.io.File;

public class MainActivity extends Activity {

    /**
     * The six teleprompter states. Exactly one tap-driven transition is
     * allowed out of any given state; see {@link #handleScriptTap()}.
     * END_OF_SCRIPT_WAITING is distinct from PAUSED_FULLSCREEN: reaching
     * the end of the script leaves the final text fully visible and
     * unobstructed with no overlay, and only becomes PAUSED_FULLSCREEN
     * (showing the reduced Restart/Back to Controls menu) once the user
     * taps -- see stopScrollingAtEnd() and showEndOfScriptMenu().
     */
    private enum UiState {
        CONTROLS,
        WAITING_FOR_TAP,
        COUNTING_DOWN,
        SCROLLING,
        PAUSED_FULLSCREEN,
        END_OF_SCRIPT_WAITING
    }

    /**
     * COUNTING_DOWN is reached from two different places -- the initial
     * Start Prompter/Restart flow (from WAITING_FOR_TAP) and the pause
     * menu's Resume button (from PAUSED_FULLSCREEN) -- and canceling the
     * countdown must return to whichever of those it came from, not
     * always the same state.
     */
    private enum CountdownOrigin {
        INITIAL_START,
        RESUME
    }

    /**
     * Prompter Orientation (Vertical setting) can require switching
     * MainActivity's requested orientation, which may recreate the
     * Activity on API 14 (no orientation|screenSize in configChanges,
     * deliberately -- see resetToWaitingFromTop()/exitFullScreenToControls()
     * doc comments). pendingTransition records which in-flight transition
     * (if any) survives that recreation, via onSaveInstanceState/onCreate,
     * so onResume() can finish the transition exactly once, whether or not
     * a recreation actually happened.
     */
    private enum PendingTransition {
        NONE,
        ENTER_PROMPTER,
        EXIT_TO_CONTROLS
    }

    private static final int REQUEST_CODE_BROWSE_FILE = 100;
    private static final int REQUEST_CODE_EDIT_SCRIPT = 200;

    private static final float MIN_FONT_SIZE_SP = 14f;
    private static final float MAX_FONT_SIZE_SP = 96f;
    private static final float DEFAULT_FONT_SIZE_SP = 32f;
    private static final float FONT_STEP_SP = 4f;

    private static final int MIN_SPEED = 1;
    private static final int MAX_SPEED = 20;
    private static final int DEFAULT_SPEED = 5;

    /**
     * Target scroll-tick interval, ~60 updates/second -- the actual gap
     * between ticks is never assumed to equal this exactly (a Handler
     * makes no such guarantee); it only sets how often movement is
     * recalculated. See performScrollTick()/performPreviewScrollTick(),
     * which measure real elapsed time via SystemClock.uptimeMillis() and
     * are correct regardless of how closely ticks track this target.
     */
    private static final long SCROLL_INTERVAL_MS = 16;

    /**
     * Preserves the pre-existing nominal scroll rate: the old tick-based
     * formula moved (scrollSpeed * 0.5) dp per assumed-50ms tick, i.e.
     * scrollSpeed * 0.5 * (1000 / 50) = scrollSpeed * 10 dp/second. This
     * constant is that same dp/second-per-Speed-unit figure, now driving
     * an elapsed-time-based calculation instead of a fixed per-tick
     * distance -- see computeScrollPixelsPerSecond().
     */
    private static final float SCROLL_DP_PER_SECOND_PER_SPEED_UNIT = 10f;

    private static final int COUNTDOWN_START_VALUE = 5;
    private static final long COUNTDOWN_INTERVAL_MS = 1000;

    private static final float OPENING_SPACE_FRACTION = 0.775f;
    private static final float END_SPACE_FRACTION = 0.4f;

    /**
     * The visual opening space at Flip and Rotate's reverse start position
     * (scrollY == maxScroll) is governed by scriptText's logical *top*
     * padding, not bottom padding -- the opposite of what an earlier
     * version of this file assumed. Physical testing disproved that
     * assumption directly: raising reverse-mode bottom padding from
     * 0.775x to 0.95x the viewport height produced no visible change in
     * the opening position at all. The reason is structural, not just
     * empirical: at scrollY == maxScroll == scriptTextHeight -
     * viewportHeight, any increase to bottom padding increases
     * scriptTextHeight and maxScroll by the exact same amount, so the two
     * changes cancel and the visible opening position is left unmoved --
     * only top padding, which maxScroll's own definition does not net
     * out, can move it. (Bottom padding still matters: it governs the
     * reverse visual *end*, at scrollY == 0, where no such cancellation
     * occurs -- see applyFullScreenScriptPadding()'s doc comment.)
     *
     * This fraction is deliberately smaller than the value that would
     * place the first line at exactly the same ~77.5%-down height Mirror
     * Off/Flip use (which would be 1 - OPENING_SPACE_FRACTION = 0.225,
     * given the relationship above) -- physical testing of that exact
     * value, before Flip and Rotate's padding sides were exchanged, still
     * placed the line too high (round one of this feature's calibration).
     * Given a choice, the line beginning slightly further down than an
     * exact match is preferred over risking it landing behind the
     * centered Tap to Start overlay again.
     */
    private static final float REVERSE_START_TOP_FRACTION = 0.15f;
    private static final float SCRIPT_PADDING_DP = 24f;

    private static final String STATE_SCRIPT_TEXT = "state_script_text";
    private static final String STATE_SCROLL_Y = "state_scroll_y";
    private static final String STATE_LOADED_FILE_PATH = "state_loaded_file_path";
    private static final String STATE_PENDING_TRANSITION = "state_pending_transition";

    private View controlsPanel;
    private ScrollView scrollView;
    private TextView scriptText;
    private TextView countdownText;
    private TextView tapInstructionText;
    private View pauseMenuOverlay;
    private Button btnResume;
    private TextView currentFileLabel;
    private TextView fontSizeLabel;
    private TextView speedLabel;
    private SeekBar speedSeekBar;
    private ToggleButton mirrorToggle;
    private Button btnPreview;
    private Button btnStart;
    private Button btnEditScript;
    private ImageButton btnSettings;

    private SharedPreferences prefs;
    private final Handler scrollHandler = new Handler();
    private Runnable scrollRunnable;
    private Runnable countdownRunnable;
    private Runnable previewScrollRunnable;
    private GestureDetector tapDetector;

    private UiState uiState = UiState.CONTROLS;
    private CountdownOrigin countdownOrigin = CountdownOrigin.INITIAL_START;
    private boolean atScriptEnd = false;
    private boolean previewScrolling = false;
    private int countdownRemaining;
    private int scrollSpeed = DEFAULT_SPEED;
    private float fontSizeSp = DEFAULT_FONT_SIZE_SP;
    private boolean mirrorEnabled = false;
    private String loadedFilePath = null;
    private int pendingRestoreScrollY = -1;
    private String textAlignment = AppConstants.TEXT_ALIGNMENT_LEFT;
    private boolean prompterOrientationVertical = false;
    private String mirrorMode = AppConstants.MIRROR_MODE_FLIP;
    private boolean loadLastScriptOnLaunch = true;
    private PendingTransition pendingTransition = PendingTransition.NONE;

    /**
     * Separate fractional-pixel carry and elapsed-time baseline per
     * scrolling context (control-screen Preview vs. full-screen Prompter),
     * since the two can be active at different times with completely
     * independent histories -- see computeScrollPixelsPerSecond(),
     * performScrollTick(), performPreviewScrollTick().
     */
    private float fullScreenScrollRemainderPx = 0f;
    private long fullScreenScrollLastTickUptimeMs;
    private float previewScrollRemainderPx = 0f;
    private long previewScrollLastTickUptimeMs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences(AppConstants.PREFS_NAME, MODE_PRIVATE);

        controlsPanel = findViewById(R.id.controlsPanel);
        scrollView = (ScrollView) findViewById(R.id.scrollView);
        scriptText = (TextView) findViewById(R.id.scriptText);
        countdownText = (TextView) findViewById(R.id.countdownText);
        tapInstructionText = (TextView) findViewById(R.id.tapInstructionText);
        pauseMenuOverlay = findViewById(R.id.pauseMenuOverlay);
        btnResume = (Button) findViewById(R.id.btnResume);
        currentFileLabel = (TextView) findViewById(R.id.currentFileLabel);
        fontSizeLabel = (TextView) findViewById(R.id.fontSizeLabel);
        speedLabel = (TextView) findViewById(R.id.speedLabel);
        speedSeekBar = (SeekBar) findViewById(R.id.speedSeekBar);
        mirrorToggle = (ToggleButton) findViewById(R.id.mirrorToggle);
        btnPreview = (Button) findViewById(R.id.btnPreview);
        btnStart = (Button) findViewById(R.id.btnStart);
        btnEditScript = (Button) findViewById(R.id.btnEditScript);
        btnSettings = (ImageButton) findViewById(R.id.btnSettings);

        Button btnOpenFile = (Button) findViewById(R.id.btnOpenFile);
        Button btnNewScript = (Button) findViewById(R.id.btnNewScript);
        Button btnFontDecrease = (Button) findViewById(R.id.btnFontDecrease);
        Button btnFontIncrease = (Button) findViewById(R.id.btnFontIncrease);
        Button btnExit = (Button) findViewById(R.id.btnExit);
        Button btnRestart = (Button) findViewById(R.id.btnRestart);
        Button btnBackToControls = (Button) findViewById(R.id.btnBackToControls);

        loadPreferences();
        applyFontSize();
        applyTextAlignment();
        applyMirror();
        mirrorToggle.setChecked(mirrorEnabled);

        speedSeekBar.setMax(MAX_SPEED - MIN_SPEED);
        speedSeekBar.setProgress(scrollSpeed - MIN_SPEED);
        updateSpeedLabel();

        if (savedInstanceState != null) {
            int pendingOrdinal = savedInstanceState.getInt(STATE_PENDING_TRANSITION,
                    PendingTransition.NONE.ordinal());
            pendingTransition = PendingTransition.values()[pendingOrdinal];
            if (pendingTransition != PendingTransition.NONE) {
                // A Vertical-orientation-triggered recreation is either
                // mid-entry or mid-exit: hide the main controls before the
                // very first frame draws, so the user never sees a flash of
                // the control screen in the wrong orientation before the
                // matching continuation (see onResume()) puts things right.
                controlsPanel.setVisibility(View.GONE);
            }
            CharSequence savedText = savedInstanceState.getCharSequence(STATE_SCRIPT_TEXT);
            if (savedText != null) {
                scriptText.setText(savedText);
            }
            loadedFilePath = savedInstanceState.getString(STATE_LOADED_FILE_PATH);
            pendingRestoreScrollY = savedInstanceState.getInt(STATE_SCROLL_Y, -1);
            updateLoadedFileLabel();
        } else if (loadedFilePath != null && loadLastScriptOnLaunch) {
            // loadScriptFile() only re-sets loadedFilePath on success; if the
            // remembered file no longer exists or can't be read, it must not
            // be left pointing at a path whose content was never actually
            // loaded into scriptText.
            String pathToRestore = loadedFilePath;
            loadedFilePath = null;
            loadScriptFile(pathToRestore);
        } else {
            // Either no remembered script, or Load Last Script on Launch is
            // Off: start in the "no script loaded" state without touching
            // the stored preference -- loadPreferences() may have already
            // populated loadedFilePath in memory from SharedPreferences, but
            // savePreferences() never writes a null path back, so clearing
            // it here in memory only leaves the remembered file untouched
            // for next time (or for the setting being switched back On).
            loadedFilePath = null;
            updateLoadedFileLabel();
        }
        updateLoadedScriptDependentControls();

        scrollRunnable = new Runnable() {
            @Override
            public void run() {
                performScrollTick();
            }
        };
        countdownRunnable = new Runnable() {
            @Override
            public void run() {
                performCountdownTick();
            }
        };
        previewScrollRunnable = new Runnable() {
            @Override
            public void run() {
                performPreviewScrollTick();
            }
        };

        tapDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                handleScriptTap();
                return true;
            }
        });
        scrollView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                tapDetector.onTouchEvent(event);
                return false;
            }
        });

        btnOpenFile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivityForResult(new Intent(MainActivity.this, FileBrowserActivity.class),
                        REQUEST_CODE_BROWSE_FILE);
            }
        });

        btnStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                enterFullScreenWaitingFromControls();
            }
        });

        btnNewScript.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopPreviewScrolling();
                Intent intent = new Intent(MainActivity.this, ScriptEditorActivity.class);
                intent.putExtra(AppConstants.EXTRA_EDITOR_MODE, AppConstants.EDITOR_MODE_CREATE_NEW);
                startActivityForResult(intent, REQUEST_CODE_EDIT_SCRIPT);
            }
        });

        btnEditScript.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (loadedFilePath == null) {
                    Toast.makeText(MainActivity.this, R.string.no_script_to_edit, Toast.LENGTH_SHORT).show();
                    return;
                }
                stopPreviewScrolling();
                Intent intent = new Intent(MainActivity.this, ScriptEditorActivity.class);
                intent.putExtra(AppConstants.EXTRA_EDITOR_MODE, AppConstants.EDITOR_MODE_EDIT_EXISTING);
                intent.putExtra(AppConstants.EXTRA_EDIT_FILE_PATH, loadedFilePath);
                startActivityForResult(intent, REQUEST_CODE_EDIT_SCRIPT);
            }
        });

        btnFontDecrease.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                changeFontSize(-FONT_STEP_SP);
            }
        });

        btnFontIncrease.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                changeFontSize(FONT_STEP_SP);
            }
        });

        btnExit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                exitFullScreenToControls();
                finish();
            }
        });

        btnPreview.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                togglePreviewScrolling();
            }
        });

        btnResume.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startCountdown(CountdownOrigin.RESUME);
            }
        });

        btnRestart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                restartFromPauseMenu();
            }
        });

        btnBackToControls.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                exitFullScreenToControls();
            }
        });

        btnSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Settings is only ever reachable while uiState == CONTROLS
                // (the gear lives on controlsPanel), so this never touches
                // uiState or Prompter mode -- only Preview needs stopping.
                stopPreviewScrolling();
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            }
        });

        mirrorToggle.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(android.widget.CompoundButton buttonView, boolean isChecked) {
                boolean wasReverseScrollMode = isReverseScrollMode();
                mirrorEnabled = isChecked;
                applyMirror();
                remapScrollPositionForMirrorModeChange(wasReverseScrollMode != isReverseScrollMode());
                savePreferences();
            }
        });

        speedSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                scrollSpeed = progress + MIN_SPEED;
                updateSpeedLabel();
                if (fromUser) {
                    savePreferences();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // MainActivity must reread Text Alignment, Prompter Orientation,
        // Mirror Mode, and Load Last Script on Launch after returning from
        // Settings; onResume() already runs on every return from any child
        // Activity (and from the background), so re-reading here -- rather
        // than wiring a dedicated result path just for Settings -- covers
        // that requirement for free.
        boolean wasReverseScrollMode = isReverseScrollMode();
        loadDisplayAndStartupPreferences();
        applyTextAlignment();
        applyMirror();
        if (uiState == UiState.CONTROLS) {
            remapScrollPositionForMirrorModeChange(wasReverseScrollMode != isReverseScrollMode());
        }
        if (pendingTransition == PendingTransition.ENTER_PROMPTER) {
            continueEnterPrompter();
        } else if (pendingTransition == PendingTransition.EXIT_TO_CONTROLS) {
            continueExitToControls();
        } else if (uiState == UiState.CONTROLS) {
            exitPrompterSystemUi();
        } else {
            enterPrompterSystemUi();
        }
        if (pendingRestoreScrollY >= 0) {
            final int restoreY = pendingRestoreScrollY;
            pendingRestoreScrollY = -1;
            scrollView.post(new Runnable() {
                @Override
                public void run() {
                    scrollView.scrollTo(0, restoreY);
                }
            });
        }
    }

    /**
     * Reapplies the hidden-bar flags whenever the window regains focus
     * while a full-screen Prompter state is active -- covering both the
     * app returning from the recent-apps switcher/another app and, on API
     * 14-18, a system dialog or the notification shade briefly stealing
     * focus and reverting the legacy flags. uiState is the only thing
     * consulted (no separate fullscreen boolean): CONTROLS is left alone
     * since normal system bars are already correct there. This never
     * triggers a state transition of its own -- it only re-issues system
     * UI flags for whatever state uiState already holds.
     */
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            reapplyPrompterSystemUiIfNeeded();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (pendingTransition == PendingTransition.NONE) {
            // Not part of our own deliberate orientation transition (see
            // enterFullScreenWaitingFromControls()/exitFullScreenToControls()):
            // this is a genuine backgrounding (Home, another app, screen
            // off, etc.), so always land back on the controls screen so
            // nothing keeps counting down or scrolling while the app is not
            // visible. When pendingTransition is already set, onPause() is
            // firing as part of a transition MainActivity itself started;
            // skipping this call here avoids re-entering it mid-flight and
            // clobbering pendingTransition -- the transition's own
            // continuation (via onResume() after any recreation) finishes
            // the job correctly on its own.
            exitFullScreenToControls();
        }
        savePreferences();
    }

    @Override
    protected void onStop() {
        super.onStop();
        scrollHandler.removeCallbacksAndMessages(null);
    }

    @Override
    protected void onDestroy() {
        scrollHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        switch (uiState) {
            case SCROLLING:
                // Mirrors a script-area tap during SCROLLING: pause into
                // the full-screen pause menu rather than leaving full
                // screen immediately.
                pauseFullScreenScrolling();
                break;
            case PAUSED_FULLSCREEN:
                // Same behavior as pressing Back to Controls.
                exitFullScreenToControls();
                break;
            case END_OF_SCRIPT_WAITING:
                // Goes directly to CONTROLS without first showing the
                // end-of-script menu -- unlike a tap, which reveals it.
                exitFullScreenToControls();
                break;
            case COUNTING_DOWN:
                // cancelCountdown() already returns to WAITING_FOR_TAP or
                // PAUSED_FULLSCREEN based on countdownOrigin.
                cancelCountdown();
                break;
            case WAITING_FOR_TAP:
                exitFullScreenToControls();
                break;
            case CONTROLS:
            default:
                finish();
                break;
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putCharSequence(STATE_SCRIPT_TEXT, scriptText.getText());
        outState.putInt(STATE_SCROLL_Y, scrollView.getScrollY());
        outState.putString(STATE_LOADED_FILE_PATH, loadedFilePath);
        outState.putInt(STATE_PENDING_TRANSITION, pendingTransition.ordinal());
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_BROWSE_FILE && resultCode == RESULT_OK && data != null) {
            String path = data.getStringExtra(AppConstants.EXTRA_FILE_PATH);
            if (path != null) {
                loadScriptFile(path);
            }
        } else if (requestCode == REQUEST_CODE_EDIT_SCRIPT) {
            if (resultCode == RESULT_OK && data != null) {
                String savedPath = data.getStringExtra(AppConstants.EXTRA_SAVED_FILE_PATH);
                boolean wasNew = data.getBooleanExtra(AppConstants.EXTRA_EDITOR_WAS_NEW, false);
                if (savedPath == null) {
                    return;
                }
                if (wasNew) {
                    promptLoadNewlySavedScript(savedPath);
                } else {
                    // Editing the already-loaded script: reload it
                    // automatically, no prompt needed.
                    loadScriptFile(savedPath);
                    Toast.makeText(this, R.string.script_saved_toast, Toast.LENGTH_SHORT).show();
                }
            } else if (data != null && data.getBooleanExtra(AppConstants.EXTRA_EDIT_FILE_UNAVAILABLE, false)) {
                // The file being edited turned out to be unreadable or gone
                // by the time the editor tried to load it -- the loaded
                // script is no longer valid, so fall back to the no-script
                // state rather than leaving a stale filename displayed.
                applyNoScriptState();
            }
        }
    }

    private void promptLoadNewlySavedScript(final String path) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.new_script_saved_title)
                .setMessage(R.string.new_script_saved_message)
                .setCancelable(false)
                .setPositiveButton(R.string.dialog_yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        SharedPreferences.Editor editor = prefs.edit();
                        editor.putString(AppConstants.KEY_LAST_DIRECTORY,
                                StorageLocations.getTeleprompterFolder().getAbsolutePath());
                        editor.commit();
                        loadScriptFile(path);
                    }
                })
                .setNegativeButton(R.string.dialog_no, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        applyNoScriptState();
                    }
                })
                .show();
    }

    /**
     * Centralized "no script loaded" reset, used both when the user declines
     * to load a newly created script and when a previously loaded file turns
     * out to be unavailable. Leaves font size, speed, mirror, last-used
     * directory, and all other saved preferences untouched.
     */
    private void applyNoScriptState() {
        stopPreviewScrolling();
        loadedFilePath = null;
        atScriptEnd = false;
        int sidePaddingPx = dpToPx(SCRIPT_PADDING_DP);
        applyScriptPadding(sidePaddingPx, sidePaddingPx);
        scriptText.setText(R.string.default_script_placeholder);
        updateLoadedFileLabel();
        scrollView.scrollTo(0, 0);
        updateLoadedScriptDependentControls();
    }

    /**
     * Start Prompter, Edit Script, and Preview only make sense once a valid
     * script is loaded; Load Script, New Script, Mirror, and Exit remain
     * available regardless. Called after every change to loadedFilePath so
     * the three buttons can never disagree with whether a script is loaded.
     */
    private void updateLoadedScriptDependentControls() {
        boolean hasScript = loadedFilePath != null;
        btnStart.setEnabled(hasScript);
        btnEditScript.setEnabled(hasScript);
        btnPreview.setEnabled(hasScript);
    }

    private void loadPreferences() {
        fontSizeSp = prefs.getFloat(AppConstants.KEY_FONT_SIZE, DEFAULT_FONT_SIZE_SP);
        scrollSpeed = prefs.getInt(AppConstants.KEY_SCROLL_SPEED, DEFAULT_SPEED);
        mirrorEnabled = prefs.getBoolean(AppConstants.KEY_MIRROR, false);
        loadedFilePath = prefs.getString(AppConstants.KEY_LAST_FILE_PATH, null);
        loadDisplayAndStartupPreferences();

        if (scrollSpeed < MIN_SPEED) {
            scrollSpeed = MIN_SPEED;
        } else if (scrollSpeed > MAX_SPEED) {
            scrollSpeed = MAX_SPEED;
        }
        fontSizeSp = clamp(fontSizeSp, MIN_FONT_SIZE_SP, MAX_FONT_SIZE_SP);
    }

    /**
     * The four Settings-screen preferences, factored out of loadPreferences()
     * so onResume() can reread just these -- MainActivity never writes any of
     * the four itself (only SettingsActivity does, saving immediately on
     * each change), so there is nothing here to reconcile with in-memory
     * state the way font size/speed/mirror-enabled/loadedFilePath need.
     * Mirror Mode (unlike the other three) has no direct visibility effect
     * of its own here -- callers must still call applyMirror() afterward to
     * actually apply it, same as callers of the other three still call
     * applyTextAlignment()/etc. separately.
     */
    private void loadDisplayAndStartupPreferences() {
        textAlignment = prefs.getString(AppConstants.KEY_TEXT_ALIGNMENT, AppConstants.TEXT_ALIGNMENT_LEFT);
        prompterOrientationVertical = AppConstants.PROMPTER_ORIENTATION_VERTICAL.equals(
                prefs.getString(AppConstants.KEY_PROMPTER_ORIENTATION,
                        AppConstants.PROMPTER_ORIENTATION_HORIZONTAL));
        mirrorMode = prefs.getString(AppConstants.KEY_MIRROR_MODE, AppConstants.MIRROR_MODE_FLIP);
        loadLastScriptOnLaunch = prefs.getBoolean(AppConstants.KEY_LOAD_LAST_SCRIPT, true);
    }

    private void savePreferences() {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putFloat(AppConstants.KEY_FONT_SIZE, fontSizeSp);
        editor.putInt(AppConstants.KEY_SCROLL_SPEED, scrollSpeed);
        editor.putBoolean(AppConstants.KEY_MIRROR, mirrorEnabled);
        if (loadedFilePath != null) {
            editor.putString(AppConstants.KEY_LAST_FILE_PATH, loadedFilePath);
        }
        editor.commit();
    }

    private void loadScriptFile(String path) {
        File file = new File(path);
        TextFileReader.Result result = TextFileReader.readTextFile(file);
        if (!result.success) {
            Toast.makeText(this, result.errorMessage, Toast.LENGTH_LONG).show();
            return;
        }
        exitFullScreenToControls();
        atScriptEnd = false;
        scriptText.setText(result.text);
        loadedFilePath = path;
        updateLoadedFileLabel();
        updateLoadedScriptDependentControls();
        savePreferences();
        scrollView.post(new Runnable() {
            @Override
            public void run() {
                scrollView.scrollTo(0, getScriptStartScrollY(computeMaxScroll()));
            }
        });
    }

    /**
     * The single place scriptText's padding is ever set. Left/right always
     * use SCRIPT_PADDING_DP; top and bottom are passed in explicitly so
     * every caller states exactly what it wants for both, rather than
     * leaving one of them implicit (as the old uniform-padding code used
     * to for bottom). Called only from the padding-transition methods
     * below, never scattered elsewhere.
     */
    private void applyScriptPadding(int topPaddingPx, int bottomPaddingPx) {
        int sidePaddingPx = dpToPx(SCRIPT_PADDING_DP);
        scriptText.setPadding(sidePaddingPx, topPaddingPx, sidePaddingPx, bottomPaddingPx);
    }

    /**
     * Full-screen opening/trailing padding (OPENING_SPACE_FRACTION,
     * END_SPACE_FRACTION), aware of Mirror On + Flip and Rotate's reverse
     * logical scrolling (see isReverseScrollMode()). Applies directly via
     * applyScriptPadding() -- the sole caller is resetToWaitingFromTop(),
     * itself the sole full-screen padding entry point for Start Prompter/
     * Restart, so no other call site duplicates this arithmetic.
     *
     * Normal/Flip: top/bottom padding are OPENING_SPACE_FRACTION/
     * END_SPACE_FRACTION of the viewport, exactly as before this feature.
     *
     * Flip and Rotate: physical testing (see REVERSE_START_TOP_FRACTION's
     * doc comment for the full derivation) established that the reverse
     * visual *opening* (scrollY == maxScroll) is governed by scriptText's
     * logical *top* padding, and the reverse visual *end* (scrollY == 0)
     * is governed by logical *bottom* padding -- top padding does not
     * cancel out of maxScroll's own definition, bottom padding does. So,
     * unlike Mirror Off/Flip, reverse mode's top and bottom fractions are
     * calibrated independently rather than reusing OPENING_SPACE_FRACTION/
     * END_SPACE_FRACTION verbatim or as a simple complement of both:
     * reverse-mode top padding is REVERSE_START_TOP_FRACTION (a
     * separately calibrated constant, not derived from
     * OPENING_SPACE_FRACTION), and reverse-mode bottom padding is
     * (1 - END_SPACE_FRACTION) of the viewport -- which does place the
     * final line at the same on-screen height END_SPACE_FRACTION gives
     * Mirror Off/Flip, since bottom padding's effect on the visual end
     * is not subject to the cancellation that makes top padding the
     * opening-controlling side.
     */
    private void applyFullScreenScriptPadding(int fullScreenHeightPx) {
        int sidePaddingPx = dpToPx(SCRIPT_PADDING_DP);
        float topFraction = isReverseScrollMode() ? REVERSE_START_TOP_FRACTION : OPENING_SPACE_FRACTION;
        float bottomFraction = isReverseScrollMode() ? (1.0f - END_SPACE_FRACTION) : END_SPACE_FRACTION;
        int newTopPaddingPx = Math.max(sidePaddingPx, Math.round(fullScreenHeightPx * topFraction));
        int newBottomPaddingPx = Math.max(sidePaddingPx, Math.round(fullScreenHeightPx * bottomFraction));
        applyScriptPadding(newTopPaddingPx, newBottomPaddingPx);
    }

    /**
     * True only for Mirror On + Flip and Rotate (AppConstants.
     * MIRROR_MODE_FLIP_ROTATE) -- the one Mirror Mode whose 180-degree
     * rotation (applyMirror()) makes the rendered content a top/bottom
     * mirror image of scrollY's untransformed layout order (see
     * applyFullScreenScriptPadding()'s doc comment). The sole place this
     * condition is computed; every direction-aware method below calls this
     * rather than comparing mirrorEnabled/mirrorMode directly, so the
     * comparison can never drift out of sync across call sites.
     */
    private boolean isReverseScrollMode() {
        return mirrorEnabled && AppConstants.MIRROR_MODE_FLIP_ROTATE.equals(mirrorMode);
    }

    /**
     * The current maximum valid scrollY, clamped to zero for a script that
     * fits entirely within the viewport. Must only be called once
     * scriptText/scrollView have actually been laid out with their final
     * padding/orientation/system-bar state for the caller's purposes (the
     * existing scrollView.post()-chaining pattern used throughout this
     * class) -- it does not itself wait for layout.
     */
    private int computeMaxScroll() {
        return Math.max(0, scriptText.getHeight() - scrollView.getHeight());
    }

    /**
     * The scrollY that represents the very beginning of the script, for
     * the currently active scroll direction: 0 normally (Mirror Off or
     * Mirror On + Flip), or maxScroll in reverse mode (Mirror On + Flip
     * and Rotate), since reverse mode's 180-degree rotation renders the
     * scrollY-0 end of the content as the visual end, not the beginning --
     * see isReverseScrollMode()'s doc comment.
     */
    private int getScriptStartScrollY(int maxScroll) {
        return isReverseScrollMode() ? maxScroll : 0;
    }

    /** The scrollY that represents the very end of the script -- the mirror image of getScriptStartScrollY(). */
    private int getScriptEndScrollY(int maxScroll) {
        return isReverseScrollMode() ? 0 : maxScroll;
    }

    /**
     * Applies wholePixels of travel (always a non-negative distance, never
     * itself negated -- see performScrollTick()/performPreviewScrollTick())
     * toward the script's visual end, adding it to currentScrollY normally
     * or subtracting it in reverse mode, then clamping to the valid
     * [0, maxScroll] range either way. Only the *direction the travel
     * distance is applied* depends on isReverseScrollMode(); the elapsed-
     * time/fractional-accumulator mechanism that produced wholePixels
     * (section 34) is entirely untouched by this method.
     */
    private int moveTowardScriptEnd(int currentScrollY, int wholePixels, int maxScroll) {
        int proposed = isReverseScrollMode() ? currentScrollY - wholePixels : currentScrollY + wholePixels;
        return Math.min(Math.max(proposed, 0), maxScroll);
    }

    /** Whether scrollY is already exactly at the script's visual end for the currently active scroll direction. */
    private boolean hasReachedScriptEnd(int scrollY, int maxScroll) {
        return scrollY == getScriptEndScrollY(maxScroll);
    }

    /**
     * Remaps the preview's current scrollY to the equivalent position under
     * a newly applied Mirror/Mirror Mode combination, only when the
     * effective scroll direction (isReverseScrollMode()) actually changed
     * -- called from onResume() (a Settings-driven Mirror Mode change) and
     * the main Mirror toggle's listener (an On/Off change that can also
     * flip the effective direction, when the saved Mirror Mode is Flip and
     * Rotate). Maps current progress (0.0 at the old direction's start,
     * 1.0 at its end) onto the equivalent progress under the new direction
     * -- symmetric, so the same formula works for either direction of
     * switch -- rather than leaving the preview sitting at what is now the
     * wrong end. A no-op whenever the direction didn't change, or the
     * script is too short to scroll at all (maxScroll <= 0).
     */
    private void remapScrollPositionForMirrorModeChange(boolean directionChanged) {
        if (!directionChanged) {
            return;
        }
        int maxScroll = computeMaxScroll();
        if (maxScroll <= 0) {
            return;
        }
        int currentScrollY = scrollView.getScrollY();
        float progress = (float) currentScrollY / (float) maxScroll;
        int mappedScrollY = Math.round(maxScroll * (1.0f - progress));
        scrollView.scrollTo(scrollView.getScrollX(), mappedScrollY);
    }

    /**
     * Scrolls to the script's visual beginning (getScriptStartScrollY()),
     * reliably even when the padding change just applied by the caller
     * (applyFullScreenScriptPadding() or the minimal Preview padding)
     * needs more than the single settle-frame already waited for elsewhere
     * in this class to fully re-layout scriptText on this project's
     * older/slower target hardware. Mirror Off/Flip's own target,
     * scrollTo(0, 0), never depends on that measurement at all, so this
     * exists purely as a bounded safeguard for the one target
     * (getScriptStartScrollY() == maxScroll in reverse mode) that does --
     * a genuinely stale measurement here would show as the opening
     * position drifting toward the wrong end, not the specific
     * wrong-padding-mapping symptom that turned out to be this class's
     * actual Flip and Rotate opening-buffer defect (see
     * applyFullScreenScriptPadding()'s doc comment).
     *
     * Scrolls once from whatever computeMaxScroll() reports right now,
     * then re-checks on the very next settled frame and re-scrolls only if
     * that recomputed maxScroll actually differs -- a single bounded
     * correction pass, never a loop, and a harmless no-op on any frame
     * where the first measurement was already correct. afterPositioned
     * (may be null) runs only once the position is confirmed, so a caller
     * that must not act on a possibly-still-wrong position (e.g. resetting
     * Preview's scrolling accumulator/baseline) can defer that action here
     * instead of racing this correction.
     */
    private void scrollToScriptStartReliably(final Runnable afterPositioned) {
        final int firstAttemptMaxScroll = computeMaxScroll();
        scrollView.scrollTo(0, getScriptStartScrollY(firstAttemptMaxScroll));
        scrollView.post(new Runnable() {
            @Override
            public void run() {
                int settledMaxScroll = computeMaxScroll();
                if (settledMaxScroll != firstAttemptMaxScroll) {
                    scrollView.scrollTo(0, getScriptStartScrollY(settledMaxScroll));
                }
                if (afterPositioned != null) {
                    afterPositioned.run();
                }
            }
        });
    }

    /**
     * Centralized script-padding switch used whenever uiState crosses the
     * CONTROLS / full-screen boundary in the direction that should keep the
     * same passage on screen (exitFullScreenToControls()). scriptText's XML
     * default (uniform 24dp padding) is exactly right for CONTROLS, so this
     * is only ever called returning to CONTROLS, never from onCreate() or
     * when entering full screen (resetToWaitingFromTop() always resets to
     * the top instead, so it applies padding directly).
     *
     * Normal/Flip mode: scrollY is measured from the top of the content in
     * the same order it is rendered, so the top-padding delta alone shifts
     * the preserved position -- a bottom-padding change never moves any
     * earlier content on screen.
     *
     * Reverse mode (Flip and Rotate): the rendered order is inverted (see
     * applyFullScreenScriptPadding()'s doc comment), so it is the
     * *bottom*-padding delta that shifts the preserved position instead --
     * the mirror image of the normal-mode case, derived the same way (a
     * fixed on-screen position must keep showing the same underlying text
     * as total content height and top/bottom padding both change).
     *
     * Either way scrollY is shifted by the mode-appropriate delta, then
     * clamped to [0, maxScroll] once the padding change has actually been
     * laid out, so the same approximate passage of text stays on screen
     * instead of jumping to a different part of the script -- including the
     * case where the prior position was already at that direction's visual
     * start, which the clamp alone reduces to correctly (see PROJECT_SPEC.md
     * section 35 for the full derivation) without a separate fast path.
     */
    private void switchScriptPaddingPreservingPosition(final int newTopPaddingPx,
            final int newBottomPaddingPx, final Runnable afterSettled) {
        final int oldTopPaddingPx = scriptText.getPaddingTop();
        final int oldBottomPaddingPx = scriptText.getPaddingBottom();
        final int topDelta = newTopPaddingPx - oldTopPaddingPx;
        final int bottomDelta = newBottomPaddingPx - oldBottomPaddingPx;
        final int previousScrollY = scrollView.getScrollY();
        final boolean reverse = isReverseScrollMode();

        applyScriptPadding(newTopPaddingPx, newBottomPaddingPx);

        scrollView.post(new Runnable() {
            @Override
            public void run() {
                int maxScroll = computeMaxScroll();
                int delta = reverse ? bottomDelta : topDelta;
                int proposed = previousScrollY + delta;
                int clamped = Math.min(Math.max(proposed, 0), maxScroll);
                scrollView.scrollTo(scrollView.getScrollX(), clamped);
                if (afterSettled != null) {
                    afterSettled.run();
                }
            }
        });
    }

    private int dpToPx(float dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    private void updateLoadedFileLabel() {
        if (loadedFilePath != null) {
            currentFileLabel.setText(getString(R.string.loaded_file_label,
                    new File(loadedFilePath).getName()));
        } else {
            currentFileLabel.setText(R.string.no_file_loaded);
        }
    }

    private void updateSpeedLabel() {
        speedLabel.setText(getString(R.string.speed_label, scrollSpeed));
    }

    /**
     * Routes a single tap on the script area to exactly one state
     * transition, based on the state the app is in right now. Reaching
     * the end of the script during WAITING_FOR_TAP can no longer happen
     * -- that now routes through END_OF_SCRIPT_WAITING instead (see
     * stopScrollingAtEnd()) -- so this no longer branches on atScriptEnd.
     *
     * A tap that reaches this method is always on the full-screen script
     * (the CONTROLS case is a no-op), and on API 14-18 a touch can briefly
     * reveal the navigation bar regardless of which state-transition
     * branch above ran. The reapply is deferred via scrollView.post() so
     * it runs strictly after whatever state transition this same tap
     * triggered has already been dispatched, rather than racing it.
     */
    private void handleScriptTap() {
        switch (uiState) {
            case WAITING_FOR_TAP:
                startCountdown(CountdownOrigin.INITIAL_START);
                break;
            case COUNTING_DOWN:
                cancelCountdown();
                break;
            case SCROLLING:
                pauseFullScreenScrolling();
                break;
            case END_OF_SCRIPT_WAITING:
                showEndOfScriptMenu();
                break;
            case PAUSED_FULLSCREEN:
                // The pause menu overlay is clickable and covers the full
                // screen while visible, so a tap never actually reaches
                // here in practice; kept explicit rather than falling
                // through to default.
                break;
            case CONTROLS:
            default:
                // Controls are already visible; a tap on the script area
                // (only reachable by scrolling it manually) is a no-op.
                break;
        }
        scrollView.post(new Runnable() {
            @Override
            public void run() {
                reapplyPrompterSystemUiIfNeeded();
            }
        });
    }

    /**
     * Start Prompter always begins a fresh full-screen session from the
     * very top of the script -- regardless of the prior manual preview
     * position, where Preview scrolling was stopped, a paused full-screen
     * position from an earlier session, or whether the previous session
     * reached the end. Only reachable from CONTROLS; Restart reaches the
     * same reset from PAUSED_FULLSCREEN via resetToWaitingFromTop(), which
     * needs no orientation handling of its own since PAUSED_FULLSCREEN is
     * already in whichever orientation the Prompter is currently using.
     *
     * Prompter Orientation (Horizontal/Vertical) only ever applies to
     * full-screen Prompter mode, never to CONTROLS, so this -- the sole
     * CONTROLS-to-full-screen entry point -- is where the requested
     * orientation is switched if Vertical is selected. Switching may
     * recreate MainActivity on API 14 (see PendingTransition's doc
     * comment); continueEnterPrompter() finishes the job either
     * synchronously below (no recreation needed) or from onResume() once
     * recreation has completed, so it runs exactly once either way.
     */
    private void enterFullScreenWaitingFromControls() {
        if (uiState != UiState.CONTROLS) {
            return;
        }
        if (scriptText.getText() == null || scriptText.getText().length() == 0) {
            Toast.makeText(this, R.string.no_script_loaded, Toast.LENGTH_SHORT).show();
            return;
        }
        stopPreviewScrolling();
        int targetOrientation = prompterOrientationVertical
                ? ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                : ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
        boolean alreadyCorrect = orientationMatches(targetOrientation);
        pendingTransition = PendingTransition.ENTER_PROMPTER;
        setRequestedOrientation(targetOrientation);
        if (alreadyCorrect) {
            continueEnterPrompter();
        }
        // Otherwise: recreation is under way; onResume() calls
        // continueEnterPrompter() once it completes.
    }

    /**
     * Finishes entering the Prompter, whether called synchronously (no
     * orientation change was needed) or from onResume() after an
     * orientation-triggered recreation. Any saved CONTROLS-preview scroll
     * position is irrelevant here -- Start Prompter always begins at the
     * top -- so it is discarded rather than left for the trailing
     * pendingRestoreScrollY block in onResume() to (harmlessly, but
     * pointlessly) apply and then have resetToWaitingFromTop() immediately
     * override.
     */
    private void continueEnterPrompter() {
        pendingTransition = PendingTransition.NONE;
        pendingRestoreScrollY = -1;
        resetToWaitingFromTop();
    }

    /**
     * Whether the Activity's current actual configuration already matches
     * the given ActivityInfo.SCREEN_ORIENTATION_* target, i.e. whether
     * calling setRequestedOrientation(target) would be a no-op (no pause/
     * recreate cycle) rather than actually changing anything.
     */
    private boolean orientationMatches(int screenOrientationConstant) {
        int current = getResources().getConfiguration().orientation;
        if (screenOrientationConstant == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
            return current == Configuration.ORIENTATION_PORTRAIT;
        }
        return current == Configuration.ORIENTATION_LANDSCAPE;
    }

    /**
     * Restart, pressed from the full-screen pause menu: behaves exactly
     * like Start Prompter, except the app was already in full-screen mode
     * (PAUSED_FULLSCREEN, not CONTROLS) and never leaves it.
     */
    private void restartFromPauseMenu() {
        if (uiState != UiState.PAUSED_FULLSCREEN) {
            return;
        }
        resetToWaitingFromTop();
    }

    /**
     * Hides the status bar and navigation bar for full-screen Prompter
     * mode. API 14-18 get the legacy combination (SYSTEM_UI_FLAG_HIDE_
     * NAVIGATION + the window's FLAG_FULLSCREEN); API 19+ additionally get
     * the guarded immersive-sticky flag set, which keeps the bars hidden
     * across a system-triggered edge swipe instead of leaving them shown
     * until the next tap. This is a temporary, per-Activity, platform-
     * supported flag combination -- not kiosk mode, lock task mode, a
     * device-owner API, or any other trick that would keep the user from
     * revealing navigation through a supported gesture.
     */
    private void enterPrompterSystemUi() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        int flags = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
        if (Build.VERSION.SDK_INT >= 19) {
            flags |= View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
        }
        getWindow().getDecorView().setSystemUiVisibility(flags);
    }

    /**
     * Restores normal status/navigation bars. Called only from
     * exitFullScreenToControls() (the single funnel back to CONTROLS) and
     * onResume(), so every path back to the controls screen restores the
     * bars the same way.
     */
    private void exitPrompterSystemUi() {
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
    }

    /**
     * Re-issues the hidden-bar flags if (and only if) uiState is still a
     * full-screen Prompter state -- uiState is the sole source of truth,
     * never a second boolean. Used after a script tap (API 14-18 can
     * briefly reveal the navigation bar on touch, with no event to detect
     * that other than re-asserting the flags after the tap's own state
     * transition has been dispatched) and when the window regains focus.
     */
    private void reapplyPrompterSystemUiIfNeeded() {
        if (uiState != UiState.CONTROLS) {
            enterPrompterSystemUi();
        }
    }

    /**
     * Shared by enterFullScreenWaitingFromControls() and
     * restartFromPauseMenu(): resets to WAITING_FOR_TAP at the very top of
     * the script with both the full-screen opening buffer (top) and
     * trailing end space (bottom) applied, ready for a fresh tap-to-start.
     * This deliberately bypasses switchScriptPaddingPreservingPosition()'s
     * delta-preserving logic (reserved for exitFullScreenToControls(),
     * where pausing mid-script and returning to the preview should keep
     * the same passage on screen) and jumps straight to scrollY 0 instead.
     *
     * Both paddings are fractions of the same measured full-screen
     * viewport height (OPENING_SPACE_FRACTION, END_SPACE_FRACTION), so
     * neither depends on font size or script length -- recomputing them
     * fresh here on every full-screen entry is enough to keep the opening
     * position and the eventual end-of-script centering correct at any
     * font size, on any screen, for any script.
     *
     * The system bars are hidden before any of this is measured: hiding
     * them changes the usable viewport height, so measuring first and
     * hiding bars afterward would leave the padding computed against the
     * wrong height. enterPrompterSystemUi() is called synchronously, then
     * an extra scrollView.post() layer is inserted ahead of the existing
     * height-measurement post() specifically to let that bar-hide's own
     * layout pass settle first -- a single bounded extra frame, not a
     * loop -- before scrollView.getHeight() is trusted.
     *
     * The final scroll-to-start step goes through
     * scrollToScriptStartReliably() rather than a direct scrollTo() call:
     * Mirror Off/Flip's own target (scrollTo(0, 0)) never depends on any
     * measurement, so it was always correct the instant this method's
     * single post()-after-padding settled -- but Flip and Rotate's target
     * (getScriptStartScrollY() == maxScroll) does depend on scriptText's
     * post-padding measured height, so it is worth reliably re-verifying
     * once the padding-driven re-layout has actually settled, as a bounded
     * safeguard, even though physical testing established that this
     * class's real Flip and Rotate opening-buffer defect was the padding
     * *mapping* itself (see applyFullScreenScriptPadding()'s doc comment
     * and PROJECT_SPEC.md section 35), not primarily a measurement-timing
     * gap -- retaining this reliability check does not depend on which of
     * the two was the actual cause.
     */
    private void resetToWaitingFromTop() {
        scrollHandler.removeCallbacksAndMessages(null);
        atScriptEnd = false;
        uiState = UiState.WAITING_FOR_TAP;
        refreshStateVisibility();
        enterPrompterSystemUi();
        // The dynamic paddings depend on the full-screen viewport height,
        // which is only accurate after both controlsPanel's visibility
        // change and the system-bar hide above have actually been laid
        // out -- hence the post()-after-layout pattern used elsewhere in
        // this class. A further, nested post() waits for the padding
        // change itself to be laid out before scrolling to 0, though
        // scrollTo(0, 0) itself needs no measurement to be valid.
        scrollView.post(new Runnable() {
            @Override
            public void run() {
                scrollView.post(new Runnable() {
                    @Override
                    public void run() {
                        applyFullScreenScriptPadding(scrollView.getHeight());
                        scrollView.post(new Runnable() {
                            @Override
                            public void run() {
                                scrollToScriptStartReliably(null);
                            }
                        });
                    }
                });
            }
        });
    }

    /**
     * Reachable from WAITING_FOR_TAP (a fresh Start Prompter/Restart tap)
     * or PAUSED_FULLSCREEN (the pause menu's Resume button); origin is
     * recorded so cancelCountdown() knows which of those to return to.
     */
    private void startCountdown(CountdownOrigin origin) {
        if (uiState != UiState.WAITING_FOR_TAP && uiState != UiState.PAUSED_FULLSCREEN) {
            return;
        }
        scrollHandler.removeCallbacksAndMessages(null);
        countdownOrigin = origin;
        uiState = UiState.COUNTING_DOWN;
        countdownRemaining = COUNTDOWN_START_VALUE;
        refreshStateVisibility();
        scrollHandler.post(countdownRunnable);
    }

    private void performCountdownTick() {
        if (uiState != UiState.COUNTING_DOWN) {
            return;
        }
        if (countdownRemaining <= 0) {
            beginActiveScrolling();
            return;
        }
        countdownText.setText(String.valueOf(countdownRemaining));
        countdownRemaining--;
        scrollHandler.postDelayed(countdownRunnable, COUNTDOWN_INTERVAL_MS);
    }

    /**
     * Cancels an in-progress countdown and returns to whichever stationary
     * full-screen state it was started from, preserving the current
     * script position either way (neither state touches scrollView while
     * the countdown runs, so there is nothing to restore).
     */
    private void cancelCountdown() {
        if (uiState != UiState.COUNTING_DOWN) {
            return;
        }
        scrollHandler.removeCallbacksAndMessages(null);
        uiState = countdownOrigin == CountdownOrigin.RESUME
                ? UiState.PAUSED_FULLSCREEN
                : UiState.WAITING_FOR_TAP;
        refreshStateVisibility();
    }

    /**
     * The sole entry point into SCROLLING, reached after every countdown
     * regardless of origin (CountdownOrigin.INITIAL_START or RESUME) --
     * so resetting the full-screen timing baseline and fractional-pixel
     * accumulator here, unconditionally, covers both a brand-new run and
     * Resume alike: the countdown's own elapsed time is never counted as
     * scrolling time, and Resume's paused scrollView position (untouched
     * by anything above) is picked up smoothly from here rather than
     * jumping, since only the baseline resets, never the position.
     */
    private void beginActiveScrolling() {
        scrollHandler.removeCallbacksAndMessages(null);
        uiState = UiState.SCROLLING;
        refreshStateVisibility();
        fullScreenScrollRemainderPx = 0f;
        fullScreenScrollLastTickUptimeMs = SystemClock.uptimeMillis();
        scrollHandler.postDelayed(scrollRunnable, SCROLL_INTERVAL_MS);
    }

    /**
     * Leaves full-screen mode from any state (waiting, counting down,
     * scrolling, or paused) and returns to the controls screen -- this is
     * also exactly what the pause menu's Back to Controls button does --
     * canceling any pending countdown/scroll callbacks and preserving the
     * current scroll position. Safe to call when already on the controls
     * screen.
     *
     * The main controls must always be landscape regardless of the
     * Prompter Orientation setting, so this -- the sole full-screen-to-
     * CONTROLS funnel -- always requests landscape. If a Vertical session
     * is active this may recreate MainActivity (see PendingTransition's
     * doc comment); continueExitToControls() finishes the job either
     * synchronously below or from onResume() once recreation completes.
     */
    private void exitFullScreenToControls() {
        if (pendingTransition == PendingTransition.ENTER_PROMPTER) {
            // Don't interrupt an in-flight Start Prompter orientation
            // transition (e.g. if this is reached re-entrantly while that
            // transition's own recreation is under way) -- its own
            // continuation already handles landing correctly.
            return;
        }
        scrollHandler.removeCallbacksAndMessages(null);
        stopPreviewScrolling();
        pendingTransition = PendingTransition.EXIT_TO_CONTROLS;
        boolean alreadyLandscape = orientationMatches(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        if (alreadyLandscape) {
            continueExitToControls();
        }
        // Otherwise: recreation is under way; onResume() calls
        // continueExitToControls() once it completes.
    }

    /**
     * Finishes returning to CONTROLS, whether called synchronously (no
     * orientation change was needed, e.g. Horizontal Prompter mode) or from
     * onResume() after an orientation-triggered recreation (Vertical mode).
     *
     * Same-instance exits (pendingRestoreScrollY still -1, untouched by any
     * recreation) keep using switchScriptPaddingPreservingPosition()'s
     * in-memory scroll-delta preservation exactly as before. A recreation-
     * driven exit has nothing valid for that method to measure from --
     * scrollView starts back at 0 in the fresh instance -- so it applies
     * minimal padding directly and leaves scroll restoration to the
     * existing savedInstanceState-driven pendingRestoreScrollY block later
     * in onResume(), which scrolls to the exact saved position once this
     * padding change (and the now-landscape layout) has settled.
     */
    private void continueExitToControls() {
        pendingTransition = PendingTransition.NONE;
        uiState = UiState.CONTROLS;
        refreshStateVisibility();
        exitPrompterSystemUi();
        int minimalPaddingPx = dpToPx(SCRIPT_PADDING_DP);
        if (pendingRestoreScrollY >= 0) {
            applyScriptPadding(minimalPaddingPx, minimalPaddingPx);
        } else {
            switchScriptPaddingPreservingPosition(minimalPaddingPx, minimalPaddingPx, null);
        }
    }

    /**
     * A tap on the full-screen script (or Android Back) during SCROLLING:
     * stop immediately, keep the exact current position, and show the
     * pause menu without ever touching CONTROLS or the scroll position.
     */
    private void pauseFullScreenScrolling() {
        if (uiState != UiState.SCROLLING) {
            return;
        }
        scrollHandler.removeCallbacksAndMessages(null);
        uiState = UiState.PAUSED_FULLSCREEN;
        refreshStateVisibility();
    }

    /**
     * Elapsed-time-driven tick: intended movement is always (pixels per
     * second) * (actual milliseconds since the last tick), never a fixed
     * per-tick distance, so drift in when the Handler actually fires
     * never changes the real-world scroll rate. Only whole pixels are
     * ever applied to the ScrollView; the leftover fraction stays in
     * fullScreenScrollRemainderPx for the next tick, so slow-speed
     * sub-pixel movement accumulates smoothly instead of being discarded
     * (the old Math.round()-per-tick behavior) or forced up to a full
     * pixel every tick (the old Math.max(1, ...) floor).
     */
    private void performScrollTick() {
        if (uiState != UiState.SCROLLING) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        long elapsedMs = now - fullScreenScrollLastTickUptimeMs;
        fullScreenScrollLastTickUptimeMs = now;
        int maxScroll = scriptText.getHeight() - scrollView.getHeight();
        if (maxScroll <= 0) {
            stopScrollingAtEnd();
            return;
        }
        fullScreenScrollRemainderPx += computeScrollPixelsPerSecond() * elapsedMs / 1000f;
        int wholePixels = (int) fullScreenScrollRemainderPx;
        if (wholePixels > 0) {
            fullScreenScrollRemainderPx -= wholePixels;
            int newY = moveTowardScriptEnd(scrollView.getScrollY(), wholePixels, maxScroll);
            scrollView.scrollTo(scrollView.getScrollX(), newY);
            if (hasReachedScriptEnd(newY, maxScroll)) {
                stopScrollingAtEnd();
                return;
            }
        }
        scrollHandler.postDelayed(scrollRunnable, SCROLL_INTERVAL_MS);
    }

    /**
     * The script reached its end during active scrolling: stop, but leave
     * the final text fully visible and unobstructed -- no pause menu, no
     * overlay of any kind -- since reaching the bottom of the ScrollView
     * doesn't mean the presenter has finished reading the final visible
     * lines yet. Stays in full-screen mode with the screen kept awake
     * (refreshStateVisibility() keeps it awake for any non-CONTROLS
     * state) so there's no time pressure. The reduced end-of-script menu
     * (Restart / Back to Controls, no Resume) only appears once the user
     * taps -- see showEndOfScriptMenu().
     */
    private void stopScrollingAtEnd() {
        scrollHandler.removeCallbacksAndMessages(null);
        atScriptEnd = true;
        uiState = UiState.END_OF_SCRIPT_WAITING;
        refreshStateVisibility();
    }

    /**
     * A tap during END_OF_SCRIPT_WAITING: reveals the reduced
     * end-of-script menu (Restart / Back to Controls; Resume stays
     * hidden since atScriptEnd is still true) by transitioning to the
     * same PAUSED_FULLSCREEN state a mid-script pause uses. Deferred via
     * post() so the buttons this reveals can never be reached by the
     * same touch sequence that triggered this tap -- by the time this
     * runs, the gesture that revealed the menu has already finished
     * dispatching, so it takes a genuinely new tap to activate either
     * button.
     */
    private void showEndOfScriptMenu() {
        if (uiState != UiState.END_OF_SCRIPT_WAITING) {
            return;
        }
        scrollView.post(new Runnable() {
            @Override
            public void run() {
                if (uiState != UiState.END_OF_SCRIPT_WAITING) {
                    return;
                }
                uiState = UiState.PAUSED_FULLSCREEN;
                refreshStateVisibility();
            }
        });
    }

    private void togglePreviewScrolling() {
        if (previewScrolling) {
            stopPreviewScrolling();
        } else {
            startPreviewScrolling();
        }
    }

    /**
     * Scrolls the script inside the control-screen preview, entirely
     * independent of uiState/the full-screen SCROLLING state: uiState stays
     * CONTROLS throughout, no countdown or Tap to Start is involved, and
     * previewScrollRunnable is posted on the same shared scrollHandler used
     * everywhere else, so every existing removeCallbacksAndMessages(null)
     * cleanup call also cancels a pending preview tick for free.
     *
     * Every press (while stopped) starts over from the very top, exactly
     * like Start Prompter's own reset-to-top -- otherwise, on a short
     * script already scrolled to its end, pressing Preview again after
     * changing Speed appeared to do nothing, since the ScrollView was
     * already sitting at its maximum position. previewScrolling is set
     * true synchronously (not inside the deferred Runnable below), so a
     * second rapid press is rejected by the guard above rather than
     * queuing a duplicate start.
     */
    private void startPreviewScrolling() {
        if (uiState != UiState.CONTROLS || previewScrolling) {
            return;
        }
        if (scriptText.getText() == null || scriptText.getText().length() == 0) {
            Toast.makeText(this, R.string.no_script_loaded, Toast.LENGTH_SHORT).show();
            return;
        }
        scrollHandler.removeCallbacks(previewScrollRunnable);
        atScriptEnd = false;
        int minimalPaddingPx = dpToPx(SCRIPT_PADDING_DP);
        applyScriptPadding(minimalPaddingPx, minimalPaddingPx);
        previewScrolling = true;
        scriptText.setKeepScreenOn(true);
        updatePreviewButtonLabel();
        // Deferred so scrollTo(0, 0) below runs after any padding-driven
        // layout change above has settled; re-checks previewScrolling/
        // uiState in case Stop (or anything else) ran in the meantime --
        // same defense-in-depth pattern as showEndOfScriptMenu().
        scrollView.post(new Runnable() {
            @Override
            public void run() {
                if (!previewScrolling || uiState != UiState.CONTROLS) {
                    return;
                }
                scrollToScriptStartReliably(new Runnable() {
                    @Override
                    public void run() {
                        if (!previewScrolling || uiState != UiState.CONTROLS) {
                            return;
                        }
                        previewScrollRemainderPx = 0f;
                        previewScrollLastTickUptimeMs = SystemClock.uptimeMillis();
                        scrollHandler.postDelayed(previewScrollRunnable, SCROLL_INTERVAL_MS);
                    }
                });
            }
        });
    }

    /**
     * Safe to call any time, including when preview scrolling is already
     * inactive: this is the single stop path called both directly (the
     * Preview/Stop button) and defensively from every control that must
     * not let preview scrolling keep running underneath it (Enter
     * Prompter, New Script, Edit Script, Load Script, Exit, Back, and the
     * Activity lifecycle via exitFullScreenToControls()).
     */
    private void stopPreviewScrolling() {
        if (!previewScrolling) {
            return;
        }
        previewScrolling = false;
        scrollHandler.removeCallbacks(previewScrollRunnable);
        scriptText.setKeepScreenOn(false);
        updatePreviewButtonLabel();
    }

    /**
     * Elapsed-time-driven, exactly like performScrollTick() -- see that
     * method's doc comment -- but with its own independent
     * previewScrollRemainderPx/previewScrollLastTickUptimeMs, so Preview
     * and full-screen scrolling can never interfere with each other's
     * accumulated sub-pixel state even though both share scrollHandler
     * and SCROLL_INTERVAL_MS.
     */
    private void performPreviewScrollTick() {
        if (!previewScrolling || uiState != UiState.CONTROLS) {
            previewScrolling = false;
            return;
        }
        long now = SystemClock.uptimeMillis();
        long elapsedMs = now - previewScrollLastTickUptimeMs;
        previewScrollLastTickUptimeMs = now;
        int maxScroll = scriptText.getHeight() - scrollView.getHeight();
        if (maxScroll <= 0) {
            stopPreviewScrolling();
            return;
        }
        previewScrollRemainderPx += computeScrollPixelsPerSecond() * elapsedMs / 1000f;
        int wholePixels = (int) previewScrollRemainderPx;
        if (wholePixels > 0) {
            previewScrollRemainderPx -= wholePixels;
            int newY = moveTowardScriptEnd(scrollView.getScrollY(), wholePixels, maxScroll);
            scrollView.scrollTo(scrollView.getScrollX(), newY);
            if (hasReachedScriptEnd(newY, maxScroll)) {
                stopPreviewScrolling();
                return;
            }
        }
        scrollHandler.postDelayed(previewScrollRunnable, SCROLL_INTERVAL_MS);
    }

    private void updatePreviewButtonLabel() {
        btnPreview.setText(previewScrolling ? R.string.btn_stop_preview : R.string.btn_preview);
    }

    /**
     * Single source of truth for what the controls panel, countdown,
     * "Tap to Start", and pause menu show, and for whether the screen
     * stays awake -- all driven entirely by uiState (and atScriptEnd for
     * the WAITING_FOR_TAP/PAUSED_FULLSCREEN special cases). Called at the
     * end of every state transition so none of these can drift out of
     * sync with each other. Every full-screen state (everything except
     * CONTROLS) keeps the screen awake; only CONTROLS clears it, which
     * also covers the Activity being paused/stopped/destroyed since
     * onPause() always routes through exitFullScreenToControls() first.
     * Preview's own keep-screen-awake handling is separate and untouched
     * here, since Preview only ever runs while uiState stays CONTROLS.
     *
     * END_OF_SCRIPT_WAITING needs no special case of its own here: every
     * condition below is phrased as "== the one state that should show
     * this", so a state that matches none of them (as END_OF_SCRIPT_WAITING
     * matches none of CONTROLS/COUNTING_DOWN/WAITING_FOR_TAP/PAUSED_FULLSCREEN)
     * automatically hides the controls panel, countdown, Tap to Start, and
     * pause menu alike -- leaving only the full-screen script itself
     * visible and unobstructed -- while still keeping the screen awake via
     * the trailing "!= CONTROLS" check.
     */
    private void refreshStateVisibility() {
        controlsPanel.setVisibility(uiState == UiState.CONTROLS ? View.VISIBLE : View.GONE);
        countdownText.setVisibility(uiState == UiState.COUNTING_DOWN ? View.VISIBLE : View.GONE);
        boolean showTapInstruction = uiState == UiState.WAITING_FOR_TAP && !atScriptEnd;
        tapInstructionText.setVisibility(showTapInstruction ? View.VISIBLE : View.GONE);
        boolean showPauseMenu = uiState == UiState.PAUSED_FULLSCREEN;
        pauseMenuOverlay.setVisibility(showPauseMenu ? View.VISIBLE : View.GONE);
        if (showPauseMenu) {
            btnResume.setVisibility(atScriptEnd ? View.GONE : View.VISIBLE);
        }
        scriptText.setKeepScreenOn(uiState != UiState.CONTROLS);
    }

    /**
     * The current Speed setting's rate in pixels per second, shared by
     * Preview and full-screen Prompter scrolling alike -- both must use
     * the same selected Speed, and both read this fresh on every tick, so
     * a live Speed change (e.g. dragging the SeekBar during Preview)
     * takes effect on the very next tick with no restart needed.
     */
    private float computeScrollPixelsPerSecond() {
        float density = getResources().getDisplayMetrics().density;
        return scrollSpeed * SCROLL_DP_PER_SECOND_PER_SPEED_UNIT * density;
    }

    private void changeFontSize(float deltaSp) {
        fontSizeSp = clamp(fontSizeSp + deltaSp, MIN_FONT_SIZE_SP, MAX_FONT_SIZE_SP);
        applyFontSize();
        savePreferences();
    }

    private void applyFontSize() {
        scriptText.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSizeSp);
        fontSizeLabel.setText(getString(R.string.font_size_label, (int) fontSizeSp));
    }

    /**
     * The single centralized place presenter-facing content's mirror
     * transform is computed and applied -- scriptText, tapInstructionText,
     * and countdownText always receive the exact same scaleX/scaleY/
     * rotation, so they can never diverge. Every value is absolute, never
     * a relative increment (no rotationBy()-style call anywhere in this
     * class), so switching Mirror off, or switching Mirror Mode while
     * Mirror stays on, can never leave a stale transform behind -- each
     * call fully recomputes and reassigns all three properties from
     * mirrorEnabled/mirrorMode's current values.
     *
     * Mirror Off (mirrorEnabled == false): scaleX=1, scaleY=1, rotation=0
     * -- the identity transform, regardless of mirrorMode, which stays
     * saved but inactive.
     * Mirror On + Flip (the pre-existing, default behavior): scaleX=-1,
     * scaleY=1, rotation=0 -- a horizontal-only flip.
     * Mirror On + Flip and Rotate: scaleX=-1, scaleY=1, rotation=180 --
     * the same horizontal flip, composed with an absolute 180-degree
     * rotation (not a different scaleY-based "vertical flip" mode).
     *
     * No explicit pivot is ever set: View's default pivot is already the
     * center of its own bounds once measured, which is exactly what a
     * centered 180-degree rotation needs, and it recomputes automatically
     * as the view's measured size changes (e.g. a Font Size change) since
     * nothing here ever freezes it with setPivotX()/setPivotY(). A 180-
     * degree rotation combined with a horizontal flip also never changes
     * a view's own bounding box, so this can never clip content within
     * its own view.
     *
     * This is still a pure draw-time Canvas transform -- it never touches
     * scriptText's measured/laid-out bounds, so ScrollView.scrollTo()/
     * getScrollY() and every maxScroll calculation keep operating on the
     * exact same untransformed coordinates regardless of what's applied
     * here. But because Flip and Rotate's 180-degree rotation pivots about
     * scriptText's own center, it renders the content ScrollView clips to
     * a given scrollY window as a top/bottom mirror image of that window's
     * untransformed layout order -- so scrollY 0 renders as the visual
     * *end* of the script, not the beginning, under Flip and Rotate. This
     * is exactly the reverse logical scrolling model isReverseScrollMode()/
     * getScriptStartScrollY()/getScriptEndScrollY()/moveTowardScriptEnd()/
     * hasReachedScriptEnd() implement on top of the unchanged scrollY
     * mechanics -- see PROJECT_SPEC.md section 35 for the full derivation.
     */
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

    /**
     * The single place scriptText's horizontal text alignment is set.
     * scriptText is shared by the control-screen preview and every
     * full-screen Prompter state, so this one call covers both -- there is
     * no duplicate script view. Deliberately touches nothing but gravity:
     * no padding, scroll position, script content, or scrolling state, so
     * it is always safe to call from onCreate()/onResume() regardless of
     * uiState. Tap to Start, the countdown, the pause/end menus, main
     * controls, the filename display, the file browser, the Script Editor,
     * and Settings all use their own views and are never touched here.
     */
    private void applyTextAlignment() {
        int gravity = AppConstants.TEXT_ALIGNMENT_CENTER.equals(textAlignment)
                ? Gravity.CENTER_HORIZONTAL : Gravity.LEFT;
        scriptText.setGravity(gravity);
    }

    private static float clamp(float value, float min, float max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }
}
