package org.zakariya.mrdoodle.util;

import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Like AsyncTask, but simpler.
 * The only "interesting" aspect to AsyncExecutor is that jobs are given ids, and if a request to run
 * a job with the same id comes in while a job with the same id is running, the new job won't be executed,
 * but rather its listener will be handed to the previous job's callback list.
 */
public class AsyncExecutor {

	public interface Job<T> {
		T execute() throws Exception;
	}

	public interface JobListener<T> {
		void onComplete(T result);

		void onError(Throwable error);
	}

	private class JobInfo<T> {
		Future future;
		List<JobListener<T>> listeners = new ArrayList<>();
	}

	private Handler mainThreadHandler;
	private ExecutorService threadPool;
	private final Map<String, JobInfo> jobsById = new HashMap<>();

	public AsyncExecutor() {
		threadPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
		mainThreadHandler = new Handler(Looper.getMainLooper());
	}

	/**
	 * Schedule a job for execution.
	 * If a job "foo" is scheduled, and another job "foo" is scheduled while the first job is still running,
	 * the later job will be ignored, but its listeners will be attached to be notified when the first job
	 * completes or fails.
	 * @param id identifier by which to refer to this job
	 * @param job the job to run in a background thread
	 * @param listener listener to be notified when job completes, or if it throws an exception while executing
	 * @param <T> the type of data the job will pass to the listener on successful execution
	 */
	public <T> void execute(final String id, final Job<T> job, JobListener<T> listener) {
		synchronized (jobsById) {
			if (jobsById.containsKey(id)) {
				@SuppressWarnings("unchecked")
				JobInfo<T> info = jobsById.get(id);
				info.listeners.add(listener);
			} else {
				final JobInfo<T> info = new JobInfo<>();
				jobsById.put(id, info);

				info.listeners.add(listener);
				info.future = threadPool.submit(new Runnable() {
					@Override
					public void run() {
						try {
							dispatchResult(info, job.execute());
						} catch (Exception e) {
							dispatchError(info, e);
						}
						synchronized (jobsById) {
							jobsById.remove(id);
						}
					}
				});
			}
		}
	}

	/**
	 * Check if a job with a given id is running and hasn't completed yet
	 * @param id the identifier for a particular job
	 * @return true if that job is running and hasn't completed yet
	 */
	public boolean isScheduled(String id) {
		synchronized (jobsById) {
			JobInfo info = jobsById.get(id);
			return info != null && !info.future.isDone() && !info.future.isCancelled();
		}
	}

	/**
	 * Terminate a job by its id. Listeners will be discarded and will not receive any notifications.
	 * @param id the id of a particular job
	 * @return true if the job existed, hadn't finished, and hadn't been canceled yet
	 */
	public boolean cancel(String id) {
		synchronized (jobsById) {
			if (jobsById.containsKey(id)) {
				JobInfo info = jobsById.get(id);
				info.listeners.clear();
				Future future = info.future;
				if (!future.isCancelled() && !future.isDone()) {
					return future.cancel(false);
				}
			}
			return false;
		}
	}

	private <T> void dispatchResult(final JobInfo<T> info, final T result) {
		synchronized (jobsById) {
			mainThreadHandler.post(new Runnable() {
				@Override
				public void run() {
					for (JobListener<T> listener : info.listeners) {
						listener.onComplete(result);
					}
				}
			});
		}
	}

	private <T> void dispatchError(final JobInfo<T> info, final Exception e) {
		synchronized (jobsById) {
			mainThreadHandler.post(new Runnable() {
				@Override
				public void run() {
					for (JobListener<T> listener : info.listeners) {
						listener.onError(e);
					}
				}
			});
		}
	}
}
