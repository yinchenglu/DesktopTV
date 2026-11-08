package com.yinchenglu.desktoptv.study;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;
import android.text.TextUtils;

import com.yinchenglu.desktoptv.R;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Rules and time ledger of the built-in study module.
 *
 * The model follows 题了个题 (StudyOnTV): a group of questions unlocks a
 * number of minutes of screen time, the daily amount of earned time is
 * capped, and wrong answers feed an extra-practice pool that must be
 * cleared before the next reward.
 */
public class Study {
	public static final String KEY_ENABLED = "preference_study_enabled";
	public static final String KEY_GRADE = "preference_study_grade";
	public static final String KEY_MODE = "preference_study_mode";
	public static final String KEY_SIZE = "preference_study_size";
	public static final String KEY_PASS = "preference_study_pass";
	public static final String KEY_UNLOCK = "preference_study_unlock";
	public static final String KEY_LIMIT = "preference_study_limit";

	public static final String MODE_GROUP = "group";
	public static final String MODE_STREAK = "streak";
	public static final String MODE_FREE = "free";

	public static final int DEFAULT_GRADE = 3;
	public static final int DEFAULT_SIZE = 10;
	public static final int DEFAULT_PASS = 70;
	public static final int DEFAULT_UNLOCK = 15;
	public static final int DEFAULT_LIMIT = 120;

	private static final String KEY_BALANCE = "study_balance";
	private static final String KEY_EARNED = "study_earned";
	private static final String KEY_USED = "study_used";
	private static final String KEY_DAY = "study_day";
	private static final String KEY_EXTRA = "study_extra";

	private final Context mContext;

	public Study(Context context) {
		mContext = context;
	}

	private SharedPreferences preferences() {
		return (PreferenceManager.getDefaultSharedPreferences(mContext));
	}

	private void ensureToday() {
		String today = today();
		SharedPreferences preferences = preferences();
		if (!TextUtils.equals(today, preferences.getString(KEY_DAY, null))) {
			preferences.edit()
					.putString(KEY_DAY, today)
					.putInt(KEY_EARNED, 0)
					.putInt(KEY_USED, 0)
					.apply();
		}
	}

	private static String today() {
		return (new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date()));
	}

	public boolean isGuardEnabled() {
		return (preferences().getBoolean(KEY_ENABLED, true));
	}

	public void setGuardEnabled(boolean enabled) {
		preferences().edit().putBoolean(KEY_ENABLED, enabled).apply();
	}

	public int getGrade() {
		return (readInt(KEY_GRADE, DEFAULT_GRADE));
	}

	public String getMode() {
		String mode = preferences().getString(KEY_MODE, MODE_GROUP);
		if (MODE_STREAK.equals(mode) || MODE_FREE.equals(mode) || MODE_GROUP.equals(mode))
			return (mode);
		return (MODE_GROUP);
	}

	public int getGroupSize() {
		return (Math.max(3, readInt(KEY_SIZE, DEFAULT_SIZE)));
	}

	public int getPassRate() {
		return (Math.max(10, Math.min(100, readInt(KEY_PASS, DEFAULT_PASS))));
	}

	public int getUnlockMinutes() {
		return (Math.max(1, readInt(KEY_UNLOCK, DEFAULT_UNLOCK)));
	}

	public int getDailyLimitMinutes() {
		return (Math.max(1, readInt(KEY_LIMIT, DEFAULT_LIMIT)));
	}

	public String getGradeName() {
		String[] names = mContext.getResources().getStringArray(R.array.study_grades);
		int index = getGrade() - 1;
		if (index < 0 || index >= names.length)
			index = DEFAULT_GRADE - 1;
		return (names[index]);
	}

	private int readInt(String key, int fallback) {
		try {
			return (Integer.parseInt(preferences().getString(key, String.valueOf(fallback))));
		} catch (Exception e) {
			return (fallback);
		}
	}

	// ---------------------------------------------------------------- ledger

	public int getBalance() {
		return (Math.max(0, preferences().getInt(KEY_BALANCE, 0)));
	}

	public void setBalance(int seconds) {
		preferences().edit().putInt(KEY_BALANCE, Math.max(0, seconds)).apply();
	}

	public int getEarnedToday() {
		ensureToday();
		return (preferences().getInt(KEY_EARNED, 0));
	}

	public int getUsedToday() {
		ensureToday();
		return (preferences().getInt(KEY_USED, 0));
	}

	public int getDailyLimitSeconds() {
		return (getDailyLimitMinutes() * 60);
	}

	public int getRemainingDailyReward() {
		return (Math.max(0, getDailyLimitSeconds() - getEarnedToday()));
	}

	/**
	 * Adds unlocked time to the balance, limited by the daily cap.
	 *
	 * @return the amount actually granted, in seconds.
	 */
	public int reward(int seconds) {
		ensureToday();
		int granted = Math.min(Math.max(0, seconds), getRemainingDailyReward());
		if (granted <= 0)
			return (0);
		SharedPreferences preferences = preferences();
		preferences.edit()
				.putInt(KEY_BALANCE, getBalance() + granted)
				.putInt(KEY_EARNED, preferences.getInt(KEY_EARNED, 0) + granted)
				.apply();
		return (granted);
	}

	public void consume(int seconds) {
		if (seconds <= 0)
			return;
		ensureToday();
		SharedPreferences preferences = preferences();
		int used = preferences.getInt(KEY_USED, 0) + seconds;
		preferences.edit()
				.putInt(KEY_BALANCE, Math.max(0, getBalance() - seconds))
				.putInt(KEY_USED, used)
				.apply();
	}

	public int getExtraPool() {
		return (Math.max(0, preferences().getInt(KEY_EXTRA, 0)));
	}

	public void addExtra(int count) {
		if (count <= 0)
			return;
		preferences().edit().putInt(KEY_EXTRA, getExtraPool() + count).apply();
	}

	public void clearExtra() {
		preferences().edit().putInt(KEY_EXTRA, 0).apply();
	}

	public void resetToday() {
		preferences().edit()
				.putInt(KEY_BALANCE, 0)
				.putInt(KEY_EARNED, 0)
				.putInt(KEY_USED, 0)
				.putInt(KEY_EXTRA, 0)
				.putString(KEY_DAY, today())
				.apply();
	}

	// ----------------------------------------------------------- watch timer

	public boolean canUseApp() {
		return (!isGuardEnabled() || getBalance() > 0);
	}

	/**
	 * Starts the countdown that runs while another application is on screen.
	 */
	public void startWatch() {
		if (!isGuardEnabled())
			return;
		int balance = getBalance();
		if (balance <= 0)
			return;
		Intent intent = new Intent(mContext, StudyGuardService.class);
		intent.putExtra(StudyGuardService.EXTRA_SECONDS, balance);
		try {
			if (Build.VERSION.SDK_INT >= 26)
				mContext.startForegroundService(intent);
			else
				mContext.startService(intent);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void stopWatch() {
		try {
			mContext.stopService(new Intent(mContext, StudyGuardService.class));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	// ------------------------------------------------------------- utilities

	public String formatDuration(int seconds) {
		int minutes = Math.max(0, seconds) / 60;
		int rest = Math.max(0, seconds) % 60;
		if (minutes <= 0)
			return (mContext.getString(R.string.study_seconds, rest));
		if (rest == 0)
			return (mContext.getString(R.string.study_minutes, minutes));
		return (mContext.getString(R.string.study_minutes_seconds, minutes, rest));
	}
}
