package org.zakariya.mrdoodle.util;

import android.os.Handler;
import android.os.Looper;

import com.squareup.otto.Bus;

/**
 * Created by shamyl on 1/2/16.
 */
public class BusProvider {
	private static final Bus UI_BUS = new Bus();
	private static Handler mainThreadHandler;

	private BusProvider() {
	}

	public static Bus getBus() {
		return UI_BUS;
	}

	public static void postOnMainThread(final Bus bus, final Object event) {
		if (mainThreadHandler == null) {
			mainThreadHandler = new Handler(Looper.getMainLooper());
		}

		mainThreadHandler.post(new Runnable() {
			@Override
			public void run() {
				bus.post(event);
			}
		});
	}
}
