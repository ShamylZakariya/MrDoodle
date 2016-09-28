package org.zakariya.mrdoodle.util;

import android.os.Handler;
import android.os.Looper;

import com.squareup.otto.Bus;
import com.squareup.otto.ThreadEnforcer;

/**
 * Created by shamyl on 1/2/16.
 */
public class BusProvider {
	private static final Bus MAIN_THREAD_BUS = new Bus(ThreadEnforcer.MAIN);
	private static final Bus ANY_THREAD_BUS = new Bus(ThreadEnforcer.ANY);
	private static Handler mainThreadHandler;

	private BusProvider() {
	}

	public static Bus getMainThreadBus() {
		return MAIN_THREAD_BUS;
	}

	public static Bus getAnyThreadBus() {
		return ANY_THREAD_BUS;
	}

	public static void postOnMainThread(final Object event) {
		if (mainThreadHandler == null) {
			mainThreadHandler = new Handler(Looper.getMainLooper());
		}

		mainThreadHandler.post(new Runnable() {
			@Override
			public void run() {
				MAIN_THREAD_BUS.post(event);
			}
		});
	}
}
