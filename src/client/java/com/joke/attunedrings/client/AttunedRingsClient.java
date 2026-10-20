package com.joke.attunedrings.client;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AttunedRingsClient implements ClientModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger("attunedrings-client");

	@Override
	public void onInitializeClient() {
		LOGGER.info("Initializing Attuned Rings Client");
		ModKeyMappings.init();
	}
}
