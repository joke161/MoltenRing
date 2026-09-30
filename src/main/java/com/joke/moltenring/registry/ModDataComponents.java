package com.joke.moltenring.registry;

import com.joke.moltenring.MoltenRing;
import com.joke.moltenring.component.RingData;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;

public class ModDataComponents {
	public static final DataComponentType<RingData> RING_DATA = Registry.register(
		BuiltInRegistries.DATA_COMPONENT_TYPE,
		MoltenRing.id("ring_data"),
		DataComponentType.<RingData>builder()
			.persistent(RingData.CODEC)
			.networkSynchronized(RingData.STREAM_CODEC)
			.build()
	);

	public static void init() {
		// Loaded via classloading
	}
}
