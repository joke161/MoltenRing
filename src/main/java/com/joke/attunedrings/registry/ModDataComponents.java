package com.joke.attunedrings.registry;

import com.joke.attunedrings.AttunedRings;
import com.joke.attunedrings.component.RingData;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;

public class ModDataComponents {
	public static final DataComponentType<RingData> RING_DATA = Registry.register(
		BuiltInRegistries.DATA_COMPONENT_TYPE,
		AttunedRings.id("ring_data"),
		DataComponentType.<RingData>builder()
			.persistent(RingData.CODEC)
			.networkSynchronized(RingData.STREAM_CODEC)
			.build()
	);

	public static void init() {
		// Loaded via classloading
	}
}
