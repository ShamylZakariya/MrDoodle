package org.zakariya.mrdoodle.events;

/**
 * Event emitted when a DoodleDocument is deleted.
 */
public class DoodleDocumentDeletedEvent {

	private String uuid;

	public DoodleDocumentDeletedEvent(String uuid) {
		this.uuid = uuid;
	}

	public String getUuid() {
		return uuid;
	}
}
