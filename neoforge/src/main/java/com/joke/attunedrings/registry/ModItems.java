package com.joke.attunedrings.registry;

import com.joke.attunedrings.AttunedRings;
import com.joke.attunedrings.component.RingData;
import com.joke.attunedrings.item.MoltenRingItem;
import com.joke.attunedrings.item.UnattunedRingItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {
	public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(AttunedRings.MOD_ID);

	public static final DeferredItem<Item> MOLTEN_RING = ITEMS.register("molten_ring", () ->
		new MoltenRingItem(
			new Item.Properties()
				.stacksTo(1)
				.durability(RingData.MAX_ENERGY)
				.component(ModDataComponents.RING_DATA.get(), RingData.DEFAULT)
		)
	);

	public static final DeferredItem<Item> UNATTUNED_RING = ITEMS.register("unattuned_ring", () ->
		new UnattunedRingItem(
			new Item.Properties()
				.stacksTo(1)
				.component(ModDataComponents.HEAT_PROGRESS.get(), 0)
		)
	);

	public static void register(IEventBus modEventBus) {
		ITEMS.register(modEventBus);
	}
}

