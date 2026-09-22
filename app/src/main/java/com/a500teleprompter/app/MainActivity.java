package com.a500teleprompter.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import java.io.File;

public class MainActivity extends Activity {

    /**
     * The four teleprompter states. Exactly one tap-driven transition is
     * allowed out of any given state; see {@link #handleScriptTap()}.
     */
    private enum UiState {
        CONTROLS,
        WAITING_FOR_TAP,
        COUNTING_DOWN,
        SCROLLING
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
    private static final long SCROLL_INTERVAL_MS = 50;

    private static final int COUNTDOWN_START_VALUE = 5;
    private static final long COUNTDOWN_INTERVAL_MS = 1000;

    private static final float OPENING_SPACE_FRACTION = 0.775f;
    private static final float SCRIPT_PADDING_DP = 24f;

    private static final String STATE_SCRIPT_TEXT = "state_script_text";
    private static final String STATE_SCROLL_Y = "state_scroll_y";
    private static final String STATE_LOADED_FILE_PATH = "state_loaded_file_path";

    private View controlsPanel;
    private ScrollView scrollView;
    private TextView scriptText;
    private TextView countdownText;
    private TextView tapInstructionText;
    private TextView currentFileLabel;
    private TextView fontSizeLabel;
    private TextView speedLabel;
    private SeekBar speedSeekBar;
    private ToggleButton mirrorToggle;
    private Button btnPreview;

    private SharedPreferences prefs;
    private final Handler scrollHandler = new Handler();
    private Runnable scrollRunnable;
    private Runnable countdownRunnable;
    private Runnable previewScrollRunnable;
    private GestureDetector tapDetector;

    private UiState uiState = UiState.CONTROLS;
    private boolean atScriptEnd = false;
    private boolean previewScrolling = false;
    private int countdownRemaining;
    private int scrollSpeed = DEFAULT_SPEED;
    private float fontSizeSp = DEFAULT_FONT_SIZE_SP;
    private boolean mirrorEnabled = false;
    private String loadedFilePath = null;
    private int pendingRestoreScrollY = -1;

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
        currentFileLabel = (TextView) findViewById(R.id.currentFileLabel);
        fontSizeLabel = (TextView) findViewById(R.id.fontSizeLabel);
        speedLabel = (TextView) findViewById(R.id.speedLabel);
        speedSeekBar = (SeekBar) findViewById(R.id.speedSeekBar);
        mirrorToggle = (ToggleButton) findViewById(R.id.mirrorToggle);
        btnPreview = (Button) findViewById(R.id.btnPreview);

        Button btnOpenFile = (Button) findViewById(R.id.btnOpenFile);
        Button btnNewScript = (Button) findViewById(R.id.btnNewScript);
        Button btnEditScript = (Button) findViewById(R.id.btnEditScript);
        Button btnStart = (Button) findViewById(R.id.btnStart);
        Button btnFontDecrease = (Button) findViewById(R.id.btnFontDecrease);
        Button btnFontIncrease = (Button) findViewById(R.id.btnFontIncrease);
        Button btnExit = (Button) findViewById(R.id.btnExit);

        loadPreferences();
        applyFontSize();
        applyMirror();
        mirrorToggle.setChecked(mirrorEnabled);

        speedSeekBar.setMax(MAX_SPEED - MIN_SPEED);
        speedSeekBar.setProgress(scrollSpeed - MIN_SPEED);
        updateSpeedLabel();

        if (savedInstanceState != null) {
            CharSequence savedText = savedInstanceState.getCharSequence(STATE_SCRIPT_TEXT);
            if (savedText != null) {
                scriptText.setText(savedText);
            }
            loadedFilePath = savedInstanceState.getString(STATE_LOADED_FILE_PATH);
            pendingRestoreScrollY = savedInstanceState.getInt(STATE_SCROLL_Y, -1);
            updateLoadedFileLabel();
        } else if (loadedFilePath != null) {
            loadScriptFile(loadedFilePath);
        }

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

