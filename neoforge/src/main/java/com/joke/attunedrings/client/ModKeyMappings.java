package com.joke.attunedrings.client;

import com.joke.attunedrings.AttunedRings;
import com.joke.attunedrings.component.RingData;
import com.joke.attunedrings.magic.ElementType;
import com.joke.attunedrings.registry.ModDataComponents;
import com.joke.attunedrings.registry.ModItems;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotResult;

import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(modid = AttunedRings.MOD_ID, value = Dist.CLIENT)
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

	@SubscribeEvent
	public static void onClientTick(ClientTickEvent.Post event) {
		Minecraft client = Minecraft.getInstance();
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
	}

	private static void handleRingClick(Player player, int slotIndex) {
		ItemStack ring = getEquippedRing(player, slotIndex);
		if (ring.is(ModItems.MOLTEN_RING.get())) {
			RingData data = ring.getOrDefault(ModDataComponents.RING_DATA.get(), RingData.DEFAULT);
			if (data.penaltyCooldown() > 0 || data.energy() <= 0) {
				player.displayClientMessage(Component.translatable("message.attunedrings.ring_broken"), true);
				return;
			}
			ClientSpellQueue.addElement(ElementType.FIRE, player);
		} else if (ring.is(ModItems.UNATTUNED_RING.get())) {
			player.displayClientMessage(Component.translatable("message.attunedrings.ring_unattuned"), true);
		} else {
			player.displayClientMessage(Component.translatable("message.attunedrings.slot_empty", slotIndex + 1), true);
		}
	}

	public static ItemStack getEquippedRing(Player player, int slotIndex) {
		List<ItemStack> rings = new ArrayList<>();
		CuriosApi.getCuriosInventory(player).ifPresent(handler -> {
			for (SlotResult result : handler.findCurios(stack ->
				stack.is(ModItems.MOLTEN_RING.get()) || stack.is(ModItems.UNATTUNED_RING.get()))) {
				if (result.slotContext().identifier().contains("ring")) {
					rings.add(result.stack());
				}
			}
		});

		if (rings.isEmpty()) {
			if (!player.getMainHandItem().isEmpty() && (player.getMainHandItem().is(ModItems.MOLTEN_RING.get()) || player.getMainHandItem().is(ModItems.UNATTUNED_RING.get()))) {
				rings.add(player.getMainHandItem());
			}
			if (!player.getOffhandItem().isEmpty() && (player.getOffhandItem().is(ModItems.MOLTEN_RING.get()) || player.getOffhandItem().is(ModItems.UNATTUNED_RING.get()))) {
				rings.add(player.getOffhandItem());
			}
		}

		if (slotIndex >= 0 && slotIndex < rings.size()) {
			return rings.get(slotIndex);
		}
		return ItemStack.EMPTY;
	}
}
