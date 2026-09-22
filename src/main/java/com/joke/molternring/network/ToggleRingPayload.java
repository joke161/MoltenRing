package com.joke.molternring.network;

import com.joke.molternring.MolternRing;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ToggleRingPayload() implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<ToggleRingPayload> TYPE =
		new CustomPacketPayload.Type<>(MolternRing.id("toggle_ring"));

	public static final StreamCodec<ByteBuf, ToggleRingPayload> STREAM_CODEC =
		StreamCodec.unit(new ToggleRingPayload());

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

