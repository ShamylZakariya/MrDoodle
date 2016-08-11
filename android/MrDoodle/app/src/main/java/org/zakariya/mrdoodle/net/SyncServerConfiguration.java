package org.zakariya.mrdoodle.net;

/**
 * Created by shamyl on 8/8/16.
 */
public class SyncServerConfiguration {

	private boolean isEmulator;

	public SyncServerConfiguration(boolean isEmulator) {
		this.isEmulator = isEmulator;
	}

	public boolean isEmulator() {
		return isEmulator;
	}

	String getSyncServerHost() {
		return isEmulator
				? "10.0.2.2:4567"
				: "10.0.1.51:4567";
	}

	String getSyncServerConnectionUrl() {
		return "ws://" + getSyncServerHost() + "/connect/";
	}

}
