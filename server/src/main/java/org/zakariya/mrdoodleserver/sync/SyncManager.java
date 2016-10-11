package org.zakariya.mrdoodleserver.sync;

import org.eclipse.jetty.websocket.api.Session;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zakariya.mrdoodleserver.services.WebSocketConnection;
import org.zakariya.mrdoodleserver.sync.mock.MockDeviceIdManager;
import org.zakariya.mrdoodleserver.sync.transport.Status;
import org.zakariya.mrdoodleserver.sync.transport.TimestampRecordEntry;
import org.zakariya.mrdoodleserver.util.Configuration;
import org.zakariya.mrdoodleserver.util.Debouncer;
import redis.clients.jedis.JedisPool;

import java.util.*;

/**
 * SyncManager
 * Top level coordinator for sync activities for a specific google account
 */
public class SyncManager implements WebSocketConnection.OnUserSessionStatusChangeListener, LockManager.Listener {

	private static final Logger logger = LoggerFactory.getLogger(SyncManager.class);
	private static final String WRITE_SESSION_NAMESPACE = "write-session";
	private static final int STATUS_BROADCAST_DEBOUNCE_MILLISECONDS = 1000;

	private Configuration configuration;
	private String storagePrefix;
	private String accountId;
	private JedisPool jedisPool;
	private TimestampRecord timestampRecord;
	private BlobStore blobStore;
	private LockManager lockManager;
	private DeviceIdManagerInterface deviceIdManager;
	private Map<String, WriteSession> writeSessionsByToken = new HashMap<>();
	private Map<String, WriteSession> writeSessionsByDeviceId = new HashMap<>();
	private Debouncer.Function<Void> debouncedStatusBroadcastCall;

	public static class WriteSession {
		private String storagePrefix;
		private String accountId;
		private String token;
		private String deviceId;
		private TimestampRecord timestampRecord;
		private BlobStore blobStore;

		WriteSession(JedisPool jedisPool, String storagePrefix, String accountId, String deviceId) {
			this.storagePrefix = storagePrefix;
			this.accountId = accountId;
			this.deviceId = deviceId;
			token = UUID.randomUUID().toString();
			timestampRecord = new TimestampRecord();
			blobStore = new BlobStore(jedisPool, storagePrefix + "/" + WRITE_SESSION_NAMESPACE + "/" + token, accountId);
		}

		public String getToken() {
			return token;
		}

		public String getStoragePrefix() {
			return storagePrefix;
		}

		public String getAccountId() {
			return accountId;
		}

		public String getDeviceId() {
			return deviceId;
		}

		public TimestampRecord getTimestampRecord() {
			return timestampRecord;
		}

		public BlobStore getBlobStore() {
			return blobStore;
		}

		public void commit(TimestampRecord toTimestampRecord, BlobStore toBlobStore) {
			timestampRecord.save(toTimestampRecord);
			blobStore.save(toBlobStore);
		}

		public void discard() {
			blobStore.discard();
		}
	}

	public SyncManager(Configuration configuration, JedisPool jedisPool, String storagePrefix, String accountId) {
		this.configuration = configuration;
		this.jedisPool = jedisPool;
		this.accountId = accountId;
		this.storagePrefix = storagePrefix;
		this.timestampRecord = new TimestampRecord(jedisPool, storagePrefix, accountId);
		this.blobStore = new BlobStore(jedisPool, storagePrefix, accountId);
		this.lockManager = new LockManager();

		// TODO: Learn how to use dependency injection to make this smarter
		List<String> deviceIds = configuration.getArray("syncManager/deviceIdManager/mock/deviceIds");
		if (deviceIds != null) {
			logger.info("Creating MockDeviceIdManager with mock device ids: {}", deviceIds);
			this.deviceIdManager = new MockDeviceIdManager(deviceIds);
		} else {
			this.deviceIdManager = new DeviceIdManager();
		}

	}

	public void close() {
		// nothing, for now
	}

	public JedisPool getJedisPool() {
		return jedisPool;
	}

	public String getAccountId() {
		return accountId;
	}

	public LockManager getLockManager() {
		return lockManager;
	}

	public TimestampRecord getTimestampRecord() {
		return timestampRecord;
	}

	public BlobStore getBlobStore() {
		return blobStore;
	}

	public DeviceIdManagerInterface getDeviceIdManager() {
		return deviceIdManager;
	}

