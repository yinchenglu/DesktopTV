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

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.app.AlertDialog;
import android.os.AsyncTask;
import android.view.View;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.CheckBoxPreference;
import android.preference.PreferenceManager;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.provider.Settings;
import android.widget.Toast;

import com.yinchenglu.desktoptv.R;
import com.yinchenglu.desktoptv.Background;
import com.yinchenglu.desktoptv.HomeImage;
import com.yinchenglu.desktoptv.Launcher;
import com.yinchenglu.desktoptv.Protection;
import com.yinchenglu.desktoptv.Setup;
import com.yinchenglu.desktoptv.study.Study;

import java.util.Locale;
import java.io.File;
import java.lang.ref.WeakReference;

@SuppressWarnings("deprecation")
public class Preferences extends PreferenceActivity {
	public static final String PREFERENCE_DEFAULT_TRANSPARENCY = "preference_default_transparency";
	public static final String PREFERENCE_TRANSPARENCY = "preference_transparency";
	public static final String PREFERENCE_HOME_IMAGE = "preference_home_image";
	public static final String PREFERENCE_IMAGE_SCALE = "preference_image_scale";
	public static final String PREFERENCE_SHOW_NETWORK = "preference_show_network";
	public static final String PREFERENCE_SHOW_IP = "preference_show_ip";
	public static final String PREFERENCE_SHOW_WALLPAPER = "preference_show_wallpaper";
	public static final String PREFERENCE_SCREEN_ON = "preference_screen_always_on";
	public static final String PREFERENCE_SHOW_DATE = "preference_show_date";
	public static final String PREFERENCE_SHOW_BATTERY = "preference_show_battery";
	public static final String PREFERENCE_GRID_X = "preference_grid_x";
	public static final String PREFERENCE_GRID_Y = "preference_grid_y";
	public static final String PREFERENCE_SHOW_NAME = "preference_show_name";
	public static final String PREFERENCE_MARGIN_X = "preference_margin_x";
	public static final String PREFERENCE_MARGIN_Y = "preference_margin_y";
	public static final String PREFERENCE_LOCKED = "preference_locked";
	public static final String PREFERENCE_TIME_FORMAT = "preference_time_format";
	private static final String PREFERENCE_ABOUT = "preference_about";
	private static final String PREFERENCE_HIDDEN_APPS = "preference_hidden_apps";
	private static final String PREFERENCE_RESET_DEFAULTS = "preference_reset_defaults";
	private static final String PREFERENCE_STUDY_RESET = "preference_study_reset";
	private static final String PREFERENCE_STUDY_STATE = "preference_study_state";
	private static final String PREFERENCE_STUDY_ENABLED = "preference_study_enabled";
	private static final String PREFERENCE_SCREENSAVER = "preference_screensaver";
	private static final int REQUEST_UNLOCK = 100;
	private static final int REQUEST_IMAGE = 101;
	private ImageImport mImageImport;
	private boolean mAuthorized;
	private AlertDialog mWallpaperDialog;
	private AsyncTask<Void, Void, Boolean> mWallpaperReset;
	private static final String CATEGORY_GRID = "category_grid";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mAuthorized = savedInstanceState != null && savedInstanceState.getBoolean("authorized");
		if (new Protection(this).isEnabled() && !mAuthorized) {
			if (savedInstanceState == null)
				startActivityForResult(Protection.getIntent(this, null), REQUEST_UNLOCK);
			return;
		}
		initialize();
	}

	private void initialize() {
		mAuthorized = true;
		Setup setup = new Setup(this);
		addPreferencesFromResource(R.xml.preferences);

		findPreference(PREFERENCE_HIDDEN_APPS).setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				startActivity(new Intent(Preferences.this, HiddenApps.class));
				return (true);
			}
		});
		findPreference(PREFERENCE_RESET_DEFAULTS).setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				confirmResetDefaults();
				return (true);
			}
		});
		findPreference(PREFERENCE_STUDY_RESET).setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				resetStudyToday();
				return (true);
			}
		});
		findPreference(PREFERENCE_STUDY_ENABLED).setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				if (newValue instanceof Boolean && !((Boolean) newValue))
					new Study(Preferences.this).stopWatch();
				updateStudyState();
				return (true);
			}
		});
		findPreference(PREFERENCE_HOME_IMAGE).setEnabled(HomeImage.getFile(this).isFile());
		findPreference("preference_choose_image").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				try {
					startActivityForResult(new Intent(Intent.ACTION_GET_CONTENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE), REQUEST_IMAGE);
				} catch (ActivityNotFoundException | SecurityException e) {
					Toast.makeText(Preferences.this, R.string.image_picker_unavailable, Toast.LENGTH_LONG).show();
				}
				return (true);
			}
		});
		findPreference("preference_reset_wallpaper").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				confirmWallpaperReset();
				return (true);
			}
		});
		findPreference("preference_background").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				try {
					startActivity(new Intent(Intent.ACTION_SET_WALLPAPER));
				} catch (ActivityNotFoundException e) {
					Toast.makeText(Preferences.this, R.string.wallpaper_unavailable, Toast.LENGTH_LONG).show();
				} catch (SecurityException e) {
					Toast.makeText(Preferences.this, R.string.wallpaper_unavailable, Toast.LENGTH_LONG).show();
				}
				return (true);
			}
		});
		bindSummary(PREFERENCE_GRID_X, R.string.summary_grid_x);
		bindSummary(PREFERENCE_MARGIN_X, R.string.summary_margin_x);
		bindSummary(PREFERENCE_MARGIN_Y, R.string.summary_margin_y);

		findPreference(CATEGORY_GRID).setEnabled(!setup.iconsLocked());
		findPreference(PREFERENCE_LOCKED).setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				findPreference(CATEGORY_GRID).setEnabled(!(boolean) newValue);
				return (true);
			}
		});

		findPreference(PREFERENCE_TRANSPARENCY).setEnabled(!setup.isDefaultTransparency());
		findPreference("preference_home").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				openHomeSettings();
				return (true);
			}
		});
		findPreference(PREFERENCE_SCREENSAVER).setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				openScreensaver();
				return (true);
			}
		});
		findPreference(PREFERENCE_DEFAULT_TRANSPARENCY).setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				findPreference(PREFERENCE_TRANSPARENCY).setEnabled(!(boolean) newValue);
				return true;
			}
		});

		PackageInfo pInfo;
		String version = "#Err";
		try {
			pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
			version = pInfo.versionName;
		} catch (PackageManager.NameNotFoundException e) {
			e.printStackTrace();
		}

		findPreference(PREFERENCE_ABOUT).setTitle(getString(R.string.version_label, version));
	}

	private void updateStudyState() {
		Preference state = findPreference(PREFERENCE_STUDY_STATE);
		if (state == null)
			return;
		Study study = new Study(this);
		state.setSummary(getString(R.string.summary_study_state,
				study.formatDuration(study.getBalance()),
				study.formatDuration(study.getEarnedToday()),
				study.formatDuration(study.getUsedToday())));
	}

	private void resetStudyToday() {
		new Study(this).resetToday();
		updateStudyState();
		Toast.makeText(this, R.string.study_reset_done, Toast.LENGTH_SHORT).show();
	}

	@Override
	protected void onResume() {
		super.onResume();
		updateStudyState();
	}

	@Override
	protected void onSaveInstanceState(Bundle state) {
		state.putBoolean("authorized", mAuthorized);
		super.onSaveInstanceState(state);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
		super.onActivityResult(requestCode, resultCode, intent);
		if (requestCode == REQUEST_IMAGE) {
			if (mAuthorized && resultCode == RESULT_OK && intent != null && intent.getData() != null && mImageImport == null) {
				findPreference("preference_choose_image").setEnabled(false);
				mImageImport = new ImageImport(this, intent.getData());
				mImageImport.execute();
			}
			return;
		}
		if (requestCode == REQUEST_UNLOCK) {
			if (resultCode == RESULT_OK) {
				mAuthorized = true;
				initialize();
			} else {
				finish();
			}
		}
	}

	private void confirmWallpaperReset() {
		mWallpaperDialog = new AlertDialog.Builder(this).setTitle(R.string.reset_wallpaper)
				.setMessage(R.string.confirm_reset_wallpaper)
				.setPositiveButton(android.R.string.ok, null)
				.setNegativeButton(android.R.string.cancel, null).create();
		mWallpaperDialog.show();
		mWallpaperDialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				resetWallpaper();
			}
		});
	}

	private void resetWallpaper() {
		if (mWallpaperReset != null)
			return;
		mWallpaperDialog.setMessage(getString(R.string.resetting_wallpaper));
		mWallpaperDialog.setCancelable(false);
		mWallpaperDialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(false);
		mWallpaperDialog.getButton(DialogInterface.BUTTON_NEGATIVE).setEnabled(false);
		final Context context = getApplicationContext();
		mWallpaperReset = new AsyncTask<Void, Void, Boolean>() {
			@Override
			protected Boolean doInBackground(Void... args) {
				try {
					Background.reset(context);
					return (true);
				} catch (Exception e) {
					return (false);
				}
			}

			@Override
			protected void onPostExecute(Boolean success) {
				mWallpaperReset = null;
				if (!isFinishing()) {
					mWallpaperDialog.dismiss();
					Toast.makeText(Preferences.this, success ? R.string.wallpaper_restored : R.string.wallpaper_error, Toast.LENGTH_LONG).show();
				}
			}
		};
		mWallpaperReset.execute();
	}

	private void confirmResetDefaults() {
		new AlertDialog.Builder(this)
				.setTitle(R.string.title_reset_defaults)
				.setMessage(R.string.confirm_reset_defaults)
				.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						resetDefaults();
					}
				})
				.setNegativeButton(android.R.string.cancel, null)
				.show();
	}

	/**
	 * Clears every preference (display settings and the home screen
	 * layout) and restarts the launcher so the first-run defaults are
	 * applied again.
	 */
	private void resetDefaults() {
		PreferenceManager.getDefaultSharedPreferences(this).edit().clear().apply();
		getSharedPreferences(Setup.APPLICATIONS_NAME, Context.MODE_PRIVATE).edit().clear().commit();
		// Remove the file so the launcher behaves like a fresh install.
		// noinspection ResultOfMethodCallIgnored
		new File(getApplicationInfo().dataDir, "shared_prefs/" + Setup.APPLICATIONS_NAME + ".xml").delete();
		Toast.makeText(this, R.string.reset_done, Toast.LENGTH_SHORT).show();
		Intent relaunch = new Intent(this, Launcher.class);
		relaunch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
		startActivity(relaunch);
		finish();
	}

	private void openHomeSettings() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && openSystemSettings(Settings.ACTION_HOME_SETTINGS))
			return;
		if (openSystemSettings(Settings.ACTION_SETTINGS))
			Toast.makeText(this, R.string.home_settings_unavailable, Toast.LENGTH_LONG).show();
		else
			Toast.makeText(this, R.string.settings_unavailable, Toast.LENGTH_LONG).show();
	}

	private void openScreensaver() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2 && openSystemSettings(Settings.ACTION_DREAM_SETTINGS))
			return;
		if (openSystemSettings(Settings.ACTION_DISPLAY_SETTINGS) || openSystemSettings(Settings.ACTION_SETTINGS)) {
			Toast.makeText(this, R.string.screensaver_unavailable, Toast.LENGTH_LONG).show();
		} else {
			Toast.makeText(this, R.string.settings_unavailable, Toast.LENGTH_LONG).show();
		}
	}

	private boolean openSystemSettings(String action) {
		try {
			startActivity(new Intent(action));
			return (true);
		} catch (ActivityNotFoundException e) {
			return (false);
		} catch (SecurityException e) {
			return (false);
		}
	}

	private void bindSummary(String key, final int resId) {
		final ListPreference p = (ListPreference) findPreference(key);
		setPreferenceSummaryValue(p, resId, p.getValue());
		p.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				setPreferenceSummaryValue(p, resId, (String) newValue);
				return true;
			}
		});
	}

	private void setPreferenceSummaryValue(ListPreference prefs, int resId, String value) {
		prefs.setSummary(
				String.format(Locale.getDefault(), getString(resId), value)
		);
	}

	private void imageImported(File file) {
		mImageImport = null;
		findPreference("preference_choose_image").setEnabled(true);
		boolean success = file != null && file.renameTo(HomeImage.getFile(this));
		if (success) {
			PreferenceManager.getDefaultSharedPreferences(this).edit().putBoolean(PREFERENCE_HOME_IMAGE, true).apply();
			CheckBoxPreference preference = (CheckBoxPreference) findPreference(PREFERENCE_HOME_IMAGE);
			preference.setEnabled(true);
			preference.setChecked(true);
		} else if (file != null) {
			file.delete();
		}
		Toast.makeText(this, success ? R.string.image_imported : R.string.image_error, Toast.LENGTH_LONG).show();
	}

	private static class ImageImport extends AsyncTask<Void, Void, File> {
		private final WeakReference<Preferences> mOwner;
		private final Context mContext;
		private final Uri mUri;

		ImageImport(Preferences owner, Uri uri) {
			mOwner = new WeakReference<>(owner);
			mContext = owner.getApplicationContext();
			mUri = uri;
		}

		@Override
		protected File doInBackground(Void... values) {
			try {
				return (HomeImage.copy(mContext, mUri));
			} catch (Exception | OutOfMemoryError e) {
				e.printStackTrace();
				return (null);
			}
		}

		@Override
		protected void onPostExecute(File file) {
			Preferences owner = mOwner.get();
			if (owner == null || owner.isFinishing()) {
				onCancelled(file);
				return;
			}
			owner.imageImported(file);
		}

		@Override
		protected void onCancelled(File file) {
			if (file != null)
				file.delete();
		}
	}

	@Override
	public void onDestroy() {
		if (mImageImport != null)
			mImageImport.cancel(true);
		if (mWallpaperReset != null)
			mWallpaperReset.cancel(true);
		if (mWallpaperDialog != null)
			mWallpaperDialog.dismiss();

		super.onDestroy();
	}
}
