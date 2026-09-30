package com.joke.moltenring.client;

import com.joke.moltenring.network.ToggleRingPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public class ModKeyMappings {
	public static final String CATEGORY = "category.moltenring";

	public static final KeyMapping TOGGLE_KEY = new KeyMapping(
		"key.moltenring.toggle",
		InputConstants.Type.KEYSYM,
		GLFW.GLFW_KEY_R,
		CATEGORY
	);

	public static void init() {
		KeyBindingHelper.registerKeyBinding(TOGGLE_KEY);

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (TOGGLE_KEY.consumeClick()) {
				if (client.player != null) {
					ClientPlayNetworking.send(new ToggleRingPayload());
				}
			}
		});
	}
}