	public WriteSession startWriteSession(String deviceId) {
		WriteSession session = new WriteSession(jedisPool, storagePrefix, accountId, deviceId);
		writeSessionsByToken.put(session.getToken(), session);
		writeSessionsByDeviceId.put(deviceId, session);
		return session;
	}

	@Nullable
	public WriteSession getWriteSession(String token) {
		return writeSessionsByToken.get(token);
	}

	public boolean commitWriteSession(String deviceId, String token) {
		WriteSession session = writeSessionsByToken.get(token);
		if (session != null) {
			session.commit(timestampRecord, blobStore);
			writeSessionsByToken.remove(token);
			writeSessionsByDeviceId.remove(deviceId);

			return true;
		} else {
			return false;
		}
	}

	private void discardActiveWriteSessionsForDeviceId(String deviceId) {
		WriteSession session = writeSessionsByDeviceId.get(deviceId);
		if (session != null) {
			session.discard();
			writeSessionsByDeviceId.remove(deviceId);
			writeSessionsByToken.remove(session.getToken());
		}
	}

	/**
	 * Get the account status, which includes info like the current timestamp head, locked documents, etc
	 *
	 * @return the current account status
	 */
	public Status getStatus(String deviceId) {
		if (deviceId == null) {
			throw new NullPointerException("DeviceId cannot be null");
		}

		Status status = new Status();
		status.deviceId = deviceId;

		TimestampRecordEntry timestampHead = getTimestampRecord().getTimestampHead();
		if (timestampHead != null) {
			status.timestampHeadSeconds = timestampHead.getTimestampSeconds();
		}

		// copy over this device's granted locks
		status.grantedLockedDocumentIds = new ArrayList<>(getLockManager().getLockedDocumentIds(deviceId));

		// foreign locks are all locks minus device's granted locks
		status.foreignLockedDocumentIds = new ArrayList<>(getLockManager().getLockedDocumentIds());
		status.foreignLockedDocumentIds.removeAll(status.grantedLockedDocumentIds);

		return status;
	}

	/**
	 * @return the current timestamp, in seconds
	 */
	public long getTimestampSeconds() {
		return (new Date()).getTime() / 1000;
	}

	/**
	 * Check if a given device id is valid, e.g, represents an active and connected device
	 * @param deviceId a device id
	 * @return true iff it was issued to a connected device
	 */
	public boolean isValidDeviceId(String deviceId) {
		return deviceIdManager.isValidDeviceId(deviceId);
	}

	/**
	 * Sends status via websocket to each connected device associated with this SyncManager's account
	 */
	public void broadcastStatusToConnectedDevices() {

		if (debouncedStatusBroadcastCall == null) {
			debouncedStatusBroadcastCall = Debouncer.debounce(new Debouncer.Function<Void>() {
				@Override
				public void apply(Void aVoid) {
					// broadcast an updated status to each connected device.
					// note: Each device get a custom status, since they each have
					// a different set of granted and foreign locks

					WebSocketConnection connection = WebSocketConnection.getInstance();
					connection.broadcast(accountId, new WebSocketConnection.BroadcastMessageProducer<Status>() {
						@Override
						public Status generate(String accountId, Session session) {
							String deviceId = deviceIdManager.getDeviceIdForWebSocketSession(session);
							return getStatus(deviceId);
						}
					});
				}
			}, STATUS_BROADCAST_DEBOUNCE_MILLISECONDS);
		}

		debouncedStatusBroadcastCall.apply(null);
	}

	@Override
	public void onUserSessionConnected(WebSocketConnection connection, Session session, String accountId) {
		// on connection, first thing we do is create a device id and send the current status
		String deviceId = getDeviceIdManager().getDeviceIdForWebSocketSession(session);
		Status status = getStatus(deviceId);
		connection.send(session, status);
	}

	@Override
	public void onUserSessionDisconnected(WebSocketConnection connection, Session session, String accountId) {
		String deviceId = getDeviceIdManager().getDeviceIdForWebSocketSession(session);

		// release any locks that device may have been holding
		getLockManager().unlock(deviceId);

		// clean up
		discardActiveWriteSessionsForDeviceId(deviceId);
		deviceIdManager.unregisterDeviceId(deviceId);
	}

	@Override
	public void onLockAcquired(String deviceId, String documentId) {
		broadcastStatusToConnectedDevices();
	}

	@Override
	public void onLockReleased(String deviceId, String documentId) {
		broadcastStatusToConnectedDevices();
	}
}

