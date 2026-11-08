package com.yinchenglu.desktoptv.study;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.yinchenglu.desktoptv.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Answer screen of the built-in study module.
 *
 * It is used in two ways: as a free practice screen opened from the
 * desktop, and as the gate that unlocks screen time before an application
 * can be watched.
 */
public class QuizActivity extends Activity {
	public static final String EXTRA_GATE = "gate";
	public static final String EXTRA_TARGET = "target";
	public static final String EXTRA_TARGET_NAME = "target_name";

	private static final int FREE_PRACTICE_SIZE = 20;
	private static final int EXTRA_STEP = 2;
	private static final int COLOR_CORRECT = Color.parseColor("#FF66BB6A");
	private static final int COLOR_WRONG = Color.parseColor("#FFEF5350");
	private static final int COLOR_ACCENT = Color.parseColor("#FFFFB74D");
	private static final int COLOR_TEXT = Color.parseColor("#FFFFFFFF");

	private final List<Question> mQuestions = new ArrayList<>();
	private final List<View> mOptionViews = new ArrayList<>();
	private final List<TextView> mOptionTexts = new ArrayList<>();

	private Study mStudy;
	private QuestionBank mBank;
	private String mMode = Study.MODE_GROUP;
	private int mGroupSize = Study.DEFAULT_SIZE;
	private int mIndex;
	private int mCorrect;
	private int mAnsweredCount;
	private int mStreak;
	private boolean mAnswered;
	private boolean mFinished;
	private boolean mPassed;
	private boolean mGate;

	private TextView mTitleView;
	private TextView mProgressView;
	private TextView mPromptView;
	private TextView mFeedbackView;
	private TextView mHintView;
	private TextView mBalanceView;
	private LinearLayout mOptionsView;
	private View mResultPanel;
	private View mResultAction;
	private TextView mResultTitle;
	private TextView mResultDetail;
	private TextView mResultActionText;

	public static Intent createIntent(Context context, boolean gate, String target, String targetName) {
		Intent intent = new Intent(context, QuizActivity.class);
		intent.putExtra(EXTRA_GATE, gate);
		if (target != null)
			intent.putExtra(EXTRA_TARGET, target);
		if (targetName != null)
			intent.putExtra(EXTRA_TARGET_NAME, targetName);
		return (intent);
	}

	/**
	 * Gate intent used when the study screen has to pop up from a service
	 * (for example when the unlocked time is over while a video plays).
	 */
	public static Intent gateIntent(Context context) {
		Intent intent = createIntent(context, true, null, null);
		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
		return (intent);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_quiz);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

		mGate = getIntent().getBooleanExtra(EXTRA_GATE, false);
		mStudy = new Study(this);
		mBank = new QuestionBank(this, mStudy.getGrade());

		mTitleView = (TextView) findViewById(R.id.quiz_title);
		mProgressView = (TextView) findViewById(R.id.quiz_progress);
		mPromptView = (TextView) findViewById(R.id.quiz_prompt);
		mFeedbackView = (TextView) findViewById(R.id.quiz_feedback);
		mHintView = (TextView) findViewById(R.id.quiz_hint);
		mBalanceView = (TextView) findViewById(R.id.quiz_balance);
		mOptionsView = (LinearLayout) findViewById(R.id.quiz_options);
		mResultPanel = findViewById(R.id.quiz_result_panel);
		mResultTitle = (TextView) findViewById(R.id.quiz_result_title);
		mResultDetail = (TextView) findViewById(R.id.quiz_result_detail);
		mResultAction = findViewById(R.id.quiz_result_action);
		mResultActionText = (TextView) findViewById(R.id.quiz_result_action_text);

