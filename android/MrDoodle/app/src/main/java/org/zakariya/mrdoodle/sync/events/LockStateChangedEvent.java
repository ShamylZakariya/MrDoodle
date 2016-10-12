package org.zakariya.mrdoodle.sync.events;

import java.util.HashSet;
import java.util.Set;

/**
 * Event fired when a lock is granted on a document.
 */

public class LockStateChangedEvent {

	private Set<String> grantedLocks;
	private Set<String> foreignLocks;

	public LockStateChangedEvent(Set<String> allGrantedLocks, Set<String> allForeignLocks) {
		this.grantedLocks = new HashSet<>(allGrantedLocks);
		this.foreignLocks = new HashSet<>(allForeignLocks);
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

	public Set<String> getGrantedLocks() {
		return grantedLocks;
	}

	public Set<String> getForeignLocks() {
		return foreignLocks;
	}

	@Override
	public String toString() {
		return "[LockStateChangedEvent grantedLocks: [" + grantedLocks + "] foreignLocks: [" + foreignLocks + "]]";
	}
}
