package com.joke.attunedrings.registry;

import com.joke.attunedrings.AttunedRings;
import com.joke.attunedrings.component.RingData;
import com.joke.attunedrings.item.MoltenRingItem;
import com.joke.attunedrings.item.UnattunedRingItem;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;

public class ModItems {
	public static final ResourceKey<Item> MOLTEN_RING_KEY = ResourceKey.create(
		Registries.ITEM,
		AttunedRings.id("molten_ring")
	);

	public static final Item MOLTEN_RING = Registry.register(
		BuiltInRegistries.ITEM,
		MOLTEN_RING_KEY,
		new MoltenRingItem(
			new Item.Properties()
				.stacksTo(1)
				.durability(RingData.MAX_ENERGY)
				.component(ModDataComponents.RING_DATA, RingData.DEFAULT)
		)
	);

	public static final ResourceKey<Item> UNATTUNED_RING_KEY = ResourceKey.create(
		Registries.ITEM,
		AttunedRings.id("unattuned_ring")
	);

	public static final Item UNATTUNED_RING = Registry.register(
		BuiltInRegistries.ITEM,
		UNATTUNED_RING_KEY,
		new UnattunedRingItem(
			new Item.Properties()
				.stacksTo(1)
				.component(ModDataComponents.HEAT_PROGRESS, 0)
		)
	);

	public static void init() {
		ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> {
			output.accept(UNATTUNED_RING);
			output.accept(MOLTEN_RING);
		});
		ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.COMBAT).register(output -> {
			output.accept(UNATTUNED_RING);
			output.accept(MOLTEN_RING);
		});
	}
}
