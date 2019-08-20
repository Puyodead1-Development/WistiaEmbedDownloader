package me.puyodead1.wistiaembeddownloader;

import java.io.InputStream;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URL;

import org.eclipse.swt.widgets.Display;

public class Downloader implements Runnable {

	private static final int MAX_BUFFER_SIZE = 1024;

	public static final String STATUSES[] = { "Downloading", "Paused", "Complete", "Cancelled", "Error" };

	public static final int DOWNLOADING = 0;
	public static final int PAUSED = 1;
	public static final int COMPLETE = 2;
	public static final int CANCELLED = 3;
	public static final int ERROR = 4;
	public static final int INVALID = 5;

	private URL url;
	private static int size;
	private static int downloaded;
	private static int status = INVALID;
	private String output;

	private static Thread thread;

	public Downloader(URL url, String output) {
		this.url = url;
		this.output = output;
		size = -1;
		downloaded = 0;
		status = DOWNLOADING;

		WistiaEmbedDownloader.log("Downloading...");
		WistiaEmbedDownloader.getProgressLbl().setVisible(true);
		download();
	}

	public String getURL() {
		return url.toString();
	}

	public int getSize() {
		return size;
	}

	public static float getProgress() {
		return ((float) downloaded / size) * 100;
	}

	public static int getStatus() {
		return status;
	}

	public static Thread getThread() {
		return thread;
	}

	private void error() {
		status = ERROR;
		stateChanged();
	}

	private void download() {
		thread = new Thread(this);
		thread.start();
	}

	public void run() {
		RandomAccessFile file = null;
		InputStream stream = null;

		try {
			// Open connection to URL.
			HttpURLConnection connection = (HttpURLConnection) url.openConnection();

			// Specify what portion of file to download.
			connection.setRequestProperty("Range", "bytes=" + downloaded + "-");

			// Connect to server.
			connection.connect();

			// Make sure response code is in the 200 range.
			if (connection.getResponseCode() / 100 != 2) {
				error();
				WistiaEmbedDownloader.error("Invalid response code");
			}

			// Check for valid content length.
			int contentLength = connection.getContentLength();
			if (contentLength < 1) {
				error();
				WistiaEmbedDownloader.error("Invalid content length");
			}

			/*
			 * Set the size for this download if it hasn't been already set.
			 */
			if (size == -1) {
				size = contentLength;
				stateChanged();
			}

			// Open file and seek to the end of it.
			file = new RandomAccessFile(output, "rw");
			file.seek(downloaded);

			stream = connection.getInputStream();
			while (status == DOWNLOADING) {
				/*
				 * Size buffer according to how much of the file is left to download.
				 */
				byte buffer[];
				if (size - downloaded > MAX_BUFFER_SIZE) {
					buffer = new byte[MAX_BUFFER_SIZE];
				} else {
					buffer = new byte[size - downloaded];
				}

				// Read from server into buffer.
				int read = stream.read(buffer);
				if (read == -1)
					break;

				// Write buffer to file.
				file.write(buffer, 0, read);
				downloaded += read;
				stateChanged();
			}

			/*
			 * Change status to complete if this point was reached because downloading has
			 * finished.
			 */
			if (status == DOWNLOADING) {
				status = COMPLETE;
				stateChanged();
			}
		} catch (Exception e) {
			error();
			StringWriter writer = new StringWriter();
			e.printStackTrace(new PrintWriter(writer));
			WistiaEmbedDownloader.error(writer.toString());
		} finally {
			// Close file.
			if (file != null) {
				try {
					file.close();
				} catch (Exception e) {
					StringWriter writer = new StringWriter();
					e.printStackTrace(new PrintWriter(writer));
					WistiaEmbedDownloader.error(writer.toString());
				}
			}

			// Close connection to server.
			if (stream != null) {
				try {
					stream.close();
				} catch (Exception e) {
					StringWriter writer = new StringWriter();
					e.printStackTrace(new PrintWriter(writer));
					WistiaEmbedDownloader.error(writer.toString());
				}
			}
		}
	}

	private void stateChanged() {
		if (status == ERROR) {
			WistiaEmbedDownloader.error("Error downloading.");
		} else if (status == DOWNLOADING) {
			// update progess bar
			final int progress = Math.round(this.getProgress());

			Display.getDefault().asyncExec(new Runnable() {
				public void run() {
					WistiaEmbedDownloader.getProgressLbl().setText(progress + "%");
				}
			});
		} else if (status == COMPLETE) {
			Display.getDefault().asyncExec(new Runnable() {
				public void run() {
					WistiaEmbedDownloader.log("Download Complete!");
					WistiaEmbedDownloader.setupForNewDownload(false);
				}
			});
		}
	}
}
