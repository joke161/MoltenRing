package com.joke.attunedrings.item;

import com.joke.attunedrings.block.TemporaryMagmaBlock;
import com.joke.attunedrings.client.ClientHooks;
import com.joke.attunedrings.component.RingData;
import com.joke.attunedrings.registry.ModBlocks;
import com.joke.attunedrings.registry.ModDataComponents;
import com.joke.attunedrings.registry.ModItems;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.SlotResult;
import top.theillusivec4.curios.api.type.capability.ICurio;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import java.util.ArrayList;
import java.util.List;

public class MoltenRingItem extends Item implements ICurioItem {
	public MoltenRingItem(Properties properties) {
		super(properties);
	}

	@Override
	public boolean canEquip(SlotContext slotContext, ItemStack stack) {
		return slotContext.identifier().contains("ring");
	}

	@Override
	public boolean canEquipFromUse(SlotContext slotContext, ItemStack stack) {
		return true;
	}

	@Override
	public ICurio.SoundInfo getEquipSound(SlotContext slotContext, ItemStack stack) {
		return new ICurio.SoundInfo(SoundEvents.ARMOR_EQUIP_GOLD.value(), 1.0f, 1.0f);
	}

	@Override
	public void curioTick(SlotContext slotContext, ItemStack stack) {
		LivingEntity entity = slotContext.entity();
		if (entity instanceof Player player && !entity.level().isClientSide()) {
			boolean activeRingSlot = slotContext.identifier().contains("ring");
			tickRing(stack, (ServerLevel) entity.level(), player, activeRingSlot);
		}
	}

