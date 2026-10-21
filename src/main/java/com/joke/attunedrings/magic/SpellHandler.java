package com.joke.attunedrings.magic;

import com.joke.attunedrings.block.TemporaryMagmaBlock;
import com.joke.attunedrings.component.RingData;
import com.joke.attunedrings.network.SyncDiscoveredSpellsPayload;
import com.joke.attunedrings.registry.ModAttachments;
import com.joke.attunedrings.registry.ModDataComponents;
import com.joke.attunedrings.registry.ModItems;
import dev.emi.trinkets.api.TrinketsApi;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SpellHandler {
	private static final Map<UUID, Integer> ACTIVE_FLAME_CONES = new ConcurrentHashMap<>();

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(SpellHandler::tickActiveChannels);
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

	public static void handleCast(ServerPlayer player, List<ElementType> queue) {
		Optional<SpellType> optionalSpell = SpellType.fromQueue(queue);
		if (optionalSpell.isEmpty()) {
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.6f, 1.5f);
			return;
		}

		SpellType spell = optionalSpell.get();

		// Find equipped Molten Ring in Trinkets
		ItemStack moltenRing = findEquippedMoltenRing(player);
		if (moltenRing == null || moltenRing.isEmpty()) {
			player.displayClientMessage(Component.translatable("message.attunedrings.no_ring_equipped"), true);
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.6f, 1.5f);
			return;
		}

		RingData data = moltenRing.getOrDefault(ModDataComponents.RING_DATA, RingData.DEFAULT);
		if (data.penaltyCooldown() > 0 || data.energy() <= 0) {
			player.displayClientMessage(Component.translatable("message.attunedrings.ring_broken"), true);
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.6f, 1.5f);
			return;
		}

		if (data.energy() < spell.getEnergyCost() && spell != SpellType.FIRE_5) {
			player.displayClientMessage(Component.translatable("message.attunedrings.not_enough_energy"), true);
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.6f, 1.5f);
			return;
		}

		ServerLevel level = player.serverLevel();

		// Execute the spell
		switch (spell) {
			case FIRE_1 -> executeFirebolt(player, level);
			case FIRE_2 -> executeFlameCone(player, level);
			case FIRE_3 -> executeLavaStrider(player, level, moltenRing);
			case FIRE_4 -> executeFireShield(player, level);
			case FIRE_5 -> executeSupernova(player, level, moltenRing);
		}

		// Deduct energy and update ring state
		int newEnergy = Math.max(0, data.energy() - spell.getEnergyCost());
		int newPenalty = (spell == SpellType.FIRE_5) ? 1200 : data.penaltyCooldown();
		boolean newActive = (spell == SpellType.FIRE_3) || data.active();

		RingData newData = new RingData(
			newActive,
			newEnergy,
			data.passiveCooldown(),
			(spell == SpellType.FIRE_3) ? 300 : data.idleTicks(),
			newPenalty,
			data.passiveGraceTicks(),
			data.exitedDanger()
		);
		moltenRing.set(ModDataComponents.RING_DATA, newData);

		// Synchronize damage predicate for texture model stages (0 -> 6)
		int targetDamage = (newPenalty > 0 || newEnergy <= 0) ? RingData.MAX_ENERGY : (RingData.MAX_ENERGY - newEnergy);
		moltenRing.setDamageValue(targetDamage);

		if (spell == SpellType.FIRE_5) {
			player.getCooldowns().addCooldown(ModItems.MOLTEN_RING, 1200);
		}

		// Discovery check
		unlockSpell(player, spell);
	}

	public static ItemStack findEquippedMoltenRing(LivingEntity entity) {
		var comp = TrinketsApi.getTrinketComponent(entity);
		if (comp.isPresent()) {
			var list = comp.get().getEquipped(ModItems.MOLTEN_RING);
			if (!list.isEmpty()) {
				return list.get(0).getB();
			}
		}
		if (entity instanceof Player player) {
			if (player.getMainHandItem().is(ModItems.MOLTEN_RING)) return player.getMainHandItem();
			if (player.getOffhandItem().is(ModItems.MOLTEN_RING)) return player.getOffhandItem();
		}
		return null;
	}

	public static void unlockSpell(ServerPlayer player, SpellType spell) {
		Set<String> discovered = player.getAttachedOrCreate(ModAttachments.DISCOVERED_SPELLS);
		if (!discovered.contains(spell.getId())) {
			Set<String> updated = new HashSet<>(discovered);
			updated.add(spell.getId());
			player.setAttached(ModAttachments.DISCOVERED_SPELLS, updated);

			player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8f, 1.8f);

			Component spellName = Component.translatable(spell.getTranslationKey());
			player.displayClientMessage(Component.translatable("message.attunedrings.discovered", spellName), false);

			ServerPlayNetworking.send(player, new SyncDiscoveredSpellsPayload(updated));
		}
	}

	private static void executeFirebolt(ServerPlayer player, ServerLevel level) {
		Vec3 eyePos = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		double range = 28.0;
		Vec3 endPos = eyePos.add(look.scale(range));

		// Raycast block
		BlockHitResult blockHit = level.clip(new ClipContext(
			eyePos, endPos, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player
		));
		double hitDist = (blockHit.getType() != HitResult.Type.MISS) ? blockHit.getLocation().distanceTo(eyePos) : range;

		// Raycast entities
		AABB scanBox = player.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0);
		LivingEntity hitTarget = null;
		double closestDist = hitDist;

		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, scanBox, target -> target != player && target.isAlive())) {
			AABB eBox = e.getBoundingBox().inflate(0.3);
			var opt = eBox.clip(eyePos, endPos);
			if (opt.isPresent()) {
				double d = eyePos.distanceTo(opt.get());
				if (d < closestDist) {
					closestDist = d;
					hitTarget = e;
				}
			}
		}

		// Particle beam
		int steps = (int) (closestDist * 3.5);
		for (int i = 0; i <= steps; i++) {
			Vec3 p = eyePos.add(look.scale((double) i / steps * closestDist));
			level.sendParticles(ParticleTypes.FLAME, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.01);
		}

		level.playSound(null, player.getX(), player.getY(), player.getZ(),
			SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.9f, 1.3f);

		if (hitTarget != null) {
			hitTarget.hurt(level.damageSources().playerAttack(player), 5.0f);
			hitTarget.igniteForSeconds(4.0f);
			level.sendParticles(ParticleTypes.LAVA, hitTarget.getX(), hitTarget.getY() + 0.5, hitTarget.getZ(), 8, 0.2, 0.2, 0.2, 0.05);
		} else if (blockHit.getType() == HitResult.Type.BLOCK) {
			BlockPos hitPos = blockHit.getBlockPos();
			Direction face = blockHit.getDirection();
			BlockPos placePos = hitPos.relative(face);

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

		// Dense streaming particles along current gaze direction
		Random rnd = new Random();
		for (int i = 0; i < 7; i++) {
			double dist = 0.8 + rnd.nextDouble() * (range - 0.8);
			double spread = 0.28 * dist;
			Vec3 dir = look.add((rnd.nextDouble() - 0.5) * spread, (rnd.nextDouble() - 0.5) * spread, (rnd.nextDouble() - 0.5) * spread).normalize();
			Vec3 p = eyePos.add(dir.scale(dist));
			level.sendParticles(ParticleTypes.FLAME, p.x, p.y, p.z, 1, dir.x * 0.15, dir.y * 0.15, dir.z * 0.15, 0.04);
			if (i % 3 == 0) {
				level.sendParticles(ParticleTypes.SMOKE, p.x, p.y, p.z, 1, dir.x * 0.05, 0.02, dir.z * 0.05, 0.01);
			}
		}

		// Apply continuous damage, fire and knockback every 4 ticks
		if (remainingTicks % 4 == 0) {
			AABB coneBox = player.getBoundingBox().inflate(range);
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, coneBox, target -> target != player && target.isAlive())) {
				Vec3 toEntity = e.position().add(0, e.getBbHeight() / 2.0, 0).subtract(eyePos);
				double dist = toEntity.length();
				if (dist <= range) {
					double dot = toEntity.normalize().dot(look);
					if (dot >= 0.70) { // ~45 degree cone
						e.hurt(level.damageSources().playerAttack(player), 2.2f);
						e.igniteForSeconds(4.0f);
						Vec3 knockDir = toEntity.normalize().scale(0.35);
						e.push(knockDir.x, 0.1, knockDir.z);
					}
				}
			}
		}
	}

	private static void executeLavaStrider(ServerPlayer player, ServerLevel level, ItemStack ringStack) {
		player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 300, 1, false, false, true));
		player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 300, 0, false, false, true));

		level.playSound(null, player.getX(), player.getY(), player.getZ(),
			SoundEvents.STRIDER_STEP_LAVA, SoundSource.PLAYERS, 1.2f, 1.1f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
			SoundEvents.LAVA_EXTINGUISH, SoundSource.PLAYERS, 0.8f, 1.5f);

		for (int i = 0; i < 24; i++) {
			double angle = i * (Math.PI * 2 / 24);
			double x = player.getX() + Math.cos(angle) * 1.5;
			double z = player.getZ() + Math.sin(angle) * 1.5;
			level.sendParticles(ParticleTypes.FLAME, x, player.getY() + 0.1, z, 1, 0, 0.05, 0, 0.02);
		}
	}

	private static void executeFireShield(ServerPlayer player, ServerLevel level) {
		player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 240, 0, false, false, true));
		player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 240, 1, false, false, true)); // 4 extra hearts

		level.playSound(null, player.getX(), player.getY(), player.getZ(),
			SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.2f, 0.8f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
			SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.8f, 1.6f);

		double radius = 5.0;
		AABB shockwaveBox = player.getBoundingBox().inflate(radius);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, shockwaveBox, target -> target != player && target.isAlive())) {
			double dist = player.distanceTo(e);
			if (dist <= radius) {
				e.hurt(level.damageSources().playerAttack(player), 5.0f);
				e.igniteForSeconds(5.0f);
				Vec3 away = e.position().subtract(player.position()).normalize().scale(1.4);
				e.push(away.x, 0.4, away.z);
			}
		}

		// Shockwave ring particles
		for (int i = 0; i < 60; i++) {
			double angle = i * (Math.PI * 2 / 60);
			double x = player.getX() + Math.cos(angle) * radius;
			double z = player.getZ() + Math.sin(angle) * radius;
			level.sendParticles(ParticleTypes.FLAME, x, player.getY() + 0.3, z, 1, Math.cos(angle) * 0.2, 0.05, Math.sin(angle) * 0.2, 0.02);
			if (i % 4 == 0) {
				level.sendParticles(ParticleTypes.LAVA, x, player.getY() + 0.3, z, 1, 0, 0, 0, 0);
			}
		}
	}

	private static void executeSupernova(ServerPlayer player, ServerLevel level, ItemStack ringStack) {
		double radius = 8.0;

		level.playSound(null, player.getX(), player.getY(), player.getZ(),
			SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.4f, 0.7f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
			SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1.2f, 1.5f);

		// Massive explosion emitter particles
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, player.getX(), player.getY() + 1.0, player.getZ(), 3, 0.5, 0.5, 0.5, 0);

		Random rnd = new Random();
		for (int i = 0; i < 120; i++) {
			double theta = rnd.nextDouble() * Math.PI * 2;
			double phi = rnd.nextDouble() * Math.PI;
			double r = 1.0 + rnd.nextDouble() * (radius - 1.0);
			double x = player.getX() + r * Math.sin(phi) * Math.cos(theta);
			double y = player.getY() + 1.0 + r * Math.cos(phi);
			double z = player.getZ() + r * Math.sin(phi) * Math.sin(theta);
			level.sendParticles(ParticleTypes.FLAME, x, y, z, 1, 0, 0.1, 0, 0.04);
			if (i % 3 == 0) {
				level.sendParticles(ParticleTypes.LAVA, x, y, z, 1, 0, 0, 0, 0);
			}
		}

		// Blast living entities
		AABB novaBox = player.getBoundingBox().inflate(radius);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, novaBox, target -> target != player && target.isAlive())) {
			double dist = player.distanceTo(e);
			if (dist <= radius) {
				e.hurt(level.damageSources().playerAttack(player), 20.0f); // 10 full hearts damage!
				e.igniteForSeconds(10.0f);
				e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 1));
				e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, 1));

				Vec3 launch = e.position().subtract(player.position()).normalize().scale(1.8);
				e.setDeltaMovement(launch.x, 1.3, launch.z);
			}
		}
	}
}
