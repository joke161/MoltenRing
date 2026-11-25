package com.joke.attunedrings.compat.epicfight;

import com.joke.attunedrings.magic.SpellType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.player.PlayerPatch;

/**
 * Isolated implementation touching Epic Fight classes directly.
 * Only classloaded by the JVM when {@link EpicFightCompat#isLoaded()} is true.
 * Preset Epic Fight spell animations are intentionally removed so custom procedural
 * armature choreography runs identically in Vanilla and Battle Mode.
 */
public final class EpicFightCompatImpl {
	private EpicFightCompatImpl() {
	}

	public static boolean isBattleMode(Player player) {
		PlayerPatch<?> patch = EpicFightCapabilities.getPlayerPatch(player);
		return patch != null && patch.isEpicFightMode();
	}

	public static boolean isInAction(Player player) {
		PlayerPatch<?> patch = EpicFightCapabilities.getPlayerPatch(player);
		return patch != null && patch.getEntityState().inaction();
	}

	public static boolean canCastSpell(Player player) {
		PlayerPatch<?> patch = EpicFightCapabilities.getPlayerPatch(player);
		if (patch != null && patch.isEpicFightMode()) {
			return !patch.getEntityState().inaction();
		}
		return true;
	}

	public static boolean tryPlayCastAnimation(ServerPlayer player, SpellType spell) {
		// Custom procedural animations handle both Vanilla and Epic Fight Battle Mode client-side
		return false;
	}
}
