package com.joke.attunedrings.network;

import com.joke.attunedrings.AttunedRings;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record FireboltAimPayload(int entityId, boolean aiming) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<FireboltAimPayload> TYPE =
		new CustomPacketPayload.Type<>(AttunedRings.id("firebolt_aim"));

	public static final StreamCodec<ByteBuf, FireboltAimPayload> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT,
		FireboltAimPayload::entityId,
		ByteBufCodecs.BOOL,
		FireboltAimPayload::aiming,
		FireboltAimPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

