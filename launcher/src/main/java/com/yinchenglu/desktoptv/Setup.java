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

package com.yinchenglu.desktoptv;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.text.TextUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.json.JSONArray;
import com.yinchenglu.desktoptv.views.ApplicationView;

import com.yinchenglu.desktoptv.activities.Preferences;

import static com.yinchenglu.desktoptv.activities.Preferences.PREFERENCE_DEFAULT_TRANSPARENCY;
import static com.yinchenglu.desktoptv.activities.Preferences.PREFERENCE_TRANSPARENCY;


public class Setup {
	public static final String APPLICATIONS_NAME = "applications";
	public static final String HOME_TILES = "home_tiles";
	public static final String HOME_REMOVED = "home_removed";
	private static final String DEFAULTS_INITIALIZED = "defaults_initialized";
	private static final String MIGRATED = "tiles_migrated";
	private static final int DEFAULT_GRID_X = 3;
	private static final int DEFAULT_GRID_Y = 2;
	private static final int DEFAULT_MARGIN_X = 5;
	private static final int DEFAULT_MARGIN_Y = 5;

	private final Context mContext;
	private SharedPreferences mPreferences;

	public Setup(Context context) {
		mContext = context;
	}

	public void initializeApplications() {
		SharedPreferences preferences = mContext.getSharedPreferences(APPLICATIONS_NAME, Context.MODE_PRIVATE);

		// Migrate the legacy application_NN slots to the ordered home tile list.
		if (!preferences.getBoolean(MIGRATED, false)) {
			List<String> tiles = getHomeTiles();
			for (int position = 0; position < 500; position++) {
				String value = preferences.getString(ApplicationView.getPreferenceKey(position), null);
				if (TextUtils.isEmpty(value))
					continue;
				if (!tiles.contains(value))
					tiles.add(value);
			}
			if (!tiles.isEmpty())
				setHomeTiles(tiles);
			preferences.edit().putBoolean(MIGRATED, true).apply();
		}

		// Un ancien accueil vidé volontairement possède encore son fichier de préférences.
		File file = new File(mContext.getApplicationInfo().dataDir, "shared_prefs/" + APPLICATIONS_NAME + ".xml");
		boolean configured = file.exists();
		if (preferences.getBoolean(DEFAULTS_INITIALIZED, false))
			return;
		SharedPreferences.Editor editor = preferences.edit();
		if (!configured && preferences.getAll().isEmpty() && getPreferences().getAll().isEmpty()) {
			String[] applications = mContext.getResources().getStringArray(R.array.default_applications);
			int count = Math.min(applications.length, Math.max(2, getGridX()) * Math.max(1, getGridY()));
			for (int position = 0; position < count; position++) {
				String packageName = applications[position].trim();
				if (!TextUtils.isEmpty(packageName))
					addHomeTile(packageName);
			}
		}
		editor.putBoolean(DEFAULTS_INITIALIZED, true).commit();
	}

	private SharedPreferences getApplicationPreferences() {
		return mContext.getSharedPreferences(APPLICATIONS_NAME, Context.MODE_PRIVATE);
	}

