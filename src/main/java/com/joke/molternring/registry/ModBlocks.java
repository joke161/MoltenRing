package com.joke.molternring.registry;

import com.joke.molternring.MolternRing;
import com.joke.molternring.block.TemporaryMagmaBlock;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;

public class ModBlocks {
	public static final ResourceKey<Block> TEMPORARY_MAGMA_KEY = ResourceKey.create(
		Registries.BLOCK,
		MolternRing.id("temporary_magma")
	);

	public static final Block TEMPORARY_MAGMA = Registry.register(
		BuiltInRegistries.BLOCK,
		TEMPORARY_MAGMA_KEY,
		new TemporaryMagmaBlock(
			BlockBehaviour.Properties.ofFullCopy(Blocks.MAGMA_BLOCK)
				.noLootTable()
				.setId(TEMPORARY_MAGMA_KEY)
		)
	);

	public static void init() {
		// Loaded via classloading
	}
}
