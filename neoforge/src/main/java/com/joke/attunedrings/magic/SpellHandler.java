package com.joke.attunedrings.magic;

import com.joke.attunedrings.compat.epicfight.EpicFightCompat;
import com.joke.attunedrings.component.RingData;
import com.joke.attunedrings.network.BurnoutInstabilityPayload;
import com.joke.attunedrings.network.FireboltAimPayload;
import com.joke.attunedrings.network.SpellCastVisualPayload;
import com.joke.attunedrings.network.SyncDiscoveredSpellsPayload;
import com.joke.attunedrings.network.SyncStackStatePayload;
import com.joke.attunedrings.registry.ModAttachments;
import com.joke.attunedrings.registry.ModDataComponents;
import com.joke.attunedrings.registry.ModItems;
import com.joke.attunedrings.registry.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SpellHandler {
	public static final int OVERCHARGED_FIREBOLT_COST = 180; // 18% ring energy

	private static final Map<UUID, Integer> ACTIVE_FLAME_CONES = new ConcurrentHashMap<>();
	private static final Map<UUID, Integer> PLAYER_STACKS = new ConcurrentHashMap<>();
	private static final Map<UUID, Float> FIREBOLT_BLOOM = new ConcurrentHashMap<>();
	private static final Map<UUID, Integer> RECENT_RING_TARGETS = new ConcurrentHashMap<>();
	private static final Set<UUID> SUBMERGED_PLAYERS = ConcurrentHashMap.newKeySet();
	private static final Random SPREAD_RND = new Random();

	public static void setPlayerStackCount(ServerPlayer player, int count) {
		if (count <= 0) {
			PLAYER_STACKS.remove(player.getUUID());
		} else {
			PLAYER_STACKS.put(player.getUUID(), Math.min(5, count));
		}
	}

	public static void clearTransientState(UUID playerUuid) {
		PLAYER_STACKS.remove(playerUuid);
		ACTIVE_FLAME_CONES.remove(playerUuid);
		FIREBOLT_BLOOM.remove(playerUuid);
		SUBMERGED_PLAYERS.remove(playerUuid);
		SpellWindupScheduler.clear(playerUuid);
	}

	public static int getPlayerStackCount(ServerPlayer player) {
		return PLAYER_STACKS.getOrDefault(player.getUUID(), 0);
	}

	public static int getVisualDurationTicks(SpellType spell) {
		return switch (spell) {
			case FIRE_1 -> 14; // 0.70s forward palm thrust + recoil
			case FIRE_2 -> 50; // 2.50s full channeling pose
			case FIRE_3 -> 20; // 1.00s low ground sweep
			case FIRE_4 -> 28; // 1.40s chest gather (0..12) -> outward shockwave hold (12..18) -> return (18..28)
			case FIRE_5 -> 26; // 1.30s (0.35s gather + slam + recovery)
		};
	}

	public static void onServerTick(ServerTickEvent.Post event) {
		SpellWindupScheduler.tick(event.getServer());
		tickActiveChannels(event.getServer());
		tickWaterQuenching(event.getServer());
		decayFireboltBloom();
		pruneRecentRingTargets(event.getServer().getTickCount());
	}

	private static void pruneRecentRingTargets(int currentTick) {
		if (RECENT_RING_TARGETS.isEmpty()) {
			return;
		}
		RECENT_RING_TARGETS.entrySet().removeIf(entry -> (currentTick - entry.getValue()) > 4);
	}

	/**
	 * Pre-ignites the target BEFORE dealing direct spell damage so vanilla's loot condition
	 * (minecraft:smelt / isOnFire) always sees the entity burning even on lethal 1-shot hits,
	 * and records the entity for LivingDropsEvent smelting fallback.
	 */
	private static void damageWithRingFire(ServerPlayer attacker, ServerLevel level, LivingEntity target,
										   float damage, float burnSeconds) {
		RECENT_RING_TARGETS.put(target.getUUID(), level.getServer().getTickCount());
		int fireTicks = Math.max(80, Math.round(burnSeconds * 20.0f));
		if (target.getRemainingFireTicks() < fireTicks) {
			target.setRemainingFireTicks(fireTicks);
		}
		target.hurt(level.damageSources().playerAttack(attacker), damage);
	}

	/**
	 * Smelting fallback for modded/edge-case mobs killed by ring spells within the last 2 ticks:
	 * replaces any smeltable raw food drop with its cooked variant from RecipeType.SMELTING.
	 */
	public static void onLivingDrops(LivingDropsEvent event) {
		if (!(event.getEntity().level() instanceof ServerLevel level)) {
			return;
		}
		Integer hitTick = RECENT_RING_TARGETS.remove(event.getEntity().getUUID());
		if (hitTick == null || (level.getServer().getTickCount() - hitTick) > 2) {
			return;
		}

		for (ItemEntity drop : event.getDrops()) {
			ItemStack raw = drop.getItem();
			if (raw.isEmpty() || !raw.has(DataComponents.FOOD)) {
				continue;
			}
			var recipeOpt = level.getRecipeManager().getRecipeFor(
				RecipeType.SMELTING, new SingleRecipeInput(raw), level
			);
			if (recipeOpt.isPresent()) {
				ItemStack smelted = recipeOpt.get().value().getResultItem(level.registryAccess());
				if (!smelted.isEmpty() && smelted.has(DataComponents.FOOD)) {
					drop.setItem(smelted.copyWithCount(raw.getCount()));
				}
			}
		}
	}

	private static void tickWaterQuenching(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!player.isAlive()) {
				SUBMERGED_PLAYERS.remove(player.getUUID());
				continue;
			}
			if (!player.isInWaterOrBubble()) {
				SUBMERGED_PLAYERS.remove(player.getUUID());
				continue;
			}

			boolean hasRing = findEquippedMoltenRing(player) != null || hasEquippedUnattunedRing(player);
			boolean hasActiveMagic = getPlayerStackCount(player) > 0
				|| ACTIVE_FLAME_CONES.containsKey(player.getUUID())
				|| SpellWindupScheduler.hasPendingCast(player.getUUID());

			if (!hasRing && !hasActiveMagic) {
				SUBMERGED_PLAYERS.remove(player.getUUID());
				continue;
			}

			boolean newlySubmerged = SUBMERGED_PLAYERS.add(player.getUUID());
			boolean hadStacks = getPlayerStackCount(player) > 0;
			boolean hadFlameCone = ACTIVE_FLAME_CONES.remove(player.getUUID()) != null;
			boolean hadWindup = SpellWindupScheduler.hasPendingCast(player.getUUID());
			boolean wasOnFire = player.isOnFire();

			if (hadWindup) {
				SpellWindupScheduler.clear(player.getUUID());
			}
			if (wasOnFire) {
				player.clearFire();
			}
			if (hadStacks) {
				setPlayerStackCount(player, 0);
				PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new SyncStackStatePayload(player.getId(), 0));
			}
			FIREBOLT_BLOOM.remove(player.getUUID());

			if (newlySubmerged || hadStacks || hadFlameCone || hadWindup || wasOnFire) {
				PacketDistributor.sendToPlayersTrackingEntity(player, new FireboltAimPayload(player.getId(), false));

				ServerLevel level = player.serverLevel();
				level.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.6f, 1.15f);

				double yawRad = Math.toRadians(player.yBodyRot);
				double fwdX = -Math.sin(yawRad);
				double fwdZ = Math.cos(yawRad);
				double rightX = -fwdZ;
				double rightZ = fwdX;
				double palmX = player.getX() + fwdX * 0.36 + rightX * 0.34;
				double palmY = player.getY() + 1.10;
				double palmZ = player.getZ() + fwdZ * 0.36 + rightZ * 0.34;

				level.sendParticles(ParticleTypes.CLOUD, palmX, palmY, palmZ, 8, 0.14, 0.14, 0.14, 0.03);
				level.sendParticles(ParticleTypes.BUBBLE_POP, palmX, palmY, palmZ, 10, 0.16, 0.16, 0.16, 0.04);
			}
		}
	}

	private static void decayFireboltBloom() {
		if (FIREBOLT_BLOOM.isEmpty()) {
			return;
		}
		float decayPerTick = 0.175f; // Decays at 3.5 deg/sec (0.175 deg/tick) when pausing fire
		Iterator<Map.Entry<UUID, Float>> it = FIREBOLT_BLOOM.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Float> entry = it.next();
			float next = entry.getValue() - decayPerTick;
			if (next <= 0.01f) {
				it.remove();
			} else {
				entry.setValue(next);
			}
		}
	}

	private static void tickActiveChannels(MinecraftServer server) {
		if (ACTIVE_FLAME_CONES.isEmpty()) {
			return;
		}

		Iterator<Map.Entry<UUID, Integer>> it = ACTIVE_FLAME_CONES.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Integer> entry = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player == null || !player.isAlive()) {
				it.remove();
				continue;
			}

			int remaining = entry.getValue() - 1;
			if (remaining <= 0) {
				it.remove();
				continue;
			}
			entry.setValue(remaining);

			streamFlameConeTick(player, player.serverLevel(), remaining);
		}
	}

	public static boolean isActivelyBlockingWithShield(Player player) {
		return player != null && (player.isBlocking()
			|| (player.isUsingItem() && player.getUseItem().getItem() instanceof ShieldItem));
	}

	public static void handleCast(ServerPlayer player, List<ElementType> queue) {
		Optional<SpellType> optionalSpell = SpellType.fromQueue(queue);
		if (optionalSpell.isEmpty()) {
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.6f, 1.5f);
			return;
		}

		if (isActivelyBlockingWithShield(player)) {
			player.displayClientMessage(Component.translatable("message.attunedrings.shield_blocking"), true);
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.5f, 1.8f);
			return;
		}

		// Epic Fight soft-dependency guard: block spell casting during combat inaction or pending wind-up
		if (!EpicFightCompat.canCastSpell(player) || SpellWindupScheduler.hasPendingCast(player.getUUID())) {
			player.displayClientMessage(Component.translatable("message.attunedrings.epicfight_inaction"), true);
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.5f, 1.8f);
			return;
		}

		SpellType spell = optionalSpell.get();

		ItemStack moltenRing = findEquippedMoltenRing(player);
		if (moltenRing == null || moltenRing.isEmpty()) {
			player.displayClientMessage(Component.translatable("message.attunedrings.no_ring_equipped"), true);
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.6f, 1.5f);
			return;
		}

		boolean creative = player.isCreative();
		RingData data = moltenRing.getOrDefault(ModDataComponents.RING_DATA.get(), RingData.DEFAULT);
		if (!creative && (data.penaltyCooldown() > 0 || data.energy() <= 0)) {
			player.displayClientMessage(Component.translatable("message.attunedrings.ring_broken"), true);
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.6f, 1.5f);
			return;
		}

		if (!creative && data.energy() < spell.getEnergyCost() && spell != SpellType.FIRE_5) {
			player.displayClientMessage(Component.translatable("message.attunedrings.not_enough_energy"), true);
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.6f, 1.5f);
			return;
		}

		// Clear synced stack aura cleanly as runes discharge into the cast
		setPlayerStackCount(player, 0);
		PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new SyncStackStatePayload(player.getId(), 0));
		PacketDistributor.sendToPlayersTrackingEntity(player, new FireboltAimPayload(player.getId(), false));

		int newEnergy = creative ? RingData.MAX_ENERGY : Math.max(0, data.energy() - spell.getEnergyCost());
		boolean triggeredBurnout = !creative && ((spell == SpellType.FIRE_5) || (newEnergy <= 0 && data.penaltyCooldown() <= 0));
		int newPenalty = creative ? 0 : (triggeredBurnout ? RingData.PENALTY_COOLDOWN : data.penaltyCooldown());
		boolean newActive = !triggeredBurnout && ((spell == SpellType.FIRE_3) || data.active());
		int unlockedMask = data.unlockedSpellsMask() | (1 << spell.ordinal());

		RingData newData = new RingData(
			newActive,
			newEnergy,
			creative ? 0 : data.passiveCooldown(),
			(spell == SpellType.FIRE_3 && !triggeredBurnout) ? 300 : (triggeredBurnout ? 0 : data.idleTicks()),
			newPenalty,
			creative ? 0 : data.passiveGraceTicks(),
			!creative && data.exitedDanger(),
			unlockedMask
		);
		moltenRing.set(ModDataComponents.RING_DATA.get(), newData);

		int targetDamage = (newPenalty > 0 || newEnergy <= 0) ? RingData.MAX_ENERGY : (RingData.MAX_ENERGY - newEnergy);
		moltenRing.setDamageValue(targetDamage);

		if (creative) {
			player.getCooldowns().removeCooldown(ModItems.MOLTEN_RING.get());
		} else if (triggeredBurnout) {
			player.getCooldowns().addCooldown(ModItems.MOLTEN_RING.get(), RingData.PENALTY_COOLDOWN);
		}

		// Synchronized animation: Epic Fight plays at natural 1.0x speed with 0.25f blend transition
		EpicFightCompat.tryPlayCastAnimation(player, spell);
		PacketDistributor.sendToPlayersTrackingEntityAndSelf(
			player,
			new SpellCastVisualPayload(player.getId(), spell.ordinal(), getVisualDurationTicks(spell))
		);

		int windupTicks = SpellWindupScheduler.getWindupTicks(player, spell);
		SpellWindupScheduler.schedule(player, spell, windupTicks, triggeredBurnout);
	}

	public static void executeSpellAndFinalize(ServerPlayer player, SpellType spell, boolean triggeredBurnout) {
		ServerLevel level = player.serverLevel();
		switch (spell) {
			case FIRE_1 -> executeFirebolt(player, level, false);
			case FIRE_2 -> executeFlameCone(player, level);
			case FIRE_3 -> executeLavaStrider(player, level);
			case FIRE_4 -> executeFireShield(player, level);
			case FIRE_5 -> executeSupernova(player, level);
		}

		if (triggeredBurnout) {
			playBurnoutAudio(player, level);
			PacketDistributor.sendToPlayer(player, new BurnoutInstabilityPayload(160));
		}

		unlockSpell(player, spell);
	}

	private static void playBurnoutAudio(ServerPlayer player, ServerLevel level) {
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
			SoundEvents.BASALT_BREAK, SoundSource.PLAYERS, 0.85f, 0.75f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
			SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.5f, 0.8f);
	}

	public static void handleOverchargedFirebolt(ServerPlayer player) {
		if (isActivelyBlockingWithShield(player)) {
			player.displayClientMessage(Component.translatable("message.attunedrings.shield_blocking"), true);
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.5f, 1.8f);
			return;
		}

		if (!EpicFightCompat.canCastSpell(player) || SpellWindupScheduler.hasPendingCast(player.getUUID())) {
			player.displayClientMessage(Component.translatable("message.attunedrings.epicfight_inaction"), true);
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.5f, 1.8f);
			return;
		}

		ItemStack moltenRing = findEquippedMoltenRing(player);
		if (moltenRing == null || moltenRing.isEmpty()) {
			player.displayClientMessage(Component.translatable("message.attunedrings.no_ring_equipped"), true);
			return;
		}

		boolean creative = player.isCreative();
		RingData data = moltenRing.getOrDefault(ModDataComponents.RING_DATA.get(), RingData.DEFAULT);
		if (!creative && (data.penaltyCooldown() > 0 || data.energy() <= 0)) {
			player.displayClientMessage(Component.translatable("message.attunedrings.ring_broken"), true);
			return;
		}

		SpellType spell = SpellType.FIRE_1;
		if (!creative && data.energy() < OVERCHARGED_FIREBOLT_COST) {
			player.displayClientMessage(Component.translatable("message.attunedrings.not_enough_energy"), true);
			return;
		}

		setPlayerStackCount(player, 0);
		PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new SyncStackStatePayload(player.getId(), 0));
		PacketDistributor.sendToPlayersTrackingEntity(player, new FireboltAimPayload(player.getId(), false));

		int newEnergy = creative ? RingData.MAX_ENERGY : Math.max(0, data.energy() - OVERCHARGED_FIREBOLT_COST);
		boolean triggeredBurnout = !creative && (newEnergy <= 0 && data.penaltyCooldown() <= 0);
		int newPenalty = creative ? 0 : (triggeredBurnout ? RingData.PENALTY_COOLDOWN : data.penaltyCooldown());
		boolean newActive = !triggeredBurnout && data.active();
		int unlockedMask = data.unlockedSpellsMask() | (1 << spell.ordinal());

		RingData newData = new RingData(
			newActive,
			newEnergy,
			creative ? 0 : data.passiveCooldown(),
			triggeredBurnout ? 0 : data.idleTicks(),
			newPenalty,
			creative ? 0 : data.passiveGraceTicks(),
			!creative && data.exitedDanger(),
			unlockedMask
		);
		moltenRing.set(ModDataComponents.RING_DATA.get(), newData);
		int targetDamage = (newPenalty > 0 || newEnergy <= 0) ? RingData.MAX_ENERGY : (RingData.MAX_ENERGY - newEnergy);
		moltenRing.setDamageValue(targetDamage);

		if (creative) {
			player.getCooldowns().removeCooldown(ModItems.MOLTEN_RING.get());
		} else if (triggeredBurnout) {
			player.getCooldowns().addCooldown(ModItems.MOLTEN_RING.get(), RingData.PENALTY_COOLDOWN);
		}

		EpicFightCompat.tryPlayCastAnimation(player, spell);
		PacketDistributor.sendToPlayersTrackingEntityAndSelf(
			player,
			new SpellCastVisualPayload(player.getId(), spell.ordinal(), 12)
		);

		executeFirebolt(player, player.serverLevel(), true);
		if (triggeredBurnout) {
			playBurnoutAudio(player, player.serverLevel());
			PacketDistributor.sendToPlayer(player, new BurnoutInstabilityPayload(160));
		}
		unlockSpell(player, spell);
	}

	private static boolean hasEquippedUnattunedRing(Player player) {
		if (player.getMainHandItem().is(ModItems.UNATTUNED_RING.get()) || player.getOffhandItem().is(ModItems.UNATTUNED_RING.get())) {
			return true;
		}
		return CuriosApi.getCuriosInventory(player)
			.map(inv -> inv.findFirstCurio(ModItems.UNATTUNED_RING.get()).isPresent())
			.orElse(false);
	}

	public static ItemStack findEquippedMoltenRing(LivingEntity entity) {
		var curioOpt = CuriosApi.getCuriosInventory(entity)
			.flatMap(inv -> inv.findFirstCurio(ModItems.MOLTEN_RING.get()));
		if (curioOpt.isPresent()) {
			return curioOpt.get().stack();
		}
		if (entity instanceof Player player) {
			if (player.getMainHandItem().is(ModItems.MOLTEN_RING.get())) return player.getMainHandItem();
			if (player.getOffhandItem().is(ModItems.MOLTEN_RING.get())) return player.getOffhandItem();
		}
		return null;
	}

	public static void unlockSpell(ServerPlayer player, SpellType spell) {
		Set<String> discovered = player.getData(ModAttachments.DISCOVERED_SPELLS.get());
		if (!discovered.contains(spell.getId())) {
			Set<String> updated = new HashSet<>(discovered);
			updated.add(spell.getId());
			player.setData(ModAttachments.DISCOVERED_SPELLS.get(), updated);

			player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8f, 1.8f);

			Component spellName = Component.translatable(spell.getTranslationKey());
			player.displayClientMessage(Component.translatable("message.attunedrings.discovered", spellName), false);

			PacketDistributor.sendToPlayer(player, new SyncDiscoveredSpellsPayload(updated));
		}
	}

	private static void executeFirebolt(ServerPlayer player, ServerLevel level, boolean overcharged) {
		Vec3 eyePos = player.getEyePosition();
		Vec3 baseLook = player.getLookAngle().normalize();

		Vec3 rightVec = baseLook.cross(new Vec3(0, 1, 0));
		if (rightVec.lengthSqr() > 0.0001) {
			rightVec = rightVec.normalize();
		} else {
			rightVec = new Vec3(1, 0, 0);
		}
		Vec3 upVec = rightVec.cross(baseLook).normalize();

		Vec3 shotDir;
		if (overcharged) {
			// Charged RMB sniper shot: pinpoint 0.0 deg accuracy, 42 block range, 8.5 damage, 6s burn
			shotDir = baseLook;
		} else {
			// Quick [R] cast: dynamic heat bloom spread (1.5 deg cold -> +2.4 deg/shot up to +-11.5 deg)
			float currentBloom = FIREBOLT_BLOOM.getOrDefault(player.getUUID(), 0.0f);
			float spreadDeg = Math.min(11.5f, Math.max(1.5f, currentBloom));
			double yawRad = (SPREAD_RND.nextDouble() * 2.0 - 1.0) * Math.toRadians(spreadDeg);
			double pitchRad = (SPREAD_RND.nextDouble() * 2.0 - 1.0) * Math.toRadians(spreadDeg);
			shotDir = baseLook
				.add(rightVec.scale(Math.tan(yawRad)))
				.add(upVec.scale(Math.tan(pitchRad)))
				.normalize();

			float nextBloom = Math.min(11.5f, (currentBloom <= 0.01f ? 1.5f : currentBloom) + 2.4f);
			FIREBOLT_BLOOM.put(player.getUUID(), nextBloom);
		}

		double range = overcharged ? 42.0 : 28.0;
		Vec3 endPos = eyePos.add(shotDir.scale(range));

		BlockHitResult blockHit = level.clip(new ClipContext(
			eyePos, endPos, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player
		));
		double hitDist = (blockHit.getType() != HitResult.Type.MISS) ? blockHit.getLocation().distanceTo(eyePos) : range;

		AABB scanBox = player.getBoundingBox().expandTowards(shotDir.scale(range)).inflate(1.2);
		LivingEntity hitTarget = null;
		double closestDist = hitDist;
		double hitInflation = overcharged ? 0.36 : 0.30;

		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, scanBox, target -> target != player && target.isAlive())) {
			AABB eBox = e.getBoundingBox().inflate(hitInflation);
			var opt = eBox.clip(eyePos, endPos);
			if (opt.isPresent()) {
				double d = eyePos.distanceTo(opt.get());
				if (d < closestDist) {
					closestDist = d;
					hitTarget = e;
				}
			}
		}

		Vec3 muzzlePos = eyePos.add(baseLook.scale(0.55)).add(rightVec.scale(0.28)).add(0, -0.15, 0);
		if (overcharged) {
			level.sendParticles(ModParticles.MAGMA_EMBER.get(), muzzlePos.x, muzzlePos.y, muzzlePos.z, 20, 0.14, 0.14, 0.14, 0.10);
			level.sendParticles(ParticleTypes.FLAME, muzzlePos.x, muzzlePos.y, muzzlePos.z, 12, 0.10, 0.10, 0.10, 0.08);
		}

		int steps = Math.max(1, (int) (closestDist * (overcharged ? 5.8 : 3.5)));
		for (int i = 0; i <= steps; i++) {
			Vec3 p = eyePos.add(shotDir.scale((double) i / steps * closestDist));
			level.sendParticles(ModParticles.MAGMA_EMBER.get(), p.x, p.y, p.z, overcharged ? 2 : 1, 0.015, 0.015, 0.015, overcharged ? 0.025 : 0.01);
			if (i % (overcharged ? 1 : 2) == 0) {
				level.sendParticles(ParticleTypes.FLAME, p.x, p.y, p.z, overcharged ? 2 : 1, 0.012, 0.012, 0.012, 0.008);
			}
			if (overcharged && i % 4 == 0) {
				level.sendParticles(ParticleTypes.LAVA, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.01);
			}
		}

		if (overcharged) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.15f, 1.55f);
			level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.9f, 1.35f);
		} else {
			level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.9f, 1.3f);
		}

		if (hitTarget != null) {
			float damage = overcharged ? 8.5f : 5.0f;
			float burnSeconds = overcharged ? 6.0f : 4.0f;
			damageWithRingFire(player, level, hitTarget, damage, burnSeconds);
			level.sendParticles(ModParticles.MAGMA_EMBER.get(), hitTarget.getX(), hitTarget.getY() + 0.6, hitTarget.getZ(),
				overcharged ? 26 : 14, overcharged ? 0.35 : 0.25, overcharged ? 0.35 : 0.25, overcharged ? 0.35 : 0.25, overcharged ? 0.12 : 0.08);
			level.sendParticles(ParticleTypes.LAVA, hitTarget.getX(), hitTarget.getY() + 0.5, hitTarget.getZ(),
				overcharged ? 12 : 6, 0.25, 0.25, 0.25, 0.06);
			if (overcharged) {
				level.playSound(null, hitTarget.getX(), hitTarget.getY(), hitTarget.getZ(),
					SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.55f, 1.85f);
			}
		} else if (blockHit.getType() == HitResult.Type.BLOCK) {
			BlockPos hitPos = blockHit.getBlockPos();
			Direction face = blockHit.getDirection();
			BlockPos placePos = hitPos.relative(face);

			Vec3 hitLoc = blockHit.getLocation();
			level.sendParticles(ModParticles.MAGMA_EMBER.get(), hitLoc.x, hitLoc.y, hitLoc.z,
				overcharged ? 20 : 10, 0.2, 0.2, 0.2, overcharged ? 0.10 : 0.06);
			if (overcharged) {
				level.sendParticles(ParticleTypes.LAVA, hitLoc.x, hitLoc.y, hitLoc.z, 6, 0.15, 0.15, 0.15, 0.05);
			}

			if (BaseFireBlock.canBePlacedAt(level, placePos, face)) {
				level.setBlockAndUpdate(placePos, BaseFireBlock.getState(level, placePos));
				level.playSound(null, placePos.getX(), placePos.getY(), placePos.getZ(),
					SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1.0f, 1.0f);
			}
		}
	}

	private static void executeFlameCone(ServerPlayer player, ServerLevel level) {
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
			SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.0f, 0.8f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
			SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.9f, 1.2f);

		ACTIVE_FLAME_CONES.put(player.getUUID(), 50); // 2.5s continuous dragon breath stream
		streamFlameConeTick(player, level, 50);
	}

	private static void streamFlameConeTick(ServerPlayer player, ServerLevel level, int remainingTicks) {
		Vec3 eyePos = player.getEyePosition();
		Vec3 look = player.getLookAngle().normalize();
		double range = 6.5;

		if (remainingTicks % 5 == 0) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, 0.6f, 1.2f);
		}

		Random rnd = new Random();
		for (int i = 0; i < 8; i++) {
			double dist = 0.8 + rnd.nextDouble() * (range - 0.8);
			double spread = 0.28 * dist;
			Vec3 dir = look.add((rnd.nextDouble() - 0.5) * spread, (rnd.nextDouble() - 0.5) * spread, (rnd.nextDouble() - 0.5) * spread).normalize();
			Vec3 p = eyePos.add(dir.scale(dist));
			if (i % 2 == 0) {
				level.sendParticles(ModParticles.MAGMA_EMBER.get(), p.x, p.y, p.z, 1, dir.x * 0.14, dir.y * 0.14, dir.z * 0.14, 0.05);
			} else {
				level.sendParticles(ParticleTypes.FLAME, p.x, p.y, p.z, 1, dir.x * 0.15, dir.y * 0.15, dir.z * 0.15, 0.04);
			}
		}

		if (remainingTicks % 4 == 0) {
			AABB coneBox = player.getBoundingBox().inflate(range);
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, coneBox, target -> target != player && target.isAlive())) {
				Vec3 toEntity = e.position().add(0, e.getBbHeight() / 2.0, 0).subtract(eyePos);
				double dist = toEntity.length();
				if (dist <= range && dist > 0.001) {
					double dot = toEntity.normalize().dot(look);
					if (dot >= 0.70) {
						damageWithRingFire(player, level, e, 2.2f, 4.0f);
						Vec3 knockDir = toEntity.normalize().scale(0.35);
						e.push(knockDir.x, 0.1, knockDir.z);
					}
				}
			}
		}
	}

	private static void executeLavaStrider(ServerPlayer player, ServerLevel level) {
		player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 300, 1, false, false, true));
		player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 300, 0, false, false, true));

		level.playSound(null, player.getX(), player.getY(), player.getZ(),
			SoundEvents.STRIDER_STEP_LAVA, SoundSource.PLAYERS, 1.2f, 1.1f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
			SoundEvents.LAVA_EXTINGUISH, SoundSource.PLAYERS, 0.8f, 1.5f);

		for (int i = 0; i < 28; i++) {
			double angle = i * (Math.PI * 2 / 28);
			double x = player.getX() + Math.cos(angle) * 1.6;
			double z = player.getZ() + Math.sin(angle) * 1.6;
			level.sendParticles(ModParticles.MAGMA_EMBER.get(), x, player.getY() + 0.12, z, 1, 0, 0.06, 0, 0.03);
		}
	}

	private static boolean isWithinSphericalReach(Vec3 center, LivingEntity target, double radius, double maxVerticalDelta) {
		AABB box = target.getBoundingBox();
		double cx = Mth.clamp(center.x, box.minX, box.maxX);
		double cy = Mth.clamp(center.y, box.minY, box.maxY);
		double cz = Mth.clamp(center.z, box.minZ, box.maxZ);
		double dx = cx - center.x;
		double dy = cy - center.y;
		double dz = cz - center.z;
		return (dx * dx + dy * dy + dz * dz) <= (radius * radius) && Math.abs(dy) <= maxVerticalDelta;
	}

	private static void executeFireShield(ServerPlayer player, ServerLevel level) {
		// Sample dynamic player coordinates at exact execution tick (post-windup)
		Vec3 playerPos = player.position();
		Vec3 center = player.getBoundingBox().getCenter();

		player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 240, 0, false, false, true));
		player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 240, 1, false, false, true));

		level.playSound(null, playerPos.x, playerPos.y, playerPos.z,
			SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.2f, 0.8f);
		level.playSound(null, playerPos.x, playerPos.y, playerPos.z,
			SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.8f, 1.6f);

		double radius = 5.0;
		AABB shockwaveBox = new AABB(center, center).inflate(radius, Math.max(radius, 4.0), radius);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, shockwaveBox, target -> target != player && target.isAlive())) {
			if (isWithinSphericalReach(center, e, radius, 4.0)) {
				damageWithRingFire(player, level, e, 5.0f, 5.0f);
				Vec3 away = e.getBoundingBox().getCenter().subtract(center);
				Vec3 horiz = new Vec3(away.x, 0.0, away.z);
				if (horiz.lengthSqr() > 0.0001) {
					horiz = horiz.normalize().scale(1.4);
					e.push(horiz.x, 0.45, horiz.z);
				} else {
					e.push(0.0, 0.45, 0.0);
				}
			}
		}

		for (int i = 0; i < 64; i++) {
			double angle = i * (Math.PI * 2 / 64);
			double x = playerPos.x + Math.cos(angle) * radius;
			double z = playerPos.z + Math.sin(angle) * radius;
			level.sendParticles(ModParticles.MAGMA_EMBER.get(), x, playerPos.y + 0.35, z, 1, Math.cos(angle) * 0.25, 0.08, Math.sin(angle) * 0.25, 0.03);
			if (i % 4 == 0) {
				level.sendParticles(ParticleTypes.LAVA, x, playerPos.y + 0.3, z, 1, 0, 0, 0, 0);
			}
		}
	}

	private static void executeSupernova(ServerPlayer player, ServerLevel level) {
		// Sample dynamic player coordinates at exact execution tick (post-windup)
		Vec3 playerPos = player.position();
		Vec3 center = player.getBoundingBox().getCenter();
		double radius = 8.0;

		level.playSound(null, playerPos.x, playerPos.y, playerPos.z,
			SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.4f, 0.7f);
		level.playSound(null, playerPos.x, playerPos.y, playerPos.z,
			SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1.2f, 1.5f);

		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, center.y, center.z, 3, 0.5, 0.5, 0.5, 0);

		Random rnd = new Random();
		for (int i = 0; i < 140; i++) {
			double theta = rnd.nextDouble() * Math.PI * 2;
			double phi = rnd.nextDouble() * Math.PI;
			double r = 1.0 + rnd.nextDouble() * (radius - 1.0);
			double x = center.x + r * Math.sin(phi) * Math.cos(theta);
			double y = center.y + r * Math.cos(phi);
			double z = center.z + r * Math.sin(phi) * Math.sin(theta);
			level.sendParticles(ModParticles.MAGMA_EMBER.get(), x, y, z, 1, 0, 0.14, 0, 0.05);
			if (i % 3 == 0) {
				level.sendParticles(ParticleTypes.FLAME, x, y, z, 1, 0, 0.08, 0, 0.04);
			}
		}

		AABB novaBox = new AABB(center, center).inflate(radius, Math.max(radius, 4.0), radius);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, novaBox, target -> target != player && target.isAlive())) {
			if (isWithinSphericalReach(center, e, radius, 4.0)) {
				damageWithRingFire(player, level, e, 20.0f, 10.0f);
				e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 1));
				e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, 1));

				Vec3 launch = e.getBoundingBox().getCenter().subtract(center);
				Vec3 horiz = new Vec3(launch.x, 0.0, launch.z);
				if (horiz.lengthSqr() > 0.0001) {
					horiz = horiz.normalize().scale(1.8);
					e.setDeltaMovement(horiz.x, 1.3, horiz.z);
				} else {
					e.setDeltaMovement(0, 1.3, 0);
				}
			}
		}
	}
}
