package org.zakariya.doodle.util;

import java.util.Arrays;

/**
 * Created by shamyl on 9/29/15.
 * Represents a growable float buffer, like c++ std::vector<float>
 */
public class FloatBuffer {
	private static final float GROWTH_FACTOR = 1.625f;
	private float buffer[] = null;
	private int size = 0;
	private int initialBufferSize;

	public FloatBuffer() {
		this(16);
	}

	public FloatBuffer(int initialBufferSize) {
		this.initialBufferSize = initialBufferSize;
		buffer = new float[initialBufferSize];
		this.size = 0;
	}

	public void add(float v) {
		if (size == buffer.length) {
			buffer = Arrays.copyOf(buffer, (int) (buffer.length * GROWTH_FACTOR));
		}

		buffer[size++] = v;
	}

	public float get(int i) {
		if (i < 0) {
			return buffer[size + i];
		} else {
			if (i > size - 1) {
				throw new ArrayIndexOutOfBoundsException(i);
			}

			return buffer[i];
		}
	}

	public void set(int i, float v) {
		if (i < 0) {
			set(size + i, v);
		} else {
			if (i > size - 1) {
				throw new ArrayIndexOutOfBoundsException(i);
			} else {
				buffer[i] = v;
			}
		}
	}

	public int size() {
		return size;
	}

	public void clear() {
		size = 0;
	}

	/**
	 * Get a compacted copy of the buffer
	 *
	 * @return buffer's contents, compacted to size, such that buffer.length == size
	 */
	public float[] getBuffer() {
		return Arrays.copyOf(buffer, size);
	}
}
