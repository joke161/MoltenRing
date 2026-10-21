package com.joke.attunedrings.client;

import com.joke.attunedrings.magic.ElementType;
import com.joke.attunedrings.network.CastSpellPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

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

	public static void addElement(ElementType element, Player player) {
		if (QUEUE.size() >= 5) {
			return;
		}

		QUEUE.add(element);

		// Escalating pitch on element queueing
		float pitch = 0.85f + (QUEUE.size() * 0.18f);
		player.level().playSound(player, player.getX(), player.getY(), player.getZ(),
			SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.7f, pitch);
	}

	public static void cast(Player player) {
		if (Screen.hasShiftDown()) {
			if (!QUEUE.isEmpty()) {
				QUEUE.clear();
				player.level().playSound(player, player.getX(), player.getY(), player.getZ(),
					SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.6f, 1.5f);
				player.displayClientMessage(Component.translatable("message.attunedrings.queue_cleared"), true);
			}
			return;
		}

		if (QUEUE.isEmpty()) {
			return;
		}

		ClientPlayNetworking.send(new CastSpellPayload(new ArrayList<>(QUEUE)));
		QUEUE.clear();
	}
}
