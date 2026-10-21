package com.joke.attunedrings.client;

import com.joke.attunedrings.network.SyncDiscoveredSpellsPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AttunedRingsClient implements ClientModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger("attunedrings-client");

	@Override
	public void onInitializeClient() {
		LOGGER.info("Initializing Attuned Rings Client");
		ModKeyMappings.init();
		SpellHudRenderer.init();
		ClientHandEffect.init();

		// Register client receiver for synced discovered spells
		ClientPlayNetworking.registerGlobalReceiver(SyncDiscoveredSpellsPayload.TYPE, (payload, context) -> {
			context.client().execute(() -> {
				ClientSpellQueue.updateDiscovered(payload.spells());
			});
		});
	}
}
