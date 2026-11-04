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

package com.yinchenglu.desktoptv.activities;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.yinchenglu.desktoptv.AppInfo;
import com.yinchenglu.desktoptv.R;
import com.yinchenglu.desktoptv.Setup;
import com.yinchenglu.desktoptv.Utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Lists every installed application so the user can hide or restore it.
 * Applications removed from the home screen are listed here as checked.
 */
public class HiddenApps extends Activity implements AdapterView.OnItemClickListener, View.OnClickListener {
	private final List<AppInfo> mApps = new ArrayList<>();
	private Setup mSetup;
	private Set<String> mHidden;
	private HiddenAdapter mAdapter;

	@Override
	protected void onCreate(Bundle state) {
		super.onCreate(state);
		setContentView(R.layout.activity_hidden_apps);
		mSetup = new Setup(this);
		mHidden = mSetup.getHiddenApps();
		mApps.clear();
		mApps.addAll(Utils.loadApplications(this, true));

		ListView list = (ListView) findViewById(R.id.list);
		mAdapter = new HiddenAdapter(this);
		list.setAdapter(mAdapter);
		list.setOnItemClickListener(this);
		findViewById(R.id.done).setOnClickListener(this);
	}

	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
		if (position < 0 || position >= mApps.size())
			return;
		String appId = mApps.get(position).getId();
		boolean hide = !mHidden.contains(appId);
		if (hide)
			mHidden.add(appId);
		else
			mHidden.remove(appId);
		mSetup.setHidden(appId, hide);
		mAdapter.notifyDataSetChanged();
	}

	@Override
	public void onClick(View view) {
		if (view.getId() == R.id.done)
			finish();
	}

	private class HiddenAdapter extends BaseAdapter {
		private final LayoutInflater mInflater;

		HiddenAdapter(Context context) {
			mInflater = LayoutInflater.from(context);
		}

		@Override
		public int getCount() {
			return (mApps.size());
		}

		@Override
		public Object getItem(int position) {
			return (mApps.get(position));
		}

		@Override
		public long getItemId(int position) {
			return (position);
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			View view = convertView;
			if (view == null)
				view = mInflater.inflate(R.layout.hidden_item, parent, false);
			AppInfo app = mApps.get(position);
			((ImageView) view.findViewById(R.id.application_icon)).setImageDrawable(app.getIcon());
			((TextView) view.findViewById(R.id.application_name)).setText(app.getName());
			((CheckBox) view.findViewById(R.id.hidden_check)).setChecked(mHidden.contains(app.getId()));
			return (view);
		}
	}
}
