package com.a500teleprompter.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Built-in file browser used to pick a .txt/.text script. Requires no
 * third-party file manager and no Storage Access Framework APIs, so it works
 * unmodified on Android 4.0 (API 14).
 */
public class FileBrowserActivity extends Activity {

    private static final Comparator<FileEntry> NAME_COMPARATOR = new Comparator<FileEntry>() {
        @Override
        public int compare(FileEntry a, FileEntry b) {
            return a.name.compareToIgnoreCase(b.name);
        }
    };

    private TextView currentPathText;
    private TextView emptyMessageText;
    private TextView selectedFileText;
    private ListView fileListView;
    private Button btnUp;
    private Button btnStorage;
    private Button btnOpen;

    private SharedPreferences prefs;
    private File currentDirectory;
    private File startDirectory;
    private List<File> storageRoots;
    private FileEntry selectedEntry;
    private FileListAdapter adapter;
    private final List<FileEntry> entries = new ArrayList<FileEntry>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file_browser);

        prefs = getSharedPreferences(AppConstants.PREFS_NAME, MODE_PRIVATE);

        currentPathText = (TextView) findViewById(R.id.currentPathText);
        emptyMessageText = (TextView) findViewById(R.id.emptyMessageText);
        selectedFileText = (TextView) findViewById(R.id.selectedFileText);
        fileListView = (ListView) findViewById(R.id.fileListView);
        btnUp = (Button) findViewById(R.id.btnUp);
        btnStorage = (Button) findViewById(R.id.btnStorage);
        Button btnTeleprompterFolder = (Button) findViewById(R.id.btnTeleprompterFolder);
        Button btnCancel = (Button) findViewById(R.id.btnCancel);
        btnOpen = (Button) findViewById(R.id.btnOpen);

        adapter = new FileListAdapter(this, entries);
        fileListView.setAdapter(adapter);

        ensureTeleprompterFolderExists();
        storageRoots = StorageLocations.findReadableRoots();
        btnStorage.setVisibility(storageRoots.size() > 1 ? View.VISIBLE : View.GONE);

        startDirectory = resolveInitialDirectory();
        navigateTo(startDirectory);

        fileListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                onEntryClicked(entries.get(position));
            }
        });

        btnUp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                navigateUp();
            }
        });

        btnTeleprompterFolder.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                File folder = StorageLocations.getTeleprompterFolder();
                if (folder.exists() && folder.canRead()) {
                    navigateTo(folder);
                } else {
                    Toast.makeText(FileBrowserActivity.this,
                            R.string.teleprompter_folder_unavailable, Toast.LENGTH_SHORT).show();
                }
            }
        });

        btnStorage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showStorageChooser();
            }
        });

        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setResult(RESULT_CANCELED);
                finish();
            }
        });

        btnOpen.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmSelection();
            }
        });
    }

    private void ensureTeleprompterFolderExists() {
        File folder = StorageLocations.getTeleprompterFolder();
        if (!folder.exists()) {
            folder.mkdirs();
        }
    }

    private File resolveInitialDirectory() {
        String lastDirectoryPath = prefs.getString(AppConstants.KEY_LAST_DIRECTORY, null);
        if (lastDirectoryPath != null) {
            File lastDirectory = new File(lastDirectoryPath);
            if (lastDirectory.exists() && lastDirectory.isDirectory() && lastDirectory.canRead()) {
                return lastDirectory;
            }
        }
        return Environment.getExternalStorageDirectory();
    }

    private void navigateTo(File directory) {
        if (directory == null || !directory.canRead()) {
            directory = Environment.getExternalStorageDirectory();
        }
        currentDirectory = directory;
        currentPathText.setText(directory.getAbsolutePath());
        selectedEntry = null;
        selectedFileText.setText(R.string.no_selection);
        btnOpen.setEnabled(false);
        btnUp.setEnabled(canNavigateUp());
        loadEntries();
    }

    private void loadEntries() {
        entries.clear();

        File[] children = currentDirectory.listFiles();
        if (children == null) {
            showEmptyMessage(getString(R.string.folder_cannot_be_accessed));
            adapter.notifyDataSetChanged();
            return;
        }
        if (children.length == 0) {
            showEmptyMessage(getString(R.string.folder_is_empty));
            adapter.notifyDataSetChanged();
            return;
        }

        List<FileEntry> directories = new ArrayList<FileEntry>();
        List<FileEntry> files = new ArrayList<FileEntry>();
        for (int i = 0; i < children.length; i++) {
            File child = children[i];
            if (child.isDirectory()) {
                if (child.canRead()) {
                    directories.add(new FileEntry(child.getName(), child.getAbsolutePath(), true));
                }
            } else if (isSupportedTextFile(child.getName())) {
                files.add(new FileEntry(child.getName(), child.getAbsolutePath(), false));
            }
        }

        if (directories.isEmpty() && files.isEmpty()) {
            showEmptyMessage(getString(R.string.no_text_files));
            adapter.notifyDataSetChanged();
            return;
        }

        Collections.sort(directories, NAME_COMPARATOR);
        Collections.sort(files, NAME_COMPARATOR);

        entries.addAll(directories);
        entries.addAll(files);
        emptyMessageText.setVisibility(View.GONE);
        fileListView.setVisibility(View.VISIBLE);
        adapter.notifyDataSetChanged();
    }

    private boolean isSupportedTextFile(String name) {
        String lower = name.toLowerCase();
        return lower.endsWith(".txt") || lower.endsWith(".text");
    }

    private void showEmptyMessage(String message) {
        entries.clear();
        emptyMessageText.setText(message);
        emptyMessageText.setVisibility(View.VISIBLE);
        fileListView.setVisibility(View.GONE);
    }

    private void onEntryClicked(FileEntry entry) {
        if (entry.isDirectory) {
            navigateTo(new File(entry.path));
        } else {
            selectedEntry = entry;
            selectedFileText.setText(getString(R.string.selected_file_label, entry.name));
            btnOpen.setEnabled(true);
        }
    }

    private boolean canNavigateUp() {
        if (currentDirectory == null) {
            return false;
        }
        if (currentDirectory.getParentFile() == null) {
            return false;
        }
        return !isStorageRoot(currentDirectory);
    }

    private boolean isStorageRoot(File directory) {
        String path = StorageLocations.canonicalPath(directory);
        for (int i = 0; i < storageRoots.size(); i++) {
            if (StorageLocations.canonicalPath(storageRoots.get(i)).equals(path)) {
                return true;
            }
        }
        return false;
    }

    private void navigateUp() {
        if (!canNavigateUp()) {
            return;
        }
        navigateTo(currentDirectory.getParentFile());
    }

    private void showStorageChooser() {
        final List<File> options = storageRoots;
        String[] labels = new String[options.size()];
        for (int i = 0; i < options.size(); i++) {
            labels[i] = options.get(i).getAbsolutePath();
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.choose_storage_title)
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        navigateTo(options.get(which));
                    }
                })
                .show();
    }

    private void confirmSelection() {
        if (selectedEntry == null) {
            Toast.makeText(this, R.string.select_file_first, Toast.LENGTH_SHORT).show();
            return;
        }
        File file = new File(selectedEntry.path);
        if (!file.exists() || !file.canRead()) {
            Toast.makeText(this, R.string.file_cannot_be_opened, Toast.LENGTH_LONG).show();
            return;
        }
        if (file.length() <= 0) {
            Toast.makeText(this, R.string.file_is_empty_message, Toast.LENGTH_LONG).show();
            return;
        }
        if (file.length() > TextFileReader.MAX_FILE_SIZE_BYTES) {
            Toast.makeText(this, R.string.file_too_large, Toast.LENGTH_LONG).show();
            return;
        }

        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(AppConstants.KEY_LAST_DIRECTORY, currentDirectory.getAbsolutePath());
        editor.commit();

        Intent result = new Intent();
        result.putExtra(AppConstants.EXTRA_FILE_PATH, file.getAbsolutePath());
        setResult(RESULT_OK, result);
        finish();
    }

    @Override
    public void onBackPressed() {
        boolean atStart = currentDirectory != null && startDirectory != null
                && StorageLocations.canonicalPath(currentDirectory)
                        .equals(StorageLocations.canonicalPath(startDirectory));
        if (atStart) {
            setResult(RESULT_CANCELED);
            super.onBackPressed();
            return;
        }
        if (canNavigateUp()) {
            navigateUp();
        } else {
            setResult(RESULT_CANCELED);
            super.onBackPressed();
        }
    }
}
