package com.a500teleprompter.app;

import android.app.Activity;
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

    private SharedPreferences prefs;
    private final Handler scrollHandler = new Handler();
    private Runnable scrollRunnable;
    private Runnable countdownRunnable;
    private GestureDetector tapDetector;

    private UiState uiState = UiState.CONTROLS;
    private boolean atScriptEnd = false;
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

        Button btnOpenFile = (Button) findViewById(R.id.btnOpenFile);
        Button btnStart = (Button) findViewById(R.id.btnStart);
        Button btnPause = (Button) findViewById(R.id.btnPause);
        Button btnBeginning = (Button) findViewById(R.id.btnBeginning);
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

        scheduleOpeningPaddingComputation();

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

        btnPause.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                exitFullScreenToControls();
            }
        });

        btnBeginning.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                returnToBeginning();
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
        }
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
     * The visible script area is not measured yet during onCreate(), so the
     * opening-space calculation is deferred to a post() callback, which
     * runs after the first layout pass.
     */
    private void scheduleOpeningPaddingComputation() {
        final View contentRoot = findViewById(android.R.id.content);
        contentRoot.post(new Runnable() {
            @Override
            public void run() {
                computeAndApplyOpeningPadding(contentRoot.getHeight());
            }
        });
    }

    /**
     * Pads the top of scriptText so the first line of a freshly loaded
     * script (scrollY == 0) initially sits about 75-80% of the way down
     * the visible teleprompter area, leaving room for it to scroll
     * naturally upward. The padding is part of scriptText's own content,
     * so it scrolls with the script rather than staying fixed on screen,
     * and it is independent of scriptText's horizontal mirror scaleX.
     */
    private void computeAndApplyOpeningPadding(int visibleAreaHeightPx) {
        if (visibleAreaHeightPx <= 0) {
            return;
        }
        int sidePaddingPx = dpToPx(SCRIPT_PADDING_DP);
        int topPaddingPx = Math.max(sidePaddingPx,
                Math.round(visibleAreaHeightPx * OPENING_SPACE_FRACTION));
        scriptText.setPadding(sidePaddingPx, topPaddingPx, sidePaddingPx, sidePaddingPx);
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

    private void enterFullScreenWaitingFromControls() {
        if (uiState != UiState.CONTROLS) {
            return;
        }
        if (scriptText.getText() == null || scriptText.getText().length() == 0) {
            Toast.makeText(this, R.string.no_script_loaded, Toast.LENGTH_SHORT).show();
            return;
        }
        scrollHandler.removeCallbacksAndMessages(null);
        uiState = UiState.WAITING_FOR_TAP;
        scriptText.setKeepScreenOn(false);
        refreshStateVisibility();
        // atScriptEnd is intentionally left as-is here rather than reset:
        // the script's scroll position did not change by entering full
        // screen, so whether it is still at its end is re-checked against
        // actual geometry once the full-screen layout has settled.
        scrollView.post(new Runnable() {
            @Override
            public void run() {
                recomputeAtScriptEndAfterLayout();
            }
        });
    }

    /**
     * Re-evaluates atScriptEnd against the actual (now full-screen)
     * ScrollView geometry after entering WAITING_FOR_TAP, so a script left
     * scrolled to its end stays marked atScriptEnd, while one the user
     * manually scrolled away from the end is correctly un-marked. Guarded
     * on uiState in case the user already moved on by the time this
     * deferred check runs.
     */
    private void recomputeAtScriptEndAfterLayout() {
        if (uiState != UiState.WAITING_FOR_TAP) {
            return;
        }
        int maxScroll = scriptText.getHeight() - scrollView.getHeight();
        atScriptEnd = maxScroll > 0 && scrollView.getScrollY() >= maxScroll;
        refreshStateVisibility();
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
        scriptText.setKeepScreenOn(false);
        uiState = UiState.CONTROLS;
        refreshStateVisibility();
    }

    private void returnToBeginning() {
        scrollHandler.removeCallbacksAndMessages(null);
        scriptText.setKeepScreenOn(false);
        atScriptEnd = false;
        uiState = UiState.CONTROLS;
        refreshStateVisibility();
        scrollView.scrollTo(0, 0);
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
