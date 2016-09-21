package org.zakariya.mrdoodleserver.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jetbrains.annotations.Nullable;
import org.zakariya.mrdoodleserver.SyncServer;
import org.zakariya.mrdoodleserver.util.Configuration;
import spark.Spark;
import spark.utils.IOUtils;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

/**
 * Created by shamyl on 9/20/16.
 */
public class BaseIntegrationTest {

	private static Configuration configuration;

	static void startServer(String configurationFilePath) {
		assertNull(configuration);

		configuration = new Configuration();
		configuration.addConfigJsonFilePath(configurationFilePath);
		SyncServer.start(configuration);

		// wait for server to spin up?
		try {
			Thread.sleep(5000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	static void stopServer() {
		System.out.println("Stopping Spark server");
		// wait for a beat and shut down
		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		Spark.stop();
		SyncServer.flushStorage(configuration);
	}

	static class Header {
		String name;
		String value;

		public Header(String name, String value) {
			this.name = name;
			this.value = value;
		}

		public String getName() {
			return name;
		}

		public String getValue() {
			return value;
		}
	}

	Map<String,String> headers(Header ...headers) {
		Map<String,String> headerMap = new HashMap<>();
		for (Header h : headers) {
			headerMap.put(h.getName(), h.getValue());
		}
		return headerMap;
	}

	Map<String,String> header(String name, String value) {
		return headers(new Header(name, value));
	}

	TestResponse request(String method, String path, @Nullable Map<String,String> headers) {
		try {
			URL url = new URL("http://localhost:4567" + path);
			HttpURLConnection connection = (HttpURLConnection) url.openConnection();
			connection.setRequestMethod(method);

			if (headers != null) {
				for (String headerName : headers.keySet()) {
					connection.setRequestProperty(headerName, headers.get(headerName));
				}
			}

			connection.setUseCaches(false);
			connection.setDoOutput(true);
			connection.connect();

			int status = connection.getResponseCode();
			String body = null;

			if (status >= 200 && status <= 299) {
				body = IOUtils.toString(connection.getInputStream());
			} else if (status >= 400) {
				body = IOUtils.toString(connection.getErrorStream());
			}

			return new TestResponse(status, body);

		} catch (IOException e) {
			e.printStackTrace();
			fail("Sending request failed: " + e.getMessage());
			return null;
		}
	}

	static class TestResponse {

		final String body;
		final int status;

		TestResponse(int status, String body) {
			this.status = status;
			this.body = body;
		}

		public String getBody() {
			return body;
		}

		public <T> T getBody(Class c) {
			ObjectMapper mapper = new ObjectMapper();
			try {
				return mapper.reader().forType(c).readValue(getBody());
			} catch (IOException e) {
				fail("Unable to convert response body: \"" + getBody() + "\" to instance of: " + c.getCanonicalName() + " error: " + e);
				e.printStackTrace();
			}
			return null;
		}

		public int getStatus() {
			return status;
		}
	}

}
