package com.joke.attunedrings.network;

import com.joke.attunedrings.AttunedRings;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record BurnoutInstabilityPayload(int durationTicks) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<BurnoutInstabilityPayload> TYPE =
		new CustomPacketPayload.Type<>(AttunedRings.id("burnout_instability"));

	public static final StreamCodec<ByteBuf, BurnoutInstabilityPayload> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT,
		BurnoutInstabilityPayload::durationTicks,
		BurnoutInstabilityPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

