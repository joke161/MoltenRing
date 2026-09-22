package com.joke.molternring.loot;

import com.joke.molternring.registry.ModItems;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.Holder;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.EmptyLootItem;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.providers.number.ints.ConstantValue;

public class ModLootModifiers {
	public static void init() {
		LootTableEvents.MODIFY.register((key, tableBuilder, source, registries) -> {
			if (BuiltInLootTables.NETHER_BRIDGE.equals(key)) {
				LootPool.Builder poolBuilder = new LootPool.Builder()
					.setRolls(Holder.direct(new ConstantValue(1)))
					.add(LootItem.lootTableItem(ModItems.MOLTEN_RING).setWeight(15))
					.add(EmptyLootItem.emptyItem().setWeight(85));

				tableBuilder.withPool(poolBuilder);
			}
		});
	}
}

