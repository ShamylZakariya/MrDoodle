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

import rx.Observer;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Func1;
import rx.schedulers.Schedulers;

/**
 * Simple helper for sharing a doodle's rendered image
 */

public class DoodleShareHelper {

	private static final String TAG = "DoodleShareHelper";
	private static final String SAVE_DIR = "images";
	private static final String SAVE_EXTENSION = ".png";

	static public void share(final Activity activity, DoodleDocument document) {

		int size = activity.getResources().getInteger(R.integer.doodle_share_image_size);
		int padding = activity.getResources().getInteger(R.integer.doodle_share_image_padding);
		final String title = document.getName();

		DoodleThumbnailRenderer.getInstance().renderThumbnail(document, size, size, padding)
				.map(new Func1<Bitmap, File>() {
					@Override
					public File call(Bitmap bitmap) {
						Context context = activity.getApplicationContext();
						File cachePath = new File(context.getCacheDir(), SAVE_DIR);

						//noinspection ResultOfMethodCallIgnored
						cachePath.mkdirs();

						try {
							String fileName = title + SAVE_EXTENSION;
							FileOutputStream stream = new FileOutputStream(cachePath + "/" + fileName);
							bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
							stream.close();
							return new File(cachePath, fileName);
						} catch (IOException e) {
							Log.e(TAG, "share::map::call onError:", e);
						}
						return null;
					}
				})
				.subscribeOn(Schedulers.io())
				.observeOn(AndroidSchedulers.mainThread())
				.subscribe(new Observer<File>() {
					@Override
					public void onCompleted() {
					}

					@Override
					public void onError(Throwable e) {
						Log.e(TAG, "share::onError: ", e);
					}

					@Override
					public void onNext(File file) {
						showShare(activity, file, title);
					}
				});
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
