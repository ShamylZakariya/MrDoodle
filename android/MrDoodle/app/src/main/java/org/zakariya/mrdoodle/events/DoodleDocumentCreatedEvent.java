package org.zakariya.mrdoodle.events;

/**
 * Event emitted when a DoodleDocument is created
 */
public class DoodleDocumentCreatedEvent {

	private String uuid;

	public DoodleDocumentCreatedEvent(String uuid) {
		this.uuid = uuid;
	}

	public String getUuid() {
		return uuid;
	}
}
