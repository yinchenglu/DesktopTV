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

import android.os.Environment;
import android.text.TextUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;

/**
 * Tiny embedded HTTP server used by the QR file transfer screen.
 * A phone on the same network opens the upload page and posts files;
 * every posted file is stored in the public downloads directory.
 */
public class TransferServer {

	public static final int DEFAULT_PORT = 8899;

	private static final int MAX_BODY = 1024 * 1024 * 1024;

	private final int mPort;
	private ServerSocket mServer;
	private Thread mThread;
	private volatile boolean mRunning;
	private int mServedCount;

	public TransferServer(int port) {
		mPort = port;
	}

	/**
	 * Opens the listening socket. Returns the real port or -1 on failure.
	 */
	public synchronized int start() {
		if (mRunning)
			return (mPort);
		try {
			mServer = new ServerSocket(mPort, 10, java.net.InetAddress.getByName("0.0.0.0"));
		} catch (IOException e) {
			e.printStackTrace();
			return (-1);
		}
		mRunning = true;
		mThread = new Thread(new Runnable() {
			@Override
			public void run() {
				acceptLoop();
			}
		});
		mThread.setDaemon(true);
		mThread.start();
		return (mPort);
	}

	public synchronized void stop() {
		mRunning = false;
		try {
			if (mServer != null)
				mServer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		mServer = null;
		mThread = null;
	}

	public static File receiveDirectory() {
		File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
		File directory = new File(downloads, "DesktopTV接收");
		if (!directory.exists())
			directory.mkdirs();
		return (directory);
	}

	public int getServedCount() {
		return (mServedCount);
	}

	private void acceptLoop() {
		while (mRunning) {
			try {
				final Socket socket = mServer.accept();
				new Thread(new Runnable() {
					@Override
					public void run() {
						try {
							handle(socket);
						} catch (Throwable t) {
							t.printStackTrace();
						}
						try {
							socket.close();
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
				}).start();
			} catch (IOException e) {
				if (mRunning)
					e.printStackTrace();
			}
		}
	}

	private void handle(Socket socket) throws IOException {
		InputStream input = socket.getInputStream();
		OutputStream output = socket.getOutputStream();

		String requestLine = readLine(input);
		if (requestLine == null)
			return;
		String[] parts = requestLine.split(" ");
		String method = parts.length > 0 ? parts[0] : "";
		String path = parts.length > 1 ? parts[1] : "/";

		String contentType = "";
		int contentLength = 0;
		String header;
		while ((header = readLine(input)) != null && header.length() > 0) {
			int colon = header.indexOf(':');
			if (colon < 0)
				continue;
			String name = header.substring(0, colon).trim().toLowerCase();
			String value = header.substring(colon + 1).trim();
			if ("content-type".equals(name))
				contentType = value;
			else if ("content-length".equals(name)) {
				try {
					contentLength = Integer.parseInt(value);
				} catch (NumberFormatException e) {
					contentLength = 0;
				}
			}
		}

		if ("POST".equals(method) && path.startsWith("/upload")) {
			handleUpload(input, output, contentType, contentLength);
		} else {
			sendPage(output, buildUploadPage());
		}
	}

	private void handleUpload(InputStream input, OutputStream output, String contentType, int contentLength) throws IOException {
		if (contentLength <= 0 || contentLength > MAX_BODY || TextUtils.isEmpty(contentType)) {
			sendPage(output, buildResultPage(0, null));
			return;
		}
		byte[] body = readFully(input, contentLength);
		List<String> saved = new ArrayList<String>();
		List<String> failed = new ArrayList<String>();

		String boundary = getBoundary(contentType);
		if (boundary != null)
			parseMultipart(body, boundary, saved, failed);
		mServedCount += saved.size();
		sendPage(output, buildResultPage(saved.size(), failed));
	}

	private static String getBoundary(String contentType) {
		int index = contentType.toLowerCase().indexOf("boundary=");
		if (index < 0)
			return (null);
		String boundary = contentType.substring(index + "boundary=".length());
		if (boundary.startsWith("\"") && boundary.endsWith("\"") && boundary.length() > 2)
			boundary = boundary.substring(1, boundary.length() - 1);
		return (boundary);
	}

	private void parseMultipart(byte[] body, String boundary, List<String> saved, List<String> failed) {
		byte[] delimiter = ("--" + boundary).getBytes();
		int position = indexOf(body, delimiter, 0);
		while (position >= 0) {
			int partStart = position + delimiter.length;
			// terminal boundary "--boundary--"
			if (partStart + 2 <= body.length && body[partStart] == '-' && body[partStart + 1] == '-')
				break;
			// skip CRLF
			if (partStart + 2 <= body.length && body[partStart] == '\r' && body[partStart + 1] == '\n')
				partStart += 2;

			int next = indexOf(body, delimiter, partStart);
			if (next < 0)
				break;
			int partEnd = next;
			// strip trailing CRLF before the delimiter
			if (partEnd >= 2 && body[partEnd - 2] == '\r' && body[partEnd - 1] == '\n')
				partEnd -= 2;

			savePart(body, partStart, partEnd, saved, failed);
			position = next;
		}
	}

	private void savePart(byte[] body, int start, int end, List<String> saved, List<String> failed) {
		try {
			byte[] headerEnd = "\r\n\r\n".getBytes();
			int headersStop = indexOf(body, headerEnd, start);
			if (headersStop < 0 || headersStop + 4 > end)
				return;
			String headers = new String(body, start, headersStop - start, "UTF-8");
			String filename = extractFilename(headers);
			if (TextUtils.isEmpty(filename))
				return;
			int dataStart = headersStop + 4;

			File directory = receiveDirectory();
			File target = new File(directory, uniqueName(directory, filename));
			FileOutputStream output = new FileOutputStream(target);
			try {
				output.write(body, dataStart, end - dataStart);
			} finally {
				output.close();
			}
			saved.add(filename);
		} catch (Exception e) {
			e.printStackTrace();
			failed.add(e.getMessage() == null ? "error" : e.getMessage());
		}
	}

	private static String extractFilename(String headers) throws Exception {
		String lower = headers.toLowerCase();
		int index = lower.indexOf("filename=");
		if (index < 0)
			return (null);
		String value = headers.substring(index + "filename=".length());
		int line = value.indexOf('\n');
		if (line >= 0)
			value = value.substring(0, line);
		int carriage = value.indexOf('\r');
		if (carriage >= 0)
			value = value.substring(0, carriage);
		value = value.trim();
		if (value.startsWith("\"") && value.endsWith("\"") && value.length() > 2)
			value = value.substring(1, value.length() - 1);
		int slash = Math.max(value.lastIndexOf('/'), value.lastIndexOf('\\'));
		if (slash >= 0)
			value = value.substring(slash + 1);
		value = URLDecoder.decode(value, "UTF-8");
		return (TextUtils.isEmpty(value) ? null : value);
	}

	private static String uniqueName(File directory, String name) {
		File target = new File(directory, name);
		if (!target.exists())
			return (name);
		int dot = name.lastIndexOf('.');
		String base = dot > 0 ? name.substring(0, dot) : name;
		String extension = dot > 0 ? name.substring(dot) : "";
		for (int i = 1; i < 1000; i++) {
			String candidate = base + "(" + i + ")" + extension;
			if (!new File(directory, candidate).exists())
				return (candidate);
		}
		return (name + "_" + System.currentTimeMillis());
	}

	private static int indexOf(byte[] data, byte[] pattern, int from) {
		if (pattern.length == 0 || from < 0 || from >= data.length)
			return (-1);
		outer:
		for (int i = from; i <= data.length - pattern.length; i++) {
			for (int j = 0; j < pattern.length; j++) {
				if (data[i + j] != pattern[j])
					continue outer;
			}
			return (i);
		}
		return (-1);
	}

	private static byte[] readFully(InputStream input, int length) throws IOException {
		ByteArrayOutputStream buffer = new ByteArrayOutputStream(length);
		byte[] chunk = new byte[64 * 1024];
		int remaining = length;
		while (remaining > 0) {
			int read = input.read(chunk, 0, Math.min(chunk.length, remaining));
			if (read < 0)
				break;
			buffer.write(chunk, 0, read);
			remaining -= read;
		}
		return (buffer.toByteArray());
	}

	private static String readLine(InputStream input) throws IOException {
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		int previous = -1;
		while (true) {
			int value = input.read();
			if (value < 0)
				break;
			if (previous == '\r' && value == '\n')
				break;
			if (previous >= 0)
				buffer.write(previous);
			previous = value;
		}
		if (buffer.size() == 0)
			return (null);
		return (new String(buffer.toByteArray(), "UTF-8"));
	}

	private void sendPage(OutputStream output, String html) throws IOException {
		byte[] body = html.getBytes("UTF-8");
		String head = "HTTP/1.1 200 OK\r\n"
				+ "Content-Type: text/html; charset=utf-8\r\n"
				+ "Content-Length: " + body.length + "\r\n"
				+ "Connection: close\r\n\r\n";
		output.write(head.getBytes("UTF-8"));
		output.write(body);
		output.flush();
	}

	private String buildUploadPage() {
		StringBuilder html = new StringBuilder();
		html.append("<!DOCTYPE html><html><head><meta charset='utf-8'>");
		html.append("<meta name='viewport' content='width=device-width,initial-scale=1'>");
		html.append("<title>DesktopTV 文件上传</title></head>");
		html.append("<body style='font-family:sans-serif;background:#101010;color:#eee;max-width:640px;margin:0 auto;padding:24px'>");
		html.append("<h2>DesktopTV 电视文件上传</h2>");
		html.append("<p style='color:#9e9e9e'>选择一个或多个文件，提交后自动传到电视。文件保存在电视的 Download/DesktopTV接收 目录。</p>");
		html.append("<form action='/upload' method='post' enctype='multipart/form-data'>");
		html.append("<input type='file' name='file' multiple style='color:#eee;padding:12px 0'>");
		html.append("<br><br><button type='submit' style='font-size:20px;padding:12px 40px;background:#2196F3;color:#fff;border:0;border-radius:8px'>开始上传</button>");
		html.append("</form>");
		html.append("<p style='color:#9e9e9e'>大文件上传时请保持本页面打开，电视会显示接收进度。</p>");
		html.append("<p style='color:#666'>DesktopTV file upload. Files are saved to Download/DesktopTV接收 on the TV.</p>");
		html.append("</body></html>");
		return (html.toString());
	}

	private String buildResultPage(int count, List<String> failed) {
		StringBuilder html = new StringBuilder();
		html.append("<!DOCTYPE html><html><head><meta charset='utf-8'>");
		html.append("<meta name='viewport' content='width=device-width,initial-scale=1'>");
		html.append("<title>上传完成</title></head>");
		html.append("<body style='font-family:sans-serif;background:#101010;color:#eee;max-width:640px;margin:0 auto;padding:24px'>");
		if (count > 0)
			html.append("<h2>✔ 已接收 ").append(count).append(" 个文件</h2>");
		else
			html.append("<h2>⚠ 没有收到文件</h2>");
		if (failed != null && !failed.isEmpty()) {
			html.append("<p style='color:#ff8a80'>失败：").append(TextUtils.join(", ", failed)).append("</p>");
		}
		html.append("<p><a href='/' style='color:#2196F3;font-size:18px'>继续上传</a></p>");
		html.append("</body></html>");
		return (html.toString());
	}
}
