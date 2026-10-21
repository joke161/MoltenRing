package com.joke.attunedrings;

import com.joke.attunedrings.loot.ModLootModifiers;
import com.joke.attunedrings.magic.SpellHandler;
import com.joke.attunedrings.network.CastSpellPayload;
import com.joke.attunedrings.network.SyncDiscoveredSpellsPayload;
import com.joke.attunedrings.registry.ModAttachments;
import com.joke.attunedrings.registry.ModBlocks;
import com.joke.attunedrings.registry.ModCreativeTabs;
import com.joke.attunedrings.registry.ModDataComponents;
import com.joke.attunedrings.registry.ModItems;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

public class AttunedRings implements ModInitializer {
	public static final String MOD_ID = "attunedrings";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Initializing Attuned Rings mod for Minecraft 1.21.1 with Trinkets support");

		ModDataComponents.init();
		ModAttachments.init();
		ModBlocks.init();
		ModItems.init();
		ModCreativeTabs.init();
		ModLootModifiers.init();
		SpellHandler.init();

		// Register networking payloads
		PayloadTypeRegistry.playC2S().register(CastSpellPayload.TYPE, CastSpellPayload.STREAM_CODEC);
		PayloadTypeRegistry.playS2C().register(SyncDiscoveredSpellsPayload.TYPE, SyncDiscoveredSpellsPayload.STREAM_CODEC);

		// Handle CastSpellPayload on server
		ServerPlayNetworking.registerGlobalReceiver(CastSpellPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			context.server().execute(() -> {
				SpellHandler.handleCast(player, payload.elements());
			});
		});

		// Sync discovered spells on player join
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayer player = handler.getPlayer();
			Set<String> discovered = player.getAttachedOrCreate(ModAttachments.DISCOVERED_SPELLS);
			ServerPlayNetworking.send(player, new SyncDiscoveredSpellsPayload(discovered));
		});
	}

	public static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
	}
}
