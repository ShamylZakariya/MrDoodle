package org.zakariya.mrdoodle.net.model;

/**
 * Represents a change pulled from the server to update local state.
 */

public class RemoteChangeReport {

	public enum Action {
		CREATE,
		UPDATE,
		DELETE
	}

	private String documentId;
	private Action action;

	public RemoteChangeReport(String documentId, Action action) {
		this.documentId = documentId;
		this.action = action;
	}

	public String getDocumentId() {
		return documentId;
	}

	public Action getAction() {
		return action;
	}

	@Override
	public String toString() {
		return "[RemoteChange id: " + documentId + " action: " + action + "]";
	}
}
