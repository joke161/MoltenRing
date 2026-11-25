package com.joke.attunedrings.item;

import com.joke.attunedrings.client.ClientHooks;
import com.joke.attunedrings.component.RingData;
import com.joke.attunedrings.registry.ModDataComponents;
import com.joke.attunedrings.registry.ModItems;
import com.joke.attunedrings.registry.ModParticles;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.SlotResult;
import top.theillusivec4.curios.api.type.capability.ICurio;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import java.util.List;

public class UnattunedRingItem extends Item implements ICurioItem {
	public static final int TARGET_HEAT = 200; // 10s in fire (200 ticks) or 5s in lava (100 ticks * 2)

	public UnattunedRingItem(Properties properties) {
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
		if (entity.level().isClientSide() || !(entity instanceof Player player)) {
			return;
		}
		processAttunement(stack, player, (ServerLevel) entity.level(), slotContext, null);
	}

	@Override
	public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
		if (level.isClientSide() || !(entity instanceof Player player)) {
			return;
		}

		boolean isHeldMain = player.getMainHandItem() == stack;
		boolean isHeldOff = player.getOffhandItem() == stack;
		if (!isHeldMain && !isHeldOff) {
			if (!isEquippedInCurios(player, stack)) {
				int heat = stack.getOrDefault(ModDataComponents.HEAT_PROGRESS.get(), 0);
				if (heat > 0) {
					if (player.isInWaterOrBubble()) {
						int drained = Math.max(0, heat - 16);
						stack.set(ModDataComponents.HEAT_PROGRESS.get(), drained);
						spawnQuenchSteam((ServerLevel) level, player, drained == 0);
					} else {
						stack.set(ModDataComponents.HEAT_PROGRESS.get(), heat - 1);
					}
				}
			}
			return;
		}

