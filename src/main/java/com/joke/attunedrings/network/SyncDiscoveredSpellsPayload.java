package com.joke.attunedrings.network;

import com.joke.attunedrings.AttunedRings;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record SyncDiscoveredSpellsPayload(Set<String> spells) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<SyncDiscoveredSpellsPayload> TYPE =
		new CustomPacketPayload.Type<>(AttunedRings.id("sync_discovered_spells"));

	public static final StreamCodec<ByteBuf, SyncDiscoveredSpellsPayload> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()),
		payload -> new ArrayList<>(payload.spells()),
		list -> new SyncDiscoveredSpellsPayload(new HashSet<>(list))
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
