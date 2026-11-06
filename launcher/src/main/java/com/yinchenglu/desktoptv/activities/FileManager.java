/*
 * Simple TV Launcher
 * Copyright 2017 Alexandre Del Bigio
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.yinchenglu.desktoptv.activities;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.yinchenglu.desktoptv.R;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Minimal file manager: browse the storage, open files, delete entries.
 */
public class FileManager extends Activity {

	private static final String[] MIME_MAP = {
			"apk", "application/vnd.android.package-archive",
			"mp4", "video/mp4",
			"mkv", "video/x-matroska",
			"avi", "video/x-msvideo",
			"flv", "video/x-flv",
			"ts", "video/mp2t",
			"mp3", "audio/mpeg",
			"wav", "audio/x-wav",
			"flac", "audio/flac",
			"ogg", "audio/ogg",
			"jpg", "image/jpeg",
			"jpeg", "image/jpeg",
			"png", "image/png",
			"gif", "image/gif",
			"webp", "image/webp",
			"bmp", "image/bmp",
			"pdf", "application/pdf",
			"txt", "text/plain",
			"log", "text/plain",
			"xml", "text/plain",
			"html", "text/html",
			"zip", "application/zip",
			"rar", "application/x-rar-compressed",
	};

	private final List<File> mEntries = new ArrayList<File>();
	private File mDirectory;
	private FileAdapter mAdapter;
	private TextView mPath;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.activity_file_manager);

		mPath = (TextView) findViewById(R.id.fm_path);
		ListView list = (ListView) findViewById(R.id.fm_list);
		mAdapter = new FileAdapter();
		list.setAdapter(mAdapter);
		list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				open(mEntries.get(position));
			}
		});
		list.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
			@Override
			public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
				showEntryMenu(mEntries.get(position));
				return (true);
			}
		});

		File start = Environment.getExternalStorageDirectory();
		if (start == null || !start.exists())
			start = new File("/");
		show(start);
	}

	@Override
	public void onBackPressed() {
		File parent = mDirectory == null ? null : mDirectory.getParentFile();
		if (parent != null && parent.isDirectory())
			show(parent);
		else
			super.onBackPressed();
	}

	private void show(File directory) {
		mDirectory = directory;
		mEntries.clear();
		File[] files = directory.listFiles();
		if (files != null) {
			List<File> items = new ArrayList<File>(Arrays.asList(files));
			Collections.sort(items, new Comparator<File>() {
				@Override
				public int compare(File left, File right) {
					if (left.isDirectory() != right.isDirectory())
						return (left.isDirectory() ? -1 : 1);
					return (left.getName().toLowerCase(Locale.getDefault())
							.compareTo(right.getName().toLowerCase(Locale.getDefault())));
				}
			});
			mEntries.addAll(items);
		}
		mPath.setText(directory.getAbsolutePath());
		mAdapter.notifyDataSetChanged();
	}

	private void open(File file) {
		if (file.isDirectory()) {
			show(file);
			return;
		}
		try {
			Intent intent = new Intent(Intent.ACTION_VIEW);
			intent.setDataAndType(Uri.fromFile(file), guessMime(file.getName()));
			startActivity(intent);
		} catch (Exception e) {
			e.printStackTrace();
			Toast.makeText(this, getString(R.string.fm_open_error, file.getName()), Toast.LENGTH_SHORT).show();
		}
	}

	private void showEntryMenu(final File file) {
		new AlertDialog.Builder(this)
				.setTitle(file.getName())
				.setItems(new String[]{
						getString(R.string.fm_open),
						getString(R.string.fm_delete)
				}, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						if (which == 0)
							open(file);
						else
							delete(file);
					}
				})
				.show();
	}

	private void delete(final File file) {
		new AlertDialog.Builder(this)
				.setMessage(getString(R.string.fm_delete_confirm, file.getName()))
				.setNegativeButton(android.R.string.cancel, null)
				.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						if (deleteRecursively(file))
							Toast.makeText(FileManager.this, R.string.fm_deleted, Toast.LENGTH_SHORT).show();
						else
							Toast.makeText(FileManager.this, R.string.fm_delete_failed, Toast.LENGTH_SHORT).show();
						show(mDirectory);
					}
				})
				.show();
	}

	private static boolean deleteRecursively(File file) {
		if (file.isDirectory()) {
			File[] children = file.listFiles();
			if (children != null) {
				for (File child : children) {
					if (!deleteRecursively(child))
						return (false);
				}
			}
		}
		return (file.delete());
	}

	private static String guessMime(String name) {
		int dot = name.lastIndexOf('.');
		if (dot >= 0) {
			String extension = name.substring(dot + 1).toLowerCase(Locale.getDefault());
			for (int i = 0; i + 1 < MIME_MAP.length; i += 2) {
				if (MIME_MAP[i].equals(extension))
					return (MIME_MAP[i + 1]);
			}
		}
		return ("application/octet-stream");
	}

	private static String formatSize(long bytes) {
		if (bytes < 1024)
			return (bytes + " B");
		if (bytes < 1024 * 1024)
			return (String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0));
		if (bytes < 1024L * 1024 * 1024)
			return (String.format(Locale.getDefault(), "%.1f MB", bytes / 1024.0 / 1024.0));
		return (String.format(Locale.getDefault(), "%.2f GB", bytes / 1024.0 / 1024.0 / 1024.0));
	}

	private class FileAdapter extends BaseAdapter {
		@Override
		public int getCount() {
			return mEntries.size();
		}

		@Override
		public Object getItem(int position) {
			return mEntries.get(position);
		}

		@Override
		public long getItemId(int position) {
			return position;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			View view = convertView;
			if (view == null)
				view = LayoutInflater.from(FileManager.this).inflate(R.layout.file_item, parent, false);

			File file = mEntries.get(position);
			ImageView icon = (ImageView) view.findViewById(R.id.file_icon);
			TextView name = (TextView) view.findViewById(R.id.file_name);
			TextView detail = (TextView) view.findViewById(R.id.file_detail);

			if (file.isDirectory()) {
				icon.setImageResource(R.drawable.ic_folder);
				File[] children = file.listFiles();
				int count = children == null ? 0 : children.length;
				detail.setText(getString(R.string.fm_item_count, count));
			} else {
				icon.setImageResource(R.drawable.ic_files);
				Date date = new Date(file.lastModified());
				String when = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(date);
				detail.setText(formatSize(file.length()) + "  " + when);
			}
			name.setText(file.getName());
			return (view);
		}
	}
}