	public List<String> getHomeTiles() {
		List<String> tiles = new ArrayList<>();
		try {
			String raw = getApplicationPreferences().getString(HOME_TILES, null);
			if (!TextUtils.isEmpty(raw)) {
				JSONArray array = new JSONArray(raw);
				for (int i = 0; i < array.length(); i++) {
					String value = array.optString(i, null);
					if (!TextUtils.isEmpty(value))
						tiles.add(value);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return tiles;
	}

	public void setHomeTiles(List<String> tiles) {
		try {
			getApplicationPreferences().edit()
					.putString(HOME_TILES, new JSONArray(tiles).toString()).apply();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void addHomeTile(String id) {
		if (TextUtils.isEmpty(id))
			return;
		List<String> tiles = getHomeTiles();
		if (tiles.contains(id))
			return;
		tiles.add(id);
		setHomeTiles(tiles);
	}

	public void removeHomeTile(String id) {
		List<String> tiles = getHomeTiles();
		if (tiles.remove(id))
			setHomeTiles(tiles);
	}

	public Set<String> getRemovedTiles() {
		return new HashSet<>(getApplicationPreferences().getStringSet(HOME_REMOVED, new HashSet<String>()));
	}

	public void addRemovedTile(String id) {
		if (TextUtils.isEmpty(id))
			return;
		Set<String> removed = getRemovedTiles();
		removed.add(id);
		getApplicationPreferences().edit().putStringSet(HOME_REMOVED, removed).apply();
	}

	public String getTimeFormat() {
		try {
			String value = getPreferences().getString(Preferences.PREFERENCE_TIME_FORMAT, null);
			if (!TextUtils.isEmpty(value))
				return value;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return "system";
	}

	private SharedPreferences getPreferences() {
		if (mPreferences == null) {
			mPreferences = PreferenceManager.getDefaultSharedPreferences(mContext);
		}
		return (mPreferences);
	}

	private int getInt(String name, int defaultValue) {
		try {
			String value = getPreferences().getString(name, null);
			if (value != null)
				return (Integer.parseInt(value));
		} catch (Exception e) {
			e.printStackTrace();
		}
		return (defaultValue);
	}

	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
	public boolean isDefaultTransparency() {
		try {
			return (getPreferences().getBoolean(PREFERENCE_DEFAULT_TRANSPARENCY, true));
		} catch (Exception e) {
			e.printStackTrace();
		}
		return (true);
	}

	public float getTransparency() {
		float defaultValue = Float.parseFloat(mContext.getString(R.string.transparency_default));
		try {
			return (getPreferences().getFloat(PREFERENCE_TRANSPARENCY, defaultValue));
		} catch (Exception e) {
			e.printStackTrace();
		}
		return (defaultValue);
	}

	public boolean showNetwork() {
		try {
			return (getPreferences().getBoolean(Preferences.PREFERENCE_SHOW_NETWORK, false));
		} catch (Exception e) {
			e.printStackTrace();
		}
		return (false);
	}

	public boolean showIp() {
		boolean defaultValue = mContext.getResources().getBoolean(R.bool.show_ip_default);
		try {
			return (getPreferences().getBoolean(Preferences.PREFERENCE_SHOW_IP, defaultValue));
		} catch (Exception e) {
			e.printStackTrace();
		}
		return (defaultValue);
	}

	public boolean useHomeImage() {
		try {
			return (getPreferences().getBoolean(Preferences.PREFERENCE_HOME_IMAGE, false));
		} catch (Exception e) {
			e.printStackTrace();
		}
		return (false);
	}

	public boolean fitHomeImage() {
		try {
			return (!"crop".equals(getPreferences().getString(Preferences.PREFERENCE_IMAGE_SCALE, "fit")));
		} catch (Exception e) {
			e.printStackTrace();
		}
		return (true);
	}

	public boolean showWallpaper() {
		boolean defaultValue = mContext.getResources().getBoolean(R.bool.show_wallpaper_default);
		try {
			return (getPreferences().getBoolean(Preferences.PREFERENCE_SHOW_WALLPAPER, defaultValue));
		} catch (Exception e) {
			e.printStackTrace();
		}
		return (defaultValue);
	}

	public boolean keepScreenOn() {
		try {
			return (getPreferences().getBoolean(Preferences.PREFERENCE_SCREEN_ON, false));
		} catch (Exception e) {
			e.printStackTrace();
		}
		return (false);
	}

	public boolean iconsLocked() {
		try {
			return (getPreferences().getBoolean(Preferences.PREFERENCE_LOCKED, false));
		} catch (Exception e) {
			e.printStackTrace();
		}
		return (false);
	}

	public boolean showDate() {
		boolean defaultValue = mContext.getResources().getBoolean(R.bool.show_date_default);
		try {
			return (getPreferences().getBoolean(Preferences.PREFERENCE_SHOW_DATE, defaultValue));
		} catch (Exception e) {
			e.printStackTrace();
		}
		return (defaultValue);
	}

	public boolean showBattery() {
		try {
			return (getPreferences().getBoolean(Preferences.PREFERENCE_SHOW_BATTERY, false));
		} catch (Exception e) {
			e.printStackTrace();
		}
		return (false);
	}

	public boolean showNames() {
		try {
			return (getPreferences().getBoolean(Preferences.PREFERENCE_SHOW_NAME, true));
		} catch (Exception e) {
			e.printStackTrace();
		}
		return (true);
	}


	public int getGridX() {
		return getInt(Preferences.PREFERENCE_GRID_X, DEFAULT_GRID_X);
	}

	public int getGridY() {
		return getInt(Preferences.PREFERENCE_GRID_Y, DEFAULT_GRID_Y);
	}

	public int getMarginX() {
		return getInt(Preferences.PREFERENCE_MARGIN_X, DEFAULT_MARGIN_X);
	}

	public int getMarginY() {
		return getInt(Preferences.PREFERENCE_MARGIN_Y, DEFAULT_MARGIN_Y);
	}
}
