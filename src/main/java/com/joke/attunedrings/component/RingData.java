package com.joke.attunedrings.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record RingData(
    boolean active,
    int energy,
    int passiveCooldown,
    int idleTicks,
    int penaltyCooldown,
    int passiveGraceTicks,
    boolean exitedDanger
) {
    public static final int MAX_ENERGY = 1200; // 60 seconds equivalent (1200 units)
    public static final int MAX_PASSIVE_CD = 200; // 10 seconds passive cooldown (200 ticks)
    public static final int MIN_COOLDOWN = 60; // 3 seconds minimum cooldown
    public static final int PENALTY_COOLDOWN = 1200; // 60 seconds Burnout cooldown (1200 ticks)
    public static final int PASSIVE_GRACE_DURATION = 100; // 5 seconds grace in lava/fire (100 ticks)

    public static final RingData DEFAULT = new RingData(false, MAX_ENERGY, 0, 0, 0, 0, false);

    public static final Codec<RingData> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            Codec.BOOL.fieldOf("active").forGetter(RingData::active),
            Codec.INT.fieldOf("energy").forGetter(RingData::energy),
            Codec.INT.fieldOf("passiveCooldown").forGetter(RingData::passiveCooldown),
            Codec.INT.fieldOf("idleTicks").forGetter(RingData::idleTicks),
            Codec.INT.fieldOf("penaltyCooldown").forGetter(RingData::penaltyCooldown),
            Codec.INT.fieldOf("passiveGraceTicks").forGetter(RingData::passiveGraceTicks),
            Codec.BOOL.fieldOf("exitedDanger").forGetter(RingData::exitedDanger)
        ).apply(instance, RingData::new)
    );

    public static final StreamCodec<ByteBuf, RingData> STREAM_CODEC = StreamCodec.of(
        (buf, data) -> {
            buf.writeBoolean(data.active());
            ByteBufCodecs.VAR_INT.encode(buf, data.energy());
            ByteBufCodecs.VAR_INT.encode(buf, data.passiveCooldown());
            ByteBufCodecs.VAR_INT.encode(buf, data.idleTicks());
            ByteBufCodecs.VAR_INT.encode(buf, data.penaltyCooldown());
            ByteBufCodecs.VAR_INT.encode(buf, data.passiveGraceTicks());
            buf.writeBoolean(data.exitedDanger());
        },
        buf -> new RingData(
            buf.readBoolean(),
            ByteBufCodecs.VAR_INT.decode(buf),
            ByteBufCodecs.VAR_INT.decode(buf),
            ByteBufCodecs.VAR_INT.decode(buf),
            ByteBufCodecs.VAR_INT.decode(buf),
            ByteBufCodecs.VAR_INT.decode(buf),
            buf.readBoolean()
        )
    );

    public boolean isReady() {
        return penaltyCooldown <= 0 && energy > 0;
    }

    public int getEnergyPercent() {
        return (int) Math.round(((double) energy / MAX_ENERGY) * 100.0);
    }
}
