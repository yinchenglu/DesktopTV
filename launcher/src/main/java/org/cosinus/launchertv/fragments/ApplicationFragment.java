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

package org.cosinus.launchertv.fragments;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.support.v4.app.Fragment;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.cosinus.launchertv.AppInfo;
import org.cosinus.launchertv.R;
import org.cosinus.launchertv.Setup;
import org.cosinus.launchertv.Network;
import org.cosinus.launchertv.Shortcuts;
import org.cosinus.launchertv.Folders;
import org.cosinus.launchertv.Protection;
import org.cosinus.launchertv.Utils;
import org.cosinus.launchertv.activities.Folder;
import org.cosinus.launchertv.activities.ApplicationList;
import org.cosinus.launchertv.activities.Preferences;
import org.cosinus.launchertv.activities.AddShortcut;
import org.cosinus.launchertv.views.ApplicationView;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@SuppressWarnings("PointlessBooleanExpression")
public class ApplicationFragment extends Fragment implements View.OnClickListener, View.OnLongClickListener {
	public static final String TAG = "ApplicationFragment";
	private static final String STATE_APPLICATION = "focused_application";
	private static final String STATE_BUTTON = "focused_button";
	private static final int REQUEST_CODE_UNLOCK = 0x22;
	// Popup menu actions
	private static final int MENU_OPEN = 0;
	private static final int MENU_MOVE_UP = 1;
	private static final int MENU_MOVE_DOWN = 2;
	private static final int MENU_UNINSTALL = 3;
	private static final int MENU_APP_INFO = 4;
	private static final int MENU_REMOVE = 5;

