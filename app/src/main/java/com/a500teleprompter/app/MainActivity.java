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

    private static final int REQUEST_CODE_BROWSE_FILE = 100;

    private static final float MIN_FONT_SIZE_SP = 14f;
    private static final float MAX_FONT_SIZE_SP = 96f;
    private static final float DEFAULT_FONT_SIZE_SP = 32f;
    private static final float FONT_STEP_SP = 4f;

    private static final int MIN_SPEED = 1;
    private static final int MAX_SPEED = 20;
    private static final int DEFAULT_SPEED = 5;
    private static final long SCROLL_INTERVAL_MS = 50;

    private static final String STATE_SCRIPT_TEXT = "state_script_text";
    private static final String STATE_SCROLL_Y = "state_scroll_y";
    private static final String STATE_LOADED_FILE_PATH = "state_loaded_file_path";

    private View controlsPanel;
    private ScrollView scrollView;
    private TextView scriptText;
    private TextView currentFileLabel;
    private TextView fontSizeLabel;
    private TextView speedLabel;
    private SeekBar speedSeekBar;
    private ToggleButton mirrorToggle;

    private SharedPreferences prefs;
    private final Handler scrollHandler = new Handler();
    private Runnable scrollRunnable;
    private GestureDetector tapDetector;

    private boolean isScrolling = false;
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

        tapDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                toggleControlsVisibility();
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
                startScrolling();
            }
        });

        btnPause.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pauseScrolling();
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
        // Auto-scrolling must not continue while the activity is backgrounded.
        pauseScrolling();
        savePreferences();
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
        pauseScrolling();
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

    private void toggleControlsVisibility() {
        if (controlsPanel.getVisibility() == View.VISIBLE) {
            controlsPanel.setVisibility(View.GONE);
        } else {
            controlsPanel.setVisibility(View.VISIBLE);
        }
    }

    private void startScrolling() {
        if (scriptText.getText() == null || scriptText.getText().length() == 0) {
            Toast.makeText(this, R.string.no_script_loaded, Toast.LENGTH_SHORT).show();
            return;
        }
        if (isScrolling) {
            return;
        }
        isScrolling = true;
        scriptText.setKeepScreenOn(true);
        controlsPanel.setVisibility(View.GONE);
        scrollHandler.postDelayed(scrollRunnable, SCROLL_INTERVAL_MS);
    }

    private void pauseScrolling() {
        isScrolling = false;
        scrollHandler.removeCallbacks(scrollRunnable);
        scriptText.setKeepScreenOn(false);
    }

    private void returnToBeginning() {
        pauseScrolling();
        scrollView.scrollTo(0, 0);
    }

    private void performScrollTick() {
        if (!isScrolling) {
            return;
        }
        int maxScroll = scriptText.getHeight() - scrollView.getHeight();
        if (maxScroll <= 0) {
            pauseScrolling();
            return;
        }
        int newY = scrollView.getScrollY() + computeScrollPixelsPerTick();
        if (newY >= maxScroll) {
            scrollView.scrollTo(scrollView.getScrollX(), maxScroll);
            pauseScrolling();
            return;
        }
        scrollView.scrollTo(scrollView.getScrollX(), newY);
        scrollHandler.postDelayed(scrollRunnable, SCROLL_INTERVAL_MS);
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
        scriptText.setScaleX(mirrorEnabled ? -1f : 1f);
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
