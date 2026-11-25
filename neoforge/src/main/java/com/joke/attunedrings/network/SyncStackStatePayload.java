package com.joke.attunedrings.network;

import com.joke.attunedrings.AttunedRings;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record SyncStackStatePayload(int entityId, int stackCount) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<SyncStackStatePayload> TYPE =
		new CustomPacketPayload.Type<>(AttunedRings.id("sync_stack_state"));

	public static final StreamCodec<ByteBuf, SyncStackStatePayload> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT,
		SyncStackStatePayload::entityId,
		ByteBufCodecs.VAR_INT,
		SyncStackStatePayload::stackCount,
		SyncStackStatePayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

