package com.joke.moltenring.client;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MoltenRingClient implements ClientModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger("moltenring-client");

	@Override
	public void onInitializeClient() {
		LOGGER.info("Initializing MoltenRing Client");
		ModKeyMappings.init();
	}
}
