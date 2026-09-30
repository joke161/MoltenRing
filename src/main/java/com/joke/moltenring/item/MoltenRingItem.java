package com.joke.moltenring.item;

import com.joke.moltenring.block.TemporaryMagmaBlock;
import com.joke.moltenring.component.RingData;
import com.joke.moltenring.registry.ModBlocks;
import com.joke.moltenring.registry.ModDataComponents;
import com.joke.moltenring.registry.ModItems;
import dev.emi.trinkets.api.SlotReference;
import dev.emi.trinkets.api.Trinket;
import dev.emi.trinkets.api.TrinketsApi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

public class MoltenRingItem extends Item implements Trinket {
	public MoltenRingItem(Properties properties) {
		super(properties);
	}

	@Override
	public void tick(ItemStack stack, SlotReference slot, LivingEntity entity) {
		if (entity instanceof Player player && !entity.level().isClientSide()) {
			tickRing(stack, (ServerLevel) entity.level(), player);
		}
	}

	@Override
	public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
		if (level.isClientSide() || !(entity instanceof Player player)) {
			return;
		}

		tickRing(stack, (ServerLevel) level, player);
	}

	public void tickRing(ItemStack stack, ServerLevel level, Player player) {
		RingData data = stack.getOrDefault(ModDataComponents.RING_DATA, RingData.DEFAULT);
		boolean active = data.active();
		int energy = data.energy();
		int passiveCd = data.passiveCooldown();
		int idleTicks = data.idleTicks();
		int penaltyCd = data.penaltyCooldown();
		int passiveGraceTicks = data.passiveGraceTicks();
		boolean exitedDanger = data.exitedDanger();
		boolean changed = false;

		boolean onVanillaCd = player.getCooldowns().isOnCooldown(this);

		// 1. Synchronize cooldowns with vanilla ItemCooldowns after reconnect/respawn or when cooldown finishes
		if (penaltyCd > 0) {
			if (!onVanillaCd) {
				player.getCooldowns().addCooldown(this, penaltyCd);
				onVanillaCd = true;
			}
		} else if (passiveCd > 0) {
			if (!onVanillaCd) {
				passiveCd = 0;
				changed = true;
			} else {
				passiveCd--;
				changed = true;
			}
		}

		// Check if player is currently in fire or lava
		boolean inLava = player.isInLava();
		BlockPos playerBlock = player.blockPosition();
		boolean inFire = level.getBlockState(playerBlock).is(BlockTags.FIRE)
			|| level.getBlockState(playerBlock.below()).is(BlockTags.FIRE);
		boolean inDanger = inLava || inFire;

		boolean isBroken = penaltyCd > 0 || energy <= 0;
		boolean onCooldown = onVanillaCd || passiveCd > 0;

		// 2. Passive protection state machine
		if (onCooldown || isBroken) {
			if (passiveGraceTicks > 0) {
				passiveGraceTicks = 0;
				exitedDanger = false;
				player.removeEffect(MobEffects.FIRE_RESISTANCE);
				changed = true;
			}
		} else if (passiveGraceTicks <= 0) {
			// Ready to trigger only if not in active lava walker, not on cooldown, not broken, and player is in danger or on fire
			if (!isLavaWalkerActive(player) && (inDanger || player.isOnFire())) {
				// Only let one ring handle passive grace if multiple rings exist
				if (!isAnyOtherRingInGrace(player, stack)) {
					passiveGraceTicks = RingData.PASSIVE_GRACE_DURATION; // 5 seconds (100 ticks)
					exitedDanger = false;
					changed = true;
					player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 40, 0, false, false, true));
					player.displayClientMessage(Component.translatable("message.moltenring.passive_triggered"), true);
					level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.7f, 1.2f);
				}
			}
		} else {
			// Currently in grace period: maintain fire resistance
			player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 40, 0, false, false, true));

			if (inDanger) {
				if (exitedDanger) {
					// Player exited danger earlier and re-entered fire/lava: cancel & apply 30-sec cooldown
					passiveGraceTicks = 0;
					exitedDanger = false;
					passiveCd = RingData.MAX_PASSIVE_CD;
					player.getCooldowns().addCooldown(this, RingData.MAX_PASSIVE_CD);
					player.removeEffect(MobEffects.FIRE_RESISTANCE);
					player.displayClientMessage(Component.translatable("message.moltenring.passive_cooldown_started"), true);
					level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.LAVA_EXTINGUISH, SoundSource.PLAYERS, 0.8f, 0.7f);
					changed = true;
				} else {
					// Player is in initial danger: count down the 5 seconds
					passiveGraceTicks--;
					changed = true;

					if (passiveGraceTicks <= 0) {
						// 5 seconds expired while still inside lava/fire!
						passiveGraceTicks = 0;
						passiveCd = RingData.MAX_PASSIVE_CD;
						player.getCooldowns().addCooldown(this, RingData.MAX_PASSIVE_CD);
						player.removeEffect(MobEffects.FIRE_RESISTANCE);
						player.displayClientMessage(Component.translatable("message.moltenring.passive_expired"), true);
						level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.LAVA_EXTINGUISH, SoundSource.PLAYERS, 0.8f, 0.7f);
					}
				}
			} else {
				// Player is OUTSIDE lava/fire blocks
				if (!exitedDanger) {
					exitedDanger = true;
					changed = true;
				}

				// Accelerate extinguishing: reduce fire to ~2.5 seconds (50 ticks)
				if (player.getRemainingFireTicks() > 50) {
					player.setRemainingFireTicks(50);
				}

				if (!player.isOnFire()) {
					// Player has completely extinguished without taking damage
					passiveGraceTicks = 0;
					exitedDanger = false;
					passiveCd = RingData.MAX_PASSIVE_CD;
					player.getCooldowns().addCooldown(this, RingData.MAX_PASSIVE_CD);
					level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.5f, 1.5f);
					player.displayClientMessage(Component.translatable("message.moltenring.passive_safe"), true);
					changed = true;
				}
			}
		}

		// 3. Penalty cooldown logic (when active energy was completely depleted)
		if (penaltyCd > 0) {
			penaltyCd--;
			energy = (RingData.PENALTY_COOLDOWN - penaltyCd) / 3;
			if (active) {
				active = false;
			}
			changed = true;
		}

		// 4. Active mode logic (Lava Walker)
		if (active) {
			if (onVanillaCd) {
				active = false;
				changed = true;
			} else if (!isPrimaryActiveRing(player, stack)) {
				// Prevent multiple active rings simultaneously
				active = false;
				changed = true;
			} else {
				idleTicks = 0;
				if (energy > 0) {
					energy--;
					changed = true;

					// Freeze lava within 3-block radius under player
					freezeLava(level, player);

					// Protect player from fire while lava walker is active
					if (player.isOnFire()) {
						player.clearFire();
					}
					player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 40, 0, false, false, false));

					if (energy <= 0) {
						energy = 0;
						active = false;
						penaltyCd = RingData.PENALTY_COOLDOWN; // 3600 ticks
						player.getCooldowns().addCooldown(this, RingData.PENALTY_COOLDOWN);
						player.removeEffect(MobEffects.FIRE_RESISTANCE);
						player.displayClientMessage(Component.translatable("message.moltenring.empty"), true);
						level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 1.0f, 0.8f);
						deactivateAllRings(player, false);
					}
				}
			}
		} else if (penaltyCd <= 0) {
			// Proportional energy regeneration: +1 charge every 3 world ticks
			if (energy < RingData.MAX_ENERGY) {
				if (level.getGameTime() % 3 == 0) {
					energy++;
					changed = true;
				}
			}
		}

		if (changed) {
			stack.set(ModDataComponents.RING_DATA, new RingData(active, energy, passiveCd, idleTicks, penaltyCd, passiveGraceTicks, exitedDanger));
		}

		int targetDamage = (penaltyCd > 0 || energy <= 0) ? RingData.MAX_ENERGY : (RingData.MAX_ENERGY - energy);
		if (stack.getDamageValue() != targetDamage) {
			stack.setDamageValue(targetDamage);
		}
	}

	public static List<ItemStack> getAllRings(Player player) {
		List<ItemStack> rings = new ArrayList<>();

		// 1. Check Trinket slots
		TrinketsApi.getTrinketComponent(player).ifPresent(component -> {
			component.forEach((slotRef, stack) -> {
				if (stack.is(ModItems.MOLTEN_RING)) {
					rings.add(stack);
				}
			});
		});

		// 2. Check Player inventory
		int size = player.getInventory().getContainerSize();
		for (int i = 0; i < size; i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stack.is(ModItems.MOLTEN_RING)) {
				rings.add(stack);
			}
		}
		return rings;
	}

	public static void toggleRingFromKeybind(Player player) {
		List<ItemStack> rings = getAllRings(player);
		if (!rings.isEmpty()) {
			toggleRing(player, rings.get(0));
		}
	}

	public static boolean isPrimaryActiveRing(Player player, ItemStack currentStack) {
		for (ItemStack stack : getAllRings(player)) {
			RingData data = stack.getOrDefault(ModDataComponents.RING_DATA, RingData.DEFAULT);
			if (data.active()) {
				return stack == currentStack;
			}
		}
		return true;
	}

	public static boolean isAnyOtherRingInGrace(Player player, ItemStack currentStack) {
		for (ItemStack stack : getAllRings(player)) {
			if (stack != currentStack) {
				RingData data = stack.getOrDefault(ModDataComponents.RING_DATA, RingData.DEFAULT);
				if (data.passiveGraceTicks() > 0) {
					return true;
				}
			}
		}
		return false;
	}

	public static boolean isLavaWalkerActive(Player player) {
		if (player.getCooldowns().isOnCooldown(ModItems.MOLTEN_RING)) {
			return false;
		}
		for (ItemStack stack : getAllRings(player)) {
			RingData data = stack.getOrDefault(ModDataComponents.RING_DATA, RingData.DEFAULT);
			if (data.active() && data.energy() > 0 && data.penaltyCooldown() <= 0) {
				return true;
			}
		}
		return false;
	}

	public static boolean hasActiveRing(Player player) {
		return isLavaWalkerActive(player);
	}

	public static void deactivateAllRings(Player player, boolean sendFeedback) {
		List<ItemStack> rings = getAllRings(player);
		int activeEnergy = RingData.MAX_ENERGY;

		// Determine active ring's energy to calculate required recharge cooldown
		for (ItemStack stack : rings) {
			RingData data = stack.getOrDefault(ModDataComponents.RING_DATA, RingData.DEFAULT);
			if (data.active()) {
				activeEnergy = Math.min(activeEnergy, data.energy());
			}
		}

		int cdTicks = sendFeedback ? RingData.calculateCooldownTicks(activeEnergy) : 0;

		for (ItemStack stack : rings) {
			RingData data = stack.getOrDefault(ModDataComponents.RING_DATA, RingData.DEFAULT);
			boolean wasActive = data.active();
			int newPassiveCd = sendFeedback
				? Math.max(data.passiveCooldown(), cdTicks)
				: data.passiveCooldown();

			if (wasActive || (sendFeedback && data.passiveCooldown() < cdTicks)) {
				stack.set(ModDataComponents.RING_DATA, new RingData(
					false,
					data.energy(),
					newPassiveCd,
					data.idleTicks(),
					data.penaltyCooldown(),
					data.passiveGraceTicks(),
					data.exitedDanger()
				));
			}
		}
		player.removeEffect(MobEffects.FIRE_RESISTANCE);
		if (sendFeedback && cdTicks > 0) {
			player.getCooldowns().addCooldown(ModItems.MOLTEN_RING, cdTicks);
			player.displayClientMessage(Component.translatable("message.moltenring.deactivated"), true);
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.6f, 0.9f);
		}
	}

	public static void activateRing(Player player, ItemStack stack) {
		RingData data = stack.getOrDefault(ModDataComponents.RING_DATA, RingData.DEFAULT);
		if (data.energy() <= 0 || data.penaltyCooldown() > 0) {
			player.displayClientMessage(Component.translatable("message.moltenring.empty"), true);
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.5f, 1.5f);
			return;
		}

		// Ensure all other rings are inactive before activating this one
		deactivateAllRings(player, false);

		stack.set(ModDataComponents.RING_DATA, new RingData(
			true,
			data.energy(),
			data.passiveCooldown(),
			0,
			0,
			data.passiveGraceTicks(),
			data.exitedDanger()
		));

		player.displayClientMessage(Component.translatable("message.moltenring.active"), true);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.6f, 1.2f);
	}

	public static boolean hasActiveRingNearby(ServerLevel level, BlockPos pos) {
		AABB box = new AABB(pos).inflate(3.8, 2.5, 3.8);
		List<Player> players = level.getEntitiesOfClass(Player.class, box);
		for (Player player : players) {
			if (hasActiveRing(player)) {
				double dx = player.getX() - (pos.getX() + 0.5);
				double dz = player.getZ() - (pos.getZ() + 0.5);
				double dy = player.getY() - (pos.getY() + 1.0);
				if (dx * dx + dz * dz <= 3.8 * 3.8 && Math.abs(dy) <= 2.5) {
					return true;
				}
			}
		}
		return false;
	}

	private void freezeLava(ServerLevel level, Player player) {
		BlockPos center = BlockPos.containing(player.getX(), player.getY() - 0.5, player.getZ());
		int radius = 3;
		BlockState magmaState = ModBlocks.TEMPORARY_MAGMA.defaultBlockState();

		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				if (dx * dx + dz * dz <= radius * radius) {
					BlockPos target = center.offset(dx, 0, dz);
					BlockState current = level.getBlockState(target);

					if (current.is(ModBlocks.TEMPORARY_MAGMA)) {
						if (current.getValue(TemporaryMagmaBlock.AGE) != 0) {
							level.setBlock(target, current.setValue(TemporaryMagmaBlock.AGE, 0), Block.UPDATE_ALL);
						}
						continue;
					}

					if (current.is(Blocks.LAVA) && level.getFluidState(target).isSource()) {
						BlockPos above = target.above();
						BlockState aboveState = level.getBlockState(above);
						if (aboveState.isAir() || aboveState.canBeReplaced()) {
							level.setBlockAndUpdate(target, magmaState);

							if (level.getRandom().nextFloat() < 0.2f) {
								level.sendParticles(ParticleTypes.FLAME, target.getX() + 0.5, target.getY() + 1.02, target.getZ() + 0.5, 1, 0.2, 0.05, 0.2, 0.02);
							}
						}
					}
				}
			}
		}
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);

		if (player.getCooldowns().isOnCooldown(this)) {
			if (!level.isClientSide()) {
				showCooldownMessage(player, stack);
			}
			return InteractionResultHolder.fail(stack);
		}

		if (!level.isClientSide()) {
			toggleRing(player, stack);
		}
		return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
	}

	public static void toggleRing(Player player, ItemStack stack) {
		if (player.getCooldowns().isOnCooldown(ModItems.MOLTEN_RING)) {
			showCooldownMessage(player, stack);
			return;
		}

		if (isLavaWalkerActive(player)) {
			deactivateAllRings(player, true);
		} else {
			activateRing(player, stack);
		}
	}

	private static void showCooldownMessage(Player player, ItemStack stack) {
		int secondsLeft = getRemainingCooldownSeconds(player, stack);
		player.displayClientMessage(Component.translatable("message.moltenring.cooldown", secondsLeft + "s"), true);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.5f, 1.5f);
	}

	private static int getRemainingCooldownSeconds(Player player, ItemStack currentStack) {
		RingData currentData = currentStack.getOrDefault(ModDataComponents.RING_DATA, RingData.DEFAULT);
		if (currentData.penaltyCooldown() > 0) {
			return (currentData.penaltyCooldown() + 19) / 20;
		}
		if (currentData.passiveCooldown() > 0) {
			return (currentData.passiveCooldown() + 19) / 20;
		}
		for (ItemStack s : getAllRings(player)) {
			RingData d = s.getOrDefault(ModDataComponents.RING_DATA, RingData.DEFAULT);
			if (d.penaltyCooldown() > 0) {
				return (d.penaltyCooldown() + 19) / 20;
			}
			if (d.passiveCooldown() > 0) {
				return (d.passiveCooldown() + 19) / 20;
			}
		}
		float percent = player.getCooldowns().getCooldownPercent(currentStack.getItem(), 0.0f);
		return Math.max(1, Math.round(percent * (RingData.PENALTY_COOLDOWN / 20f)));
	}

	@Override
	public boolean isBarVisible(ItemStack stack) {
		RingData data = stack.getOrDefault(ModDataComponents.RING_DATA, RingData.DEFAULT);
		return data.active() || data.energy() < RingData.MAX_ENERGY || data.penaltyCooldown() > 0;
	}

	@Override
	public int getBarWidth(ItemStack stack) {
		RingData data = stack.getOrDefault(ModDataComponents.RING_DATA, RingData.DEFAULT);
		return Math.round(13.0f * ((float) data.energy() / RingData.MAX_ENERGY));
	}

	@Override
	public int getBarColor(ItemStack stack) {
		RingData data = stack.getOrDefault(ModDataComponents.RING_DATA, RingData.DEFAULT);
		if (data.penaltyCooldown() > 0) {
			return 0xFF3333; // Red while in penalty cooldown
		}
		float fraction = (float) data.energy() / RingData.MAX_ENERGY;
		return Mth.hsvToRgb(fraction / 3.0f, 1.0f, 1.0f);
	}

	@Override
	public boolean isFoil(ItemStack stack) {
		RingData data = stack.getOrDefault(ModDataComponents.RING_DATA, RingData.DEFAULT);
		return data.active() || super.isFoil(stack);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipAdder, TooltipFlag flag) {
		RingData data = stack.getOrDefault(ModDataComponents.RING_DATA, RingData.DEFAULT);

		tooltipAdder.add(Component.translatable("tooltip.moltenring.passive"));
		tooltipAdder.add(Component.translatable("tooltip.moltenring.active"));

		Component stateComp;
		if (data.active()) {
			stateComp = Component.translatable("tooltip.moltenring.state_active");
		} else if (data.penaltyCooldown() > 0) {
			stateComp = Component.translatable("tooltip.moltenring.state_cooldown", (data.penaltyCooldown() + 19) / 20);
		} else if (data.passiveGraceTicks() > 0) {
			stateComp = Component.translatable("tooltip.moltenring.state_grace", (data.passiveGraceTicks() + 19) / 20);
		} else if (data.passiveCooldown() > 0) {
			stateComp = Component.translatable("tooltip.moltenring.state_passive_cooldown", (data.passiveCooldown() + 19) / 20);
		} else if (data.energy() < RingData.MAX_ENERGY) {
			stateComp = Component.translatable("tooltip.moltenring.state_recharging");
		} else {
			stateComp = Component.translatable("tooltip.moltenring.state_ready");
		}

		tooltipAdder.add(Component.translatable("tooltip.moltenring.status", data.getEnergyPercent(), stateComp));
	}
}
