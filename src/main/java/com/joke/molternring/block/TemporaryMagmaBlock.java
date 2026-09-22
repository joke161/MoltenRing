package com.joke.molternring.block;

import com.joke.molternring.item.MoltenRingItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

public class TemporaryMagmaBlock extends Block {
	public static final int MAX_AGE = 3;
	public static final IntegerProperty AGE = BlockStateProperties.AGE_3;

	public TemporaryMagmaBlock(BlockBehaviour.Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(AGE, 0));
	}

	@Override
	public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
		// Custom temporary magma block does not damage entities walking on it
	}

	@Override
	public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
		level.scheduleTick(pos, this, Mth.nextInt(level.getRandom(), 40, 70));
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (MoltenRingItem.hasActiveRingNearby(level, pos)) {
			// Player with active ring is maintaining this magma block: do not decay or melt
			if (state.getValue(AGE) != 0) {
				level.setBlock(pos, state.setValue(AGE, 0), Block.UPDATE_ALL);
			}
			level.scheduleTick(pos, this, Mth.nextInt(random, 40, 80));
			return;
		}

		int currentAge = state.getValue(AGE);
		if (currentAge < MAX_AGE) {
			level.setBlock(pos, state.setValue(AGE, currentAge + 1), Block.UPDATE_ALL);
			level.scheduleTick(pos, this, Mth.nextInt(random, 20, 40));
		} else {
			melt(level, pos);
		}
	}

	public void melt(ServerLevel level, BlockPos pos) {
		level.setBlockAndUpdate(pos, Blocks.LAVA.defaultBlockState());
		level.playSound(null, pos, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 0.4f, 2.0f + level.getRandom().nextFloat() * 0.4f);
		level.sendParticles(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 1.05, pos.getZ() + 0.5, 3, 0.2, 0.1, 0.2, 0.01);
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(AGE);
	}
}
