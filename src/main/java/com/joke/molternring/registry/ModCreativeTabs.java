package com.joke.molternring.registry;

import com.joke.molternring.MolternRing;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

public class ModCreativeTabs {
	public static final ResourceKey<CreativeModeTab> MOLTERN_RING_TAB_KEY = ResourceKey.create(
		Registries.CREATIVE_MODE_TAB,
		MolternRing.id("general")
	);

	public static final CreativeModeTab MOLTERN_RING_TAB = Registry.register(
		BuiltInRegistries.CREATIVE_MODE_TAB,
		MOLTERN_RING_TAB_KEY,
		FabricCreativeModeTab.builder()
			.title(Component.translatable("itemGroup.molternring"))
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

