package com.joke.molternring.client;

import com.joke.molternring.MolternRing;
import com.joke.molternring.network.ToggleRingPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;

public class ModKeyMappings {
	public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(MolternRing.id("general"));

	public static final KeyMapping TOGGLE_KEY = new KeyMapping(
		"key.molternring.toggle",
		InputConstants.KEY_R,
		CATEGORY
	);

	public static void init() {
		KeyMappingHelper.registerKeyMapping(TOGGLE_KEY);

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (TOGGLE_KEY.consumeClick()) {
				if (client.player != null) {
					ClientPlayNetworking.send(new ToggleRingPayload());
				}
			}
		});
	}
}

