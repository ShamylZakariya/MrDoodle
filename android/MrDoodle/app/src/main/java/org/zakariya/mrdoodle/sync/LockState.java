package org.zakariya.mrdoodle.sync;

import org.zakariya.mrdoodle.sync.events.LockStateChangedEvent;
import org.zakariya.mrdoodle.util.BusProvider;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Represents all document grantedLocks held by this device
 */

public class LockState {

	private Set<String> grantedLocks = new HashSet<>();
	private Set<String> foreignLocks = new HashSet<>();

	public LockState() {
	}

	/**
	 * @param documentId the id of a document
	 * @return true if the document is not locked by this or any other device
	 */
	public boolean isUnlocked(String documentId) {
		return !isLockedByThisDevice(documentId) && !isLockedByAnotherDevice(documentId);
	}

	/**
	 * @param documentId the id of a document
	 * @return true if the document is locked by this device
	 */
	public boolean isLockedByThisDevice(String documentId) {
		return grantedLocks.contains(documentId);
	}

	/**
	 * @param documentId the id of a document
	 * @return true if the document is locked by another device
	 */
	public boolean isLockedByAnotherDevice(String documentId) {
		return foreignLocks.contains(documentId);
	}

	/**
	 * Update lock state. If state changes as a result of the update, emit
	 * LockStateChangedEvent describing the new lock state.
	 *
	 * @param grantedLocks new list of locks this device owns
	 * @param foreignLocks new list of locks owned by other devices
	 */
	void update(Collection<String> grantedLocks, Collection<String> foreignLocks) {

		Set<String> newGrantedLocks = new HashSet<>(grantedLocks);
		Set<String> newForeignLocks = new HashSet<>(foreignLocks);
		boolean shouldNotify = !newGrantedLocks.equals(this.grantedLocks)
				|| !newForeignLocks.equals(this.foreignLocks);

		this.grantedLocks = newGrantedLocks;
		this.foreignLocks = newForeignLocks;
		if (shouldNotify) {
			notifyLockStatusChanged();
		}
	}

	void addGrantedLock(String documentId) {
		if (grantedLocks.add(documentId)) {
			notifyLockStatusChanged();
		}
	}

	void removeGrantedLock(String documentId) {
		if (grantedLocks.remove(documentId)) {
			notifyLockStatusChanged();
		}
	}

	void addForeignLock(String documentId) {
		if (foreignLocks.add(documentId)) {
			notifyLockStatusChanged();
		}
	}

	void removeForeignLock(String documentId) {
		if (foreignLocks.remove(documentId)) {
			notifyLockStatusChanged();
		}
	}

	private void notifyLockStatusChanged() {
		BusProvider.postOnMainThread(new LockStateChangedEvent(grantedLocks, foreignLocks));
	}

}
