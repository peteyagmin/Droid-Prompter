package com.a500teleprompter.app;

import android.content.Context;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.List;

final class FileListAdapter extends ArrayAdapter<FileEntry> {

    private final LayoutInflater inflater;

    FileListAdapter(Context context, List<FileEntry> entries) {
        super(context, 0, entries);
        inflater = LayoutInflater.from(context);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        TextView view;
        if (convertView instanceof TextView) {
            view = (TextView) convertView;
        } else {
            view = (TextView) inflater.inflate(R.layout.list_item_entry, parent, false);
        }

        FileEntry entry = getItem(position);
        if (entry.isDirectory) {
            view.setText(entry.name + "/");
            view.setTextColor(getContext().getResources().getColor(R.color.directory_color));
            view.setTypeface(null, Typeface.BOLD);
        } else {
            view.setText(entry.name);
            view.setTextColor(getContext().getResources().getColor(R.color.file_color));
            view.setTypeface(null, Typeface.NORMAL);
        }
        return view;
    }
}
