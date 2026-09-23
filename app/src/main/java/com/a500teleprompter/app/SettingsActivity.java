package com.a500teleprompter.app;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.RadioButton;
import android.widget.RadioGroup;

/**
 * A plain platform-Views settings screen: Text Alignment, Prompter
 * Orientation, and Load Last Script on Launch. Remains landscape (see
 * AndroidManifest.xml) and keeps normal status/navigation bars throughout --
 * it is never a full-screen Prompter state. Every control saves to
 * SharedPreferences immediately on change, so there is no separate Save
 * step and no unsaved-changes state to protect on Back/Done.
 */
public class SettingsActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        final SharedPreferences prefs = getSharedPreferences(AppConstants.PREFS_NAME, MODE_PRIVATE);

        RadioGroup textAlignmentGroup = (RadioGroup) findViewById(R.id.textAlignmentGroup);
        RadioButton radioAlignLeft = (RadioButton) findViewById(R.id.radioAlignLeft);
        RadioButton radioAlignCenter = (RadioButton) findViewById(R.id.radioAlignCenter);
        RadioGroup prompterOrientationGroup = (RadioGroup) findViewById(R.id.prompterOrientationGroup);
        RadioButton radioOrientationHorizontal = (RadioButton) findViewById(R.id.radioOrientationHorizontal);
        RadioButton radioOrientationVertical = (RadioButton) findViewById(R.id.radioOrientationVertical);
        CheckBox loadLastScriptCheckBox = (CheckBox) findViewById(R.id.loadLastScriptCheckBox);
        Button btnDone = (Button) findViewById(R.id.btnSettingsDone);

        String alignment = prefs.getString(AppConstants.KEY_TEXT_ALIGNMENT, AppConstants.TEXT_ALIGNMENT_LEFT);
        if (AppConstants.TEXT_ALIGNMENT_CENTER.equals(alignment)) {
            radioAlignCenter.setChecked(true);
        } else {
            radioAlignLeft.setChecked(true);
        }

        String orientation = prefs.getString(AppConstants.KEY_PROMPTER_ORIENTATION,
                AppConstants.PROMPTER_ORIENTATION_HORIZONTAL);
        if (AppConstants.PROMPTER_ORIENTATION_VERTICAL.equals(orientation)) {
            radioOrientationVertical.setChecked(true);
        } else {
            radioOrientationHorizontal.setChecked(true);
        }

        loadLastScriptCheckBox.setChecked(prefs.getBoolean(AppConstants.KEY_LOAD_LAST_SCRIPT, true));

        textAlignmentGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                String value = checkedId == R.id.radioAlignCenter
                        ? AppConstants.TEXT_ALIGNMENT_CENTER : AppConstants.TEXT_ALIGNMENT_LEFT;
                SharedPreferences.Editor editor = prefs.edit();
                editor.putString(AppConstants.KEY_TEXT_ALIGNMENT, value);
                editor.commit();
            }
        });

        prompterOrientationGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                String value = checkedId == R.id.radioOrientationVertical
                        ? AppConstants.PROMPTER_ORIENTATION_VERTICAL : AppConstants.PROMPTER_ORIENTATION_HORIZONTAL;
                SharedPreferences.Editor editor = prefs.edit();
                editor.putString(AppConstants.KEY_PROMPTER_ORIENTATION, value);
                editor.commit();
            }
        });

        loadLastScriptCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                SharedPreferences.Editor editor = prefs.edit();
                editor.putBoolean(AppConstants.KEY_LOAD_LAST_SCRIPT, isChecked);
                editor.commit();
            }
        });

        btnDone.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }
}
