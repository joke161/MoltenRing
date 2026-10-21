package com.joke.attunedrings.item;

import com.joke.attunedrings.block.TemporaryMagmaBlock;
import com.joke.attunedrings.component.RingData;
import com.joke.attunedrings.registry.ModBlocks;
import com.joke.attunedrings.registry.ModDataComponents;
import com.joke.attunedrings.registry.ModItems;
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
			if (inDanger || player.isOnFire()) {
				if (!isAnyOtherRingInGrace(player, stack)) {
					passiveGraceTicks = RingData.PASSIVE_GRACE_DURATION; // 5 seconds (100 ticks)
					exitedDanger = false;
					changed = true;
					player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 40, 0, false, false, true));
					player.displayClientMessage(Component.translatable("message.attunedrings.passive_triggered"), true);
					level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.7f, 1.2f);
				}
			}
		} else {
			// In grace period: maintain fire resistance
			player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 40, 0, false, false, true));

			if (inDanger) {
				if (exitedDanger) {
					// Player re-entered danger: trigger 10-second cooldown
					passiveGraceTicks = 0;
					exitedDanger = false;
					passiveCd = RingData.MAX_PASSIVE_CD;
					player.getCooldowns().addCooldown(this, RingData.MAX_PASSIVE_CD);
					player.removeEffect(MobEffects.FIRE_RESISTANCE);
					player.displayClientMessage(Component.translatable("message.attunedrings.passive_cooldown_started"), true);
					level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.LAVA_EXTINGUISH, SoundSource.PLAYERS, 0.8f, 0.7f);
					changed = true;
				} else {
					passiveGraceTicks--;
					changed = true;

					if (passiveGraceTicks <= 0) {
						passiveGraceTicks = 0;
						passiveCd = RingData.MAX_PASSIVE_CD;
						player.getCooldowns().addCooldown(this, RingData.MAX_PASSIVE_CD);
						player.removeEffect(MobEffects.FIRE_RESISTANCE);
						player.displayClientMessage(Component.translatable("message.attunedrings.passive_expired"), true);
						level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.LAVA_EXTINGUISH, SoundSource.PLAYERS, 0.8f, 0.7f);
					}
				}
			} else {
				if (!exitedDanger) {
					exitedDanger = true;
					changed = true;
				}

				if (player.getRemainingFireTicks() > 40) {
					player.setRemainingFireTicks(40);
				}

				if (!player.isOnFire()) {
					passiveGraceTicks = 0;
					exitedDanger = false;
					passiveCd = RingData.MAX_PASSIVE_CD;
					player.getCooldowns().addCooldown(this, RingData.MAX_PASSIVE_CD);
					level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.5f, 1.5f);
					player.displayClientMessage(Component.translatable("message.attunedrings.passive_safe"), true);
					changed = true;
				}
			}
		}

		// 3. Penalty cooldown logic (Burnout / Supernova / Attunement)
		if (penaltyCd > 0) {
			penaltyCd--;
			energy = RingData.MAX_ENERGY - penaltyCd;
			if (active) {
				active = false;
			}
			changed = true;
		}

		// 4. Active mode logic (Lava Strider - Spell 3)
		if (active) {
			if (idleTicks > 0) {
				idleTicks--;
				changed = true;
				if (idleTicks <= 0) {
					active = false;
					changed = true;
				}
			}
			if (onVanillaCd) {
				active = false;
				changed = true;
			} else {
				freezeLava(level, player);

				if (player.isOnFire()) {
					player.clearFire();
				}
				player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 40, 0, false, false, false));

				if (player.tickCount % 2 == 0) {
					level.sendParticles(ParticleTypes.FLAME, player.getX(), player.getY() + 0.1, player.getZ(), 2, 0.1, 0.05, 0.1, 0.01);
				}
			}
		} else if (penaltyCd <= 0) {
			// Energy regeneration: +1 charge per tick when not at max
			if (energy < RingData.MAX_ENERGY) {
				energy++;
				changed = true;
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

	public static boolean hasActiveRing(Player player) {
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

	public static List<ItemStack> getAllRings(Player player) {
		List<ItemStack> rings = new ArrayList<>();
		TrinketsApi.getTrinketComponent(player).ifPresent(component -> {
			component.forEach((slotRef, stack) -> {
				if (stack.is(ModItems.MOLTEN_RING)) {
					rings.add(stack);
				}
			});
		});
		if (player.getMainHandItem().is(ModItems.MOLTEN_RING)) rings.add(player.getMainHandItem());
		if (player.getOffhandItem().is(ModItems.MOLTEN_RING)) rings.add(player.getOffhandItem());
		return rings;
	}

	private static boolean isAnyOtherRingInGrace(Player player, ItemStack currentStack) {
		for (ItemStack s : getAllRings(player)) {
			if (s != currentStack) {
				RingData d = s.getOrDefault(ModDataComponents.RING_DATA, RingData.DEFAULT);
				if (d.passiveGraceTicks() > 0) {
					return true;
				}
			}
		}
		return false;
	}

	private static void freezeLava(ServerLevel level, Player player) {
		BlockPos center = player.blockPosition().below();
		int radius = 3;

		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				if (dx * dx + dz * dz <= radius * radius) {
					BlockPos target = center.offset(dx, 0, dz);
					BlockState state = level.getBlockState(target);

					if (state.is(Blocks.LAVA) && state.getFluidState().isSource()) {
						BlockPos above = target.above();
						if (level.getBlockState(above).isAir() || level.getBlockState(above).canBeReplaced()) {
							BlockState magma = ModBlocks.TEMPORARY_MAGMA.defaultBlockState()
								.setValue(TemporaryMagmaBlock.AGE, 0);
							level.setBlockAndUpdate(target, magma);
							level.scheduleTick(target, ModBlocks.TEMPORARY_MAGMA, 40 + level.random.nextInt(20));

							level.sendParticles(ParticleTypes.LAVA,
								target.getX() + 0.5, target.getY() + 1.05, target.getZ() + 0.5,
								1, 0.2, 0.1, 0.2, 0.01);
						}
					}
				}
			}
		}
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		return InteractionResultHolder.pass(player.getItemInHand(hand));
	}

	@Override
	public boolean isBarVisible(ItemStack stack) {
		RingData data = stack.getOrDefault(ModDataComponents.RING_DATA, RingData.DEFAULT);
		return data.penaltyCooldown() > 0 || data.energy() < RingData.MAX_ENERGY;
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
			return 0xFF3333;
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

		tooltipAdder.add(Component.translatable("tooltip.attunedrings.passive_new"));
		tooltipAdder.add(Component.translatable("tooltip.attunedrings.active_new"));

		Component stateComp;
		if (data.penaltyCooldown() > 0) {
			stateComp = Component.translatable("tooltip.attunedrings.state_cooldown", (data.penaltyCooldown() + 19) / 20);
		} else if (data.passiveGraceTicks() > 0) {
			stateComp = Component.translatable("tooltip.attunedrings.state_grace", (data.passiveGraceTicks() + 19) / 20);
		} else if (data.passiveCooldown() > 0) {
			stateComp = Component.translatable("tooltip.attunedrings.state_passive_cooldown", (data.passiveCooldown() + 19) / 20);
		} else if (data.energy() < RingData.MAX_ENERGY) {
			stateComp = Component.translatable("tooltip.attunedrings.state_recharging");
		} else {
			stateComp = Component.translatable("tooltip.attunedrings.state_ready");
		}

		tooltipAdder.add(Component.translatable("tooltip.attunedrings.status", data.getEnergyPercent(), stateComp));
	}
}
