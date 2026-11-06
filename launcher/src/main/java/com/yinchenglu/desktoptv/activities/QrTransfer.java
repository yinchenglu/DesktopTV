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
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.Window;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import com.yinchenglu.desktoptv.Network;
import com.yinchenglu.desktoptv.R;
import com.yinchenglu.desktoptv.TransferServer;

/**
 * Shows a QR code pointing at the embedded upload server so a phone
 * on the same network can send files to the TV.
 */
public class QrTransfer extends Activity {

	private static final int QR_SIZE = 480;

	private TransferServer mServer;
	private TextView mStatus;
	private TextView mUrl;
	private ImageView mQrImage;
	private final Handler mHandler = new Handler();
	private final Runnable mTick = new Runnable() {
		@Override
		public void run() {
			updateProgress();
			mHandler.postDelayed(this, 1000);
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.activity_qr_transfer);

		mQrImage = (ImageView) findViewById(R.id.qr_image);
		mUrl = (TextView) findViewById(R.id.qr_url);
		mStatus = (TextView) findViewById(R.id.qr_status);

		TransferServer server = new TransferServer(TransferServer.DEFAULT_PORT);
		int port = server.start();
		if (port < 0) {
			server.stop();
			// try a few fallback ports
			for (int candidate : new int[]{8898, 8890, 8900, 8080}) {
				server = new TransferServer(candidate);
				port = server.start();
				if (port > 0)
					break;
				server.stop();
			}
		}
		mServer = port > 0 ? server : null;

		if (port > 0) {
			String ip = Network.readIpv4(this);
			boolean hasIp = ip != null && ip.matches("\\d+\\.\\d+\\.\\d+\\.\\d+");
			String url = "http://" + (hasIp ? ip : "<IP>") + ":" + port + "/";
			if (hasIp) {
				mUrl.setText(url);
				showQr(url);
				mStatus.setText(getString(R.string.qr_hint));
			} else {
				mQrImage.setVisibility(View.GONE);
				mUrl.setText(url);
				mStatus.setText(R.string.qr_no_ip);
			}
		} else {
			mQrImage.setVisibility(View.GONE);
			mStatus.setText(R.string.qr_server_failed);
			Toast.makeText(this, R.string.qr_server_failed, Toast.LENGTH_LONG).show();
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		mHandler.post(mTick);
	}

	@Override
	protected void onPause() {
		super.onPause();
		mHandler.removeCallbacks(mTick);
	}

	@Override
	protected void onDestroy() {
		if (mServer != null)
			mServer.stop();
		mServer = null;
		super.onDestroy();
	}

	private void updateProgress() {
		if (mServer == null || mStatus == null)
			return;
		int received = mServer.getServedCount();
		if (received > 0)
			mStatus.setText(getString(R.string.qr_received, received, TransferServer.receiveDirectory().getAbsolutePath()));
	}

	private void showQr(String content) {
		try {
			QRCodeWriter writer = new QRCodeWriter();
			BitMatrix matrix = writer.encode(content, BarcodeFormat.QR_CODE, QR_SIZE, QR_SIZE);
			Bitmap bitmap = Bitmap.createBitmap(matrix.getWidth(), matrix.getHeight(), Bitmap.Config.RGB_565);
			for (int x = 0; x < matrix.getWidth(); x++) {
				for (int y = 0; y < matrix.getHeight(); y++) {
					bitmap.setPixel(x, y, matrix.get(x, y) ? Color.BLACK : Color.WHITE);
				}
			}
			mQrImage.setImageBitmap(bitmap);
			mQrImage.setVisibility(View.VISIBLE);
		} catch (WriterException e) {
			e.printStackTrace();
			mQrImage.setVisibility(View.GONE);
			mUrl.setText(content);
		}
	}
}
