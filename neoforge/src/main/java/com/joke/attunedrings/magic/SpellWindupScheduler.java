package com.joke.attunedrings.magic;

import com.joke.attunedrings.compat.epicfight.EpicFightCompat;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Delays spell impact execution in Epic Fight Battle Mode so gameplay effects land
 * cleanly on the wind-up/strike frame while keeping Epic Fight playback speed at 1.0x.
 */
public final class SpellWindupScheduler {
	private static final Map<UUID, PendingWindup> PENDING = new ConcurrentHashMap<>();

	private static final class PendingWindup {
		final SpellType spell;
		int ticksRemaining;
		final boolean triggeredBurnout;

		PendingWindup(SpellType spell, int ticksRemaining, boolean triggeredBurnout) {
			this.spell = spell;
			this.ticksRemaining = ticksRemaining;
			this.triggeredBurnout = triggeredBurnout;
		}
	}

	private SpellWindupScheduler() {
	}

	public static int getWindupTicks(ServerPlayer player, SpellType spell) {
		if (!EpicFightCompat.isBattleMode(player)) {
			return 0;
		}
		return switch (spell) {
			case FIRE_1 -> 0;
			case FIRE_2 -> 4;
			case FIRE_3 -> 6;
			case FIRE_4 -> 12;
			case FIRE_5 -> 10;
		};
	}

	public static boolean hasPendingCast(UUID playerUuid) {
		return PENDING.containsKey(playerUuid);
	}

	public static void clear(UUID playerUuid) {
		PENDING.remove(playerUuid);
	}

	public static void schedule(ServerPlayer player, SpellType spell, int delayTicks, boolean triggeredBurnout) {
		if (delayTicks <= 0) {
			SpellHandler.executeSpellAndFinalize(player, spell, triggeredBurnout);
			return;
		}
		PENDING.put(player.getUUID(), new PendingWindup(spell, delayTicks, triggeredBurnout));
	}

	public static void tick(MinecraftServer server) {
		if (PENDING.isEmpty()) {
			return;
		}

		Iterator<Map.Entry<UUID, PendingWindup>> it = PENDING.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, PendingWindup> entry = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player == null || !player.isAlive()) {
				it.remove();
				continue;
			}

			PendingWindup pending = entry.getValue();
			pending.ticksRemaining--;
			if (pending.ticksRemaining <= 0) {
				it.remove();
				SpellHandler.executeSpellAndFinalize(player, pending.spell, pending.triggeredBurnout);
			}
		}
	}
}

