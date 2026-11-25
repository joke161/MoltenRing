package com.joke.attunedrings.registry;

import com.joke.attunedrings.AttunedRings;
import com.mojang.serialization.Codec;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

public class ModAttachments {
	public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
		DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, AttunedRings.MOD_ID);

	public static final Supplier<AttachmentType<Set<String>>> DISCOVERED_SPELLS =
		ATTACHMENT_TYPES.register("discovered_spells", () -> AttachmentType.<Set<String>>builder(() -> (Set<String>) new HashSet<String>())
			.serialize(Codec.STRING.listOf().xmap(HashSet::new, ArrayList::new))
			.copyOnDeath()
			.build());

	public static void register(IEventBus modEventBus) {
		ATTACHMENT_TYPES.register(modEventBus);
	}
}