		InteractionHand hand = isHeldMain ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
		processAttunement(stack, player, (ServerLevel) level, null, hand);
	}

	private void spawnQuenchSteam(ServerLevel level, Player player, boolean finished) {
		if (player.tickCount % 2 == 0) {
			level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.95, player.getZ(), 2, 0.14, 0.12, 0.14, 0.02);
			level.sendParticles(ParticleTypes.BUBBLE_POP, player.getX(), player.getY() + 0.95, player.getZ(), 2, 0.14, 0.12, 0.14, 0.03);
		}
		if (finished) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.55f, 1.3f);
		}
	}

	private boolean isEquippedInCurios(Player player, ItemStack stack) {
		return CuriosApi.getCuriosInventory(player)
			.map(inv -> {
				for (SlotResult result : inv.findCurios(this)) {
					if (result.stack() == stack) {
						return true;
					}
				}
				return false;
			})
			.orElse(false);
	}

	private void processAttunement(ItemStack stack, Player player, ServerLevel serverLevel, SlotContext slotContext, InteractionHand hand) {
		boolean inWater = player.isInWaterOrBubble();
		boolean inLava = !inWater && player.isInLava();
		boolean inFire = !inWater && (serverLevel.getBlockState(player.blockPosition()).is(BlockTags.FIRE)
			|| serverLevel.getBlockState(player.blockPosition().below()).is(BlockTags.FIRE));

		int heat = stack.getOrDefault(ModDataComponents.HEAT_PROGRESS.get(), 0);

		boolean creative = player.isCreative();
		if (inWater && heat > 0) {
			heat = Math.max(0, heat - 16);
			spawnQuenchSteam(serverLevel, player, heat == 0);
		} else if (inLava) {
			heat += creative ? 10 : 2; // Creative: 20 ticks (1s)
		} else if (inFire) {
			heat += creative ? 10 : 1; // Creative: 20 ticks (1s)
		} else if (heat > 0) {
			heat -= 1; // gradual cooling
		}

		if (inLava || inFire) {
			if (player.tickCount % 4 == 0) {
				serverLevel.sendParticles(ParticleTypes.SMOKE, player.getX(), player.getY() + 0.8, player.getZ(), 2, 0.2, 0.2, 0.2, 0.01);
				serverLevel.sendParticles(ModParticles.MAGMA_EMBER.get(), player.getX(), player.getY() + 0.8, player.getZ(), 2, 0.15, 0.15, 0.15, 0.025);
			}
			if (player.tickCount % 16 == 0) {
				float pitch = 0.8f + ((float) heat / TARGET_HEAT) * 0.8f;
				serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.4f, pitch);
			}
		}

		if (heat >= TARGET_HEAT) {
			serverLevel.sendParticles(ParticleTypes.LAVA, player.getX(), player.getY() + 0.8, player.getZ(), 25, 0.4, 0.4, 0.4, 0.1);
			serverLevel.sendParticles(ParticleTypes.FLAME, player.getX(), player.getY() + 0.8, player.getZ(), 35, 0.5, 0.5, 0.5, 0.08);

			serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 1.0f, 0.9f);
			serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.0f, 0.7f);

			player.displayClientMessage(Component.translatable("message.attunedrings.attuned_molten"), true);

			ItemStack moltenStack = new ItemStack(ModItems.MOLTEN_RING.get());
			if (creative) {
				RingData fullData = new RingData(false, RingData.MAX_ENERGY, 0, 0, 0, 0, false, RingData.ALL_SPELLS_MASK);
				moltenStack.set(ModDataComponents.RING_DATA.get(), fullData);
				moltenStack.setDamageValue(0);
				player.getCooldowns().removeCooldown(ModItems.MOLTEN_RING.get());
			} else {
				RingData shatteredData = new RingData(false, 0, 0, 0, RingData.PENALTY_COOLDOWN, 0, false, 0);
				moltenStack.set(ModDataComponents.RING_DATA.get(), shatteredData);
				moltenStack.setDamageValue(RingData.MAX_ENERGY);
				player.getCooldowns().addCooldown(ModItems.MOLTEN_RING.get(), RingData.PENALTY_COOLDOWN);
			}

			if (slotContext != null) {
				CuriosApi.getCuriosInventory(player).ifPresent(inv ->
					inv.setEquippedCurio(slotContext.identifier(), slotContext.index(), moltenStack)
				);
			} else if (hand != null) {
				player.setItemInHand(hand, moltenStack);
			}
			return;
		}

		stack.set(ModDataComponents.HEAT_PROGRESS.get(), heat);
	}

	@Override
	public boolean isBarVisible(ItemStack stack) {
		int heat = stack.getOrDefault(ModDataComponents.HEAT_PROGRESS.get(), 0);
		return heat > 0;
	}

	@Override
	public int getBarWidth(ItemStack stack) {
		int heat = stack.getOrDefault(ModDataComponents.HEAT_PROGRESS.get(), 0);
		return Math.round(13.0f * ((float) heat / TARGET_HEAT));
	}

	@Override
	public int getBarColor(ItemStack stack) {
		return 0xFF7700;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipAdder, TooltipFlag flag) {
		tooltipAdder.add(Component.translatable("tooltip.attunedrings.unattuned_desc"));
		int heat = stack.getOrDefault(ModDataComponents.HEAT_PROGRESS.get(), 0);
		if (heat > 0) {
			int pct = Math.min(100, (int) Math.round(((double) heat / TARGET_HEAT) * 100.0));
			tooltipAdder.add(Component.translatable("tooltip.attunedrings.heat_progress", pct));
		}

		boolean shiftDown = FMLEnvironment.dist == Dist.CLIENT && ClientHooks.isShiftDown();
		if (shiftDown) {
			tooltipAdder.add(Component.translatable("tooltip.attunedrings.unattuned_shift_guide"));
		} else {
			tooltipAdder.add(Component.translatable("tooltip.attunedrings.hold_shift"));
		}
	}
}

