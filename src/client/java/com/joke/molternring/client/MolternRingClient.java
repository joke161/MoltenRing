package com.joke.molternring.client;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MolternRingClient implements ClientModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger("molternring-client");

	@Override
	public void onInitializeClient() {
		LOGGER.info("Initializing MolternRing Client");
		ModKeyMappings.init();
	}
}