	private ImageView mNetworkIcon;
	private TextView mIp;
	private Network mNetwork;
	private TextView mClock;
	private TextView mDate;
	private DateFormat mTimeFormat;
	private DateFormat mDateFormat;
	private TextView mBatteryLevel;
	private ImageView mBatteryIcon;
	private BroadcastReceiver mBatteryChangedReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			final int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
			mBatteryLevel.setText(
					String.format(getResources().getString(R.string.battery_level_text), level)
			);
			final int batteryIconId = intent.getIntExtra(BatteryManager.EXTRA_ICON_SMALL, 0);
			mBatteryIcon.setImageDrawable(getResources().getDrawable(batteryIconId));
		}
	};
	private boolean mBatteryChangedReceiverRegistered = false;

	private final Handler mHandler = new Handler();
	private final Runnable mTimerTick = new Runnable() {
		@Override
		public void run() {
			setClock();
		}
	};

	private int mGridX = 3;
	private int mGridY = 2;
	private boolean mShowNames;
	private LinearLayout mBatteryLayout;
	private LinearLayout mContainer;
	private ApplicationView[][] mApplications = null;
	private List<String> mTileIds = new ArrayList<>();
	private View mSettings;
	private Setup mSetup;
	private boolean mShowingShortcut = false;
	private final SharedPreferences.OnSharedPreferenceChangeListener mShortcutChangedListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
		@Override
		public void onSharedPreferenceChanged(SharedPreferences preferences, String key) {
			if (Shortcuts.isPending(key))
				showShortcut();
		}
	};
	private boolean mPackageChangedReceiverRegistered = false;
	private final BroadcastReceiver mPackageChangedReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			rebuildHome();
		}
	};


	public ApplicationFragment() {
		// Required empty public constructor
	}

	public static ApplicationFragment newInstance() {
		return new ApplicationFragment();
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.fragment_application, container, false);

		mSetup = new Setup(getContext());
		mContainer = (LinearLayout) view.findViewById(R.id.container);
		mSettings = view.findViewById(R.id.settings);
		mIp = (TextView) view.findViewById(R.id.ip_address);
		mIp.setOnClickListener(this);
		mNetworkIcon = (ImageView) view.findViewById(R.id.network_icon);
		mNetwork = new Network(getContext(), mIp, mNetworkIcon);
		mClock = (TextView) view.findViewById(R.id.clock);
		mDate = (TextView) view.findViewById(R.id.date);
		mBatteryLayout = (LinearLayout) view.findViewById(R.id.battery_layout);
		mBatteryLevel = (TextView) view.findViewById(R.id.battery_level);
		mBatteryIcon = (ImageView) view.findViewById(R.id.battery_icon);

		updateTimeFormats();
		mDateFormat = android.text.format.DateFormat.getLongDateFormat(getActivity());

		mSettings.setOnClickListener(this);

		rebuildHome();

		return view;
	}

	@Override
	public void onSaveInstanceState(Bundle state) {
		super.onSaveInstanceState(state);
		if (getView() == null)
			return;
		View focused = getView().findFocus();
		if (focused instanceof ApplicationView) {
			state.putInt(STATE_APPLICATION, ((ApplicationView) focused).getPosition());
		} else if (focused != null) {
			state.putInt(STATE_BUTTON, focused.getId());
		}
	}

	@Override
	public void onViewStateRestored(Bundle state) {
		super.onViewStateRestored(state);
		if (state == null)
			return;
		int position = state.getInt(STATE_APPLICATION, -1);
		if (position >= 0 && position < mTileIds.size()) {
			focusPosition(position);
		} else {
			View focused = getView().findViewById(state.getInt(STATE_BUTTON, View.NO_ID));
			if (focused != null)
				focused.requestFocus();
		}
	}

	private void focusPosition(int position) {
		if (position < 0 || position >= mTileIds.size())
			return;
		int y = position / mGridX;
		int x = position % mGridX;
		if (y < mGridY && mApplications[y] != null && x < mApplications[y].length && mApplications[y][x] != null)
			mApplications[y][x].requestFocus();
	}

	/**
	 * Builds the home screen: manually placed tiles first, then every
	 * remaining installed application sorted by name. All applications
	 * are directly visible on the home screen.
	 */
	private void rebuildHome() {
		View focusedBefore = getView() != null ? getView().findFocus() : null;
		int focusPosition = focusedBefore instanceof ApplicationView ? ((ApplicationView) focusedBefore).getPosition() : -1;

		mGridX = Math.max(2, mSetup.getGridX());
		mShowNames = mSetup.showNames();

		mTileIds = getDisplayedTiles();
		int count = mTileIds.size();
		mGridY = Math.max(1, (count + mGridX - 1) / mGridX);

		int marginX = Utils.getPixelFromDp(getContext(), mSetup.getMarginX());
		int marginY = Utils.getPixelFromDp(getContext(), mSetup.getMarginY());

		mContainer.removeAllViews();
		mApplications = new ApplicationView[mGridY][mGridX];

		int position = 0;
		for (int y = 0; y < mGridY; y++) {
			LinearLayout ll = new LinearLayout(getContext());
			ll.setOrientation(LinearLayout.HORIZONTAL);
			ll.setGravity(Gravity.TOP);
			ll.setFocusable(false);
			ll.setLayoutParams(new LinearLayout.LayoutParams(
					LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
			));

			for (int x = 0; x < mGridX; x++) {
				if (position >= count) {
					mApplications[y][x] = null;
					continue;
				}
				ApplicationView av = new ApplicationView(getContext());
				av.setOnClickListener(this);
				av.setOnLongClickListener(this);
				av.setOnMenuOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						onLongClick(v);
					}
				});
				av.setPosition(position);
				av.showName(mShowNames);
				if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1) {
					av.setId(0x00FFFFFF + position + 1);
				} else {
					av.setId(View.generateViewId());
				}
				LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
				lp.setMargins(x == 0 ? 0 : marginX, y == 0 ? 0 : marginY,
						x + 1 == mGridX ? 0 : marginX, marginY);
				av.setLayoutParams(lp);
				ll.addView(av);
				mApplications[y][x] = av;
				position++;
			}
			mContainer.addView(ll);
		}

		updateApplications();
		setApplicationOrder();

		if (focusPosition >= 0) {
			focusPosition = Math.min(focusPosition, mTileIds.size() - 1);
			focusPosition(focusPosition);
		}
	}

	/**
	 * Returns the tile identifiers displayed on the home screen:
	 * the manually ordered tiles followed by all remaining
	 * installed applications (sorted by name).
	 */
	private List<String> getDisplayedTiles() {
		List<String> tiles = mSetup.getHomeTiles();
		PackageManager pm = getActivity().getPackageManager();
		Shortcuts shortcuts = new Shortcuts(getContext());
		Folders folders = new Folders(getContext());
		Protection protection = new Protection(getContext());
		Set<String> removed = mSetup.getRemovedTiles();

		boolean changed = false;
		java.util.Iterator<String> it = tiles.iterator();
		while (it.hasNext()) {
			String id = it.next();
			boolean valid;
			if (TextUtils.isEmpty(id))
				valid = false;
			else if (Folders.isFolder(id))
				valid = folders.get(id) != null;
			else if (Shortcuts.isShortcut(id))
				valid = shortcuts.get(id) != null;
			else {
				try {
					pm.getPackageInfo(id, 0);
					valid = true;
				} catch (Exception e) {
					valid = false;
				}
			}
			if (!valid || removed.contains(id) || protection.isHidden(id)) {
				it.remove();
				changed = true;
			}
		}
		if (changed)
			mSetup.setHomeTiles(tiles);

		List<AppInfo> apps = Utils.loadApplications(getContext());
		for (AppInfo app : apps) {
			if (!tiles.contains(app.getId()) && !removed.contains(app.getId()))
				tiles.add(app.getId());
		}
		return tiles;
	}

	private void setApplicationOrder() {
		for (int y = 0; y < mGridY; y++) {
			for (int x = 0; x < mGridX; x++) {
				ApplicationView app = mApplications[y][x];
				if (app == null)
					continue;

				int upId = R.id.settings;
				if (y > 0 && mApplications[y - 1][x] != null)
					upId = mApplications[y - 1][x].getId();

				int downId = mSetup.showIp() ? R.id.ip_address : R.id.settings;
				if (y + 1 < mGridY && mApplications[y + 1][x] != null)
					downId = mApplications[y + 1][x].getId();

				int leftId = R.id.settings;
				if (x > 0 && mApplications[y][x - 1] != null)
					leftId = mApplications[y][x - 1].getId();
				else if (y > 0) {
					ApplicationView previous = lastTileOfRow(y - 1);
					if (previous != null)
						leftId = previous.getId();
				}

				int rightId = R.id.settings;
				if (x + 1 < mGridX && mApplications[y][x + 1] != null)
					rightId = mApplications[y][x + 1].getId();
				else if (y + 1 < mGridY && mApplications[y + 1][0] != null)
					rightId = mApplications[y + 1][0].getId();

				app.setNextFocusLeftId(leftId);
				app.setNextFocusRightId(rightId);
				app.setNextFocusUpId(upId);
				app.setNextFocusDownId(downId);
			}
		}

		ApplicationView first = firstTileOfRow(0);
		ApplicationView last = lastTileOfRow(mGridY - 1);
		mIp.setNextFocusLeftId(last == null ? R.id.settings : last.getId());
		mIp.setNextFocusRightId(R.id.settings);
		mIp.setNextFocusUpId(first == null ? R.id.settings : first.getId());
		mIp.setNextFocusDownId(R.id.settings);
		mSettings.setNextFocusLeftId(R.id.settings);
		mSettings.setNextFocusRightId(R.id.settings);
		mSettings.setNextFocusUpId(R.id.settings);
		mSettings.setNextFocusDownId(first == null ? R.id.settings : first.getId());
	}

	private ApplicationView firstTileOfRow(int y) {
		if (y < 0 || y >= mGridY)
			return null;
		for (int x = 0; x < mGridX; x++) {
			if (mApplications[y][x] != null)
				return mApplications[y][x];
		}
		return null;
	}

	private ApplicationView lastTileOfRow(int y) {
		if (y < 0 || y >= mGridY)
			return null;
		for (int x = mGridX - 1; x >= 0; x--) {
			if (mApplications[y][x] != null)
				return mApplications[y][x];
		}
		return null;
	}


	private void updateApplications() {
		PackageManager pm = getActivity().getPackageManager();

		for (int y = 0; y < mGridY; y++) {
			for (int x = 0; x < mGridX; x++) {
				ApplicationView app = mApplications[y][x];
				if (app == null)
					continue;
				String appId = app.getPosition() < mTileIds.size() ? mTileIds.get(app.getPosition()) : null;
				setApplication(pm, app, appId);
			}
		}
	}


	private void setApplication(PackageManager pm, ApplicationView app, String appId) {
		if (new Protection(getContext()).isHidden(appId))
			appId = null;
		try {
			if (Shortcuts.isShortcut(appId) || Folders.isFolder(appId)) {
				AppInfo shortcut = Folders.isFolder(appId) ? new Folders(getContext()).get(appId) : new Shortcuts(getContext()).get(appId);
				if (shortcut == null)
					setUnavailableApplication(app, appId);
				else
					app.setImageDrawable(shortcut.getIcon()).setText(shortcut.getName()).setAppId(appId);
				return;
			}

			if (TextUtils.isEmpty(appId) == false) {
				PackageInfo pi = pm.getPackageInfo(appId, 0);
				if (pi.applicationInfo.enabled) {
					AppInfo appInfo = new AppInfo(pm, pi.applicationInfo);
					app.setImageDrawable(appInfo.getIcon())
							.setText(appInfo.getName())
							.setAppId(appInfo.getPackageName());
				} else {
					setUnavailableApplication(app, appId);
				}
			} else {
				app.setImageResource(R.drawable.ic_add)
						.setText("")
						.setAppId(null);
			}
		} catch (PackageManager.NameNotFoundException e) {
			setUnavailableApplication(app, appId);
		} catch (Exception e) {
			e.printStackTrace();
			setUnavailableApplication(app, appId);
		}
	}

	private void setUnavailableApplication(ApplicationView app, String appId) {
		app.setImageResource(android.R.drawable.ic_dialog_alert)
				.setText(getString(R.string.application_unavailable, appId))
				.setAppId(appId);
	}

	private void showShortcut() {
		if (!isResumed() || mShowingShortcut)
			return;
		String pending = new Shortcuts(getContext()).getPending();
		if (pending != null) {
			mShowingShortcut = true;
			Intent intent = new Intent(getActivity(), AddShortcut.class);
			intent.putExtra(AddShortcut.SHORTCUT_ID, pending);
			startActivity(intent);
		}
	}

	private void registerPackageReceiver() {
		if (mPackageChangedReceiverRegistered)
			return;
		IntentFilter filter = new IntentFilter(Intent.ACTION_PACKAGE_ADDED);
		filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
		filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
		filter.addAction(Intent.ACTION_PACKAGE_REPLACED);
		filter.addDataScheme("package");
		getActivity().registerReceiver(mPackageChangedReceiver, filter);
		mPackageChangedReceiverRegistered = true;
		IntentFilter storageFilter = new IntentFilter(Intent.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE);
		storageFilter.addAction(Intent.ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE);
		getActivity().registerReceiver(mPackageChangedReceiver, storageFilter);
	}

	@Override
	public void onResume() {
		super.onResume();
		mContainer.setKeepScreenOn(mSetup.keepScreenOn());
		mDate.setVisibility(mSetup.showDate() ? View.VISIBLE : View.GONE);
		mBatteryLayout.setVisibility(mSetup.showBattery() ? View.VISIBLE : View.INVISIBLE);
		mIp.setVisibility(mSetup.showIp() ? View.VISIBLE : View.GONE);
		mNetworkIcon.setVisibility(mSetup.showNetwork() ? View.VISIBLE : View.GONE);
		if (mSetup.showIp() || mSetup.showNetwork())
			mNetwork.start();
		else
			mNetwork.stop();
		updateTimeFormats();
		mDateFormat = android.text.format.DateFormat.getLongDateFormat(getActivity());
		setClock();
		rebuildHome();
		getActivity().getSharedPreferences(Setup.APPLICATIONS_NAME, Context.MODE_PRIVATE)
				.registerOnSharedPreferenceChangeListener(mShortcutChangedListener);
		mShowingShortcut = false;
		showShortcut();
		registerPackageReceiver();
		if (mSetup.showBattery() && !mBatteryChangedReceiverRegistered) {
			getActivity().registerReceiver(this.mBatteryChangedReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
			mBatteryChangedReceiverRegistered = true;
		}
	}

	@Override
	public void onPause() {
		super.onPause();
		mNetwork.stop();
		getActivity().getSharedPreferences(Setup.APPLICATIONS_NAME, Context.MODE_PRIVATE)
				.unregisterOnSharedPreferenceChangeListener(mShortcutChangedListener);
		mHandler.removeCallbacks(mTimerTick);
		if (mBatteryChangedReceiverRegistered) {
			getActivity().unregisterReceiver(this.mBatteryChangedReceiver);
			mBatteryChangedReceiverRegistered = false;
		}
		if (mPackageChangedReceiverRegistered) {
			getActivity().unregisterReceiver(mPackageChangedReceiver);
			mPackageChangedReceiverRegistered = false;
		}
	}

	@Override
	public void onDestroyView() {
		if (mNetwork != null)
			mNetwork.stop();
		mNetwork = null;
		mIp = null;
		mNetworkIcon = null;
		mBatteryLayout = null;
		super.onDestroyView();
	}

	/**
	 * Clock format: follows the system setting by default, and can be
	 * forced to 24-hour or 12-hour mode from the preferences.
	 */
	private void updateTimeFormats() {
		String preference = mSetup.getTimeFormat();
		if ("24".equals(preference))
			mTimeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
		else if ("12".equals(preference))
			mTimeFormat = new SimpleDateFormat("ah:mm", Locale.getDefault());
		else
			mTimeFormat = android.text.format.DateFormat.getTimeFormat(getActivity());
	}

	private void setClock() {
		Date date = new Date(System.currentTimeMillis());
		String time = mTimeFormat.format(date);
		String day = mDateFormat.format(date);
		if (!TextUtils.equals(mClock.getText(), time))
			mClock.setText(time);
		if (!TextUtils.equals(mDate.getText(), day))
			mDate.setText(day);
		mHandler.postDelayed(mTimerTick, 1000);
	}

	@Override
	public boolean onLongClick(View v) {
		if (v instanceof ApplicationView) {
			ApplicationView appView = (ApplicationView) v;
			if (mSetup.iconsLocked()) {
				Toast.makeText(getActivity(), R.string.home_locked, Toast.LENGTH_SHORT).show();
			} else if (appView.hasApp()) {
				showApplicationMenu(appView);
			}
			return (true);
		}
		return (false);
	}

	/**
	 * Popup menu shown when the menu key is pressed (or the tile is
	 * long pressed) on a selected application.
	 */
	private void showApplicationMenu(final ApplicationView appView) {
		final int position = appView.getPosition();
		final String appId = appView.getAppId();
		if (TextUtils.isEmpty(appId))
			return;

		boolean isPackage = !Folders.isFolder(appId) && !Shortcuts.isShortcut(appId);

		final List<Integer> actions = new ArrayList<>();
		List<String> labels = new ArrayList<>();

		labels.add(getString(R.string.menu_open));
		actions.add(MENU_OPEN);
		if (position > 0) {
			labels.add(getString(R.string.menu_move_up));
			actions.add(MENU_MOVE_UP);
		}
		if (position < mTileIds.size() - 1) {
			labels.add(getString(R.string.menu_move_down));
			actions.add(MENU_MOVE_DOWN);
		}
		if (isPackage) {
			labels.add(getString(R.string.menu_uninstall));
			actions.add(MENU_UNINSTALL);
			labels.add(getString(R.string.menu_app_info));
			actions.add(MENU_APP_INFO);
		}
		labels.add(getString(R.string.menu_remove));
		actions.add(MENU_REMOVE);

		new AlertDialog.Builder(getActivity())
				.setTitle(appView.getName())
				.setItems(labels.toArray(new String[labels.size()]), new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						handleMenuAction(actions.get(which), appView, appId);
					}
				})
				.show();
	}

	private void handleMenuAction(int action, ApplicationView appView, String appId) {
		int position = appView.getPosition();
		switch (action) {
			case MENU_OPEN:
				openApplication(appId, appView.getName());
				break;
			case MENU_MOVE_UP:
			case MENU_MOVE_DOWN: {
				int target = action == MENU_MOVE_UP ? position - 1 : position + 1;
				if (target < 0 || target >= mTileIds.size())
					break;
				List<String> tiles = new ArrayList<>(mTileIds);
				Collections.swap(tiles, position, target);
				mSetup.setHomeTiles(tiles);
				rebuildHome();
				focusPosition(target);
				break;
			}
			case MENU_UNINSTALL:
				try {
					Intent uninstall = new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + appId));
					startActivity(uninstall);
				} catch (Exception e) {
					e.printStackTrace();
					Toast.makeText(getActivity(), R.string.uninstall_unavailable, Toast.LENGTH_LONG).show();
				}
				break;
			case MENU_APP_INFO:
				try {
					startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
							Uri.parse("package:" + appId)));
				} catch (Exception e) {
					e.printStackTrace();
					Toast.makeText(getActivity(), R.string.settings_unavailable, Toast.LENGTH_LONG).show();
				}
				break;
			case MENU_REMOVE:
				mSetup.addRemovedTile(appId);
				mSetup.removeHomeTile(appId);
				rebuildHome();
				break;
			default:
				break;
		}
	}

	@Override
	public void onClick(View v) {
		if (v instanceof ApplicationView) {
			openApplication((ApplicationView) v);
			return;
		}

		int id = v.getId();
		if (id == R.id.ip_address) {
			mNetwork.showDetails();
		} else if (id == R.id.settings) {
			startActivity(new Intent(getContext(), Preferences.class));
		}

	}

	private void openApplication(ApplicationView v) {
		if (v.hasApp() == false)
			return;

		openApplication(v.getAppId(), v.getName());
	}

	private void openApplication(String appId) {
		openApplication(appId, appId);
	}

	private void openApplication(String appId, String name) {
		if (new Protection(getContext()).isLocked(appId)) {
			startActivityForResult(Protection.getIntent(getContext(), appId), REQUEST_CODE_UNLOCK);
			return;
		}
		launchApplication(appId, name);
	}

	private void launchApplication(String appId, String name) {
		try {
			if (Folders.isFolder(appId)) {
				Intent intent = new Intent(getActivity(), Folder.class);
				intent.putExtra(ApplicationList.APP_ID, appId);
				intent.putExtra(Folder.AUTHORIZED, true);
				startActivity(intent);
				return;
			}
			if (Shortcuts.isShortcut(appId)) {
				new Shortcuts(getContext()).launch(appId);
				return;
			}
			Intent startApp = Utils.getLaunchIntentForPackage(getContext(), appId);
			if (startApp == null) {
				Toast.makeText(getActivity(), getString(R.string.application_unavailable, appId), Toast.LENGTH_LONG).show();
				return;
			}
			Toast.makeText(getActivity(), name, Toast.LENGTH_SHORT).show();
			startActivity(startApp);
		} catch (Exception e) {
			String message = Shortcuts.isShortcut(appId) ? getString(R.string.application_unavailable, name) : name + " : " + e.getMessage();
			Toast.makeText(getActivity(), message, Toast.LENGTH_LONG).show();
		}
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent intent) {
		super.onActivityResult(requestCode, resultCode, intent);
		if (requestCode == REQUEST_CODE_UNLOCK) {
			if (resultCode == Activity.RESULT_OK && intent != null) {
				String id = intent.getStringExtra(ApplicationList.APP_ID);
				openApplication(id, id);
			}
		}
	}


}
