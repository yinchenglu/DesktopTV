package com.yinchenglu.desktoptv.study;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;

import com.yinchenglu.desktoptv.R;

/**
 * Counts down the unlocked screen time while another application is on
 * screen and brings the study screen back when the time is over.
 */
public class StudyGuardService extends Service {
	public static final String EXTRA_SECONDS = "seconds";

	private static final int NOTIFICATION_ID = 0x5701;
	private static final String CHANNEL_ID = "study_guard";

	private final Handler mHandler = new Handler();
	private int mRemaining;
	private long mLastTick;

	private final Runnable mTick = new Runnable() {
		@Override
		public void run() {
			long now = SystemClock.elapsedRealtime();
			int elapsed = (int) Math.max(1, (now - mLastTick) / 1000);
			mLastTick = now;
			mRemaining -= elapsed;
			if (mRemaining <= 0) {
				mRemaining = 0;
				new Study(StudyGuardService.this).setBalance(0);
				showQuiz();
				return;
			}
			new Study(StudyGuardService.this).setBalance(mRemaining);
			mHandler.postDelayed(this, 1000);
		}
	};

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if (intent != null && intent.hasExtra(EXTRA_SECONDS))
			mRemaining = intent.getIntExtra(EXTRA_SECONDS, 0);
		if (mRemaining <= 0)
			mRemaining = new Study(this).getBalance();
		mLastTick = SystemClock.elapsedRealtime();
		startForegroundCompat();
		mHandler.removeCallbacks(mTick);
		if (mRemaining > 0)
			mHandler.postDelayed(mTick, 1000);
		return (START_NOT_STICKY);
	}

	private void startForegroundCompat() {
		try {
			Notification notification = buildNotification();
			if (Build.VERSION.SDK_INT >= 26) {
				NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
				if (manager != null) {
					NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
							getString(R.string.study_channel_title),
							NotificationManager.IMPORTANCE_MIN);
					channel.setShowBadge(false);
					manager.createNotificationChannel(channel);
				}
				startForeground(NOTIFICATION_ID, notification);
			} else {
				startForeground(NOTIFICATION_ID, notification);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@SuppressWarnings("deprecation")
	private Notification buildNotification() {
		Notification.Builder builder;
		if (Build.VERSION.SDK_INT >= 26)
			builder = new Notification.Builder(this, CHANNEL_ID);
		else
			builder = new Notification.Builder(this);
		builder.setSmallIcon(R.drawable.ic_study)
				.setContentTitle(getString(R.string.study_channel_title))
				.setContentText(getString(R.string.study_channel_text))
				.setOngoing(true);
		return (builder.build());
	}

	private void showQuiz() {
		mHandler.removeCallbacks(mTick);
		new Study(this).setBalance(0);
		Intent intent = QuizActivity.gateIntent(this);
		try {
			startActivity(intent);
		} catch (Exception e) {
			e.printStackTrace();
		}
		stopSelf();
	}

	@Override
	public void onDestroy() {
		mHandler.removeCallbacks(mTick);
		int balance = new Study(this).getBalance();
		if (mRemaining > 0 && balance > 0)
			new Study(this).setBalance(mRemaining);
		super.onDestroy();
	}

	@Override
	public IBinder onBind(Intent intent) {
		return (null);
	}
}
