package com.yinchenglu.desktoptv.study;

/**
 * One question of the built-in study module.
 */
public class Question {
	public static final int TYPE_CHOICE = 0;
	public static final int TYPE_JUDGE = 1;

	private final String mSubject;
	private final String mPrompt;
	private final String[] mOptions;
	private final int mAnswer;
	private final String mExplain;
	private final int mType;

	public Question(String subject, String prompt, String[] options, int answer, String explain, int type) {
		mSubject = subject;
		mPrompt = prompt;
		mOptions = options;
		mAnswer = answer;
		mExplain = explain;
		mType = type;
	}

	public String getSubject() {
		return (mSubject);
	}

	public String getPrompt() {
		return (mPrompt);
	}

	public String[] getOptions() {
		return (mOptions);
	}

	public int getAnswer() {
		return (mAnswer);
	}

	public String getExplain() {
		return (mExplain);
	}

	public int getType() {
		return (mType);
	}

	public boolean isCorrect(int index) {
		return (index == mAnswer);
	}

	public static String letter(int index) {
		return (String.valueOf((char) ('A' + index)));
	}
}
