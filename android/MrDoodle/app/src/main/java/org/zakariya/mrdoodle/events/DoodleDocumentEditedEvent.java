package org.zakariya.mrdoodle.events;

/**
 * Event emitted when a DoodleDocument is edited
 */
public class DoodleDocumentEditedEvent {

	private String uuid;

	public DoodleDocumentEditedEvent(String uuid) {
		this.uuid = uuid;
	}

	public String getUuid() {
		return uuid;
	}
}
