package com.a500teleprompter.app;

final class AppConstants {

    static final String PREFS_NAME = "teleprompter_prefs";

    static final String KEY_FONT_SIZE = "font_size_sp";
    static final String KEY_SCROLL_SPEED = "scroll_speed";
    static final String KEY_MIRROR = "mirror_enabled";
    static final String KEY_LAST_FILE_PATH = "last_file_path";
    static final String KEY_LAST_DIRECTORY = "last_directory";

    static final String EXTRA_FILE_PATH = "com.a500teleprompter.app.EXTRA_FILE_PATH";

    static final String EXTRA_EDITOR_MODE = "com.a500teleprompter.app.EXTRA_EDITOR_MODE";
    static final String EXTRA_EDIT_FILE_PATH = "com.a500teleprompter.app.EXTRA_EDIT_FILE_PATH";
    static final String EXTRA_SAVED_FILE_PATH = "com.a500teleprompter.app.EXTRA_SAVED_FILE_PATH";
    static final String EXTRA_EDITOR_WAS_NEW = "com.a500teleprompter.app.EXTRA_EDITOR_WAS_NEW";

    static final int EDITOR_MODE_CREATE_NEW = 1;
    static final int EDITOR_MODE_EDIT_EXISTING = 2;

    private AppConstants() {
    }
}