		mResultAction.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				onResultAction();
			}
		});

		startGroup();
	}

	private void startGroup() {
		mMode = mStudy.getMode();
		if (mGate && Study.MODE_FREE.equals(mMode))
			mMode = Study.MODE_GROUP;
		mGroupSize = Study.MODE_FREE.equals(mMode) ? FREE_PRACTICE_SIZE : mStudy.getGroupSize();

		int build = Study.MODE_STREAK.equals(mMode) ? Math.max(mGroupSize * 3, 15) : mGroupSize;
		// The extra-practice pool is answered on top of the regular group.
		if (mGate && !Study.MODE_FREE.equals(mMode))
			build += mStudy.getExtraPool();
		mQuestions.clear();
		mQuestions.addAll(mBank.buildGroup(build));

		mIndex = 0;
		mCorrect = 0;
		mAnsweredCount = 0;
		mStreak = 0;
		mFinished = false;
		mPassed = false;
		mResultPanel.setVisibility(View.GONE);
		if (mGate)
			mBalanceView.setText(getString(R.string.study_balance_label,
					mStudy.formatDuration(mStudy.getBalance())));
		else
			mBalanceView.setVisibility(View.GONE);
		showQuestion();
	}

	private void showQuestion() {
		if (mIndex >= mQuestions.size()) {
			finishGroup();
			return;
		}
		Question question = mQuestions.get(mIndex);
		mAnswered = false;

		mTitleView.setText(getString(R.string.study_title_format,
				mStudy.getGradeName(), question.getSubject()));
		if (Study.MODE_STREAK.equals(mMode))
			mProgressView.setText(getString(R.string.study_streak_progress, mStreak, mGroupSize));
		else
			mProgressView.setText(getString(R.string.study_progress,
					mIndex + 1, mQuestions.size()));

		mPromptView.setText(question.getPrompt());

		mOptionsView.removeAllViews();
		mOptionViews.clear();
		mOptionTexts.clear();
		LayoutInflater inflater = getLayoutInflater();
		String[] options = question.getOptions();
		for (int i = 0; i < options.length; i++) {
			final int index = i;
			View row = inflater.inflate(R.layout.quiz_option, mOptionsView, false);
			((TextView) row.findViewById(R.id.option_letter)).setText(Question.letter(i));
			TextView text = (TextView) row.findViewById(R.id.option_text);
			text.setText(options[i]);
			text.setTextColor(COLOR_TEXT);
			row.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					onOption(index);
				}
			});
			mOptionsView.addView(row);
			mOptionViews.add(row);
			mOptionTexts.add(text);
		}

		mFeedbackView.setVisibility(View.GONE);
		mHintView.setText(R.string.study_hint_choose);

		mOptionsView.post(new Runnable() {
			@Override
			public void run() {
				if (!mOptionViews.isEmpty())
					mOptionViews.get(0).requestFocus();
			}
		});
	}

	private void onOption(int index) {
		if (mFinished)
			return;
		if (mAnswered) {
			nextQuestion();
			return;
		}
		Question question = mQuestions.get(mIndex);
		mAnswered = true;
		mAnsweredCount++;
		boolean correct = question.isCorrect(index);
		if (correct) {
			mCorrect++;
			mStreak++;
		} else {
			mStreak = 0;
		}

		for (int i = 0; i < mOptionTexts.size(); i++) {
			if (i == question.getAnswer())
				mOptionTexts.get(i).setTextColor(COLOR_CORRECT);
			else if (i == index)
				mOptionTexts.get(i).setTextColor(COLOR_WRONG);
		}

		mFeedbackView.setVisibility(View.VISIBLE);
		if (correct)
			mFeedbackView.setTextColor(COLOR_CORRECT);
		else
			mFeedbackView.setTextColor(COLOR_ACCENT);
		mFeedbackView.setText(correct
				? getString(R.string.study_correct, question.getExplain())
				: getString(R.string.study_wrong, Question.letter(question.getAnswer()), question.getExplain()));
		mHintView.setText(R.string.study_hint_next);
	}

	private void nextQuestion() {
		if (Study.MODE_STREAK.equals(mMode) && mStreak >= mGroupSize) {
			finishGroup();
			return;
		}
		mIndex++;
		if (mIndex >= mQuestions.size()) {
			finishGroup();
			return;
		}
		showQuestion();
	}

	private void finishGroup() {
		mFinished = true;
		int total = mAnsweredCount;
		int rate = total == 0 ? 0 : mCorrect * 100 / total;
		if (Study.MODE_FREE.equals(mMode))
			mPassed = false;
		else if (Study.MODE_STREAK.equals(mMode))
			mPassed = mStreak >= mGroupSize;
		else
			mPassed = rate >= mStudy.getPassRate();

		StringBuilder detail = new StringBuilder();
		if (!Study.MODE_FREE.equals(mMode))
			detail.append(getString(R.string.study_rate, rate, mCorrect, total));

		if (mGate) {
			if (mPassed) {
				int granted = mStudy.reward(mStudy.getUnlockMinutes() * 60);
				mStudy.clearExtra();
				mResultTitle.setText(R.string.study_pass_title);
				mResultTitle.setTextColor(COLOR_CORRECT);
				detail.append("\n").append(granted > 0
						? getString(R.string.study_reward, mStudy.formatDuration(granted))
						: getString(R.string.study_reward_none));
				mResultActionText.setText(R.string.study_action_start);
				mBalanceView.setText(getString(R.string.study_balance_label,
						mStudy.formatDuration(mStudy.getBalance())));
			} else {
				mStudy.addExtra(EXTRA_STEP);
				mResultTitle.setText(R.string.study_fail_title);
				mResultTitle.setTextColor(COLOR_WRONG);
				detail.append("\n").append(getString(R.string.study_extra_pool, EXTRA_STEP));
				mResultActionText.setText(R.string.study_action_again);
			}
			setResult(mPassed ? RESULT_OK : RESULT_CANCELED);
		} else {
			mResultTitle.setText(R.string.study_practice_title);
			mResultTitle.setTextColor(COLOR_ACCENT);
			if (detail.length() == 0)
				detail.append(getString(R.string.study_rate, rate, mCorrect, total));
			mResultActionText.setText(R.string.study_action_next);
			setResult(RESULT_CANCELED);
		}

		mResultDetail.setText(detail.toString());
		mResultPanel.setVisibility(View.VISIBLE);
		mResultAction.requestFocus();
	}

	private void onResultAction() {
		if (mGate && mPassed) {
			// Keep counting down the freshly granted time while the user is
			// watching (the launcher stops it again when it comes forward).
			mStudy.startWatch();
			finish();
			return;
		}
		startGroup();
	}

	@Override
	public void onBackPressed() {
		if (mGate) {
			leaveToHome();
			return;
		}
		finish();
	}

	private void leaveToHome() {
		try {
			Intent intent = new Intent(Intent.ACTION_MAIN);
			intent.addCategory(Intent.CATEGORY_HOME);
			intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			startActivity(intent);
		} catch (Exception e) {
			e.printStackTrace();
		}
		finish();
	}

	@Override
	public boolean dispatchKeyEvent(KeyEvent event) {
		if (!mFinished && event.getAction() == KeyEvent.ACTION_DOWN) {
			if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_LEFT) {
				moveOptionFocus(-1);
				return (true);
			}
			if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_RIGHT) {
				moveOptionFocus(1);
				return (true);
			}
		}
		return (super.dispatchKeyEvent(event));
	}

	private void moveOptionFocus(int delta) {
		if (mOptionViews.isEmpty())
			return;
		for (int i = 0; i < mOptionViews.size(); i++) {
			View option = mOptionViews.get(i);
			if (!option.isFocused())
				continue;
			int target = Math.max(0, Math.min(mOptionViews.size() - 1, i + delta));
			mOptionViews.get(target).requestFocus();
			return;
		}
		if (!mOptionViews.isEmpty())
			mOptionViews.get(0).requestFocus();
	}
}