	@Override
	public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
		if (level.isClientSide() || !(entity instanceof Player player)) {
			return;
		}
		boolean heldInHands = player.getMainHandItem() == stack || player.getOffhandItem() == stack;
		tickRing(stack, (ServerLevel) level, player, heldInHands);
	}

	public void tickRing(ItemStack stack, ServerLevel level, Player player) {
		boolean heldInHands = player.getMainHandItem() == stack || player.getOffhandItem() == stack;
		tickRing(stack, level, player, heldInHands || isEquippedInCurios(player, stack));
	}

	private boolean isEquippedInCurios(Player player, ItemStack stack) {
		return CuriosApi.getCuriosInventory(player)
			.map(inv -> {
				for (SlotResult result : inv.findCurios(ModItems.MOLTEN_RING.get())) {
					if (result.stack() == stack && result.slotContext().identifier().contains("ring")) {
						return true;
					}
				}
				return false;
			})
			.orElse(false);
	}

	private void tickRing(ItemStack stack, ServerLevel level, Player player, boolean isActiveSlot) {
		RingData data = stack.getOrDefault(ModDataComponents.RING_DATA.get(), RingData.DEFAULT);
		boolean active = data.active();
		int energy = data.energy();
		int passiveCd = data.passiveCooldown();
		int idleTicks = data.idleTicks();
		int penaltyCd = data.penaltyCooldown();
		int passiveGraceTicks = data.passiveGraceTicks();
		boolean exitedDanger = data.exitedDanger();
		int unlockedMask = data.unlockedSpellsMask();
		boolean changed = false;

		boolean onVanillaCd = player.getCooldowns().isOnCooldown(this);

		// Creative mode testing bypass: full energy, zero burnout/cooldowns, all spells revealed
		if (player.isCreative()) {
			if (onVanillaCd) {
				player.getCooldowns().removeCooldown(this);
			}
			if (penaltyCd != 0 || passiveCd != 0 || passiveGraceTicks != 0 || exitedDanger
				|| energy < RingData.MAX_ENERGY || unlockedMask != RingData.ALL_SPELLS_MASK) {
				penaltyCd = 0;
				passiveCd = 0;
				passiveGraceTicks = 0;
				exitedDanger = false;
				energy = RingData.MAX_ENERGY;
				unlockedMask = RingData.ALL_SPELLS_MASK;
				changed = true;
			}
		} else {
			// 1. Synchronize cooldowns with vanilla ItemCooldowns
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
		}

		boolean inWater = player.isInWaterOrBubble();
		boolean inLava = player.isInLava();
		BlockPos playerBlock = player.blockPosition();
		boolean inFire = level.getBlockState(playerBlock).is(BlockTags.FIRE)
			|| level.getBlockState(playerBlock.below()).is(BlockTags.FIRE);
		boolean inDanger = inLava || inFire;

		boolean isBroken = penaltyCd > 0 || energy <= 0;
		boolean onCooldown = onVanillaCd || passiveCd > 0;

		// 2. Passive protection state machine (only active when equipped in Curios or held in hands; bypassed in Creative)
		if (player.isCreative() || !isActiveSlot) {
			if (passiveGraceTicks > 0 || exitedDanger) {
				passiveGraceTicks = 0;
				exitedDanger = false;
				changed = true;
			}
		} else if (onCooldown || isBroken) {
			if (passiveGraceTicks > 0) {
				passiveGraceTicks = 0;
				exitedDanger = false;
				player.removeEffect(MobEffects.FIRE_RESISTANCE);
				changed = true;
			}
		} else if (passiveGraceTicks <= 0) {
			if (inDanger || player.isOnFire()) {
				if (!isAnyOtherRingInGrace(player, stack)) {
					passiveGraceTicks = RingData.PASSIVE_GRACE_DURATION; // 5s
					exitedDanger = false;
					changed = true;
					player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 40, 0, false, false, true));
					player.displayClientMessage(Component.translatable("message.attunedrings.passive_triggered"), true);
					level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.7f, 1.2f);
				}
			}
		} else {
			player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 40, 0, false, false, true));

			if (inDanger) {
				if (exitedDanger) {
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
			if (inWater && penaltyCd > 0) {
				penaltyCd--; // 2x faster water cooling
				if (player.tickCount % 10 == 0) {
					double yawRad = Math.toRadians(player.yBodyRot);
					double palmX = player.getX() - Math.sin(yawRad) * 0.34 - Math.cos(yawRad) * 0.34;
					double palmZ = player.getZ() + Math.cos(yawRad) * 0.34 - Math.sin(yawRad) * 0.34;
					level.sendParticles(ParticleTypes.BUBBLE_POP, palmX, player.getY() + 1.05, palmZ, 3, 0.12, 0.12, 0.12, 0.02);
					level.sendParticles(ParticleTypes.CLOUD, palmX, player.getY() + 1.10, palmZ, 2, 0.08, 0.08, 0.08, 0.02);
				}
			}
			if (inWater) {
				if (penaltyCd <= 0) {
					player.getCooldowns().removeCooldown(this);
				} else if (player.tickCount % 5 == 0) {
					player.getCooldowns().addCooldown(this, penaltyCd);
				}
			}
			energy = RingData.MAX_ENERGY - penaltyCd;
			if (active) {
				active = false;
			}
			changed = true;
		}

		// 4. Active mode logic (Lava Strider - Spell 3; deactivated when unequipped from active slots)
		if (active) {
			if (idleTicks > 0) {
				idleTicks--;
				changed = true;
				if (idleTicks <= 0) {
					active = false;
					changed = true;
				}
			}
			if (!isActiveSlot || onVanillaCd || inWater) {
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
			if (energy < RingData.MAX_ENERGY) {
				energy++;
				changed = true;
			}
		}

		if (changed) {
			stack.set(ModDataComponents.RING_DATA.get(), new RingData(active, energy, passiveCd, idleTicks, penaltyCd, passiveGraceTicks, exitedDanger, unlockedMask));
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
		if (player.getCooldowns().isOnCooldown(ModItems.MOLTEN_RING.get())) {
			return false;
		}
		for (ItemStack stack : getAllRings(player)) {
			RingData data = stack.getOrDefault(ModDataComponents.RING_DATA.get(), RingData.DEFAULT);
			if (data.active() && data.energy() > 0 && data.penaltyCooldown() <= 0) {
				return true;
			}
		}
		return false;
	}

	public static List<ItemStack> getAllRings(Player player) {
		List<ItemStack> rings = new ArrayList<>();
		CuriosApi.getCuriosInventory(player).ifPresent(handler -> {
			for (SlotResult result : handler.findCurios(ModItems.MOLTEN_RING.get())) {
				rings.add(result.stack());
			}
		});
		if (player.getMainHandItem().is(ModItems.MOLTEN_RING.get())) rings.add(player.getMainHandItem());
		if (player.getOffhandItem().is(ModItems.MOLTEN_RING.get())) rings.add(player.getOffhandItem());
		return rings;
	}

	private static boolean isAnyOtherRingInGrace(Player player, ItemStack currentStack) {
		for (ItemStack s : getAllRings(player)) {
			if (s != currentStack) {
				RingData d = s.getOrDefault(ModDataComponents.RING_DATA.get(), RingData.DEFAULT);
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
							BlockState magma = ModBlocks.TEMPORARY_MAGMA.get().defaultBlockState()
								.setValue(TemporaryMagmaBlock.AGE, 0);
							level.setBlockAndUpdate(target, magma);
							level.scheduleTick(target, ModBlocks.TEMPORARY_MAGMA.get(), 40 + level.random.nextInt(20));

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
	public boolean isBarVisible(ItemStack stack) {
		return false;
	}

	@Override
	public int getBarWidth(ItemStack stack) {
		RingData data = stack.getOrDefault(ModDataComponents.RING_DATA.get(), RingData.DEFAULT);
		return Math.round(13.0f * ((float) data.energy() / RingData.MAX_ENERGY));
	}

	@Override
	public int getBarColor(ItemStack stack) {
		RingData data = stack.getOrDefault(ModDataComponents.RING_DATA.get(), RingData.DEFAULT);
		if (data.penaltyCooldown() > 0) {
			return 0xFF3333;
		}
		float fraction = (float) data.energy() / RingData.MAX_ENERGY;
		return Mth.hsvToRgb(fraction / 3.0f, 1.0f, 1.0f);
	}

	@Override
	public boolean isFoil(ItemStack stack) {
		RingData data = stack.getOrDefault(ModDataComponents.RING_DATA.get(), RingData.DEFAULT);
		return data.active() || super.isFoil(stack);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipAdder, TooltipFlag flag) {
		RingData data = stack.getOrDefault(ModDataComponents.RING_DATA.get(), RingData.DEFAULT);

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

		boolean shiftDown = FMLEnvironment.dist == Dist.CLIENT && ClientHooks.isShiftDown();
		if (shiftDown) {
			boolean creative = FMLEnvironment.dist == Dist.CLIENT && ClientHooks.isLocalPlayerCreative();
			tooltipAdder.add(Component.translatable("tooltip.attunedrings.shift_spells_header"));
			for (int tier = 1; tier <= 5; tier++) {
				if (creative || data.isSpellUnlocked(tier)) {
					tooltipAdder.add(Component.translatable("tooltip.attunedrings.spell_slot_" + tier));
				} else {
					tooltipAdder.add(Component.translatable("tooltip.attunedrings.spell_slot_locked", tier));
				}
			}
			tooltipAdder.add(Component.translatable("tooltip.attunedrings.shift_water_note"));
		} else {
			tooltipAdder.add(Component.translatable("tooltip.attunedrings.hold_shift"));
		}
	}
}

