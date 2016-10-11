package org.zakariya.mrdoodleserver.util;

import java.util.concurrent.Callable;

/**
 * Simple debounce implementation.
 */
public class Debouncer {

	public interface Function<T> {
		void apply(T t);

		@Override
		boolean equals(Object object);
	}

	public static <T> Function<T> debounce(final Function<T> function, final int delayMilliseconds) {
		return new Function<T>() {
			private java.util.concurrent.ScheduledFuture<Void> timeout;

			@Override
			public void apply(T v) {
				clearTimeout(timeout);

				java.util.concurrent.ScheduledExecutorService scheduler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
				timeout = scheduler.schedule(new Callable<Void>() {
					@Override
					public Void call() throws Exception {
						function.apply(v);
						return null;
					}
				}, delayMilliseconds, java.util.concurrent.TimeUnit.MILLISECONDS);

				scheduler.shutdown();
			}
		};
	}

	private static void clearTimeout(java.util.concurrent.ScheduledFuture scheduledFuture) {
		if (scheduledFuture != null && !scheduledFuture.isDone() && !scheduledFuture.isCancelled()) {
			scheduledFuture.cancel(false);
		}
	}

}
