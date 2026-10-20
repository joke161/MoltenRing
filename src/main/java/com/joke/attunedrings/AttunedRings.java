package com.joke.attunedrings;

import com.joke.attunedrings.item.MoltenRingItem;
import com.joke.attunedrings.loot.ModLootModifiers;
import com.joke.attunedrings.network.ToggleRingPayload;
import com.joke.attunedrings.registry.ModBlocks;
import com.joke.attunedrings.registry.ModCreativeTabs;
import com.joke.attunedrings.registry.ModDataComponents;
import com.joke.attunedrings.registry.ModItems;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AttunedRings implements ModInitializer {
	public static final String MOD_ID = "attunedrings";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Initializing Attuned Rings mod for Minecraft 1.21.1 with Trinkets support");

		ModDataComponents.init();
		ModBlocks.init();
		ModItems.init();
		ModCreativeTabs.init();
		ModLootModifiers.init();

		// Register networking packet for keybind toggle
		PayloadTypeRegistry.playC2S().register(ToggleRingPayload.TYPE, ToggleRingPayload.STREAM_CODEC);

		ServerPlayNetworking.registerGlobalReceiver(ToggleRingPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			context.server().execute(() -> {
				MoltenRingItem.toggleRingFromKeybind(player);
			});
		});
	}

	public static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
	}
}
