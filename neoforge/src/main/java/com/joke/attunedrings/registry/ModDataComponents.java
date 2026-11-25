package com.joke.attunedrings.registry;

import com.joke.attunedrings.AttunedRings;
import com.joke.attunedrings.component.RingData;
import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModDataComponents {
	public static final DeferredRegister.DataComponents DATA_COMPONENT_TYPES =
		DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, AttunedRings.MOD_ID);

	public static final DeferredHolder<DataComponentType<?>, DataComponentType<RingData>> RING_DATA =
		DATA_COMPONENT_TYPES.registerComponentType("ring_data", builder ->
			builder.persistent(RingData.CODEC).networkSynchronized(RingData.STREAM_CODEC));

	public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> HEAT_PROGRESS =
		DATA_COMPONENT_TYPES.registerComponentType("heat_progress", builder ->
			builder.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));

	public static void register(IEventBus modEventBus) {
		DATA_COMPONENT_TYPES.register(modEventBus);
	}
}

