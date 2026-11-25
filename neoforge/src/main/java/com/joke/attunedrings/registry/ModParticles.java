package com.joke.attunedrings.registry;

import com.joke.attunedrings.AttunedRings;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModParticles {
	public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =
		DeferredRegister.create(Registries.PARTICLE_TYPE, AttunedRings.MOD_ID);

	public static final DeferredHolder<ParticleType<?>, SimpleParticleType> MAGMA_EMBER =
		PARTICLE_TYPES.register("magma_ember", () -> new SimpleParticleType(false));

	public static void register(IEventBus modEventBus) {
		PARTICLE_TYPES.register(modEventBus);
	}
}

