package org.zakariya.mrdoodleserver.sync.transport;

/**
 * Response to requests to lock or unlock a document
 */
public class LockRequestResponse {
	/**
	 * Id of the document
	 */
	public String documentId;

	/**
	 * lock status of the document
	 */
	public boolean locked;

	/**
	 * True if the request to lock or unlock the document was granted. This
	 * will ONLY be true IFF the lock status of the document changed as result of request
	 */
	public boolean granted;
}
