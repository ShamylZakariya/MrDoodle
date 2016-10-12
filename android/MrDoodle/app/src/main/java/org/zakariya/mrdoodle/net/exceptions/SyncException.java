package org.zakariya.mrdoodle.net.exceptions;

import retrofit2.Response;

/**
 * Created by shamyl on 9/10/16.
 */
public class SyncException extends Exception {

	public SyncException(String message) {
		super(message);
	}

	public SyncException(String message, Response response) {
		super(message + " code: " + response.code() + " message: " + response.message());
	}
}
