package org.zakariya.mrdoodleserver.routes;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.net.MediaType;
import org.slf4j.Logger;
import org.zakariya.mrdoodleserver.sync.UserRecordAccess;
import org.zakariya.mrdoodleserver.util.Configuration;
import redis.clients.jedis.JedisPool;
import spark.Response;
import spark.ResponseTransformer;

import static spark.Spark.halt;

/**
 * Base class for Routers
 */
public abstract class Router {

	static final String RESPONSE_TYPE_JSON = MediaType.JSON_UTF_8.toString();
	static final String RESPONSE_TYPE_TEXT = MediaType.PLAIN_TEXT_UTF_8.toString();
	static final String RESPONSE_TYPE_OCTET_STREAM = MediaType.OCTET_STREAM.toString();

	private JedisPool jedisPool;
	private String storagePrefix;
	private String apiVersion;
	private ResponseTransformer jsonResponseTransformer;

	Router(JedisPool jedisPool, String storagePrefix, String apiVersion) {
		this.jedisPool = jedisPool;
		this.storagePrefix = storagePrefix;
		this.apiVersion = apiVersion;
	}

	JedisPool getJedisPool() {
		return jedisPool;
	}

	String getStoragePrefix() {
		return storagePrefix;
	}

	String getApiVersion() {
		return apiVersion;
	}

	ResponseTransformer getJsonResponseTransformer() {
		if (jsonResponseTransformer == null) {
			jsonResponseTransformer = createJsonResponseTransformer();
		}
		return jsonResponseTransformer;
	}

	public abstract Logger getLogger();

	public abstract void initializeRoutes();

	///////////////////////////////////////////////////////////////////

	void sendErrorAndHalt(Response response, int code, String message, Exception e) {
		getLogger().error(message, e);
		response.type(RESPONSE_TYPE_TEXT);
		halt(code, message);
	}

	void sendErrorAndHalt(Response response, int code, String message) {
		getLogger().error(message);
		response.type(RESPONSE_TYPE_TEXT);
		halt(code, message);
	}

	///////////////////////////////////////////////////////////////////

	@SuppressWarnings("WeakerAccess")
	protected ResponseTransformer createJsonResponseTransformer() {
		return new JsonResponseTransformer();
	}

	private class JsonResponseTransformer implements ResponseTransformer {

		private ObjectMapper objectMapper = new ObjectMapper();

		@Override
		public String render(Object o) throws Exception {
			return objectMapper.writeValueAsString(o);
		}
	}
}
