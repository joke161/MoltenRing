package com.joke.attunedrings.client;

import com.joke.attunedrings.magic.ElementType;
import com.joke.attunedrings.registry.ModItems;
import com.mojang.blaze3d.platform.InputConstants;
import dev.emi.trinkets.api.TrinketsApi;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class ModKeyMappings {
	public static final String CATEGORY = "category.attunedrings";

	public static final KeyMapping KEY_RING_1 = new KeyMapping(
		"key.attunedrings.ring_1",
		InputConstants.Type.KEYSYM,
		GLFW.GLFW_KEY_Z,
		CATEGORY
	);

	public static final KeyMapping KEY_RING_2 = new KeyMapping(
		"key.attunedrings.ring_2",
		InputConstants.Type.KEYSYM,
		GLFW.GLFW_KEY_X,
		CATEGORY
	);

	public static final KeyMapping KEY_CAST = new KeyMapping(
		"key.attunedrings.cast",
		InputConstants.Type.KEYSYM,
		GLFW.GLFW_KEY_R,
		CATEGORY
	);

	public static void init() {
		KeyBindingHelper.registerKeyBinding(KEY_RING_1);
		KeyBindingHelper.registerKeyBinding(KEY_RING_2);
		KeyBindingHelper.registerKeyBinding(KEY_CAST);

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.player == null) {
				return;
			}

			while (KEY_RING_1.consumeClick()) {
				handleRingClick(client.player, 0);
			}

			while (KEY_RING_2.consumeClick()) {
				handleRingClick(client.player, 1);
			}

			while (KEY_CAST.consumeClick()) {
				ClientSpellQueue.cast(client.player);
			}
		});
	}

	private static void handleRingClick(Player player, int slotIndex) {
		ItemStack ring = getEquippedRing(player, slotIndex);
		if (ring.is(ModItems.MOLTEN_RING)) {
			ClientSpellQueue.addElement(ElementType.FIRE, player);
		} else if (ring.is(ModItems.UNATTUNED_RING)) {
			player.displayClientMessage(Component.translatable("message.attunedrings.ring_unattuned"), true);
		} else {
			player.displayClientMessage(Component.translatable("message.attunedrings.slot_empty", slotIndex + 1), true);
		}
	}

	public static ItemStack getEquippedRing(Player player, int slotIndex) {
		List<ItemStack> rings = new ArrayList<>();
		TrinketsApi.getTrinketComponent(player).ifPresent(comp -> {
			for (var tuple : comp.getAllEquipped()) {
				if (tuple.getA().inventory().getSlotType().getName().contains("ring")) {
					rings.add(tuple.getB());
				}
			}
		});

		if (rings.isEmpty()) {
			if (!player.getMainHandItem().isEmpty() && (player.getMainHandItem().is(ModItems.MOLTEN_RING) || player.getMainHandItem().is(ModItems.UNATTUNED_RING))) {
				rings.add(player.getMainHandItem());
			}
			if (!player.getOffhandItem().isEmpty() && (player.getOffhandItem().is(ModItems.MOLTEN_RING) || player.getOffhandItem().is(ModItems.UNATTUNED_RING))) {
				rings.add(player.getOffhandItem());
			}
		}

		if (slotIndex >= 0 && slotIndex < rings.size()) {
			return rings.get(slotIndex);
		}
		return ItemStack.EMPTY;
	}
}
