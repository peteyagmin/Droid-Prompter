package com.a500teleprompter.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
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
 * existing script overwrites its own file path in place.
 */
public class ScriptEditorActivity extends Activity {

    private int editorMode;
    private String existingFilePath;

    private TextView titleText;
    private EditText filenameField;
    private EditText scriptBodyField;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_script_editor);

        titleText = (TextView) findViewById(R.id.editorTitleText);
        filenameField = (EditText) findViewById(R.id.filenameField);
        scriptBodyField = (EditText) findViewById(R.id.scriptBodyField);
        Button btnCancel = (Button) findViewById(R.id.btnEditorCancel);
        Button btnSave = (Button) findViewById(R.id.btnEditorSave);

        editorMode = getIntent().getIntExtra(AppConstants.EXTRA_EDITOR_MODE,
                AppConstants.EDITOR_MODE_CREATE_NEW);
        existingFilePath = getIntent().getStringExtra(AppConstants.EXTRA_EDIT_FILE_PATH);

        if (editorMode == AppConstants.EDITOR_MODE_EDIT_EXISTING && existingFilePath != null) {
            setUpEditExisting();
        } else {
            setUpCreateNew();
        }

        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setResult(RESULT_CANCELED);
                finish();
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

    private void setUpCreateNew() {
        editorMode = AppConstants.EDITOR_MODE_CREATE_NEW;
        titleText.setText(R.string.title_new_script);
        filenameField.setEnabled(true);
        filenameField.setText("");
        scriptBodyField.setText("");
    }

    private void setUpEditExisting() {
        titleText.setText(R.string.title_edit_script);
        File file = new File(existingFilePath);
        filenameField.setText(file.getName());
        filenameField.setEnabled(false);

        TextFileReader.Result result = TextFileReader.readTextFile(file);
        if (!result.success) {
            Toast.makeText(this, result.errorMessage, Toast.LENGTH_LONG).show();
            setResult(RESULT_CANCELED);
            finish();
            return;
        }
        scriptBodyField.setText(result.text);
    }

    private void saveNew() {
        String rawName = filenameField.getText().toString().trim();
        if (rawName.length() == 0) {
            Toast.makeText(this, R.string.filename_required, Toast.LENGTH_SHORT).show();
            return;
        }
        if (rawName.indexOf('/') >= 0 || rawName.indexOf('\\') >= 0) {
            Toast.makeText(this, R.string.filename_invalid_chars, Toast.LENGTH_SHORT).show();
            return;
        }
        String fileName = ensureTxtExtension(rawName);

        File folder = StorageLocations.getTeleprompterFolder();
        if (!folder.exists()) {
            folder.mkdirs();
        }
        if (!folder.canWrite()) {
            Toast.makeText(this, R.string.script_save_failed, Toast.LENGTH_LONG).show();
            return;
        }

        File target = new File(folder, fileName);
        if (!writeTextFile(target, scriptBodyField.getText().toString())) {
            Toast.makeText(this, R.string.script_save_failed, Toast.LENGTH_LONG).show();
            return;
        }

        Intent result = new Intent();
        result.putExtra(AppConstants.EXTRA_SAVED_FILE_PATH, target.getAbsolutePath());
        result.putExtra(AppConstants.EXTRA_EDITOR_WAS_NEW, true);
        setResult(RESULT_OK, result);
        finish();
    }

    private void saveExisting() {
        File target = new File(existingFilePath);
        if (!writeTextFile(target, scriptBodyField.getText().toString())) {
            Toast.makeText(this, R.string.script_save_failed, Toast.LENGTH_LONG).show();
            return;
        }

        Intent result = new Intent();
        result.putExtra(AppConstants.EXTRA_SAVED_FILE_PATH, target.getAbsolutePath());
        result.putExtra(AppConstants.EXTRA_EDITOR_WAS_NEW, false);
        setResult(RESULT_OK, result);
        finish();
    }

    private static String ensureTxtExtension(String name) {
        String lower = name.toLowerCase();
        if (lower.endsWith(".txt") || lower.endsWith(".text")) {
            return name;
        }
        return name + ".txt";
    }

    private static boolean writeTextFile(File target, String content) {
        Writer writer = null;
        try {
            writer = new OutputStreamWriter(new FileOutputStream(target), "UTF-8");
            writer.write(content);
            return true;
        } catch (IOException e) {
            return false;
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException ignored) {
                    // Nothing more we can do; the write already finished or failed.
                }
            }
        }
    }
}
