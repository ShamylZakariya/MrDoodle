package org.zakariya.mrdoodleserver.util;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Test org.zakariya.mrdoodleserver.util.Debouncer
 */
public class DebouncerTest {

	static final class Counter {
		int count = 0;

		public Counter() {
		}

		public int getCount() {
			return count;
		}

		public void setCount(int count) {
			this.count = count;
		}

		public void incrementCount() {
			count++;
		}
	}

	@Test
	public void debounce() throws Exception {

		Counter counter = new Counter();

		Debouncer.Function<Integer> debouncedCall = Debouncer.debounce(new Debouncer.Function<Integer>() {
			@Override
			public void apply(Integer v) {
				counter.setCount(v);
			}
		}, 500);

		assertEquals("Counter value should be zero", 0, counter.getCount());

		debouncedCall.apply(1);
		debouncedCall.apply(2);
		debouncedCall.apply(3);
		debouncedCall.apply(4);

		assertEquals("Counter value should still be zero", 0, counter.getCount());

		// wait for debouncer to trigger
		Thread.sleep(600);

		assertEquals("After debounce timeout, counter should now have been set", 4, counter.getCount());

		// now we'll test incrementing

		counter.setCount(0);
		debouncedCall = Debouncer.debounce(new Debouncer.Function<Integer>() {
			@Override
			public void apply(Integer v) {
				counter.incrementCount();
			}
		}, 500);

		assertEquals("Counter value should be zero", 0, counter.getCount());
		debouncedCall.apply(null);
		debouncedCall.apply(null);
		debouncedCall.apply(null);
		debouncedCall.apply(null);

		assertEquals("Counter value should still be zero", 0, counter.getCount());

		// wait for debouncer to trigger
		Thread.sleep(600);
		assertEquals("After debounce timeout, counter incrementer should have only run once", 1, counter.getCount());

	}

}