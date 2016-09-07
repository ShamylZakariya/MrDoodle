package org.zakariya.mrdoodleserver.util;

import java.util.Map;

import static org.junit.Assert.*;

/**
 * Created by shamyl on 8/1/16.
 */
public class ConfigurationTest {
	@org.junit.Before
	public void setUp() throws Exception {

	}

	@org.junit.After
	public void tearDown() throws Exception {

	}

	@org.junit.Test
	public void get() throws Exception {

		Configuration configuration = new Configuration();
		configuration.addConfigJsonFilePath("test.json");
		configuration.addConfigJsonFilePath("test_2.json");

		assertEquals("foo == \"1\"", "1", configuration.get("foo"));

		assertEquals("a/b/c/value == \"1\"", "1", configuration.get("a/b/c/value"));

		// confirm overload from test_2.json overrides same value in test.json
		assertEquals("a/b/c/value2 == \"2\"", "2", configuration.get("a/b/c/value2"));

		assertEquals("a/b/c/d/value == \"3\"", "3", configuration.get("a/b/c/d/value"));

		// test double and int reads
		assertEquals("a/b/c/doubleValue == 3.14159", 3.14159, configuration.getDouble("a/b/c/doubleValue", 0),0.01);
		assertEquals("foo == 1", 1, (long)configuration.getInt("foo", 0), 1);

		// test handling of missing values
		assertNull("non-existent entry must be null", configuration.get("non/existent"));
		assertEquals("non-existent int must equal specified default value of 1", 1, configuration.getInt("non-existent", 1));
		assertEquals("non-existent double must equal specified default value of 1.5", 1.5, configuration.getDouble("non-existent", 1.5), 0.01);


		Map<String,Object> mapValue = configuration.getMap("map");
		assertNotNull("map value should be non-null", mapValue);
		assertEquals("map should have 5 values", 5, mapValue.size());
		assertEquals("map[a] should equal \"a\"", "a", mapValue.get("a"));
		assertEquals("map[b] should equal \"b\"", "b", mapValue.get("b"));
		assertEquals("map[c] should equal \"c\"", "c", mapValue.get("c"));
		assertEquals("map[d] should equal \"d\"", "d", mapValue.get("d"));
		assertEquals("map[e] should equal \"e\"", "e", mapValue.get("e"));

	}

}