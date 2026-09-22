package com.joke.molternring.registry;

import com.joke.molternring.MolternRing;
import com.joke.molternring.component.RingData;
import com.joke.molternring.item.MoltenRingItem;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;

public class ModItems {
	public static final ResourceKey<Item> MOLTEN_RING_KEY = ResourceKey.create(
		Registries.ITEM,
		MolternRing.id("molten_ring")
	);

	public static final Item MOLTEN_RING = Registry.register(
		BuiltInRegistries.ITEM,
		MOLTEN_RING_KEY,
		new MoltenRingItem(
			new Item.Properties()
				.stacksTo(1)
				.durability(RingData.MAX_ENERGY)
				.component(ModDataComponents.RING_DATA, RingData.DEFAULT)
				.setId(MOLTEN_RING_KEY)
		)
	);

	public static void init() {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> output.accept(MOLTEN_RING));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output -> output.accept(MOLTEN_RING));
	}
}
