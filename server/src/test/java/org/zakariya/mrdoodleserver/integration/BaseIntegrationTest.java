package org.zakariya.mrdoodleserver.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jetbrains.annotations.Nullable;
import org.zakariya.mrdoodleserver.SyncServer;
import org.zakariya.mrdoodleserver.util.Configuration;
import spark.Spark;
import spark.utils.IOUtils;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

/**
 * Base class for Integration tests
 */
public class BaseIntegrationTest {

	private static Configuration configuration;

	static void startServer(String configurationFilePath) {
		assertNull(configuration);

		configuration = new Configuration();
		configuration.addConfigJsonFilePath(configurationFilePath);

		// start server, flushing storage
		SyncServer.start(configuration, true);

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

		Header(String name, String value) {
			this.name = name;
			this.value = value;
		}

		String getName() {
			return name;
		}

		String getValue() {
			return value;
		}
	}

	Map<String, String> headers(Header... headers) {
		Map<String, String> headerMap = new HashMap<>();
		for (Header h : headers) {
			headerMap.put(h.getName(), h.getValue());
		}
		return headerMap;
	}

	Map<String, String> header(String name, String value) {
		return headers(new Header(name, value));
	}

	TestResponse request(String method, String path) {
		return _request(method, path, null, null);
	}

	TestResponse request(String method, String path, @Nullable Map<String, String> headers) {
		return _request(method, path, headers, null);
	}

	TestResponse request(String method, String path, @Nullable Map<String, String> headers, FormPart part) {
		ArrayList<FormPart> parts = new ArrayList<>();
		parts.add(part);

		return _request(method, path, headers, parts);
	}

	private TestResponse _request(String method, String path, @Nullable Map<String, String> headers, @Nullable List<FormPart> formParts) {

		URL url;
		HttpURLConnection connection = null;

		try {
			url = new URL("http://localhost:4567" + path);
			connection = (HttpURLConnection) url.openConnection();
			connection.setRequestMethod(method);

			if (headers != null) {
				for (String headerName : headers.keySet()) {
					connection.setRequestProperty(headerName, headers.get(headerName));
				}
			}

			connection.setUseCaches(false);
			connection.setDoOutput(true);

			if (formParts != null) {
				MultipartFormBuilder builder = MultipartFormBuilder.with(connection);
				for (FormPart part : formParts) {
					part.apply(builder);
				}
				builder.finish();
			}

			connection.connect();
			int status = connection.getResponseCode();
			String responseBody = null;

			if (status >= 200 && status <= 299) {
				responseBody = IOUtils.toString(connection.getInputStream());
			} else if (status >= 400) {
				responseBody = IOUtils.toString(connection.getErrorStream());
			}

			return new TestResponse(status, responseBody);

		} catch (IOException e) {
			e.printStackTrace();
			fail("Sending request failed: " + e.getMessage());
			return null;
		} finally {
			if (connection != null) {
				connection.disconnect();
			}
		}
	}

	static class TestResponse {

		private final String body;
		private final int status;

		TestResponse(int status, String body) {
			this.status = status;
			this.body = body;
		}

		String getBody() {
			return body;
		}

		byte[] getBodyBytes() {
			return body.getBytes();
		}

		<T> T getBody(Class c) {
			ObjectMapper mapper = new ObjectMapper();
			try {
				return mapper.reader().forType(c).readValue(getBody());
			} catch (IOException e) {
				fail("Unable to convert response body: \"" + getBody() + "\" to instance of: " + c.getCanonicalName() + " error: " + e);
				e.printStackTrace();
			}
			return null;
		}

		<T> T getBody(TypeReference typeReference) {
			ObjectMapper mapper = new ObjectMapper();
			try {
				return mapper.reader().forType(typeReference).readValue(getBody());
			} catch (IOException e) {
				fail("Unable to convert response body: \"" + getBody() + "\" to instance of: " + typeReference.getType().getTypeName() + " error: " + e);
				e.printStackTrace();
			}
			return null;
		}


		int getStatus() {
			return status;
		}
	}

	interface FormPart {
		void apply(MultipartFormBuilder builder) throws IOException;
	}

	static class BytePart implements FormPart {
		private String name;
		private byte[] data;

		public BytePart(String name, byte[] data) {
			this.name = name;
			this.data = data;
		}

		public String getName() {
			return name;
		}

		public byte[] getData() {
			return data;
		}

		@Override
		public void apply(MultipartFormBuilder builder) throws IOException {
			builder.addFormDataPart(name, data);
		}
	}

	static class FormFieldPart implements FormPart {
		private String name;
		private String data;

		public FormFieldPart(String name, String data) {
			this.name = name;
			this.data = data;
		}

		public String getName() {
			return name;
		}

		public String getData() {
			return data;
		}

		@Override
		public void apply(MultipartFormBuilder builder) throws IOException {
			builder.addFormFieldPart(name, data);
		}
	}

	/**
	 * This utility class provides an abstraction layer for sending multipart HTTP
	 * Adapted from www.codejava.net
	 */
	static class MultipartFormBuilder {
		private final String boundary;
		private static final String LINE_FEED = "\r\n";
		private static final String CHARSET = "UTF-8";
		private OutputStream outputStream;
		private PrintWriter writer;

		public static MultipartFormBuilder with(HttpURLConnection httpConn) throws IOException {
			return new MultipartFormBuilder(httpConn);
		}

		private MultipartFormBuilder(HttpURLConnection httpConn) throws IOException {

			// creates a unique boundary based on time stamp
			boundary = "===" + System.currentTimeMillis() + "===";

			httpConn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
			outputStream = httpConn.getOutputStream();
			writer = new PrintWriter(new OutputStreamWriter(outputStream, CHARSET), true);
		}

		/**
		 * Adds a form field to the request
		 *
		 * @param name  field name
		 * @param value field value
		 */
		public MultipartFormBuilder addFormFieldPart(String name, String value) {
			writer.append("--").append(boundary).append(LINE_FEED);
			writer.append("Content-Disposition: form-data; name=\"").append(name).append("\"").append(LINE_FEED);
			writer.append("Content-Type: text/plain; charset=" + CHARSET).append(LINE_FEED);
			writer.append(LINE_FEED);
			writer.append(value).append(LINE_FEED);
			writer.flush();
			return this;
		}

		public MultipartFormBuilder addFormDataPart(String name, byte[] data) throws IOException {
			writer.append("--").append(boundary).append(LINE_FEED);
			writer.append("Content-Disposition: form-data; name=\"").append(name).append("\"").append(LINE_FEED);
			writer.append("Content-Type: application/octet-stream").append(LINE_FEED);
			writer.append("Content-Transfer-Encoding: binary").append(LINE_FEED);
			writer.append(LINE_FEED);
			writer.flush();

			// pump data into outputStream which routes to writer
			outputStream.write(data);
			outputStream.flush();
			writer.flush();

			return this;
		}

		public MultipartFormBuilder finish() {
			writer.append(LINE_FEED).flush();
			writer.append("--").append(boundary).append("--").append(LINE_FEED);
			writer.close();
			return this;
		}
	}

}
