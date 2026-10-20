package com.joke.attunedrings.loot;

import com.joke.attunedrings.registry.ModItems;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.EmptyLootItem;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;

public class ModLootModifiers {
	public static void init() {
		LootTableEvents.MODIFY.register((key, tableBuilder, source, registries) -> {
			if (BuiltInLootTables.NETHER_BRIDGE.equals(key)) {
				LootPool.Builder poolBuilder = new LootPool.Builder()
					.setRolls(ConstantValue.exactly(1.0f))
					.add(LootItem.lootTableItem(ModItems.MOLTEN_RING).setWeight(15))
					.add(EmptyLootItem.emptyItem().setWeight(85));

				tableBuilder.withPool(poolBuilder);
			}
		});
	}
}
