package org.zakariya.mrdoodleserver.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

/**
 * Basic Configuration management.
 * Load an arbitrary JSON file, and query for values in it as string, int or double. Deep paths
 * can be read via directory-like paths, such as "path/to/value".
 * Multiple JSON files can be loaded, with later loads "overriding" values in earlier loads.
 */
@SuppressWarnings("WeakerAccess")
public class Configuration {

	static final Logger logger = LoggerFactory.getLogger(Configuration.class);

	private List<JsonNode> rootNodes = new ArrayList<JsonNode>();

	public Configuration() {
	}

	/**
	 * Add a configuration JSON file to the configuration. Each configuration added can "override" values in
	 * previous configurations. So if a file with value "foo" is set to "bar" is added first, and a second file
	 * is added where "foo" is set to "baz", "baz" will win.
	 *
	 * @param configurationJsonFile path to a JSON file
	 */
	public void addConfigJsonFilePath(String configurationJsonFile) {
		try {
			InputStream is = new FileInputStream(configurationJsonFile);
			BufferedInputStream bis = new BufferedInputStream(is);

			ObjectMapper mapper = new ObjectMapper();
			rootNodes.add(mapper.readTree(bis));
		} catch (FileNotFoundException e) {
			logger.error("Unable to open configurationJSONFile", e);
		} catch (IOException e) {
			logger.error("Unable to read configurationJSONFile", e);
		}
	}

	/**
	 * Check if the configuration has a particular value
	 * @param path the path to the value
	 * @return true if the value exists and is non-empty (when treated as a string)
	 */
	public boolean has(String path) {
		String value = get(path);
		return value != null && !value.isEmpty();
	}

	/**
	 * Get the string value of the item at a given path. The path can contain directory separators, as such: "a/b/c/leaf"
	 *
	 * @param path the deep path into configuration
	 * @return the string value of the item at the end of the path, or null
	 */
	@Nullable
	public String get(String path) {
		String[] parts = path.split("/");

		// walk from last to first root node, since "newer" ones override older ones
		for (int i = rootNodes.size() - 1; i >= 0; i--) {
			JsonNode node = rootNodes.get(i);

			for (String part : parts) {
				node = node.get(part);
				if (node == null) {
					break;
				}
			}

			if (node != null) {
				return node.asText();
			}
		}

		return null;
	}

	/**
	 * Get the integer value of the item specified by `path`, or the default value if the item didn't exist, or could not be parsed into an integer.
	 *
	 * @param path         deep path into configuration, where forward slashes denote structure, e.g., 'a/b/c/leaf'
	 * @param defaultValue the default value to return if the item specified did not exist, or could not be converted to int
	 * @return the integer value of the item specified by the path
	 */
	public int getInt(String path, int defaultValue) {
		String v = get(path);
		if (v != null) {
			try {
				return Integer.parseInt(v);
			} catch (NumberFormatException e) {
				return defaultValue;
			}
		} else {
			return defaultValue;
		}
	}

	/**
	 * Get the double value of the item specified by `path`, or the default value if the item didn't exist, or could not be parsed into a double.
	 *
	 * @param path         deep path into configuration, where forward slashes denote structure, e.g., 'a/b/c/leaf'
	 * @param defaultValue the default value to return if the item specified did not exist, or could not be converted to double
	 * @return the double value of the item specified by the path
	 */
	public double getDouble(String path, double defaultValue) {
		String v = get(path);
		if (v != null) {
			try {
				return Double.parseDouble(v);
			} catch (NumberFormatException e) {
				return defaultValue;
			}
		} else {
			return defaultValue;
		}
	}

	/**
	 * Get the boolean value of the item specified by `path`, or the default value if the item didn't exist
	 * @param path deep path into configuration, where forward slashes denote structure, e.g., 'a/b/c/leaf'
	 * @param defaultValue the default value to return if the item specified did not exist
	 * @return the bool value of the item specified by the path
	 */
	public boolean getBoolean(String path, boolean defaultValue) {
		String v = get(path);
		if (v != null) {
			return Boolean.parseBoolean(v);
		} else {
			return defaultValue;
		}
	}


}
