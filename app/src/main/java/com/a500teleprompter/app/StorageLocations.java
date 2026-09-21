package com.a500teleprompter.app;

import android.os.Environment;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Finds readable shared-storage roots without relying on any API introduced
 * after level 14. Removable-storage paths are not guaranteed on any given
 * device, so every legacy path is only used when it actually exists and can
 * be read.
 */
final class StorageLocations {

    static final String TELEPROMPTER_FOLDER_NAME = "Teleprompter";

    private static final String[] LEGACY_REMOVABLE_PATHS = {
            "/mnt/external_sd",
            "/mnt/sdcard",
            "/mnt/sdcard2",
            "/storage/sdcard0",
            "/storage/sdcard1",
            "/Removable/MicroSD"
    };

    private StorageLocations() {
    }

    static List<File> findReadableRoots() {
        List<File> roots = new ArrayList<File>();

        File primary = Environment.getExternalStorageDirectory();
        if (primary != null && primary.exists() && primary.canRead()) {
            roots.add(primary);
        }

        for (int i = 0; i < LEGACY_REMOVABLE_PATHS.length; i++) {
            File candidate = new File(LEGACY_REMOVABLE_PATHS[i]);
            if (candidate.exists() && candidate.isDirectory() && candidate.canRead()
                    && !containsSamePath(roots, candidate)) {
                roots.add(candidate);
            }
        }

        return roots;
    }

    static File getTeleprompterFolder() {
        File primary = Environment.getExternalStorageDirectory();
        return new File(primary, TELEPROMPTER_FOLDER_NAME);
    }

    private static boolean containsSamePath(List<File> roots, File candidate) {
        String candidatePath = canonicalPath(candidate);
        for (int i = 0; i < roots.size(); i++) {
            if (canonicalPath(roots.get(i)).equals(candidatePath)) {
                return true;
            }
        }
        return false;
    }

    static String canonicalPath(File file) {
        try {
            return file.getCanonicalPath();
        } catch (IOException e) {
            return file.getAbsolutePath();
        }
    }
}
