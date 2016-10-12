package org.zakariya.mrdoodle.model;

import java.io.IOException;

/**
 * thrown when a doodle document can't be instantiated from a malformed bye sequence
 */

public class MalformedDoodleDocumentException extends IOException {
	public MalformedDoodleDocumentException(String message) {
		super(message);
	}

	public MalformedDoodleDocumentException(String message, Throwable cause) {
		super(message, cause);
	}

	public MalformedDoodleDocumentException(Throwable cause) {
		super(cause);
	}
}
