package org.zakariya.mrdoodle.sync.events;

import java.util.HashSet;
import java.util.Set;

/**
 * Event fired when a lock is granted on a document.
 */

public class LockStateChangedEvent {

	private Set<String> grantedLocks;
	private Set<String> foreignLocks;

	public LockStateChangedEvent(Set<String> grantedLocks, Set<String> foreignLocks) {
		this.grantedLocks = new HashSet<>(grantedLocks);
		this.foreignLocks = new HashSet<>(foreignLocks);
	}

	public boolean isUnlocked(String documentId) {
		return !isLockedByThisDevice(documentId) && !isLockedByAnotherDevice(documentId);
	}

	public boolean isLockedByThisDevice(String documentId) {
		return grantedLocks.contains(documentId);
	}

	public boolean isLockedByAnotherDevice(String documentId) {
		return foreignLocks.contains(documentId);
	}


}
