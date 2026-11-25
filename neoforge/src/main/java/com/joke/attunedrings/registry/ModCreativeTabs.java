package com.joke.attunedrings.registry;

import com.joke.attunedrings.AttunedRings;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModCreativeTabs {
	public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
		DeferredRegister.create(Registries.CREATIVE_MODE_TAB, AttunedRings.MOD_ID);

	public static final DeferredHolder<CreativeModeTab, CreativeModeTab> ATTUNED_RINGS_TAB =
		CREATIVE_MODE_TABS.register("general", () -> CreativeModeTab.builder()
			.title(Component.translatable("itemGroup.attunedrings"))
			.icon(() -> new ItemStack(ModItems.MOLTEN_RING.get()))
			.displayItems((params, output) -> {
				output.accept(ModItems.UNATTUNED_RING.get());
				output.accept(ModItems.MOLTEN_RING.get());
			})
			.build());

	public static void register(IEventBus modEventBus) {
		CREATIVE_MODE_TABS.register(modEventBus);
		modEventBus.addListener(ModCreativeTabs::addCreative);
	}

	private static void addCreative(BuildCreativeModeTabContentsEvent event) {
		if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES || event.getTabKey() == CreativeModeTabs.COMBAT) {
			event.accept(ModItems.UNATTUNED_RING);
			event.accept(ModItems.MOLTEN_RING);
		}
	}
}

