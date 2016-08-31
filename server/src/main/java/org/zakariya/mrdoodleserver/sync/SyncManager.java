package org.zakariya.mrdoodleserver.sync;

import org.eclipse.jetty.websocket.api.Session;
import org.zakariya.mrdoodleserver.services.WebSocketConnection;
import org.zakariya.mrdoodleserver.sync.transport.Status;
import redis.clients.jedis.JedisPool;

import java.util.Date;

/**
 * SyncManager
 * Top level coordinator for sync activities for a specific google account
 */
public class SyncManager implements WebSocketConnection.OnUserSessionStatusChangeListener {

	private JedisPool jedisPool;
	private TimestampRecord timestampRecord;
	private BlobStore blobStore;

	public SyncManager(JedisPool jedisPool, String accountId) {
		this.jedisPool = jedisPool;
		this.timestampRecord = new TimestampRecord(jedisPool, accountId);
		this.blobStore = new BlobStore(jedisPool, accountId);
	}

	public JedisPool getJedisPool() {
		return jedisPool;
	}

	public TimestampRecord getTimestampRecord() {
		return timestampRecord;
	}

	public BlobStore getBlobStore() {
		return blobStore;
	}

	/**
	 * Get the account status, which includes info like the current timestamp head, locked documents, etc
	 *
	 * @return the current account status
	 */
	public Status getStatus() {
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
	public long getTimestampSeconds() {
		return (new Date()).getTime() / 1000;
	}
}
