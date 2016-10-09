package org.zakariya.mrdoodleserver.sync;

import org.junit.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Created by shamyl on 10/8/16.
 */
public class LockManagerTest {

	private static final String DEVICE_ID_1 = "abcde";
	private static final String DEVICE_ID_2 = "fghij";

	private static class LockManagerListener implements LockManager.Listener {

		Map<String,Set<String>> locksByDeviceId = new HashMap<>();
		Set<String> lockedDocumentIds = new HashSet<>();

		@Override
		public void onLockAcquired(String deviceId, String documentId) {
			Set<String> locks = locksByDeviceId.get(deviceId);
			if (locks == null) {
				locks = new HashSet<>();
				locksByDeviceId.put(deviceId, locks);
			}
			locks.add(documentId);
			lockedDocumentIds.add(documentId);
		}

		@Override
		public void onLockReleased(String deviceId, String documentId) {
			Set<String> locks = locksByDeviceId.get(deviceId);
			if (locks == null) {
				throw new IllegalStateException("onLockRelease called for device which never got a lock in the first place");
			}
			locks.remove(documentId);
			lockedDocumentIds.remove(documentId);
		}

		boolean isLocked(String documentId) {
			return lockedDocumentIds.contains(documentId);
		}

		boolean hasLock(String deviceId, String documentId) {
			Set<String> locks = locksByDeviceId.get(deviceId);
			if (locks != null) {
				return locks.contains(documentId);
			} else {
				return false;
			}
		}

	}

	@Test
	public void addListener() throws Exception {

		LockManager lockManager = new LockManager();
		LockManagerListener listener = new LockManagerListener();
		lockManager.addListener(listener);

		lockManager.lock(DEVICE_ID_1, "a");
		assertTrue("Listener should know that document is locked", listener.isLocked("a"));

	}

	@Test
	public void removeListener() throws Exception {
		LockManager lockManager = new LockManager();
		LockManagerListener listener = new LockManagerListener();
		lockManager.addListener(listener);

		lockManager.lock(DEVICE_ID_1, "a");
		assertTrue("Listener should know that document is locked", listener.isLocked("a"));

		lockManager.removeListener(listener);
		lockManager.unlock(DEVICE_ID_1, "a");
		assertTrue("After removing listener, it should not know that document has been unlocked", listener.isLocked("a"));
	}

	@Test
	public void lock() throws Exception {
		LockManager lockManager = new LockManager();
		lockManager.lock(DEVICE_ID_1, "a");
		lockManager.lock(DEVICE_ID_1, "b");
		assertTrue("lock manager should lock", lockManager.isLocked("a"));
		assertTrue("lock manager should lock", lockManager.isLocked("b"));
		assertFalse("lock manager lock reporting should be accurate", lockManager.isLocked("Q"));
	}

	@Test
	public void unlock() throws Exception {
		LockManager lockManager = new LockManager();
		lockManager.lock(DEVICE_ID_1, "a");
		lockManager.lock(DEVICE_ID_1, "b");
		assertTrue("lock manager should lock", lockManager.isLocked("a"));
		assertTrue("lock manager should lock", lockManager.isLocked("b"));

		lockManager.unlock(DEVICE_ID_1, "a");
		assertFalse("lock manager should unlock", lockManager.isLocked("a"));
		assertTrue("lock manager unlock shouldn't affect other locks", lockManager.isLocked("b"));

		lockManager.unlock(DEVICE_ID_1, "b");
		assertFalse("lock manager should unlock", lockManager.isLocked("b"));
	}

	@Test
	public void unlockByDeviceId() throws Exception {
		LockManager lockManager = new LockManager();
		lockManager.lock(DEVICE_ID_1, "a");
		lockManager.lock(DEVICE_ID_1, "b");
		lockManager.lock(DEVICE_ID_2, "c");
		lockManager.lock(DEVICE_ID_2, "d");
		assertTrue("lock manager should lock", lockManager.isLocked("a"));
		assertTrue("lock manager should lock", lockManager.isLocked("b"));
		assertTrue("lock manager should lock", lockManager.isLocked("c"));
		assertTrue("lock manager should lock", lockManager.isLocked("d"));

		lockManager.unlock(DEVICE_ID_1);
		assertFalse("unlock of a device id should release all that device's locks", lockManager.isLocked("a"));
		assertFalse("unlock of a device id should release all that device's locks", lockManager.isLocked("b"));
		assertTrue("unlocking one device's locks shouldn't affect other devices locks", lockManager.isLocked("c"));
		assertTrue("unlocking one device's locks shouldn't affect other devices locks", lockManager.isLocked("d"));

		lockManager.unlock(DEVICE_ID_2);
		assertFalse("unlock of a device id should release all that device's locks", lockManager.isLocked("c"));
		assertFalse("unlock of a device id should release all that device's locks", lockManager.isLocked("d"));
	}

	@Test
	public void lockedDocumentIds() throws Exception {
		LockManager lockManager = new LockManager();
		lockManager.lock(DEVICE_ID_1, "a");
		lockManager.lock(DEVICE_ID_1, "b");
		lockManager.lock(DEVICE_ID_2, "c");
		lockManager.lock(DEVICE_ID_2, "d");

		Set<String> locks = lockManager.getLockedDocumentIds();
		assertTrue("locked document set should include locked document", locks.contains("a"));
		assertTrue("locked document set should include locked document", locks.contains("b"));
		assertTrue("locked document set should include locked document", locks.contains("c"));
		assertTrue("locked document set should include locked document", locks.contains("d"));
		assertFalse("locked document set shouldn't include documents which were not locked", locks.contains("q"));

		lockManager.unlock(DEVICE_ID_1, "a");
		locks = lockManager.getLockedDocumentIds();
		assertFalse("locked document set should not include unlocked document", locks.contains("a"));
	}

}