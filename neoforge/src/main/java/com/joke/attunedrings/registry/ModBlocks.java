package com.joke.attunedrings.registry;

import com.joke.attunedrings.AttunedRings;
import com.joke.attunedrings.block.TemporaryMagmaBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlocks {
	public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(AttunedRings.MOD_ID);

	public static final DeferredBlock<Block> TEMPORARY_MAGMA = BLOCKS.registerBlock(
		"temporary_magma",
		TemporaryMagmaBlock::new,
		BlockBehaviour.Properties.ofFullCopy(Blocks.MAGMA_BLOCK).noLootTable()
	);

	public static void register(IEventBus modEventBus) {
		BLOCKS.register(modEventBus);
	}
}