        mirrorToggle.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(android.widget.CompoundButton buttonView, boolean isChecked) {
                mirrorEnabled = isChecked;
                applyMirror();
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

    @Override
    protected void onPause() {
        super.onPause();
        // The activity is being backgrounded: always land back on the
        // controls screen so nothing keeps counting down or scrolling
        // while the app is not visible.
        exitFullScreenToControls();
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
        if (uiState != UiState.CONTROLS) {
            exitFullScreenToControls();
            return;
        }
        finish();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putCharSequence(STATE_SCRIPT_TEXT, scriptText.getText());
        outState.putInt(STATE_SCROLL_Y, scrollView.getScrollY());
        outState.putString(STATE_LOADED_FILE_PATH, loadedFilePath);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_BROWSE_FILE && resultCode == RESULT_OK && data != null) {
            String path = data.getStringExtra(AppConstants.EXTRA_FILE_PATH);
            if (path != null) {
                loadScriptFile(path);
            }
        } else if (requestCode == REQUEST_CODE_EDIT_SCRIPT && resultCode == RESULT_OK && data != null) {
            String savedPath = data.getStringExtra(AppConstants.EXTRA_SAVED_FILE_PATH);
            boolean wasNew = data.getBooleanExtra(AppConstants.EXTRA_EDITOR_WAS_NEW, false);
            if (savedPath == null) {
                return;
            }
            if (wasNew) {
                promptLoadNewlySavedScript(savedPath);
            } else {
                // Editing the already-loaded script: reload it automatically,
                // no prompt needed.
                loadScriptFile(savedPath);
            }
        }
    }

