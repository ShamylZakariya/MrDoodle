package org.zakariya.mrdoodleserver.sync;

import org.junit.After;
import org.junit.Before;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import static org.junit.Assert.*;

/**
 * Created by szakariy on 8/31/16.
 */
public class BlobStoreTest {

	final JedisPool pool = new JedisPool("localhost");
	static final String accountId = "blobStoreTestAccount";
	static final String MAIN_NAMESPACE = "test";
	static final String TEMP_NAMESPACE = "test-temp";
	BlobStore mainStore;
	BlobStore tempStore;

	@Before
	public void setUp() throws Exception {
		mainStore = new BlobStore(pool, MAIN_NAMESPACE, accountId);
		tempStore = new BlobStore(pool, TEMP_NAMESPACE, accountId);
	}

	@After
	public void tearDown() throws Exception {
		mainStore.discard();
		tempStore.discard();
	}

	@org.junit.Test
	public void basicReadWriteDeleteTests() throws Exception {
		// confirm writing and then reading out an item results in an equal item
		BlobStore.Entry e = new BlobStore.Entry("A", "Foo", 10, "Data".getBytes());
		mainStore.set(e);
		BlobStore.Entry eCopy = mainStore.get(e.getUuid());
		assertEquals("Loaded blob entry should be equal to the original", e, eCopy);

		// confirm that get on a deleted item returns null
		mainStore.delete(e.getUuid());
		BlobStore.Entry eNull = mainStore.get(e.getUuid());
		assertNull("After deleting blob, should be null", eNull);

		// confirm the data is deleted
		try (Jedis jedis = pool.getResource()) {
			assertFalse("blob uuid should be deleted", jedis.exists(BlobStore.getEntryUuidKey(accountId, mainStore.getNamespace(), e.getUuid())));
			assertFalse("blob modelClass should be deleted", jedis.exists(BlobStore.getEntryModelClassKey(accountId, mainStore.getNamespace(), e.getUuid())));
			assertFalse("blob timestamp should be deleted", jedis.exists(BlobStore.getEntryTimestampKey(accountId, mainStore.getNamespace(), e.getUuid())));
			assertFalse("blob data should be deleted", jedis.exists(BlobStore.getEntryDataKey(accountId, mainStore.getNamespace(), e.getUuid())));
		}
	}

	@org.junit.Test
	public void testBlobStoreMerging() throws Exception {
		BlobStore.Entry entryInMain = new BlobStore.Entry("A", "Foo", 10, "Main".getBytes());
		BlobStore.Entry entryInMainThatWillBeDeleted = new BlobStore.Entry("B", "Bar", 11, "Memento Mori".getBytes());
		BlobStore.Entry entryInTemp = new BlobStore.Entry("C", "Baz", 12, "Temp".getBytes());

		mainStore.set(entryInMain);
		mainStore.set(entryInMainThatWillBeDeleted);

		tempStore.set(entryInTemp);
		tempStore.delete(entryInMainThatWillBeDeleted.getUuid());

		// confirm that mainStore has entryInMain, and entryInMainThatWillBeDeleted, and that they're not in tempStore
		assertNotNull(mainStore.get(entryInMain.getUuid()));
		assertEquals(entryInMain, mainStore.get(entryInMain.getUuid()));
		assertNotNull(mainStore.get(entryInMainThatWillBeDeleted.getUuid()));
		assertEquals(entryInMainThatWillBeDeleted, mainStore.get(entryInMainThatWillBeDeleted.getUuid()));
		assertNull("entryInMain shouldn't be in the temp store", tempStore.get(entryInMain.getUuid()));
		assertNull("entryInMainThatWillBeDeleted shouldn't be in the temp store", tempStore.get(entryInMainThatWillBeDeleted.getUuid()));

		// confirm tempStore has entryInTemp
		assertNotNull(tempStore.get(entryInTemp.getUuid()));
		assertEquals(entryInTemp, tempStore.get(entryInTemp.getUuid()));
		assertNull("entryInMainThatWillBeDeleted shouldn't be in the tempStore", tempStore.get(entryInMainThatWillBeDeleted.getUuid()));
		assertNull("entryInTemp shouldn't be in the main store", mainStore.get(entryInTemp.getUuid()));

		// now merge
		tempStore.save(mainStore);

		// now confirm entryInTemp is in the mainStore, and entryInMainThatWillBeDeleted is NOT
		assertNotNull(mainStore.get(entryInTemp.getUuid()));
		assertEquals(entryInTemp, mainStore.get(entryInTemp.getUuid()));
		assertNull("entryInMainThatWillBeDeleted should no longer be in the main store", mainStore.get(entryInMainThatWillBeDeleted.getUuid()));
	}

}