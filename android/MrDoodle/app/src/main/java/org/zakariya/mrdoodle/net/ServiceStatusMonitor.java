package org.zakariya.mrdoodle.net;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import org.zakariya.mrdoodle.events.ServiceStatusAvailableEvent;
import org.zakariya.mrdoodle.net.transport.ServiceStatus;
import org.zakariya.mrdoodle.util.BusProvider;

import java.util.ArrayList;
import java.util.List;

import rx.Subscriber;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

/**
 * Queries for ServiceStatus via the StatusApi, and holds on to the current service status,
 * notifiying listeners on change.
 * ServiceStatus has nothing to do with being online or network reachability. Rather, it queries
 * a json file on a server which describes if sync is running, or in scheduled downtime, or
 * discontinued.
 */

public class ServiceStatusMonitor {

	public interface ServiceStatusListener {
		void onServiceStatusDidChange(ServiceStatus status);
	}

	private static final String TAG = "ServiceStatusMonitor";
	private static final String PREF_KEY_SERVICE_STATUS = "ServiceStatus";
	private ServiceStatus serviceStatus = null;
	private ServiceStatus mockServiceStatus = null;
	private SharedPreferences sharedPreferences;
	private List<ServiceStatusListener> listeners = new ArrayList<>();

	public ServiceStatusMonitor(Context context) {
		this.sharedPreferences = context.getSharedPreferences(ServiceStatusMonitor.class.getSimpleName(), Context.MODE_PRIVATE);

		// load persisted serviceStatus
		String statusJson = sharedPreferences.getString(PREF_KEY_SERVICE_STATUS, null);
		if (!TextUtils.isEmpty(statusJson)) {
			Gson gson = new Gson();
			try {
				serviceStatus = gson.fromJson(statusJson, ServiceStatus.class);
			} catch (JsonSyntaxException e) {
				serviceStatus = null;
				Log.e(TAG, "loadServiceStatus: unable to parse persisted ServiceStatus JSON string", e);
			}
		}

		// go default if needed - this should only happen on first run.
		// assume that service is alive and running.
		if (serviceStatus == null) {
			serviceStatus = new ServiceStatus();
			serviceStatus.serverStatus = ServiceStatus.ServerStatus.RUNNING.ordinal();
			serviceStatus.serverStatusMessage = null;
			serviceStatus.alertMessage = null;
		}

		// now attempt to load status from net
		loadServiceStatus();
	}

	public ServiceStatus getMockServiceStatus() {
		return mockServiceStatus;
	}

	/**
	 * Set a mock ServiceStatus for testing purposes
	 * @param mockServiceStatus a mock ServiceStatus which will override the real one
	 */
	public void setMockServiceStatus(ServiceStatus mockServiceStatus) {
		this.mockServiceStatus = mockServiceStatus;
		setServiceStatus(mockServiceStatus);
	}

	public ServiceStatus getServiceStatus() {
		return serviceStatus;
	}

	synchronized public void addServiceStatusListener(ServiceStatusListener listener) {
		listeners.add(listener);
		listener.onServiceStatusDidChange(serviceStatus);
	}

	synchronized public void removeServiceStatusListener(ServiceStatusListener listener) {
		listeners.remove(listener);
	}

	private void loadServiceStatus() {
		// update serviceStatus from net (if possible)
		StatusApiConfiguration config = new StatusApiConfiguration();
		StatusApi statusApi = new StatusApi(config);
		statusApi.getServiceStatus()
				.subscribeOn(Schedulers.io())
				.observeOn(AndroidSchedulers.mainThread())
				.subscribe(new Subscriber<ServiceStatus>() {
					@Override
					public void onCompleted() {
					}

					@Override
					public void onError(Throwable e) {
						Log.e(TAG, "loadServiceStatus - onError: ", e);
					}

					@Override
					public void onNext(ServiceStatus serviceStatus) {
						setServiceStatus(serviceStatus);
					}
				});
	}


	synchronized private void setServiceStatus(ServiceStatus status) {

		if (mockServiceStatus != null) {
			status = mockServiceStatus;
		}

		this.serviceStatus = status;

		// persist
		Gson gson = new Gson();
		String serviceStatusJson = gson.toJson(serviceStatus);
		sharedPreferences.edit().putString(PREF_KEY_SERVICE_STATUS, serviceStatusJson).apply();

		// now broadcast
		for (ServiceStatusListener listener : listeners) {
			listener.onServiceStatusDidChange(serviceStatus);
		}

		BusProvider.getMainThreadBus().post(new ServiceStatusAvailableEvent(serviceStatus));
	}

}
