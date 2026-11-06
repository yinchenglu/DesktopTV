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

package com.yinchenglu.desktoptv.fragments;

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
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.yinchenglu.desktoptv.AppInfo;
import com.yinchenglu.desktoptv.R;
import com.yinchenglu.desktoptv.Setup;
import com.yinchenglu.desktoptv.Network;
import com.yinchenglu.desktoptv.Shortcuts;
import com.yinchenglu.desktoptv.Folders;
import com.yinchenglu.desktoptv.Protection;
import com.yinchenglu.desktoptv.Utils;
import com.yinchenglu.desktoptv.activities.Folder;
import com.yinchenglu.desktoptv.activities.ApplicationList;
import com.yinchenglu.desktoptv.activities.Preferences;
import com.yinchenglu.desktoptv.activities.AddShortcut;
import com.yinchenglu.desktoptv.activities.FileManager;
import com.yinchenglu.desktoptv.activities.QrTransfer;
import com.yinchenglu.desktoptv.views.ApplicationView;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
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
	private static final int MENU_MOVE = 1;
	private static final int MENU_UNINSTALL = 3;
	private static final int MENU_APP_INFO = 4;
	private static final int MENU_REMOVE = 5;

	private ImageView mNetworkIcon;
	private TextView mIp;
	private TextView mMoveHint;
	private View mCleanup;
	private View mFileManager;
	private View mQrTransfer;
	private Network mNetwork;
	private boolean mMoveMode = false;
	private int mMovePosition = -1;
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
	private boolean mCleaning = false;
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
		mCleanup = view.findViewById(R.id.cleanup);
		mFileManager = view.findViewById(R.id.filemanager);
		mQrTransfer = view.findViewById(R.id.qrtransfer);
		mIp = (TextView) view.findViewById(R.id.ip_address);
		mMoveHint = (TextView) view.findViewById(R.id.move_hint);
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
		mCleanup.setOnClickListener(this);
		mFileManager.setOnClickListener(this);
		mQrTransfer.setOnClickListener(this);

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
		int focusButtonId = View.NO_ID;
		if (focusedBefore != null && !(focusedBefore instanceof ApplicationView) && isTopButton(focusedBefore))
			focusButtonId = focusedBefore.getId();

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
					// Keep the grid geometry of a full row so the icons of a
					// partially filled last row do not stretch to fill it.
					mApplications[y][x] = null;
					View filler = new View(getContext());
					filler.setFocusable(false);
					LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(0, 0, 1);
					flp.setMargins(x == 0 ? 0 : marginX, y == 0 ? 0 : marginY,
							x + 1 == mGridX ? 0 : marginX, marginY);
					filler.setLayoutParams(flp);
					ll.addView(filler);
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
		} else if (focusButtonId != View.NO_ID && getView() != null) {
			View button = getView().findViewById(focusButtonId);
			if (button != null)
				button.requestFocus();
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
		Set<String> hidden = mSetup.getHiddenApps();

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
			if (!valid || hidden.contains(id)) {
				it.remove();
				changed = true;
			}
		}
		if (changed)
			mSetup.setHomeTiles(tiles);

		List<AppInfo> apps = Utils.loadApplications(getContext());
		for (AppInfo app : apps) {
			if (!tiles.contains(app.getId()) && !hidden.contains(app.getId()))
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

				int upId;
				if (y > 0) {
					ApplicationView above = mApplications[y - 1][x] != null
							? mApplications[y - 1][x] : lastTileOfRow(y - 1);
					upId = above == null ? topButtonForColumn(x).getId() : above.getId();
				} else {
					// First row: the up key reaches the buttons of the top bar.
					upId = topButtonForColumn(x).getId();
				}

				int downId = app.getId();
				if (y + 1 < mGridY) {
					ApplicationView below = mApplications[y + 1][x] != null
							? mApplications[y + 1][x] : lastTileOfRow(y + 1);
					if (below != null)
						downId = below.getId();
				}

				int leftId = app.getId();
				if (x > 0 && mApplications[y][x - 1] != null)
					leftId = mApplications[y][x - 1].getId();
				else if (y > 0) {
					ApplicationView previous = lastTileOfRow(y - 1);
					if (previous != null)
						leftId = previous.getId();
				}

				int rightId = app.getId();
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

		setTopBarNavigation();
	}

	/**
	 * Buttons of the top bar, from left to right.
	 */
	private View[] topButtons() {
		return (new View[]{mCleanup, mFileManager, mQrTransfer, mSettings});
	}

	private boolean isTopButton(View view) {
		for (View button : topButtons()) {
			if (button != null && button == view)
				return (true);
		}
		return (false);
	}

	/**
	 * Top bar button horizontally aligned with the given grid column.
	 */
	private View topButtonForColumn(int x) {
		View[] buttons = topButtons();
		if (mGridX <= 1)
			return (buttons[buttons.length - 1]);
		int index = Math.round((float) x * (buttons.length - 1) / (mGridX - 1));
		index = Math.max(0, Math.min(buttons.length - 1, index));
		return (buttons[index]);
	}

	/**
	 * Grid column placed under the given top bar button.
	 */
	private int columnForTopButton(int index) {
		View[] buttons = topButtons();
		if (buttons.length <= 1)
			return (0);
		int column = Math.round((float) index * (mGridX - 1) / (buttons.length - 1));
		return (Math.max(0, Math.min(mGridX - 1, column)));
	}

	/**
	 * First tile of the first row closest to the given column.
	 */
	private ApplicationView firstRowTileNearColumn(int column) {
		if (mGridY < 1)
			return (null);
		for (int offset = 0; offset < mGridX; offset++) {
			int left = column - offset;
			if (left >= 0 && left < mGridX && mApplications[0][left] != null)
				return (mApplications[0][left]);
			int right = column + offset;
			if (right >= 0 && right < mGridX && mApplications[0][right] != null)
				return (mApplications[0][right]);
		}
		return (null);
	}

	/**
	 * Makes the top bar reachable with the remote control: left/right
	 * walk through the buttons and the down key drops back into the grid.
	 */
	private void setTopBarNavigation() {
		View[] buttons = topButtons();
		for (int i = 0; i < buttons.length; i++) {
			View button = buttons[i];
			if (button == null)
				continue;
			button.setNextFocusLeftId(buttons[Math.max(0, i - 1)].getId());
			button.setNextFocusRightId(buttons[Math.min(buttons.length - 1, i + 1)].getId());
			button.setNextFocusUpId(button.getId());
			ApplicationView below = firstRowTileNearColumn(columnForTopButton(i));
			button.setNextFocusDownId(below == null ? button.getId() : below.getId());
		}
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
		if (mSetup.isHidden(appId))
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
		// Nothing focused yet: start on the first tile instead of letting the
		// system pick the first focusable button of the top bar.
		if (getView() != null && getView().findFocus() == null) {
			ApplicationView first = firstRowTileNearColumn(0);
			if (first != null)
				first.requestFocus();
		}
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
		setMoveMode(false);
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
		final String appId = appView.getAppId();
		if (TextUtils.isEmpty(appId))
			return;

		boolean isPackage = !Folders.isFolder(appId) && !Shortcuts.isShortcut(appId);

		final List<Integer> actions = new ArrayList<>();
		List<String> labels = new ArrayList<>();

		labels.add(getString(R.string.menu_open));
		actions.add(MENU_OPEN);
		if (mTileIds.size() > 1) {
			labels.add(getString(R.string.menu_move));
			actions.add(MENU_MOVE);
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
		switch (action) {
			case MENU_OPEN:
				openApplication(appId, appView.getName());
				break;
			case MENU_MOVE:
				startMoveMode(appView.getPosition());
				break;
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
				mSetup.setHidden(appId, true);
				rebuildHome();
				Toast.makeText(getActivity(), R.string.menu_remove_done, Toast.LENGTH_SHORT).show();
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

		if (v.getId() == R.id.settings)
			startActivity(new Intent(getContext(), Preferences.class));
		else if (v.getId() == R.id.cleanup)
			cleanupMemory();
		else if (v.getId() == R.id.filemanager)
			startActivity(new Intent(getContext(), FileManager.class));
		else if (v.getId() == R.id.qrtransfer)
			startActivity(new Intent(getContext(), QrTransfer.class));
	}

	/**
	 * Kills every background process and reports the memory freed.
	 * The launcher itself is never killed.
	 */
	private void cleanupMemory() {
		final Activity activity = getActivity();
		if (activity == null || mCleaning)
			return;
		mCleaning = true;
		Toast.makeText(activity, R.string.cleaning_memory, Toast.LENGTH_SHORT).show();
		new Thread(new Runnable() {
			@Override
			public void run() {
				final long freed = Utils.cleanMemory(activity.getApplicationContext());
				if (getActivity() == null || isDetached())
					return;
				getActivity().runOnUiThread(new Runnable() {
					@Override
					public void run() {
						mCleaning = false;
						if (getActivity() != null)
							Toast.makeText(getActivity(),
									getString(R.string.memory_cleaned, freed),
									Toast.LENGTH_SHORT).show();
					}
				});
			}
		}).start();
	}

	/**
	 * Remote keys are intercepted by the hosting activity while a tile is
	 * being moved, so the direction keys reorder the grid instead of moving
	 * the focus.
	 */
	public boolean handleKeyEvent(KeyEvent event) {
		if (!mMoveMode)
			return (false);
		if (event.getAction() == KeyEvent.ACTION_UP)
			return (true);
		switch (event.getKeyCode()) {
			case KeyEvent.KEYCODE_DPAD_UP:
				// Already on the first row: nothing above to move into.
				if (mMovePosition >= mGridX)
					moveTileTo(mMovePosition - mGridX);
				return (true);
			case KeyEvent.KEYCODE_DPAD_DOWN:
				// Already on the last row: nothing below to move into.
				if (mMovePosition < (mGridY - 1) * mGridX)
					moveTileTo(mMovePosition + mGridX);
				return (true);
			case KeyEvent.KEYCODE_DPAD_LEFT:
				moveTileTo(mMovePosition - 1);
				return (true);
			case KeyEvent.KEYCODE_DPAD_RIGHT:
				moveTileTo(mMovePosition + 1);
				return (true);
			case KeyEvent.KEYCODE_ENTER:
			case KeyEvent.KEYCODE_DPAD_CENTER:
			case KeyEvent.KEYCODE_NUMPAD_ENTER:
			case KeyEvent.KEYCODE_BACK:
				setMoveMode(false);
				Toast.makeText(getActivity(), R.string.move_saved, Toast.LENGTH_SHORT).show();
				return (true);
			case KeyEvent.KEYCODE_MENU:
				return (true);
			default:
				return (false);
		}
	}

	private void startMoveMode(int position) {
		if (position < 0 || position >= mTileIds.size())
			return;
		mMovePosition = position;
		setMoveMode(true);
		focusPosition(position);
	}

	private void setMoveMode(boolean moving) {
		mMoveMode = moving;
		if (!moving)
			mMovePosition = -1;
		if (mMoveHint != null)
			mMoveHint.setVisibility(moving ? View.VISIBLE : View.GONE);
	}

	/**
	 * Inserts the tile being moved into the target slot instead of swapping
	 * it with the tile already there: the tiles between the old and the new
	 * slot all slide back by one place.
	 * <p>
	 * The target is clamped to the grid, so the leftmost tile of a row can
	 * move left to the last slot of the previous row (and the rightmost tile
	 * of a row to the first slot of the next one).
	 */
	private void moveTileTo(int target) {
		if (mMovePosition < 0)
			return;
		int count = mTileIds.size();
		if (count < 2)
			return;
		if (target < 0)
			target = 0;
		if (target >= count)
			target = count - 1;
		if (target == mMovePosition)
			return;
		List<String> tiles = new ArrayList<>(mTileIds);
		String moved = tiles.remove(mMovePosition);
		tiles.add(target, moved);
		mSetup.setHomeTiles(tiles);
		mMovePosition = target;
		rebuildHome();
		focusPosition(target);
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
