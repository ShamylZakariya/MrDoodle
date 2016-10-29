package org.zakariya.mrdoodle.sync;

import org.zakariya.mrdoodle.BuildConfig;
import org.zakariya.mrdoodle.util.EmulatorDetection;

/**
 * Created by shamyl on 8/8/16.
 */
public class SyncConfiguration {

	private boolean isEmulator;
	private String userAgent;

	public SyncConfiguration() {
		this(EmulatorDetection.isEmulator());
	}

	public SyncConfiguration(boolean isEmulator) {
		this.isEmulator = isEmulator;
	}

	public boolean isEmulator() {
		return isEmulator;
	}

	public String getSyncServerHost() {
		return isEmulator
				? "10.0.2.2:4567"
				: BuildConfig.LOCAL_IP + ":4567";
	}

	public String getApiPath() {
		return "/api/v1";
	}

	public String getSyncServiceUrl() {
		return "http://" + getSyncServerHost() + getApiPath() + '/';
	}

	public String getSyncServerConnectionUrl() {
		return "ws://" + getSyncServerHost() + getApiPath() + "/connect/";
	}

	public String getUserAgent() {
		return userAgent;
	}

	public void setUserAgent(String userAgent) {
		this.userAgent = userAgent;
	}
}
