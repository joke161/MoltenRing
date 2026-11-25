package com.joke.attunedrings.client;

import com.joke.attunedrings.magic.SpellType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.util.Set;

public final class ClientHooks {
	private ClientHooks() {
	}

	public static void handleSyncDiscoveredSpells(Set<String> spells) {
		ClientSpellQueue.updateDiscovered(spells);
	}

	public static void handleBurnoutInstability(int durationTicks) {
		ClientBurnoutEffect.trigger(durationTicks);
	}

	public static void handleSyncStackState(int entityId, int stackCount) {
		ClientSpellVisuals.updateStackCount(entityId, stackCount);
	}

	public static void handleSpellCastVisual(int entityId, SpellType spell, int durationTicks) {
		ClientSpellVisuals.triggerCastVisual(entityId, spell, durationTicks);
	}

	public static void handleSyncFireboltAim(int entityId, boolean aiming) {
		ClientSpellVisuals.setRemoteAiming(entityId, aiming);
	}

	public static boolean isShiftDown() {
		return Screen.hasShiftDown();
	}

	public static boolean isLocalPlayerCreative() {
		Minecraft mc = Minecraft.getInstance();
		return mc.player != null && mc.player.isCreative();
	}
}
