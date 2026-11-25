package com.joke.attunedrings;

import com.joke.attunedrings.client.ClientHooks;
import com.joke.attunedrings.compat.epicfight.EpicFightCompat;
import com.joke.attunedrings.magic.SpellHandler;
import com.joke.attunedrings.network.BurnoutInstabilityPayload;
import com.joke.attunedrings.network.CastOverchargedFireboltPayload;
import com.joke.attunedrings.network.CastSpellPayload;
import com.joke.attunedrings.network.FireboltAimPayload;
import com.joke.attunedrings.network.SpellCastVisualPayload;
import com.joke.attunedrings.network.SyncDiscoveredSpellsPayload;
import com.joke.attunedrings.network.SyncStackStatePayload;
import com.joke.attunedrings.magic.SpellWindupScheduler;
import com.joke.attunedrings.registry.ModAttachments;
import com.joke.attunedrings.registry.ModBlocks;
import com.joke.attunedrings.registry.ModCreativeTabs;
import com.joke.attunedrings.registry.ModDataComponents;
import com.joke.attunedrings.registry.ModItems;
import com.joke.attunedrings.registry.ModParticles;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

@Mod(AttunedRings.MOD_ID)
public class AttunedRings {
	public static final String MOD_ID = "attunedrings";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public AttunedRings(IEventBus modEventBus, ModContainer modContainer) {
		LOGGER.info("Initializing Attuned Rings mod for NeoForge 1.21.1 with Curios support");

		ModDataComponents.register(modEventBus);
		ModAttachments.register(modEventBus);
		ModBlocks.register(modEventBus);
		ModItems.register(modEventBus);
		ModCreativeTabs.register(modEventBus);
		ModParticles.register(modEventBus);

		if (FMLEnvironment.dist == Dist.CLIENT) {
			EpicFightCompat.registerClientCompat(modEventBus);
		}

		modEventBus.addListener(AttunedRings::registerPayloads);

		NeoForge.EVENT_BUS.addListener(SpellHandler::onServerTick);
		NeoForge.EVENT_BUS.addListener(SpellHandler::onLivingDrops);
		NeoForge.EVENT_BUS.addListener(AttunedRings::onPlayerLoggedIn);
		NeoForge.EVENT_BUS.addListener(AttunedRings::onStartTracking);
	}

	private static void registerPayloads(RegisterPayloadHandlersEvent event) {
		PayloadRegistrar registrar = event.registrar("1");

		registrar.playToServer(
			CastSpellPayload.TYPE,
			CastSpellPayload.STREAM_CODEC,
			(payload, context) -> context.enqueueWork(() -> {
				if (context.player() instanceof ServerPlayer serverPlayer) {
					SpellHandler.handleCast(serverPlayer, payload.elements());
				}
			})
		);

		registrar.playBidirectional(
			SyncStackStatePayload.TYPE,
			SyncStackStatePayload.STREAM_CODEC,
			(payload, context) -> context.enqueueWork(() -> {
				if (context.flow().isServerbound()) {
					if (context.player() instanceof ServerPlayer serverPlayer) {
						int clamped = Math.max(0, Math.min(5, payload.stackCount()));
						SpellHandler.setPlayerStackCount(serverPlayer, clamped);
						PacketDistributor.sendToPlayersTrackingEntityAndSelf(
							serverPlayer,
							new SyncStackStatePayload(serverPlayer.getId(), clamped)
						);
					}
				} else if (FMLEnvironment.dist == Dist.CLIENT) {
					ClientHooks.handleSyncStackState(payload.entityId(), payload.stackCount());
				}
			})
		);

		registrar.playToClient(
			SyncDiscoveredSpellsPayload.TYPE,
			SyncDiscoveredSpellsPayload.STREAM_CODEC,
			(payload, context) -> context.enqueueWork(() -> {
				if (FMLEnvironment.dist == Dist.CLIENT) {
					ClientHooks.handleSyncDiscoveredSpells(payload.spells());
				}
			})
		);

		registrar.playToClient(
			BurnoutInstabilityPayload.TYPE,
			BurnoutInstabilityPayload.STREAM_CODEC,
			(payload, context) -> context.enqueueWork(() -> {
				if (FMLEnvironment.dist == Dist.CLIENT) {
					ClientHooks.handleBurnoutInstability(payload.durationTicks());
				}
			})
		);

		registrar.playToClient(
			SpellCastVisualPayload.TYPE,
			SpellCastVisualPayload.STREAM_CODEC,
			(payload, context) -> context.enqueueWork(() -> {
				if (FMLEnvironment.dist == Dist.CLIENT) {
					ClientHooks.handleSpellCastVisual(payload.entityId(), payload.getSpell(), payload.durationTicks());
				}
			})
		);

		registrar.playBidirectional(
			FireboltAimPayload.TYPE,
			FireboltAimPayload.STREAM_CODEC,
			(payload, context) -> context.enqueueWork(() -> {
				if (context.flow().isServerbound()) {
					if (context.player() instanceof ServerPlayer serverPlayer) {
						PacketDistributor.sendToPlayersTrackingEntity(
							serverPlayer,
							new FireboltAimPayload(serverPlayer.getId(), payload.aiming())
						);
					}
				} else if (FMLEnvironment.dist == Dist.CLIENT) {
					ClientHooks.handleSyncFireboltAim(payload.entityId(), payload.aiming());
				}
			})
		);

		registrar.playToServer(
			CastOverchargedFireboltPayload.TYPE,
			CastOverchargedFireboltPayload.STREAM_CODEC,
			(payload, context) -> context.enqueueWork(() -> {
				if (context.player() instanceof ServerPlayer serverPlayer) {
					SpellHandler.handleOverchargedFirebolt(serverPlayer);
				}
			})
		);
	}

	private static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
		if (event.getEntity() instanceof ServerPlayer serverPlayer) {
			Set<String> discovered = serverPlayer.getData(ModAttachments.DISCOVERED_SPELLS.get());
			PacketDistributor.sendToPlayer(serverPlayer, new SyncDiscoveredSpellsPayload(discovered));
			SpellHandler.clearTransientState(serverPlayer.getUUID());
		}
	}

	private static void onStartTracking(PlayerEvent.StartTracking event) {
		if (event.getTarget() instanceof ServerPlayer target && event.getEntity() instanceof ServerPlayer watcher) {
			int stacks = SpellHandler.getPlayerStackCount(target);
			if (stacks > 0) {
				PacketDistributor.sendToPlayer(watcher, new SyncStackStatePayload(target.getId(), stacks));
			}
		}
	}

	public static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
	}
}
