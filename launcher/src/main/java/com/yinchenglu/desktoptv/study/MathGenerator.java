package com.yinchenglu.desktoptv.study;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Generates grade-appropriate arithmetic questions on the fly, so the
 * question pool for a grade is unlimited (无重复刷题).
 */
public final class MathGenerator {
	private static final int[] SMALL_DELTAS = {1, -1, 2, -2, 3, -3, 10, -10};
	private static final String SUBJECT = "数学";

	private MathGenerator() {
	}

	public static Question next(int grade, Random random) {
		switch (grade) {
			case 1:
				return (grade1(random));
			case 2:
				return (grade2(random));
			case 3:
				return (grade3(random));
			case 4:
				return (grade4(random));
			case 5:
				return (grade5(random));
			case 6:
				return (grade6(random));
			default:
				return (grade3(random));
		}
	}

	private static Question grade1(Random r) {
		if (r.nextBoolean()) {
			int a = 1 + r.nextInt(10);
			int b = 1 + r.nextInt(10);
			return (numeric(a + " + " + b, a + b, new int[]{1, -1, 2, -2, 10, -10}, r));
		}
		int a = 3 + r.nextInt(18);
		int b = 1 + r.nextInt(a);
		return (numeric(a + " - " + b, a - b, new int[]{1, -1, 2, -2, 10, -10}, r));
	}

	private static Question grade2(Random r) {
		int kind = r.nextInt(3);
		if (kind == 0) {
			int a = 10 + r.nextInt(50);
			int b = 5 + r.nextInt(34);
			return (numeric(a + " + " + b, a + b, new int[]{1, -1, 10, -10, 2, -2}, r));
		}
		if (kind == 1) {
			int a = 30 + r.nextInt(69);
			int b = 1 + r.nextInt(a - 1);
			return (numeric(a + " - " + b, a - b, new int[]{1, -1, 10, -10, 2, -2}, r));
		}
		int a = 2 + r.nextInt(8);
		int b = 2 + r.nextInt(8);
		return (numeric(a + " × " + b, a * b, new int[]{a, -a, b, -b, 1, -1}, r));
	}

	private static Question grade3(Random r) {
		int kind = r.nextInt(4);
		if (kind == 0) {
			int a = 100 + r.nextInt(700);
			int b = 100 + r.nextInt(500);
			return (numeric(a + " + " + b, a + b, new int[]{1, -1, 10, -10, 100, -100}, r));
		}
		if (kind == 1) {
			int a = 300 + r.nextInt(600);
			int b = 100 + r.nextInt(199);
			return (numeric(a + " - " + b, a - b, new int[]{1, -1, 10, -10, 100, -100}, r));
		}
		if (kind == 2) {
			int a = 12 + r.nextInt(900);
			int b = 2 + r.nextInt(8);
			return (numeric(a + " × " + b, a * b, new int[]{a, -a, b, -b, 10, -10}, r));
		}
		int b = 2 + r.nextInt(8);
		int q = 12 + r.nextInt(80);
		return (numeric((b * q) + " ÷ " + b, q, new int[]{1, -1, 2, -2, 10, -10}, r));
	}

	private static Question grade4(Random r) {
		int kind = r.nextInt(3);
		if (kind == 0) {
			int a = 100 + r.nextInt(800);
			int b = 11 + r.nextInt(88);
			return (numeric(a + " × " + b, a * b, new int[]{a, -a, b, -b, 10, -10}, r));
		}
		if (kind == 1) {
			int b = 11 + r.nextInt(79);
			int q = 11 + r.nextInt(79);
			return (numeric((b * q) + " ÷ " + b, q, new int[]{1, -1, 2, -2, 10, -10}, r));
		}
		int b = 2 + r.nextInt(8);
		int c = 2 + r.nextInt(8);
		int a = 10 + r.nextInt(80);
		return (numeric(a + " + " + b + " × " + c, a + b * c, new int[]{b, -b, c, -c, 10, -10}, r));
	}

	private static Question grade5(Random r) {
		int kind = r.nextInt(3);
		if (kind == 0) {
			int a = 10 + r.nextInt(80);
			int b = 10 + r.nextInt(80);
			int sum = a + b;
			List<String> wrongs = new ArrayList<>();
			addUnique(wrongs, dec1(sum + 10));
			addUnique(wrongs, dec1(sum - 10));
			addUnique(wrongs, dec1(sum + 1));
			addUnique(wrongs, String.valueOf(sum));
			while (wrongs.size() < 3)
				addUnique(wrongs, dec1(sum + 20 + wrongs.size()));
			return (choice(dec1(a) + " + " + dec1(b) + " = ?", dec1(sum), wrongs,
					dec1(a) + " + " + dec1(b) + " = " + dec1(sum), r));
		}
		if (kind == 1) {
			int a = 110 + r.nextInt(80);
			int b = 10 + r.nextInt(80);
			if (b > a)
				b = a - 5;
			List<String> wrongs = new ArrayList<>();
			int diff = a - b;
			addUnique(wrongs, dec1(diff + 10));
			addUnique(wrongs, dec1(diff - 10));
			addUnique(wrongs, dec1(diff + 1));
			addUnique(wrongs, String.valueOf(diff));
			while (wrongs.size() < 3)
				addUnique(wrongs, dec1(diff + 20 + wrongs.size()));
			return (choice(dec1(a) + " - " + dec1(b) + " = ?", dec1(diff), wrongs,
					dec1(a) + " - " + dec1(b) + " = " + dec1(diff), r));
		}
		int a = 2 + r.nextInt(8);
		int b = 11 + r.nextInt(80);
		int c = 11 + r.nextInt(80);
		List<String> wrongs = new ArrayList<>();
		int result = a * (b + c);
		addUnique(wrongs, String.valueOf(a * b + c));
		addUnique(wrongs, String.valueOf(a * b + a * c + a));
		addUnique(wrongs, String.valueOf(result - a));
		addUnique(wrongs, String.valueOf(result + b));
		while (wrongs.size() < 3)
			addUnique(wrongs, String.valueOf(result + 5 + wrongs.size()));
		return (choice(a + " × (" + b + " + " + c + ") = ?", String.valueOf(result), wrongs,
				a + " × (" + b + " + " + c + ") = " + result, r));
	}

