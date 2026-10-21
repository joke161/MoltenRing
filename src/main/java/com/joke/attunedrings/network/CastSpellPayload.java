package com.joke.attunedrings.network;

import com.joke.attunedrings.AttunedRings;
import com.joke.attunedrings.magic.ElementType;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

public record CastSpellPayload(List<ElementType> elements) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<CastSpellPayload> TYPE =
		new CustomPacketPayload.Type<>(AttunedRings.id("cast_spell"));

	public static final StreamCodec<ByteBuf, CastSpellPayload> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()),
		payload -> payload.elements().stream().map(ElementType::name).toList(),
		list -> new CastSpellPayload(list.stream().map(ElementType::valueOf).toList())
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
