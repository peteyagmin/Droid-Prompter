package com.a500teleprompter.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Environment;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;

/**
 * Full-screen plain-text editor used both to type a brand new script
 * (CREATE_NEW) and to revise the currently loaded one (EDIT_EXISTING). New
 * scripts are always written into the shared Teleprompter folder; editing an
 * existing script overwrites its own file path in place. Saves are written
 * to a temporary file first and only renamed over the target once fully
 * written, so a failed save can never destroy existing content.
 */
public class ScriptEditorActivity extends Activity {

    private static final String INVALID_FILENAME_CHARS = "/\\:*?\"<>|";

    private static final String STATE_EDITOR_MODE = "state_editor_mode";
    private static final String STATE_EXISTING_FILE_PATH = "state_existing_file_path";
    private static final String STATE_FILENAME_TEXT = "state_filename_text";
    private static final String STATE_BODY_TEXT = "state_body_text";
    private static final String STATE_BASELINE_FILENAME = "state_baseline_filename";
    private static final String STATE_BASELINE_BODY = "state_baseline_body";
    private static final String STATE_SELECTION_START = "state_selection_start";
    private static final String STATE_SELECTION_END = "state_selection_end";
    private static final String STATE_FILENAME_SELECTION_START = "state_filename_selection_start";
    private static final String STATE_FILENAME_SELECTION_END = "state_filename_selection_end";
    private static final String STATE_COMPACT_MODE = "state_compact_mode";
    private static final String STATE_FOCUS_ON_BODY = "state_focus_on_body";

    private int editorMode;
    private String existingFilePath;

    private TextView titleText;
    private View titleAndFilenameSection;
    private EditText filenameField;
    private EditText scriptBodyField;
    private View btnEditFilename;

    /** Whether the title+filename section is currently collapsed to give scriptBodyField room above the keyboard. */
    private boolean compactMode;

