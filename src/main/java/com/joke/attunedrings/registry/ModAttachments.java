package com.joke.attunedrings.registry;

import com.joke.attunedrings.AttunedRings;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class ModAttachments {
	public static final AttachmentType<Set<String>> DISCOVERED_SPELLS = AttachmentRegistry.<Set<String>>builder()
		.persistent(Codec.STRING.listOf().xmap(HashSet::new, ArrayList::new))
		.initializer(HashSet::new)
		.copyOnDeath()
		.buildAndRegister(AttunedRings.id("discovered_spells"));

	public static void init() {
		// Loaded via classloading
	}
}
