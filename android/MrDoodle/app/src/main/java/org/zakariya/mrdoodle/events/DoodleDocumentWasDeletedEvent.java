package org.zakariya.mrdoodle.events;

/**
 * Event emitted when a DoodleDocument is deleted.
 */
public class DoodleDocumentWasDeletedEvent {

	private String uuid;

	public DoodleDocumentWasDeletedEvent(String uuid) {
		this.uuid = uuid;
	}

	public String getUuid() {
		return uuid;
	}
}
