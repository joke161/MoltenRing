package com.joke.moltenring.registry;

import com.joke.moltenring.MoltenRing;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

public class ModCreativeTabs {
	public static final ResourceKey<CreativeModeTab> MOLTEN_RING_TAB_KEY = ResourceKey.create(
		Registries.CREATIVE_MODE_TAB,
		MoltenRing.id("general")
	);

	public static final CreativeModeTab MOLTEN_RING_TAB = Registry.register(
		BuiltInRegistries.CREATIVE_MODE_TAB,
		MOLTEN_RING_TAB_KEY,
		FabricItemGroup.builder()
			.title(Component.translatable("itemGroup.moltenring"))
			.icon(() -> new ItemStack(ModItems.MOLTEN_RING))
			.displayItems((params, output) -> {
				output.accept(ModItems.MOLTEN_RING);
			})
			.build()
	);

	public static void init() {
		// Loaded via classloading
	}
}
