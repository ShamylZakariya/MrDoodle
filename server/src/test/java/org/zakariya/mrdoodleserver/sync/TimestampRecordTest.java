package org.zakariya.mrdoodleserver.sync;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.After;
import org.junit.Before;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.io.IOException;
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
	public void testTimestampRecordEntryPersistence() throws IOException {
		ObjectMapper mapper = new ObjectMapper();
		TimestampRecord.Entry a = new TimestampRecord.Entry("a", 10, TimestampRecord.Action.WRITE);
		String json = mapper.writeValueAsString(a);
		TimestampRecord.Entry b = mapper.readValue(json, TimestampRecord.Entry.class);

		assertEquals("Deserialized entry should be equal", a, b);
	}

	@org.junit.Test
	public void testTimestamps() {

		// create a non-persisting record
		TimestampRecord timestampRecord = new TimestampRecord(null, null);
		timestampRecord.record("A", 10, TimestampRecord.Action.WRITE);
		timestampRecord.record("B", 11, TimestampRecord.Action.WRITE);
		timestampRecord.record("C", 12, TimestampRecord.Action.WRITE);
		timestampRecord.record("D", 13, TimestampRecord.Action.WRITE);

		assertEquals("A == 10", 10, timestampRecord.getTimestampSeconds("A"));
		assertEquals("Unassigned values should == -1", -1, timestampRecord.getTimestampSeconds("FOO"));

		assertEquals("should have 4 entries", 4, timestampRecord.getEntries().size());
		assertEquals("should have 4 entries", 4, timestampRecord.getEntriesSince(9).size());
		assertEquals("should have 4 entries", 4, timestampRecord.getEntriesSince(10).size());
		assertEquals("should have 3 entries", 3, timestampRecord.getEntriesSince(11).size());
		assertEquals("should have 2 entries", 2, timestampRecord.getEntriesSince(12).size());
		assertEquals("should have 1 entries", 1, timestampRecord.getEntriesSince(13).size());
		assertEquals("should have 0 entries", 0, timestampRecord.getEntriesSince(14).size());

		Map<String, TimestampRecord.Entry> result = timestampRecord.getEntriesSince(12);
		assertEquals("timestamps since 12 should have \"C\" == 12", 12, result.get("C").getTimestampSeconds());
		assertEquals("timestamps since 12 should have \"D\" == 13", 13, result.get("D").getTimestampSeconds());
	}

	@org.junit.Test
	public void testTimestampPersistence() {
		// create a persisting record
		TimestampRecord tr0 = new TimestampRecord(pool, accountId);
		tr0.record("A", 10, TimestampRecord.Action.WRITE);
		tr0.record("B", 11, TimestampRecord.Action.WRITE);
		tr0.record("C", 12, TimestampRecord.Action.WRITE);
		tr0.record("D", 13, TimestampRecord.Action.WRITE);
		tr0.record("E", 20, TimestampRecord.Action.WRITE);
		tr0.record("F", 21, TimestampRecord.Action.WRITE);
		tr0.record("G", 22, TimestampRecord.Action.WRITE);
		tr0.record("H", 23, TimestampRecord.Action.WRITE);
		tr0.save();

		// this record should have same entries as tr0
		TimestampRecord tr1 = new TimestampRecord(pool, accountId);
		assertEquals("should have same value for uuid A", tr0.getTimestampSeconds("A"), tr1.getTimestampSeconds("A"));
		assertEquals("should have same value for uuid B", tr0.getTimestampSeconds("B"), tr1.getTimestampSeconds("B"));
		assertEquals("should have same value for uuid C", tr0.getTimestampSeconds("C"), tr1.getTimestampSeconds("C"));
		assertEquals("should have same value for uuid D", tr0.getTimestampSeconds("D"), tr1.getTimestampSeconds("D"));
		assertEquals("should have same value for uuid E", tr0.getTimestampSeconds("E"), tr1.getTimestampSeconds("E"));
		assertEquals("should have same value for uuid F", tr0.getTimestampSeconds("F"), tr1.getTimestampSeconds("F"));
		assertEquals("should have same value for uuid G", tr0.getTimestampSeconds("G"), tr1.getTimestampSeconds("G"));
		assertEquals("should have same value for uuid H", tr0.getTimestampSeconds("H"), tr1.getTimestampSeconds("H"));

		try (Jedis jedis = pool.getResource()) {
			jedis.del(TimestampRecord.getJedisKey(accountId));
		}
	}

	@org.junit.Test
	public void testTimestampSaveDebounce() throws Exception {


		// create a persisting record
		TimestampRecord tr0 = new TimestampRecord(pool, accountId);
		tr0.record("A", 10, TimestampRecord.Action.WRITE);
		tr0.record("B", 11, TimestampRecord.Action.WRITE);
		tr0.record("C", 12, TimestampRecord.Action.WRITE);
		tr0.record("D", 13, TimestampRecord.Action.WRITE);

		// the debounced save will not have run yet, so this pool should be empty
		TimestampRecord tr1 = new TimestampRecord(pool, accountId);
		assertTrue("pool should be empty since debounced save hasn't had time to run", tr1.isEmpty());

		Thread.sleep(3500);

		tr1 = new TimestampRecord(pool, accountId);
		assertTrue("after debounced save has run, new timestamp record should have same contents as saved one", tr0.getEntries().equals(tr1.getEntries()));

		try (Jedis jedis = pool.getResource()) {
			jedis.del(TimestampRecord.getJedisKey(accountId));
		}

	}

	@org.junit.Test
	public void testTimestampRecordMerge() {
		TimestampRecord tr0 = new TimestampRecord();
		tr0.record("A", 10, TimestampRecord.Action.WRITE);
		tr0.record("B", 11, TimestampRecord.Action.WRITE);
		tr0.record("C", 12, TimestampRecord.Action.WRITE);
		tr0.record("D", 13, TimestampRecord.Action.WRITE);

		TimestampRecord tr1 = new TimestampRecord();
		tr1.record("E", 20, TimestampRecord.Action.WRITE);
		tr1.record("A", 21, TimestampRecord.Action.DELETE);
		tr1.save(tr0);

		TimestampRecord reference = new TimestampRecord();
		reference.record("A", 21, TimestampRecord.Action.DELETE);
		reference.record("B", 11, TimestampRecord.Action.WRITE);
		reference.record("C", 12, TimestampRecord.Action.WRITE);
		reference.record("D", 13, TimestampRecord.Action.WRITE);
		reference.record("E", 20, TimestampRecord.Action.WRITE);

		assertTrue("After merge, tr0 should have same values as reference", tr0.getEntries().equals(reference.getEntries()));
	}

}