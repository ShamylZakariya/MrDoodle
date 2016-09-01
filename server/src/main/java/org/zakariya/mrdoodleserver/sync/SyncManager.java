package org.zakariya.mrdoodleserver.sync;

import org.eclipse.jetty.websocket.api.Session;
import org.jetbrains.annotations.Nullable;
import org.zakariya.mrdoodleserver.services.WebSocketConnection;
import org.zakariya.mrdoodleserver.sync.transport.Status;
import redis.clients.jedis.JedisPool;

import java.security.Timestamp;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * SyncManager
 * Top level coordinator for sync activities for a specific google account
 */
class SyncManager implements WebSocketConnection.OnUserSessionStatusChangeListener {

	private String accountId;
	private JedisPool jedisPool;
	private TimestampRecord timestampRecord;
	private BlobStore blobStore;
	private Map<String, WriteSession> writeSessions = new HashMap<>();
	
	static class WriteSession {
		private String token;
		private TimestampRecord timestampRecord;
		private BlobStore blobStore;
		
		WriteSession(JedisPool jedisPool) {
			token = UUID.randomUUID().toString();
			timestampRecord = new TimestampRecord();
			blobStore = new BlobStore(jedisPool, token, "temp");
		}

		public String getToken() {
			return token;
		}

		public TimestampRecord getTimestampRecord() {
			return timestampRecord;
		}

		public BlobStore getBlobStore() {
			return blobStore;
		}

		void commit(TimestampRecord toTimestampRecord, BlobStore toBlobStore) {
			timestampRecord.save(toTimestampRecord);
			blobStore.save(toBlobStore);
		}
	}

	SyncManager(JedisPool jedisPool, String accountId) {
		this.jedisPool = jedisPool;
		this.accountId = accountId;
		this.timestampRecord = new TimestampRecord(jedisPool, accountId);
		this.blobStore = new BlobStore(jedisPool, accountId);
	}

	void close() {
		jedisPool.close();
	}

	JedisPool getJedisPool() {
		return jedisPool;
	}

	public String getAccountId() {
		return accountId;
	}

	TimestampRecord getTimestampRecord() {
		return timestampRecord;
	}

	BlobStore getBlobStore() {
		return blobStore;
	}

	WriteSession startWriteSession() {
		WriteSession session = new WriteSession(jedisPool);
		writeSessions.put(session.getToken(), session);
		return session;
	}

	@Nullable
	WriteSession getWriteSession(String token) {
		return writeSessions.get(token);
	}

	boolean commitWriteSession(String token) {
		WriteSession session = writeSessions.get(token);
		if (session != null) {
			session.commit(timestampRecord, blobStore);
			writeSessions.remove(token);
			return true;
		} else {
			return false;
		}
	}

	/**
	 * Get the account status, which includes info like the current timestamp head, locked documents, etc
	 *
	 * @return the current account status
	 */
	Status getStatus() {
		Status status = new Status();
		status.timestampHead = timestampRecord.getTimestampHead().getTimestampSeconds();
		return status;
	}

	@Override
	public void onUserSessionConnected(WebSocketConnection connection, Session session, String googleId) {
		// on connection, first thing we do is send the current status
		connection.send(session, getStatus());
	}

	@Override
	public void onUserSessionDisconnected(WebSocketConnection connection, Session session, String googleId) {
	}

	/**
	 * @return the current timestamp, in seconds
	 */
	long getTimestampSeconds() {
		return (new Date()).getTime() / 1000;
	}
}
