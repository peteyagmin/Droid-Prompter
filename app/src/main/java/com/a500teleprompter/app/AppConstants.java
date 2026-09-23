package com.a500teleprompter.app;

final class AppConstants {

    static final String PREFS_NAME = "teleprompter_prefs";

    static final String KEY_FONT_SIZE = "font_size_sp";
    static final String KEY_SCROLL_SPEED = "scroll_speed";
    static final String KEY_MIRROR = "mirror_enabled";
    static final String KEY_LAST_FILE_PATH = "last_file_path";
    static final String KEY_LAST_DIRECTORY = "last_directory";

    static final String KEY_TEXT_ALIGNMENT = "text_alignment";
    static final String TEXT_ALIGNMENT_LEFT = "left";
    static final String TEXT_ALIGNMENT_CENTER = "center";

    static final String KEY_PROMPTER_ORIENTATION = "prompter_orientation";
    static final String PROMPTER_ORIENTATION_HORIZONTAL = "horizontal";
    static final String PROMPTER_ORIENTATION_VERTICAL = "vertical";

    static final String KEY_LOAD_LAST_SCRIPT = "load_last_script_on_launch";

    static final String EXTRA_FILE_PATH = "com.a500teleprompter.app.EXTRA_FILE_PATH";

    static final String EXTRA_EDITOR_MODE = "com.a500teleprompter.app.EXTRA_EDITOR_MODE";
    static final String EXTRA_EDIT_FILE_PATH = "com.a500teleprompter.app.EXTRA_EDIT_FILE_PATH";
    static final String EXTRA_SAVED_FILE_PATH = "com.a500teleprompter.app.EXTRA_SAVED_FILE_PATH";
    static final String EXTRA_EDITOR_WAS_NEW = "com.a500teleprompter.app.EXTRA_EDITOR_WAS_NEW";
    static final String EXTRA_EDIT_FILE_UNAVAILABLE = "com.a500teleprompter.app.EXTRA_EDIT_FILE_UNAVAILABLE";

    static final int EDITOR_MODE_CREATE_NEW = 1;
    static final int EDITOR_MODE_EDIT_EXISTING = 2;

    private AppConstants() {
    }
}
