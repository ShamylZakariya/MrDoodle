package org.zakariya.mrdoodle.net.exceptions;

import retrofit2.Response;

/**
 * LockException
 * Exception thrown when requesting to server to lock or unlock documents
 */

public class LockException extends Exception {

	public LockException(String message) {
		super(message);
	}

	public LockException(String message, Response response) {
		super(message + " code: " + response.code() + " message: " + response.message());
	}

}
