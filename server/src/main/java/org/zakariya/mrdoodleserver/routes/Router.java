package org.zakariya.mrdoodleserver.routes;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.zakariya.mrdoodleserver.sync.UserRecordAccess;
import org.zakariya.mrdoodleserver.util.Configuration;
import redis.clients.jedis.JedisPool;
import spark.ResponseTransformer;

/**
 * Base class for Routers
 */
public abstract class Router {

	private Configuration configuration;
	private JedisPool jedisPool;
	private String storagePrefix;
	private String apiVersion;
	private ResponseTransformer jsonResponseTransformer;

	Router(Configuration configuration, JedisPool jedisPool) {
		this.configuration = configuration;
		this.jedisPool = jedisPool;
		this.storagePrefix = configuration.get("prefix", Defaults.STORAGE_PREFIX);
		this.apiVersion = configuration.get("version");
	}

	protected Configuration getConfiguration() {
		return configuration;
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

	ResponseTransformer createJsonResponseTransformer() {
		return new JsonResponseTransformer();
	}

	public abstract void initializeRoutes();

	///////////////////////////////////////////////////////////////////

	private class JsonResponseTransformer implements ResponseTransformer {

		private ObjectMapper objectMapper = new ObjectMapper();

		@Override
		public String render(Object o) throws Exception {
			return objectMapper.writeValueAsString(o);
		}
	}
}
