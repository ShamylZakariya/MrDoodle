package org.zakariya.mrdoodle.model;

import java.io.IOException;

public class DoodleDocumentNotFoundException extends IOException {
	public DoodleDocumentNotFoundException(String message) {
		super(message);
	}

	public DoodleDocumentNotFoundException(String message, Throwable cause) {
		super(message, cause);
	}

	public DoodleDocumentNotFoundException(Throwable cause) {
		super(cause);
	}
}
