package org.zakariya.mrdoodle.util;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.support.v4.content.FileProvider;
import android.util.Log;

import org.zakariya.mrdoodle.R;
import org.zakariya.mrdoodle.model.DoodleDocument;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Simple helper for sharing a doodle's rendered image
 */

public class DoodleShareHelper {

	private static final String TAG = "DoodleShareHelper";
	private static final String SAVE_DIR = "images";
	private static final String SAVE_FILE = "share.png";

	static public void share(final Activity activity, DoodleDocument document) {

		int size = activity.getResources().getInteger(R.integer.doodle_share_image_size);
		int padding = activity.getResources().getInteger(R.integer.doodle_share_image_padding);
		final String title = document.getName();
		DoodleThumbnailRenderer.getInstance().renderThumbnail(document, size, size, padding, new DoodleThumbnailRenderer.Callbacks() {
			@Override
			public void onThumbnailReady(Bitmap thumbnail) {
				onRenderingAvailable(activity, thumbnail, title);
			}
		});
	}

	private static void onRenderingAvailable(Activity activity, Bitmap thumbnail, String title) {

		// TODO: We get a pretty big hit when saving this image to disk, I think. May want to use Rx and run this in background. If I do this, consider making DoodleThumbnailRender offer an observable!

		try {
			// save the thumbnail to cache dir
			Context context = activity.getApplicationContext();
			File cachePath = new File(context.getCacheDir(), SAVE_DIR);

			//noinspection ResultOfMethodCallIgnored
			cachePath.mkdirs();

			FileOutputStream stream = new FileOutputStream(cachePath + "/" + SAVE_FILE);
			thumbnail.compress(Bitmap.CompressFormat.PNG, 100, stream);
			stream.close();

			File shareFile = new File(cachePath, SAVE_FILE);
			showShare(activity, shareFile, title);

		} catch (IOException e) {
			Log.e(TAG, "onRenderingAvailable: ", e);
			e.printStackTrace();
		}
	}

	private static void showShare(Activity activity, File shareFile, String title) {
		Context context = activity.getApplicationContext();
		String fileProviderName = context.getString(R.string.file_provider_name);
		Uri contentUri = FileProvider.getUriForFile(context, fileProviderName, shareFile);

		if (contentUri != null) {
			Log.i(TAG, "showShare: contentUri:" + contentUri + " file: " + shareFile);

			// create an intent granting temp permission for receiving app to read this file
			Intent shareIntent = new Intent();
			shareIntent.setAction(Intent.ACTION_SEND);
			shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
			shareIntent.setDataAndType(contentUri, activity.getContentResolver().getType(contentUri));
			shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
			shareIntent.putExtra(Intent.EXTRA_TEXT, title);
			shareIntent.putExtra(Intent.EXTRA_SUBJECT, title);

			String chooserTitle = context.getString(R.string.doodle_share_chooser_title);
			activity.startActivity(Intent.createChooser(shareIntent, chooserTitle));

		} else {
			Log.e(TAG, "showShare: Unable to create a content URI for file: " + shareFile);
		}
	}

}
