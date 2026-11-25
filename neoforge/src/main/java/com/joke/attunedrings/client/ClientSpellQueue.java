package com.joke.attunedrings.client;

import com.joke.attunedrings.compat.epicfight.EpicFightCompat;
import com.joke.attunedrings.magic.ElementType;
import com.joke.attunedrings.magic.SpellHandler;
import com.joke.attunedrings.network.CastOverchargedFireboltPayload;
import com.joke.attunedrings.network.CastSpellPayload;
import com.joke.attunedrings.network.SyncStackStatePayload;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ClientSpellQueue {
	private static final List<ElementType> QUEUE = new ArrayList<>();
	private static final Set<String> DISCOVERED_SPELLS = new HashSet<>();

	public static List<ElementType> getQueue() {
		return QUEUE;
	}

	public static Set<String> getDiscoveredSpells() {
		return DISCOVERED_SPELLS;
	}

	public static void updateDiscovered(Set<String> spells) {
		DISCOVERED_SPELLS.clear();
		DISCOVERED_SPELLS.addAll(spells);
	}

	public static boolean isDiscovered(String spellId) {
		return DISCOVERED_SPELLS.contains(spellId);
	}

	private static void syncQueueState() {
		PacketDistributor.sendToServer(new SyncStackStatePayload(0, QUEUE.size()));
	}

	public static void forceClear() {
		if (!QUEUE.isEmpty()) {
			QUEUE.clear();
			syncQueueState();
		}
	}

	public static void addElement(ElementType element, Player player) {
		if (player.isInWaterOrBubble() || QUEUE.size() >= 5 || ClientSpellVisuals.findPlayerMoltenRingClient(player).isEmpty()) {
			return;
		}

		QUEUE.add(element);
		syncQueueState();
		ClientHandEffect.spawnStackGainBurst(player);
		ClientSpellVisuals.onLocalStackPrimed(player);

		float pitch = 0.95f + (QUEUE.size() * 0.12f);
		player.level().playSound(player, player.getX(), player.getY(), player.getZ(),
			SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.7f, pitch);
	}

	public static void cast(Player player) {
		if (Screen.hasShiftDown()) {
			if (!QUEUE.isEmpty()) {
				QUEUE.clear();
				syncQueueState();
				player.level().playSound(player, player.getX(), player.getY(), player.getZ(),
					SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.6f, 1.5f);
				player.displayClientMessage(Component.translatable("message.attunedrings.queue_cleared"), true);
			}
			return;
		}

		if (QUEUE.isEmpty()) {
			return;
		}

		if (ClientSpellVisuals.findPlayerMoltenRingClient(player).isEmpty()) {
			QUEUE.clear();
			syncQueueState();
			player.displayClientMessage(Component.translatable("message.attunedrings.no_ring_equipped"), true);
			return;
		}

		if (SpellHandler.isActivelyBlockingWithShield(player)) {
			player.displayClientMessage(Component.translatable("message.attunedrings.shield_blocking"), true);
			player.level().playSound(player, player.getX(), player.getY(), player.getZ(),
				SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.5f, 1.8f);
			return;
		}

		if (!EpicFightCompat.canCastSpell(player)) {
			player.displayClientMessage(Component.translatable("message.attunedrings.epicfight_inaction"), true);
			player.level().playSound(player, player.getX(), player.getY(), player.getZ(),
				SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.5f, 1.8f);
			return;
		}

		PacketDistributor.sendToServer(new CastSpellPayload(new ArrayList<>(QUEUE)));
		QUEUE.clear();
		syncQueueState();
	}

	public static boolean castOverchargedFirebolt(Player player) {
		if (QUEUE.size() != 1) {
			return false;
		}
		if (SpellHandler.isActivelyBlockingWithShield(player)) {
			player.displayClientMessage(Component.translatable("message.attunedrings.shield_blocking"), true);
			player.level().playSound(player, player.getX(), player.getY(), player.getZ(),
				SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.5f, 1.8f);
			return false;
		}
		if (!EpicFightCompat.canCastSpell(player)) {
			player.displayClientMessage(Component.translatable("message.attunedrings.epicfight_inaction"), true);
			player.level().playSound(player, player.getX(), player.getY(), player.getZ(),
				SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.5f, 1.8f);
			return false;
		}
		PacketDistributor.sendToServer(CastOverchargedFireboltPayload.INSTANCE);
		QUEUE.clear();
		syncQueueState();
		return true;
	}
}
