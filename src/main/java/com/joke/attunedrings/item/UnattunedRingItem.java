package com.joke.attunedrings.item;

import com.joke.attunedrings.component.RingData;
import com.joke.attunedrings.registry.ModDataComponents;
import com.joke.attunedrings.registry.ModItems;
import dev.emi.trinkets.api.Trinket;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

public class UnattunedRingItem extends Item implements Trinket {
	public static final int TARGET_HEAT = 200; // 10s in fire (200 ticks) or 5s in lava (100 ticks * 2)

	public UnattunedRingItem(Properties properties) {
		super(properties);
	}

	@Override
	public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
		if (level.isClientSide() || !(entity instanceof Player player)) {
			return;
		}

		boolean isHeldMain = player.getMainHandItem() == stack;
		boolean isHeldOff = player.getOffhandItem() == stack;
		if (!isHeldMain && !isHeldOff) {
			// Slowly cool down if stored in inventory
			int heat = stack.getOrDefault(ModDataComponents.HEAT_PROGRESS, 0);
			if (heat > 0) {
				stack.set(ModDataComponents.HEAT_PROGRESS, heat - 1);
			}
			return;
		}

		ServerLevel serverLevel = (ServerLevel) level;
		boolean inLava = player.isInLava();
		boolean inFire = level.getBlockState(player.blockPosition()).is(BlockTags.FIRE)
			|| level.getBlockState(player.blockPosition().below()).is(BlockTags.FIRE);

		int heat = stack.getOrDefault(ModDataComponents.HEAT_PROGRESS, 0);

		if (inLava) {
			heat += 2;
		} else if (inFire) {
			heat += 1;
		} else if (heat > 0) {
			heat -= 1; // gradual cooling
		}

		if (inLava || inFire) {
			if (player.tickCount % 4 == 0) {
				serverLevel.sendParticles(ParticleTypes.SMOKE, player.getX(), player.getY() + 0.8, player.getZ(), 2, 0.2, 0.2, 0.2, 0.01);
				serverLevel.sendParticles(ParticleTypes.FLAME, player.getX(), player.getY() + 0.8, player.getZ(), 1, 0.15, 0.15, 0.15, 0.02);
			}
			if (player.tickCount % 16 == 0) {
				float pitch = 0.8f + ((float) heat / TARGET_HEAT) * 0.8f;
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.4f, pitch);
			}
		}

		if (heat >= TARGET_HEAT) {
			// Attunement completed!
			serverLevel.sendParticles(ParticleTypes.LAVA, player.getX(), player.getY() + 0.8, player.getZ(), 25, 0.4, 0.4, 0.4, 0.1);
			serverLevel.sendParticles(ParticleTypes.FLAME, player.getX(), player.getY() + 0.8, player.getZ(), 35, 0.5, 0.5, 0.5, 0.08);

			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 1.0f, 0.9f);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.0f, 0.7f);

			player.displayClientMessage(Component.translatable("message.attunedrings.attuned_molten"), true);

			ItemStack moltenStack = new ItemStack(ModItems.MOLTEN_RING);
			RingData shatteredData = new RingData(false, 0, 0, 0, 1200, 0, false);
			moltenStack.set(ModDataComponents.RING_DATA, shatteredData);
			moltenStack.setDamageValue(RingData.MAX_ENERGY);
			player.getCooldowns().addCooldown(ModItems.MOLTEN_RING, 1200);

			if (isHeldMain) {
				player.setItemInHand(InteractionHand.MAIN_HAND, moltenStack);
			} else {
				player.setItemInHand(InteractionHand.OFF_HAND, moltenStack);
			}
			return;
		}

		stack.set(ModDataComponents.HEAT_PROGRESS, heat);
	}

	@Override
	public boolean isBarVisible(ItemStack stack) {
		int heat = stack.getOrDefault(ModDataComponents.HEAT_PROGRESS, 0);
		return heat > 0;
	}

	@Override
	public int getBarWidth(ItemStack stack) {
		int heat = stack.getOrDefault(ModDataComponents.HEAT_PROGRESS, 0);
		return Math.round(13.0f * ((float) heat / TARGET_HEAT));
	}

	@Override
	public int getBarColor(ItemStack stack) {
		return 0xFF7700; // Glowing fiery orange during heat progress
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipAdder, TooltipFlag flag) {
		tooltipAdder.add(Component.translatable("tooltip.attunedrings.unattuned_desc"));
		int heat = stack.getOrDefault(ModDataComponents.HEAT_PROGRESS, 0);
		if (heat > 0) {
			int pct = Math.min(100, (int) Math.round(((double) heat / TARGET_HEAT) * 100.0));
			tooltipAdder.add(Component.translatable("tooltip.attunedrings.heat_progress", pct));
		}
	}
}
