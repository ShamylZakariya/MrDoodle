package org.zakariya.mrdoodleserver.sync;

import org.junit.After;
import org.junit.Before;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.Map;

import static org.junit.Assert.*;

/**
 * Created by shamyl on 8/17/16.
 */
public class TimestampRecordTest {

	final JedisPool pool = new JedisPool("localhost");
	final String accountId = "testAccount";


	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
		try (Jedis jedis = pool.getResource()) {
			jedis.del(TimestampRecord.getJedisKey(accountId));
		}
	}

	@org.junit.Test
	public void testTimestamps() {

		// create a non-persisting record
		TimestampRecord timestampRecord = new TimestampRecord(null, null);
		timestampRecord.setTimestamp("A", 10);
		timestampRecord.setTimestamp("B", 11);
		timestampRecord.setTimestamp("C", 12);
		timestampRecord.setTimestamp("D", 13);

		assertEquals("A == 10", 10, timestampRecord.getTimestamp("A"));
		assertEquals("Unassigned values should == -1", -1, timestampRecord.getTimestamp("FOO"));

		assertEquals("should have 4 entries", 4, timestampRecord.getTimestamps().size());
		assertEquals("should have 4 entries", 4, timestampRecord.getTimestampsSince(9).size());
		assertEquals("should have 4 entries", 4, timestampRecord.getTimestampsSince(10).size());
		assertEquals("should have 3 entries", 3, timestampRecord.getTimestampsSince(11).size());
		assertEquals("should have 2 entries", 2, timestampRecord.getTimestampsSince(12).size());
		assertEquals("should have 1 entries", 1, timestampRecord.getTimestampsSince(13).size());
		assertEquals("should have 0 entries", 0, timestampRecord.getTimestampsSince(14).size());

		Map<String,Long> result = timestampRecord.getTimestampsSince(12);
		assertEquals("timestamps since 12 should have \"C\" == 12", 12, (long)result.get("C"));
		assertEquals("timestamps since 12 should have \"D\" == 13", 13, (long)result.get("D"));
	}

	@org.junit.Test
	public void testTimestampPersistence() {
		// create a persisting record
		TimestampRecord tr0 = new TimestampRecord(pool, accountId);
		tr0.setTimestamp("A", 10);
		tr0.setTimestamp("B", 11);
		tr0.setTimestamp("C", 12);
		tr0.setTimestamp("D", 13);
		tr0.setTimestamp("E", 20);
		tr0.setTimestamp("F", 21);
		tr0.setTimestamp("G", 22);
		tr0.setTimestamp("H", 23);
		tr0.save();

		// this record should have same entries as tr0
		TimestampRecord tr1 = new TimestampRecord(pool, accountId);
		assertEquals("should have same value for uuid A", tr0.getTimestamp("A"), tr1.getTimestamp("A"));
		assertEquals("should have same value for uuid B", tr0.getTimestamp("B"), tr1.getTimestamp("B"));
		assertEquals("should have same value for uuid C", tr0.getTimestamp("C"), tr1.getTimestamp("C"));
		assertEquals("should have same value for uuid D", tr0.getTimestamp("D"), tr1.getTimestamp("D"));
		assertEquals("should have same value for uuid E", tr0.getTimestamp("E"), tr1.getTimestamp("E"));
		assertEquals("should have same value for uuid F", tr0.getTimestamp("F"), tr1.getTimestamp("F"));
		assertEquals("should have same value for uuid G", tr0.getTimestamp("G"), tr1.getTimestamp("G"));
		assertEquals("should have same value for uuid H", tr0.getTimestamp("H"), tr1.getTimestamp("H"));

		try (Jedis jedis = pool.getResource()) {
			jedis.del(TimestampRecord.getJedisKey(accountId));
		}
	}

	@org.junit.Test
	public void testTimestampSaveDebounce() throws Exception {


		// create a persisting record
		TimestampRecord tr0 = new TimestampRecord(pool, accountId);
		tr0.setTimestamp("A", 10);
		tr0.setTimestamp("B", 11);
		tr0.setTimestamp("C", 12);
		tr0.setTimestamp("D", 13);

		// the debounced save will not have run yet, so this pool should be empty
		TimestampRecord tr1 = new TimestampRecord(pool, accountId);
		assertTrue("pool should be empty since debounced save hasn't had time to run", tr1.isEmpty());

		Thread.sleep(3500);

		tr1 = new TimestampRecord(pool, accountId);
		assertTrue("after debounced save has run, new timestamp record should have same contents as saved one", tr0.getTimestamps().equals(tr1.getTimestamps()));

		try (Jedis jedis = pool.getResource()) {
			jedis.del(TimestampRecord.getJedisKey(accountId));
		}

	}

}