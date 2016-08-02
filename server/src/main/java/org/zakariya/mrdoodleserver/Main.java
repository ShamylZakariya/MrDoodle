package org.zakariya.mrdoodleserver;

import org.zakariya.mrdoodleserver.util.Configuration;

/**
 * Created by shamyl on 8/1/16.
 */
public class Main {


	public static void main(String[] args) {
		Configuration configuration = new Configuration();
		configuration.addConfigJsonFilePath("configuration.json");
		configuration.addConfigJsonFilePath("configuration_secret.json");

	}

}
