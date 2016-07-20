package org.zakariya.mrdoodle.util;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;

import java.lang.reflect.Field;

/**
 * Created by shamyl on 12/22/15.
 */
public class FragmentUtils {

	/**
	 * Adapted from:
	 * http://stackoverflow.com/questions/27004721/start-activity-from-fragment-using-transition-api-21-support
	 *
	 * @param fragment
	 * @param intent
	 * @param requestCode
	 * @param options
	 */
	public static void startActivityForResult(Fragment fragment, Intent intent,
	                                          int requestCode, Bundle options) {
		if (Build.VERSION.SDK_INT >= 16) {
			if ((requestCode & 0xffff0000) != 0) {
				throw new IllegalArgumentException("Can only use lower 16 bits" +
						" for requestCode");
			}
			if (requestCode != -1) {
				try {
					Field mIndex = Fragment.class.getDeclaredField("mIndex");
					mIndex.setAccessible(true);
					requestCode = ((mIndex.getInt(fragment) + 1) << 16) + (requestCode & 0xffff);
				} catch (NoSuchFieldException | IllegalAccessException e) {
					throw new RuntimeException(e);
				}
			}
			ActivityCompat.startActivityForResult(fragment.getActivity(), intent,
					requestCode, options);
		} else {
			fragment.getActivity().startActivityFromFragment(fragment, intent, requestCode);
		}
	}

}