	private static Question grade6(Random r) {
		int kind = r.nextInt(3);
		if (kind == 0) {
			int[] percents = {10, 20, 25, 50, 75};
			int percent = percents[r.nextInt(percents.length)];
			int base = 20 * (1 + r.nextInt(9));
			int result = base * percent / 100;
			List<String> wrongs = new ArrayList<>();
			addUnique(wrongs, String.valueOf(base * percent / 10));
			addUnique(wrongs, String.valueOf(base - result));
			addUnique(wrongs, String.valueOf(result + base / 10));
			addUnique(wrongs, String.valueOf(base + result));
			while (wrongs.size() < 3)
				addUnique(wrongs, String.valueOf(result + 1 + wrongs.size()));
			return (choice(base + " 的 " + percent + "% 是多少？", String.valueOf(result), wrongs,
					base + " × " + percent + "% = " + result, r));
		}
		if (kind == 1) {
			int a = 2 + r.nextInt(7);
			int b = 2 + r.nextInt(7);
			int k = 2 + r.nextInt(8);
			int c = a * k;
			int answer = b * k;
			List<String> wrongs = new ArrayList<>();
			addUnique(wrongs, String.valueOf(b));
			addUnique(wrongs, String.valueOf(c));
			addUnique(wrongs, String.valueOf(answer + b));
			addUnique(wrongs, String.valueOf(answer - 1));
			while (wrongs.size() < 3)
				addUnique(wrongs, String.valueOf(answer + 1 + wrongs.size()));
			return (choice(a + " : " + b + " = " + c + " : ?", String.valueOf(answer), wrongs,
					a + " : " + b + " = " + (b * k) + " : " + answer, r));
		}
		int radius = 2 + r.nextInt(9);
		int circumference = 628 * radius;
		List<String> wrongs = new ArrayList<>();
		addUnique(wrongs, dec2(circumference + 100));
		addUnique(wrongs, dec2(circumference - 100));
		addUnique(wrongs, dec2(314 * radius));
		addUnique(wrongs, dec2(628 * (radius + 1)));
		while (wrongs.size() < 3)
			addUnique(wrongs, dec2(circumference + 10 + wrongs.size()));
		return (choice("半径 " + radius + " 厘米的圆，周长是多少厘米？（π 取 3.14）",
				dec2(circumference), wrongs,
				"C = 2πr = 2 × 3.14 × " + radius + " = " + dec2(circumference), r));
	}

	/**
	 * Builds a question whose options are numbers around {@code result};
	 * one question out of five becomes a true/false check instead.
	 */
	private static Question numeric(String caption, int result, int[] deltas, Random r) {
		if (r.nextInt(5) == 0) {
			boolean truth = r.nextInt(2) == 0;
			int shown = result;
			if (!truth) {
				shown = result + SMALL_DELTAS[r.nextInt(SMALL_DELTAS.length)];
				if (shown < 0)
					shown = result + 1;
			}
			return (judge(caption + " = " + shown, truth, caption + " = " + result));
		}
		return (choice(caption + " = ?", String.valueOf(result), wrongNumbers(result, deltas), caption + " = " + result, r));
	}

	private static List<String> wrongNumbers(int result, int[] deltas) {
		List<String> wrongs = new ArrayList<>();
		for (int i = 0; i < deltas.length && wrongs.size() < 3; i++) {
			int value = result + deltas[i];
			if (value < 0)
				value = result - deltas[i];
			if (value < 0 || value == result)
				continue;
			addUnique(wrongs, String.valueOf(value));
		}
		int extra = 1;
		while (wrongs.size() < 3) {
			addUnique(wrongs, String.valueOf(result + 200 + extra));
			extra++;
		}
		return (wrongs);
	}

	private static void addUnique(List<String> list, String value) {
		if (value != null && !list.contains(value))
			list.add(value);
	}

	private static String dec1(int tenths) {
		return (String.valueOf(tenths / 10) + "." + (tenths % 10));
	}

	private static String dec2(int hundredths) {
		int decimals = hundredths % 100;
		return (String.valueOf(hundredths / 100) + "."
				+ (decimals < 10 ? "0" + decimals : String.valueOf(decimals)));
	}

	private static Question choice(String prompt, String correct, List<String> wrongs, String explain, Random r) {
		List<String> pool = new ArrayList<>();
		for (String wrong : wrongs) {
			if (wrong != null && !wrong.equals(correct) && !pool.contains(wrong))
				pool.add(wrong);
		}
		int filler = 1;
		while (pool.size() < 3) {
			String value = correct + filler;
			if (!value.equals(correct) && !pool.contains(value))
				pool.add(value);
			filler++;
		}
		String[] options = new String[4];
		int answer = r.nextInt(4);
		options[answer] = correct;
		int index = 0;
		for (int i = 0; i < 4; i++) {
			if (i == answer)
				continue;
			options[i] = pool.get(index);
			index++;
		}
		return (new Question(SUBJECT, prompt, options, answer, explain, Question.TYPE_CHOICE));
	}

	private static Question judge(String prompt, boolean truth, String explain) {
		return (new Question(SUBJECT, "判断：" + prompt, new String[]{"正确", "错误"},
				truth ? 0 : 1, explain, Question.TYPE_JUDGE));
	}
}
