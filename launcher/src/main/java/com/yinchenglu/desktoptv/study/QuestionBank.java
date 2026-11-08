package com.yinchenglu.desktoptv.study;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Per-grade question source: a small curated bank of language questions
 * shipped in {@code assets/study/questions.json}, mixed with arithmetic
 * generated on the fly for the selected grade.
 */
public class QuestionBank {
	private static final String ASSET = "study/questions.json";

	private final int mGrade;
	private final Random mRandom = new Random();
	private final List<Question> mStatic = new ArrayList<>();

	public QuestionBank(Context context, int grade) {
		mGrade = grade;
		load(context);
	}

	private void load(Context context) {
		InputStream input = null;
		try {
			input = context.getAssets().open(ASSET);
			ByteArrayOutputStream output = new ByteArrayOutputStream();
			byte[] buffer = new byte[4096];
			int read;
			while ((read = input.read(buffer)) > 0)
				output.write(buffer, 0, read);
			JSONArray array = new JSONArray(new String(output.toByteArray(), "UTF-8"));
			for (int i = 0; i < array.length(); i++) {
				JSONObject item = array.optJSONObject(i);
				if (item == null || item.optInt("g", mGrade) != mGrade)
					continue;
				JSONArray options = item.optJSONArray("o");
				if (options == null || options.length() < 2)
					continue;
				String[] texts = new String[options.length()];
				for (int j = 0; j < texts.length; j++)
					texts[j] = options.optString(j, "");
				int answer = item.optInt("a", 0);
				if (answer < 0 || answer >= texts.length)
					continue;
				mStatic.add(new Question(item.optString("s", ""), item.optString("q", ""),
						texts, answer, item.optString("e", ""),
						texts.length == 2 ? Question.TYPE_JUDGE : Question.TYPE_CHOICE));
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (input != null) {
				try {
					input.close();
				} catch (Exception ignored) {
					// nothing to do
				}
			}
		}
	}

	/**
	 * Builds a group of {@code count} questions: curated ones are drawn
	 * first, arithmetic fills the rest so a group is never short.
	 */
	public List<Question> buildGroup(int count) {
		List<Question> group = new ArrayList<>();
		List<Question> pool = new ArrayList<>(mStatic);
		Collections.shuffle(pool, mRandom);
		Set<String> prompts = new HashSet<>();
		int attempts = 0;
		while (group.size() < count && attempts < count * 4) {
			attempts++;
			Question question = (!pool.isEmpty() && mRandom.nextInt(3) != 0)
					? pool.remove(0)
					: MathGenerator.next(mGrade, mRandom);
			if (prompts.add(question.getPrompt()))
				group.add(question);
		}
		while (group.size() < count)
			group.add(MathGenerator.next(mGrade, mRandom));
		return (group);
	}
}
