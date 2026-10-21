package com.joke.attunedrings.registry;

import com.joke.attunedrings.AttunedRings;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

public class ModCreativeTabs {
	public static final ResourceKey<CreativeModeTab> ATTUNED_RINGS_TAB_KEY = ResourceKey.create(
		Registries.CREATIVE_MODE_TAB,
		AttunedRings.id("general")
	);

	public static final CreativeModeTab ATTUNED_RINGS_TAB = Registry.register(
		BuiltInRegistries.CREATIVE_MODE_TAB,
		ATTUNED_RINGS_TAB_KEY,
		FabricItemGroup.builder()
			.title(Component.translatable("itemGroup.attunedrings"))
			.icon(() -> new ItemStack(ModItems.MOLTEN_RING))
			.displayItems((params, output) -> {
				output.accept(ModItems.UNATTUNED_RING);
				output.accept(ModItems.MOLTEN_RING);
			})
			.build()
	);

	public static void init() {
		// Loaded via classloading
	}
}
