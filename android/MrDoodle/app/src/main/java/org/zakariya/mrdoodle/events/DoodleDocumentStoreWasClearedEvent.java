package org.zakariya.mrdoodle.events;

/**
 * Event sent after a reset & sync, before new doodle documents brought down from upstream are processed.
 * Listeners should use this to clear any references to existing DoodleDocuments
 */

public class DoodleDocumentStoreWasClearedEvent {
}
