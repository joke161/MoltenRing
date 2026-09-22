package com.joke.molternring;

import com.joke.molternring.item.MoltenRingItem;
import com.joke.molternring.loot.ModLootModifiers;
import com.joke.molternring.network.ToggleRingPayload;
import com.joke.molternring.registry.ModBlocks;
import com.joke.molternring.registry.ModCreativeTabs;
import com.joke.molternring.registry.ModDataComponents;
import com.joke.molternring.registry.ModItems;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MolternRing implements ModInitializer {
	public static final String MOD_ID = "molternring";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Initializing MolternRing mod for Minecraft 26.3");

		ModDataComponents.init();
		ModBlocks.init();
		ModItems.init();
		ModCreativeTabs.init();
		ModLootModifiers.init();

		// Register networking packet for keybind toggle
		PayloadTypeRegistry.serverboundPlay().register(ToggleRingPayload.TYPE, ToggleRingPayload.STREAM_CODEC);

		ServerPlayNetworking.registerGlobalReceiver(ToggleRingPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			context.server().execute(() -> {
				int size = player.getInventory().getContainerSize();
				for (int i = 0; i < size; i++) {
					ItemStack stack = player.getInventory().getItem(i);
					if (stack.is(ModItems.MOLTEN_RING)) {
						MoltenRingItem.toggleRing(player, stack);
						return;
					}
				}
			});
		});
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
