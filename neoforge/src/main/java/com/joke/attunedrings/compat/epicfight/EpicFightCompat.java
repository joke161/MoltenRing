package com.joke.attunedrings.compat.epicfight;

import com.joke.attunedrings.magic.SpellType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;

/**
 * Safe soft-dependency facade for Epic Fight.
 * Holds zero bytecode references to {@code yesman.epicfight.*};
 * implementation classes are only loaded when "epicfight" is present in ModList.
 */
public final class EpicFightCompat {
	private EpicFightCompat() {
	}

	public static boolean isLoaded() {
		return ModList.get().isLoaded("epicfight");
	}

	public static boolean isBattleMode(Player player) {
		if (!isLoaded()) {
			return false;
		}
		return EpicFightCompatImpl.isBattleMode(player);
	}

	public static boolean isInAction(Player player) {
		if (!isLoaded()) {
			return false;
		}
		return EpicFightCompatImpl.isInAction(player);
	}

	/**
	 * Blocks spell casting when the player is in Epic Fight combat mode and locked in inaction
	 * (dodges, attack recovery, stunlock, hitstun, etc.).
	 */
	public static boolean canCastSpell(Player player) {
		if (!isLoaded()) {
			return true;
		}
		return EpicFightCompatImpl.canCastSpell(player);
	}

	/**
	 * Plays a smooth caster-focused Epic Fight combat animation with extended transition blending
	 * when the player is in battle mode.
	 *
	 * @return true if an Epic Fight animation was played (suppressing vanilla hand swing); false otherwise.
	 */
	public static boolean tryPlayCastAnimation(ServerPlayer player, SpellType spell) {
		if (!isLoaded()) {
			return false;
		}
		return EpicFightCompatImpl.tryPlayCastAnimation(player, spell);
	}

	public static void registerClientCompat(IEventBus modEventBus) {
		if (isLoaded()) {
			EpicFightClientCompatImpl.register(modEventBus);
		}
	}

	/**
	 * Reads the physical Use (RMB) input when Epic Fight Battle Mode is loaded so
	 * empty-hand 1-stack Firebolt aiming is never swallowed by Battle Mode controls.
	 */
	public static boolean isUseKeyHeld(boolean vanillaKeyDown) {
		if (!isLoaded()) {
			return vanillaKeyDown;
		}
		return EpicFightClientCompatImpl.isUseKeyHeld(vanillaKeyDown);
	}

	/**
	 * Unified render gate: true whenever Epic Fight's PatchedEntityRenderer is configured
	 * for the player and vanilla fallback debugging/init is not forcing the vanilla model.
	 */
	public static boolean isEpicFightRenderingPlayer(Player player) {
		if (!isLoaded()) {
			return false;
		}
		return EpicFightClientCompatImpl.isEpicFightRenderingPlayer(player);
	}

	/**
	 * Per-frame handshake: returns true if Epic Fight's FirstPersonRenderer actually drew
	 * the first-person magma overlay during this render pass, resetting the flag afterward.
	 */
	public static boolean consumeFirstPersonRenderedThisPass() {
		if (!isLoaded()) {
			return false;
		}
		return EpicFightClientCompatImpl.consumeFirstPersonRenderedThisPass();
	}

	/**
	 * Per-frame 3rd-person handshake: true when EpicFightMagmaLayer already rendered for
	 * this player on the current frame, allowing vanilla PlayerMagmaLayer to act as fallback
	 * during transitions without double-rendering.
	 */
	public static boolean didEpicFightRender3pThisFrame(Player player) {
		if (!isLoaded()) {
			return false;
		}
		return EpicFightClientCompatImpl.didEpicFightRender3pThisFrame(player);
	}
}