    private void promptLoadNewlySavedScript(final String path) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.script_saved_title)
                .setMessage(getString(R.string.load_new_script_prompt, new File(path).getName()))
                .setCancelable(false)
                .setPositiveButton(R.string.dialog_yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        loadScriptFile(path);
                    }
                })
                .setNegativeButton(R.string.dialog_no, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        clearLoadedScript();
                    }
                })
                .show();
    }

    private void clearLoadedScript() {
        loadedFilePath = null;
        scriptText.setText(R.string.default_script_placeholder);
        updateLoadedFileLabel();
        scrollView.scrollTo(0, 0);
    }

    private void loadPreferences() {
        fontSizeSp = prefs.getFloat(AppConstants.KEY_FONT_SIZE, DEFAULT_FONT_SIZE_SP);
        scrollSpeed = prefs.getInt(AppConstants.KEY_SCROLL_SPEED, DEFAULT_SPEED);
        mirrorEnabled = prefs.getBoolean(AppConstants.KEY_MIRROR, false);
        loadedFilePath = prefs.getString(AppConstants.KEY_LAST_FILE_PATH, null);

        if (scrollSpeed < MIN_SPEED) {
            scrollSpeed = MIN_SPEED;
        } else if (scrollSpeed > MAX_SPEED) {
            scrollSpeed = MAX_SPEED;
        }
        fontSizeSp = clamp(fontSizeSp, MIN_FONT_SIZE_SP, MAX_FONT_SIZE_SP);
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
        savePreferences();
        scrollView.post(new Runnable() {
            @Override
            public void run() {
                scrollView.scrollTo(0, 0);
            }
        });
    }

    /**
     * Centralized script-padding switch used whenever uiState crosses the
     * CONTROLS / full-screen boundary. scriptText's XML default (uniform
     * 24dp padding) is exactly right for CONTROLS, so this is only ever
     * called from the two transition methods, never from onCreate().
     *
     * If the script is already at its very start (scrollY == 0), the new
     * padding is applied and scrollY is left at 0: that alone produces the
     * correct per-mode look (first line near the top in CONTROLS, first
     * line ~77.5% down in full screen) with no scroll adjustment needed.
     * Otherwise scrollY is shifted by the padding delta, then clamped once
     * the padding change has actually been laid out, so the same
     * approximate passage of text stays on screen instead of jumping to a
     * different part of the script.
     */
    private void switchTopPaddingPreservingPosition(final int newTopPaddingPx, final Runnable afterSettled) {
        final int oldTopPaddingPx = scriptText.getPaddingTop();
        final int delta = newTopPaddingPx - oldTopPaddingPx;
        final int sidePaddingPx = dpToPx(SCRIPT_PADDING_DP);
        final int previousScrollY = scrollView.getScrollY();
        final boolean atStart = previousScrollY <= 0;

        scriptText.setPadding(sidePaddingPx, newTopPaddingPx, sidePaddingPx, sidePaddingPx);

        scrollView.post(new Runnable() {
            @Override
            public void run() {
                if (!atStart) {
                    int maxScroll = Math.max(0, scriptText.getHeight() - scrollView.getHeight());
                    int proposed = previousScrollY + delta;
                    int clamped = Math.min(Math.max(proposed, 0), maxScroll);
                    scrollView.scrollTo(scrollView.getScrollX(), clamped);
                }
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
     * transition, based on the state the app is in right now.
     */
    private void handleScriptTap() {
        switch (uiState) {
            case WAITING_FOR_TAP:
                if (atScriptEnd) {
                    exitFullScreenToControls();
                } else {
                    startCountdown();
                }
                break;
            case COUNTING_DOWN:
                cancelCountdownToWaiting();
                break;
            case SCROLLING:
                exitFullScreenToControls();
                break;
            case CONTROLS:
            default:
                // Controls are already visible; a tap on the script area
                // (only reachable by scrolling it manually) is a no-op.
                break;
        }
    }

    /**
     * Enter Prompter always begins a fresh full-screen session from the very
     * top of the script -- regardless of the prior manual preview position,
     * where Preview scrolling was stopped, a paused full-screen position
     * from an earlier session, or whether the previous session reached the
     * end. This deliberately bypasses switchTopPaddingPreservingPosition's
     * delta-preserving logic (reserved for exitFullScreenToControls(),
     * where pausing mid-script and returning to the preview should keep the
     * same passage on screen) and jumps straight to scrollY 0 instead.
     */
    private void enterFullScreenWaitingFromControls() {
        if (uiState != UiState.CONTROLS) {
            return;
        }
        if (scriptText.getText() == null || scriptText.getText().length() == 0) {
            Toast.makeText(this, R.string.no_script_loaded, Toast.LENGTH_SHORT).show();
            return;
        }
        scrollHandler.removeCallbacksAndMessages(null);
        stopPreviewScrolling();
        atScriptEnd = false;
        uiState = UiState.WAITING_FOR_TAP;
        scriptText.setKeepScreenOn(false);
        refreshStateVisibility();
        // The dynamic opening-buffer padding depends on the full-screen
        // viewport height, which is only accurate after controlsPanel's
        // visibility change (just requested above) has actually been laid
        // out -- hence the post()-after-layout pattern used elsewhere in
        // this class. A second, nested post() waits for that padding
        // change to be laid out in turn before scrolling to 0, though
        // scrollTo(0, 0) itself needs no measurement to be valid.
        scrollView.post(new Runnable() {
            @Override
            public void run() {
                int fullScreenHeightPx = scrollView.getHeight();
                int sidePaddingPx = dpToPx(SCRIPT_PADDING_DP);
                int newTopPaddingPx = Math.max(sidePaddingPx,
                        Math.round(fullScreenHeightPx * OPENING_SPACE_FRACTION));
                scriptText.setPadding(sidePaddingPx, newTopPaddingPx, sidePaddingPx, sidePaddingPx);
                scrollView.post(new Runnable() {
                    @Override
                    public void run() {
                        scrollView.scrollTo(0, 0);
                    }
                });
            }
        });
    }

    private void startCountdown() {
        if (uiState != UiState.WAITING_FOR_TAP) {
            return;
        }
        scrollHandler.removeCallbacksAndMessages(null);
        uiState = UiState.COUNTING_DOWN;
        countdownRemaining = COUNTDOWN_START_VALUE;
        scriptText.setKeepScreenOn(true);
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

    private void cancelCountdownToWaiting() {
        if (uiState != UiState.COUNTING_DOWN) {
            return;
        }
        scrollHandler.removeCallbacksAndMessages(null);
        scriptText.setKeepScreenOn(false);
        uiState = UiState.WAITING_FOR_TAP;
        refreshStateVisibility();
    }

    private void beginActiveScrolling() {
        scrollHandler.removeCallbacksAndMessages(null);
        uiState = UiState.SCROLLING;
        scriptText.setKeepScreenOn(true);
        refreshStateVisibility();
        scrollHandler.postDelayed(scrollRunnable, SCROLL_INTERVAL_MS);
    }

    /**
     * Leaves full-screen mode from any state (waiting, counting down, or
     * scrolling) and returns to the controls screen, canceling any pending
     * countdown/scroll callbacks and preserving the current scroll
     * position. Safe to call when already on the controls screen.
     */
    private void exitFullScreenToControls() {
        scrollHandler.removeCallbacksAndMessages(null);
        stopPreviewScrolling();
        scriptText.setKeepScreenOn(false);
        uiState = UiState.CONTROLS;
        refreshStateVisibility();
        switchTopPaddingPreservingPosition(dpToPx(SCRIPT_PADDING_DP), null);
    }

    private void performScrollTick() {
        if (uiState != UiState.SCROLLING) {
            return;
        }
        int maxScroll = scriptText.getHeight() - scrollView.getHeight();
        if (maxScroll <= 0) {
            stopScrollingAtEnd();
            return;
        }
        int newY = scrollView.getScrollY() + computeScrollPixelsPerTick();
        if (newY >= maxScroll) {
            scrollView.scrollTo(scrollView.getScrollX(), maxScroll);
            stopScrollingAtEnd();
            return;
        }
        scrollView.scrollTo(scrollView.getScrollX(), newY);
        scrollHandler.postDelayed(scrollRunnable, SCROLL_INTERVAL_MS);
    }

    /**
     * The script reached its end during active scrolling: stop, but stay
     * in full-screen mode (waiting for a tap) rather than forcing the user
     * back to the controls screen. "Tap to Start" is suppressed in this
     * flavor of WAITING_FOR_TAP since there is no more content to scroll.
     */
    private void stopScrollingAtEnd() {
        scrollHandler.removeCallbacksAndMessages(null);
        scriptText.setKeepScreenOn(false);
        atScriptEnd = true;
        uiState = UiState.WAITING_FOR_TAP;
        refreshStateVisibility();
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
     */
    private void startPreviewScrolling() {
        if (uiState != UiState.CONTROLS || previewScrolling) {
            return;
        }
        if (scriptText.getText() == null || scriptText.getText().length() == 0) {
            Toast.makeText(this, R.string.no_script_loaded, Toast.LENGTH_SHORT).show();
            return;
        }
        previewScrolling = true;
        scriptText.setKeepScreenOn(true);
        updatePreviewButtonLabel();
        scrollHandler.removeCallbacks(previewScrollRunnable);
        scrollHandler.postDelayed(previewScrollRunnable, SCROLL_INTERVAL_MS);
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

    private void performPreviewScrollTick() {
        if (!previewScrolling || uiState != UiState.CONTROLS) {
            previewScrolling = false;
            return;
        }
        int maxScroll = scriptText.getHeight() - scrollView.getHeight();
        if (maxScroll <= 0) {
            stopPreviewScrolling();
            return;
        }
        int newY = scrollView.getScrollY() + computeScrollPixelsPerTick();
        if (newY >= maxScroll) {
            scrollView.scrollTo(scrollView.getScrollX(), maxScroll);
            stopPreviewScrolling();
            return;
        }
        scrollView.scrollTo(scrollView.getScrollX(), newY);
        scrollHandler.postDelayed(previewScrollRunnable, SCROLL_INTERVAL_MS);
    }

    private void updatePreviewButtonLabel() {
        btnPreview.setText(previewScrolling ? R.string.btn_stop_preview : R.string.btn_preview);
    }

    /**
     * Single source of truth for what the countdown, "Tap to Start", and
     * controls overlays show, driven entirely by uiState (and atScriptEnd
     * for the one WAITING_FOR_TAP special case). Called at the end of
     * every state transition so the three overlays can never drift out of
     * sync with each other.
     */
    private void refreshStateVisibility() {
        controlsPanel.setVisibility(uiState == UiState.CONTROLS ? View.VISIBLE : View.GONE);
        countdownText.setVisibility(uiState == UiState.COUNTING_DOWN ? View.VISIBLE : View.GONE);
        boolean showTapInstruction = uiState == UiState.WAITING_FOR_TAP && !atScriptEnd;
        tapInstructionText.setVisibility(showTapInstruction ? View.VISIBLE : View.GONE);
    }

    private int computeScrollPixelsPerTick() {
        float density = getResources().getDisplayMetrics().density;
        float dpPerTick = scrollSpeed * 0.5f;
        int px = Math.round(dpPerTick * density);
        return Math.max(1, px);
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

    private void applyMirror() {
        float scaleX = mirrorEnabled ? -1f : 1f;
        scriptText.setScaleX(scaleX);
        countdownText.setScaleX(scaleX);
        tapInstructionText.setScaleX(scaleX);
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
