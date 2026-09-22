package com.joke.molternring.component;

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
    public static final int MAX_ENERGY = 1200; // 60 seconds (60 * 20 ticks)
    public static final int MAX_PASSIVE_CD = 600; // 30 seconds (30 * 20 ticks)
    public static final int MIN_COOLDOWN = 100; // 5 seconds minimum cooldown for misclick protection (5 * 20 ticks)
    public static final int PENALTY_COOLDOWN = 3600; // 3 minutes penalty lock (180 * 20 ticks)
    public static final int PASSIVE_GRACE_DURATION = 100; // 5 seconds grace in lava/fire (5 * 20 ticks)

    public static int calculateCooldownTicks(int energy) {
        int spent = Math.max(0, MAX_ENERGY - energy);
        return Math.max(MIN_COOLDOWN, spent * 3);
    }

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

    public static final StreamCodec<ByteBuf, RingData> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.BOOL, RingData::active,
        ByteBufCodecs.VAR_INT, RingData::energy,
        ByteBufCodecs.VAR_INT, RingData::passiveCooldown,
        ByteBufCodecs.VAR_INT, RingData::idleTicks,
        ByteBufCodecs.VAR_INT, RingData::penaltyCooldown,
        ByteBufCodecs.VAR_INT, RingData::passiveGraceTicks,
        ByteBufCodecs.BOOL, RingData::exitedDanger,
        RingData::new
    );

    public boolean isReady() {
        return penaltyCooldown <= 0 && energy > 0;
    }

    public int getEnergyPercent() {
        return (int) Math.round(((double) energy / MAX_ENERGY) * 100.0);
    }
}
