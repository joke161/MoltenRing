package com.joke.attunedrings.network;

import com.joke.attunedrings.AttunedRings;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record CastOverchargedFireboltPayload() implements CustomPacketPayload {
	public static final CastOverchargedFireboltPayload INSTANCE = new CastOverchargedFireboltPayload();

	public static final CustomPacketPayload.Type<CastOverchargedFireboltPayload> TYPE =
		new CustomPacketPayload.Type<>(AttunedRings.id("cast_overcharged_firebolt"));

	public static final StreamCodec<ByteBuf, CastOverchargedFireboltPayload> STREAM_CODEC =
		StreamCodec.unit(INSTANCE);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

