package com.joke.attunedrings.network;

import com.joke.attunedrings.AttunedRings;
import com.joke.attunedrings.magic.SpellType;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record SpellCastVisualPayload(int entityId, int spellOrdinal, int durationTicks) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<SpellCastVisualPayload> TYPE =
		new CustomPacketPayload.Type<>(AttunedRings.id("spell_cast_visual"));

	public static final StreamCodec<ByteBuf, SpellCastVisualPayload> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT,
		SpellCastVisualPayload::entityId,
		ByteBufCodecs.VAR_INT,
		SpellCastVisualPayload::spellOrdinal,
		ByteBufCodecs.VAR_INT,
		SpellCastVisualPayload::durationTicks,
		SpellCastVisualPayload::new
	);

	public SpellType getSpell() {
		SpellType[] values = SpellType.values();
		if (spellOrdinal >= 0 && spellOrdinal < values.length) {
			return values[spellOrdinal];
		}
		return SpellType.FIRE_1;
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

