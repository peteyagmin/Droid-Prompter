package com.a500teleprompter.app;

final class FileEntry {

    final String name;
    final String path;
    final boolean isDirectory;

    FileEntry(String name, String path, boolean isDirectory) {
        this.name = name;
        this.path = path;
        this.isDirectory = isDirectory;
    }
}
