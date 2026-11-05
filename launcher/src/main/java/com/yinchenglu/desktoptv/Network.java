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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.RequiresApi;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

/**
 * Displays the connection type and the local IPv4 address below it.
 */
public class Network extends BroadcastReceiver {
	public enum State { NONE, WIFI, ETHERNET, OTHER, UNKNOWN }

	private static final int REFRESH_DELAY = 5000;
	private final Context mContext;
	private final TextView mView;
	private final ImageView mIcon;
	private final Handler mHandler = new Handler(Looper.getMainLooper());
	private boolean mRunning;
	private boolean mRegistered;
	private final Runnable mRefresh = new Runnable() {
		@Override
		public void run() {
			if (mRunning)
				refresh();
		}
	};

	public Network(Context context, TextView view, ImageView icon) {
		mIcon = icon;
		mContext = context.getApplicationContext();
		mView = view;
	}

	public void start() {
		if (mRunning)
			return;
		mRunning = true;
		try {
			IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
				mContext.registerReceiver(this, filter, Context.RECEIVER_NOT_EXPORTED);
			else
				mContext.registerReceiver(this, filter);
			mRegistered = true;
		} catch (SecurityException e) {
			e.printStackTrace();
		}
		refresh();
	}

	public void stop() {
		mRunning = false;
		mHandler.removeCallbacks(mRefresh);
		if (mRegistered) {
			mContext.unregisterReceiver(this);
			mRegistered = false;
		}
	}

	@Override
	public void onReceive(Context context, Intent intent) {
		if (mRunning)
			refresh();
	}

	private void refresh() {
		if (mIcon != null && mIcon.getVisibility() == View.VISIBLE)
			setState(getState(mContext));
		String text = mView.getVisibility() == View.VISIBLE ? readIpv4(mContext) : "";
		if (!TextUtils.equals(mView.getText(), text))
			mView.setText(text);
		mHandler.removeCallbacks(mRefresh);
		mHandler.postDelayed(mRefresh, REFRESH_DELAY);
	}

	/**
	 * Returns the IPv4 address of the connected interface. The lookup does
	 * not depend on the reported connectivity state: some TV boxes never
	 * report an active network, so the interfaces are simply enumerated.
	 * IPv6 and link local addresses are ignored so the value stays
	 * readable on a television.
	 */
	public static String readIpv4(Context context) {
		try {
			String address = findIpv4Cm(context);
			if (address == null)
				address = findIpv4();
			if (address != null)
				return (address);
			if (getState(context) == State.UNKNOWN)
				return (context.getString(R.string.network_unavailable));
			return (context.getString(R.string.network_disconnected));
		} catch (Exception e) {
			e.printStackTrace();
			return ("");
		}
	}

	/**
	 * Asks the connectivity service for the addresses of every network.
	 * More reliable than interface enumeration on some devices.
	 */
	@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
	private static String findIpv4Cm(Context context) {
		try {
			ConnectivityManager manager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
			if (manager == null)
				return (null);
			for (android.net.Network network : manager.getAllNetworks()) {
				android.net.LinkProperties properties = manager.getLinkProperties(network);
				if (properties == null)
					continue;
				for (android.net.LinkAddress linkAddress : properties.getLinkAddresses()) {
					InetAddress address = linkAddress.getAddress();
					if (address instanceof Inet4Address
							&& !address.isLoopbackAddress()
							&& !address.isLinkLocalAddress()
							&& !address.isAnyLocalAddress())
						return (address.getHostAddress());
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return (null);
	}

	private static String findIpv4() throws Exception {
		Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
		while (interfaces != null && interfaces.hasMoreElements()) {
			NetworkInterface network = interfaces.nextElement();
			if (!network.isUp() || network.isLoopback())
				continue;
			Enumeration<InetAddress> addresses = network.getInetAddresses();
			while (addresses.hasMoreElements()) {
				InetAddress address = addresses.nextElement();
				if (!(address instanceof Inet4Address))
					continue;
				if (address.isLoopbackAddress() || address.isLinkLocalAddress() || address.isAnyLocalAddress())
					continue;
				return (address.getHostAddress());
			}
		}
		return (null);
	}

	private void setState(State state) {
		int icon;
		int label;
		switch (state) {
			case WIFI: icon = R.drawable.ic_network_wifi; label = R.string.network_wifi; break;
			case ETHERNET: icon = R.drawable.ic_network_ethernet; label = R.string.network_ethernet; break;
			case OTHER: icon = R.drawable.ic_network_other; label = R.string.network_other; break;
			case NONE: icon = R.drawable.ic_network_off; label = R.string.network_disconnected; break;
			default: icon = R.drawable.ic_network_off; label = R.string.network_unavailable; break;
		}
		mIcon.setImageResource(icon);
		mIcon.setContentDescription(mContext.getString(label));
	}

	public static State getState(Context context) {
		try {
			ConnectivityManager manager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
			if (manager == null)
				return (State.UNKNOWN);
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
				return (Connections.getState(manager));
			NetworkInfo active = manager.getActiveNetworkInfo();
			if (active != null && active.isConnected())
				return (getType(active));
			State state = State.NONE;
			for (NetworkInfo network : manager.getAllNetworkInfo()) {
				if (network == null || !network.isConnected())
					continue;
				State type = getType(network);
				if (type == State.ETHERNET)
					return (type);
				if (state != State.WIFI)
					state = type;
			}
			return (state);
		} catch (Exception e) {
			e.printStackTrace();
			return (State.UNKNOWN);
		}
	}

	private static State getType(NetworkInfo network) {
		if (network.getType() == ConnectivityManager.TYPE_ETHERNET)
			return (State.ETHERNET);
		if (network.getType() == ConnectivityManager.TYPE_WIFI)
			return (State.WIFI);
		return (State.OTHER);
	}

	@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
	private static class Connections {
		static State getState(ConnectivityManager manager) {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
				NetworkCapabilities active = manager.getNetworkCapabilities(manager.getActiveNetwork());
				if (active != null)
					return (getType(active));
			} else {
				NetworkInfo active = manager.getActiveNetworkInfo();
				if (active != null && active.isConnected())
					return (Network.getType(active));
			}
			State state = State.NONE;
			for (android.net.Network network : manager.getAllNetworks()) {
				NetworkCapabilities capabilities = manager.getNetworkCapabilities(network);
				if (capabilities == null)
					continue;
				State type = getType(capabilities);
				if (type == State.ETHERNET)
					return (type);
				if (state != State.WIFI)
					state = type;
			}
			return (state);
		}

		private static State getType(NetworkCapabilities capabilities) {
			if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
				return (State.ETHERNET);
			if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))
				return (State.WIFI);
			return (State.OTHER);
		}
	}

}
