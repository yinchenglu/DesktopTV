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

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import java.util.List;

/**
 * Kills the background processes of every installed application
 * (except this launcher) and returns how many megabytes of free
 * memory were gained.
 */
public class MemoryCleaner {
	public static long clean(Context context) {
		ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
		if (manager == null)
			return (0);

		ActivityManager.MemoryInfo before = new ActivityManager.MemoryInfo();
		manager.getMemoryInfo(before);

		try {
			PackageManager packageManager = context.getPackageManager();
			List<PackageInfo> packages = packageManager.getInstalledPackages(0);
			for (PackageInfo info : packages) {
				if (info == null || context.getPackageName().equals(info.packageName))
					continue;
				try {
					manager.killBackgroundProcesses(info.packageName);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		try {
			Thread.sleep(800);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}

		ActivityManager.MemoryInfo after = new ActivityManager.MemoryInfo();
		manager.getMemoryInfo(after);

		long freed = (after.availMem - before.availMem) / (1024 * 1024);
		return (freed > 0 ? freed : 0);
	}
}