    /** Snapshot of the fields' content as of the last open or successful save, used to detect unsaved changes. */
    private String baselineFilename = "";
    private String baselineBody = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_script_editor);

        titleText = (TextView) findViewById(R.id.editorTitleText);
        titleAndFilenameSection = findViewById(R.id.titleAndFilenameSection);
        filenameField = (EditText) findViewById(R.id.filenameField);
        scriptBodyField = (EditText) findViewById(R.id.scriptBodyField);
        btnEditFilename = findViewById(R.id.btnEditFilename);
        Button btnCancel = (Button) findViewById(R.id.btnEditorCancel);
        Button btnSave = (Button) findViewById(R.id.btnEditorSave);

        if (savedInstanceState != null) {
            editorMode = savedInstanceState.getInt(STATE_EDITOR_MODE, AppConstants.EDITOR_MODE_CREATE_NEW);
            existingFilePath = savedInstanceState.getString(STATE_EXISTING_FILE_PATH);
        } else {
            editorMode = getIntent().getIntExtra(AppConstants.EXTRA_EDITOR_MODE,
                    AppConstants.EDITOR_MODE_CREATE_NEW);
            existingFilePath = getIntent().getStringExtra(AppConstants.EXTRA_EDIT_FILE_PATH);
        }

        if (editorMode == AppConstants.EDITOR_MODE_EDIT_EXISTING && existingFilePath != null) {
            setUpEditExisting();
        } else {
            setUpCreateNew();
        }

        if (savedInstanceState != null) {
            restoreUnsavedState(savedInstanceState);
        }

        // filenameField's imeOptions offers a "Next" action (see
        // activity_script_editor.xml). Some keyboards honor actionNext and
        // call onEditorAction with IME_ACTION_NEXT; others (seen on
        // hardware) instead send a raw Enter key. Both are handled here,
        // routed through the same guarded method so a single key press
        // can never advance focus twice.
        filenameField.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_NEXT) {
                    advanceFromFilenameToBody();
                    return true;
                }
                return false;
            }
        });
        filenameField.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (keyCode == KeyEvent.KEYCODE_ENTER
                        && event.getAction() == KeyEvent.ACTION_DOWN
                        && event.getRepeatCount() == 0) {
                    advanceFromFilenameToBody();
                    return true;
                }
                return false;
            }
        });

        // Focusing scriptBodyField is what triggers compact mode: the
        // title+filename section collapses to give the body the vertical
        // space the keyboard would otherwise eat.
        scriptBodyField.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    enterCompactMode();
                    ensureVisibleAboveKeyboard(scriptBodyField);
                }
            }
        });

        btnEditFilename.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                exitCompactModeForFilenameEdit();
            }
        });

        applyInitialFocusAndCompactState(savedInstanceState);

        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleCancelOrBack();
            }
        });

        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (editorMode == AppConstants.EDITOR_MODE_EDIT_EXISTING) {
                    saveExisting();
                } else {
                    saveNew();
                }
            }
        });
    }

    @Override
    public void onBackPressed() {
        handleCancelOrBack();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_EDITOR_MODE, editorMode);
        outState.putString(STATE_EXISTING_FILE_PATH, existingFilePath);
        outState.putString(STATE_FILENAME_TEXT, filenameField.getText().toString());
        outState.putString(STATE_BODY_TEXT, scriptBodyField.getText().toString());
        outState.putString(STATE_BASELINE_FILENAME, baselineFilename);
        outState.putString(STATE_BASELINE_BODY, baselineBody);
        outState.putInt(STATE_SELECTION_START, scriptBodyField.getSelectionStart());
        outState.putInt(STATE_SELECTION_END, scriptBodyField.getSelectionEnd());
        outState.putInt(STATE_FILENAME_SELECTION_START, filenameField.getSelectionStart());
        outState.putInt(STATE_FILENAME_SELECTION_END, filenameField.getSelectionEnd());
        outState.putBoolean(STATE_COMPACT_MODE, compactMode);
        outState.putBoolean(STATE_FOCUS_ON_BODY, scriptBodyField.hasFocus());
    }

    private void restoreUnsavedState(Bundle savedInstanceState) {
        String savedFilename = savedInstanceState.getString(STATE_FILENAME_TEXT);
        if (savedFilename != null) {
            filenameField.setText(savedFilename);
        }
        String savedBody = savedInstanceState.getString(STATE_BODY_TEXT);
        if (savedBody != null) {
            scriptBodyField.setText(savedBody);
        }
        baselineFilename = savedInstanceState.getString(STATE_BASELINE_FILENAME, baselineFilename);
        baselineBody = savedInstanceState.getString(STATE_BASELINE_BODY, baselineBody);

        int selStart = savedInstanceState.getInt(STATE_SELECTION_START, -1);
        int selEnd = savedInstanceState.getInt(STATE_SELECTION_END, -1);
        int length = scriptBodyField.getText().length();
        if (selStart >= 0 && selEnd >= 0 && selStart <= length && selEnd <= length) {
            scriptBodyField.setSelection(selStart, selEnd);
        }
    }

    /**
     * Decides, once on creation, whether the editor should open in compact
     * mode and which field should hold focus. EDIT_EXISTING always jumps
     * straight into the body in compact mode since filenameField is
     * disabled there. CREATE_NEW without saved state leaves the default
     * (title+filename visible, first focusable field focused) untouched.
     * A configuration change restores whichever field the user was
     * actually in, favoring the body whenever compact mode was active.
     */
    private void applyInitialFocusAndCompactState(Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            boolean wasCompact = savedInstanceState.getBoolean(STATE_COMPACT_MODE, false);
            boolean focusWasOnBody = savedInstanceState.getBoolean(STATE_FOCUS_ON_BODY, false);
            if (wasCompact || focusWasOnBody) {
                scriptBodyField.requestFocus();
            } else {
                filenameField.requestFocus();
                int start = savedInstanceState.getInt(STATE_FILENAME_SELECTION_START, -1);
                int end = savedInstanceState.getInt(STATE_FILENAME_SELECTION_END, -1);
                int length = filenameField.getText().length();
                if (start >= 0 && end >= 0 && start <= length && end <= length) {
                    filenameField.setSelection(start, end);
                }
            }
        } else if (editorMode == AppConstants.EDITOR_MODE_EDIT_EXISTING) {
            scriptBodyField.requestFocus();
        }
    }

    private void enterCompactMode() {
        if (compactMode) {
            return;
        }
        compactMode = true;
        titleAndFilenameSection.setVisibility(View.GONE);
        if (editorMode == AppConstants.EDITOR_MODE_CREATE_NEW) {
            btnEditFilename.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Restores the title+filename section without touching filenameField's
     * text or triggering a save, then hands focus back to it so the user
     * can correct the filename.
     */
    private void exitCompactModeForFilenameEdit() {
        compactMode = false;
        titleAndFilenameSection.setVisibility(View.VISIBLE);
        btnEditFilename.setVisibility(View.GONE);
        filenameField.requestFocus();
        filenameField.setSelection(filenameField.getText().length());
        showKeyboardFor(filenameField);
        ensureVisibleAboveKeyboard(filenameField);
    }

    /**
     * Consumes filenameField's Next/Enter, never inserting a newline and
     * never treating it as Save. Guarded against double-firing: once the
     * body already has focus, a second Next/Enter event for the same
     * keypress (some keyboards deliver both an editor action and a raw
     * key event) is a no-op.
     */
    private void advanceFromFilenameToBody() {
        if (scriptBodyField.hasFocus()) {
            return;
        }
        scriptBodyField.requestFocus();
        scriptBodyField.setSelection(scriptBodyField.getText().length());
        showKeyboardFor(scriptBodyField);
    }

    /** Scrolls/pans the given field into view above the keyboard once the pending layout pass settles; API 14-safe. */
    private void ensureVisibleAboveKeyboard(final View view) {
        view.post(new Runnable() {
            @Override
            public void run() {
                view.requestRectangleOnScreen(new Rect(0, 0, view.getWidth(), view.getHeight()), true);
            }
        });
    }

    /** Explicitly keeps the soft keyboard open across a focus hand-off between two EditTexts. */
    private void showKeyboardFor(final View view) {
        view.post(new Runnable() {
            @Override
            public void run() {
                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT);
                }
            }
        });
    }

    private void setUpCreateNew() {
        editorMode = AppConstants.EDITOR_MODE_CREATE_NEW;
        titleText.setText(R.string.title_new_script);
        filenameField.setEnabled(true);
        filenameField.setText("");
        scriptBodyField.setText("");
        updateBaseline();
    }

    private void setUpEditExisting() {
        titleText.setText(R.string.title_edit_script);
        File file = new File(existingFilePath);
        filenameField.setText(file.getName());
        filenameField.setEnabled(false);

        TextFileReader.Result result = TextFileReader.readTextFile(file);
        if (!result.success) {
            Toast.makeText(this, result.errorMessage, Toast.LENGTH_LONG).show();
            Intent resultIntent = new Intent();
            resultIntent.putExtra(AppConstants.EXTRA_EDIT_FILE_UNAVAILABLE, true);
            setResult(RESULT_CANCELED, resultIntent);
            finish();
            return;
        }
        scriptBodyField.setText(result.text);
        updateBaseline();
    }

    private void updateBaseline() {
        baselineFilename = filenameField.getText().toString();
        baselineBody = scriptBodyField.getText().toString();
    }

    private boolean hasUnsavedChanges() {
        return !filenameField.getText().toString().equals(baselineFilename)
                || !scriptBodyField.getText().toString().equals(baselineBody);
    }

    private void handleCancelOrBack() {
        if (hasUnsavedChanges()) {
            showDiscardChangesDialog();
        } else {
            setResult(RESULT_CANCELED);
            finish();
        }
    }

    private void showDiscardChangesDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.discard_changes_title)
                .setMessage(R.string.discard_changes_message)
                .setCancelable(false)
                .setPositiveButton(R.string.btn_discard, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        setResult(RESULT_CANCELED);
                        finish();
                    }
                })
                .setNegativeButton(R.string.btn_keep_editing, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                })
                .show();
    }

    private void saveNew() {
        String rawName = filenameField.getText().toString().trim();
        if (rawName.length() == 0) {
            Toast.makeText(this, R.string.filename_required, Toast.LENGTH_SHORT).show();
            return;
        }
        if (containsInvalidFilenameChars(rawName)) {
            Toast.makeText(this, R.string.filename_invalid_chars, Toast.LENGTH_SHORT).show();
            return;
        }
        if (isBlank(scriptBodyField.getText().toString())) {
            Toast.makeText(this, R.string.script_content_required, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!isExternalStorageWritable()) {
            Toast.makeText(this, R.string.storage_unavailable, Toast.LENGTH_LONG).show();
            return;
        }

        String fileName = ensureTxtExtension(rawName);
        File folder = StorageLocations.getTeleprompterFolder();
        if (!folder.exists()) {
            folder.mkdirs();
        }
        if (!folder.canWrite()) {
            Toast.makeText(this, R.string.storage_unavailable, Toast.LENGTH_LONG).show();
            return;
        }

        final File target = new File(folder, fileName);
        if (target.exists()) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.file_already_exists_title)
                    .setMessage(R.string.file_already_exists_message)
                    .setCancelable(true)
                    .setPositiveButton(R.string.btn_replace, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            finishSaveNew(target);
                        }
                    })
                    .setNegativeButton(R.string.btn_cancel, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    })
                    .show();
            return;
        }

        finishSaveNew(target);
    }

    private void finishSaveNew(File target) {
        if (!writeTextFileAtomically(target, scriptBodyField.getText().toString())) {
            Toast.makeText(this, R.string.script_save_failed, Toast.LENGTH_LONG).show();
            return;
        }
        updateBaseline();

        Intent result = new Intent();
        result.putExtra(AppConstants.EXTRA_SAVED_FILE_PATH, target.getAbsolutePath());
        result.putExtra(AppConstants.EXTRA_EDITOR_WAS_NEW, true);
        setResult(RESULT_OK, result);
        finish();
    }

    private void saveExisting() {
        if (isBlank(scriptBodyField.getText().toString())) {
            Toast.makeText(this, R.string.script_content_required, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!isExternalStorageWritable()) {
            Toast.makeText(this, R.string.storage_unavailable, Toast.LENGTH_LONG).show();
            return;
        }

        File target = new File(existingFilePath);
        if (!writeTextFileAtomically(target, scriptBodyField.getText().toString())) {
            Toast.makeText(this, R.string.script_save_failed, Toast.LENGTH_LONG).show();
            return;
        }
        updateBaseline();

        Intent result = new Intent();
        result.putExtra(AppConstants.EXTRA_SAVED_FILE_PATH, target.getAbsolutePath());
        result.putExtra(AppConstants.EXTRA_EDITOR_WAS_NEW, false);
        setResult(RESULT_OK, result);
        finish();
    }

    private static boolean isBlank(String text) {
        return text.trim().length() == 0;
    }

    private static boolean containsInvalidFilenameChars(String name) {
        for (int i = 0; i < INVALID_FILENAME_CHARS.length(); i++) {
            if (name.indexOf(INVALID_FILENAME_CHARS.charAt(i)) >= 0) {
                return true;
            }
        }
        return false;
    }

    private static boolean isExternalStorageWritable() {
        return Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState());
    }

    private static String ensureTxtExtension(String name) {
        String lower = name.toLowerCase();
        if (lower.endsWith(".txt") || lower.endsWith(".text")) {
            return name;
        }
        return name + ".txt";
    }

    /**
     * Writes content to a temporary file in target's own directory, then
     * renames it over target only once the write has fully succeeded and
     * the stream is closed. File.renameTo() within the same directory is
     * the safest atomic-ish replace available without APIs newer than
     * level 14 (java.nio.file's ATOMIC_MOVE requires API 26). If anything
     * fails before the rename, target is never touched, so a failed save
     * can never destroy the original content.
     */
    private static boolean writeTextFileAtomically(File target, String content) {
        File tempFile = new File(target.getParentFile(), target.getName() + ".tmp");
        Writer writer = null;
        boolean writeOk;
        try {
            writer = new OutputStreamWriter(new FileOutputStream(tempFile), "UTF-8");
            writer.write(content);
            writer.flush();
            writeOk = true;
        } catch (IOException e) {
            writeOk = false;
        } catch (SecurityException e) {
            writeOk = false;
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException ignored) {
                    // Nothing more we can do; the write already finished or failed.
                }
            }
        }

        if (!writeOk) {
            tempFile.delete();
            return false;
        }
        if (!tempFile.renameTo(target)) {
            tempFile.delete();
            return false;
        }
        return true;
    }
}
