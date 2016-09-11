package org.zakariya.mrdoodleserver.sync;

import org.eclipse.jetty.websocket.api.Session;
import org.jetbrains.annotations.Nullable;
import org.zakariya.mrdoodleserver.services.WebSocketConnection;
import org.zakariya.mrdoodleserver.sync.transport.Status;
import org.zakariya.mrdoodleserver.util.Configuration;
import redis.clients.jedis.JedisPool;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * SyncManager
 * Top level coordinator for sync activities for a specific google account
 */
class SyncManager implements WebSocketConnection.OnUserSessionStatusChangeListener {

	private Configuration configuration;
	private String accountId;
	private JedisPool jedisPool;
	private TimestampRecord timestampRecord;
	private BlobStore blobStore;
	private Map<String, WriteSession> writeSessions = new HashMap<>();
	private ScheduledExecutorService writeSessionDiscardScheduler = Executors.newSingleThreadScheduledExecutor();


	static class WriteSession {
		private String token;
		private TimestampRecord timestampRecord;
		private BlobStore blobStore;
		private ScheduledFuture discardFuture;

		WriteSession(JedisPool jedisPool) {
			token = UUID.randomUUID().toString();
			timestampRecord = new TimestampRecord();
			blobStore = new BlobStore(jedisPool, token, "temp");
		}

		String getToken() {
			return token;
		}

		TimestampRecord getTimestampRecord() {
			return timestampRecord;
		}

		BlobStore getBlobStore() {
			return blobStore;
		}

		public ScheduledFuture getDiscardFuture() {
			return discardFuture;
		}

		public void setDiscardFuture(ScheduledFuture discardFuture) {
			this.discardFuture = discardFuture;
		}

		void commit(TimestampRecord toTimestampRecord, BlobStore toBlobStore) {
			timestampRecord.save(toTimestampRecord);
			blobStore.save(toBlobStore);
		}

		void discard() {
			blobStore.discard();
		}
	}

	SyncManager(Configuration configuration, JedisPool jedisPool, String accountId) {
		this.configuration = configuration;
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

	String getAccountId() {
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

		// now schedule a discard after a timeout. this way, if a client fails to close
		// a write session (crash, loss of connection, etc) we will eventually discard
		// the write session. this is mainly to be tidy since the blobStore
		// uses some disc space

		session.setDiscardFuture(writeSessionDiscardScheduler.schedule(
				() -> discardWriteSession(session),
				configuration.getInt("syncManager/writeSessionInactiveDiscardDelayHours", 1),
				TimeUnit.HOURS));

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

			if (session.getDiscardFuture() != null && !session.getDiscardFuture().isCancelled()) {
				session.getDiscardFuture().cancel(false);
				session.setDiscardFuture(null);
			}

			return true;
		} else {
			return false;
		}
	}

	private void discardWriteSession(WriteSession session) {
		if (!session.getDiscardFuture().isCancelled()) {
			session.discard();
			writeSessions.remove(session.getToken());
		}
	}

	/**
	 * Get the account status, which includes info like the current timestamp head, locked documents, etc
	 *
	 * @return the current account status
	 */
	Status getStatus() {
		Status status = new Status();

		TimestampRecord.Entry timestampHead = timestampRecord.getTimestampHead();
		if (timestampHead != null) {
			status.timestampHead = timestampHead.getTimestampSeconds();
		}

		return status;
	}

	@Override
	public void onUserSessionConnected(WebSocketConnection connection, Session session, String userId) {
		// on connection, first thing we do is send the current status
		connection.send(session, getStatus());
	}

	@Override
	public void onUserSessionDisconnected(WebSocketConnection connection, Session session, String userId) {
	}

	/**
	 * @return the current timestamp, in seconds
	 */
	long getTimestampSeconds() {
		return (new Date()).getTime() / 1000;
	}
}
