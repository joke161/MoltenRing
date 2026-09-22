package com.joke.molternring.registry;

import com.joke.molternring.MolternRing;
import com.joke.molternring.component.RingData;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;

public class ModDataComponents {
	public static final DataComponentType<RingData> RING_DATA = Registry.register(
		BuiltInRegistries.DATA_COMPONENT_TYPE,
		MolternRing.id("ring_data"),
		DataComponentType.<RingData>builder()
			.persistent(RingData.CODEC)
			.networkSynchronized(RingData.STREAM_CODEC)
			.ignoreSwapAnimation()
			.build()
	);

	public static void init() {
		// Loaded via classloading
	}
}
