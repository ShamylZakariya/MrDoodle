package org.zakariya.mrdoodle.events;

/**
 * Event emitted when a DoodleDocument is deleted.
 */
public class DoodleDocumentWillBeDeletedEvent {

	private String uuid;

	public DoodleDocumentWillBeDeletedEvent(String uuid) {
		this.uuid = uuid;
	}

	public String getUuid() {
		return uuid;
	}
}
