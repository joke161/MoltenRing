package com.joke.attunedrings.client;

import com.joke.attunedrings.AttunedRings;
import com.joke.attunedrings.client.particle.MagmaEmberParticle;
import com.joke.attunedrings.registry.ModParticles;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.Random;

/**
 * Event-driven palm particles:
 * - Single 5-spark burst on [Z] stack gain
 * - Tiny (0.02..0.03) tight inward sparks shifted down-right while holding RMB to aim Firebolt
 * - Subtle dark smoke during Stage 6 Burnout
 */
@EventBusSubscriber(modid = AttunedRings.MOD_ID, value = Dist.CLIENT)
public class ClientHandEffect {
	private static final Random RND = new Random();

	private static Vec3 resolveRightPalmPosition(Player player, boolean isFirstPerson, boolean isAiming) {
		if (isFirstPerson) {
			Vec3 eyePos = player.getEyePosition();
			Vec3 lookVec = player.getViewVector(1.0f);
			Vec3 worldUp = new Vec3(0, 1, 0);
			Vec3 rightVec = lookVec.cross(worldUp);
			if (rightVec.lengthSqr() > 0.0001) {
				rightVec = rightVec.normalize();
			} else {
				rightVec = new Vec3(1, 0, 0);
			}
			Vec3 upVec = rightVec.cross(lookVec).normalize();

			if (isAiming) {
				// Anchor down and right so center screen & crosshair stay 100% clear
				return eyePos.add(lookVec.scale(0.55))
					.add(rightVec.scale(0.44))
					.subtract(upVec.scale(0.32));
			}
			return eyePos.add(lookVec.scale(0.42))
				.add(rightVec.scale(0.36))
				.subtract(upVec.scale(0.22));
		}

		double yawRad = Math.toRadians(player.yBodyRot);
		double fwdX = -Math.sin(yawRad);
		double fwdZ = Math.cos(yawRad);
		double rightX = -fwdZ;
		double rightZ = fwdX;

		double fwdDist = isAiming ? 0.62 : 0.34;
		double rightDist = 0.34;
		double height = isAiming ? 1.32 : 0.96;

		return player.position().add(
			fwdX * fwdDist + rightX * rightDist,
			height,
			fwdZ * fwdDist + rightZ * rightDist
		);
	}

	/**
	 * Spawns a crisp 5-spark burst at the casting palm when a stack is gained via [Z].
	 */
	public static void spawnStackGainBurst(Player player) {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null || player == null) {
			return;
		}
		boolean isFirstPerson = client.player == player && client.options.getCameraType() == CameraType.FIRST_PERSON;
		Vec3 palmPos = resolveRightPalmPosition(player, isFirstPerson, false);

		for (int i = 0; i < 5; i++) {
			double vx = (RND.nextDouble() - 0.5) * 0.04;
			double vy = 0.015 + RND.nextDouble() * 0.03;
			double vz = (RND.nextDouble() - 0.5) * 0.04;
			client.level.addParticle(ModParticles.MAGMA_EMBER.get(), palmPos.x, palmPos.y, palmPos.z, vx, vy, vz);
		}
	}

	@SubscribeEvent
	public static void onClientTick(ClientTickEvent.Post event) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.level == null || client.isPaused()) {
			return;
		}

		LocalPlayer player = client.player;
		boolean isBurnout = ClientSpellVisuals.isPlayerInBurnout(player);
		boolean isAiming = ClientSpellVisuals.isLocalAiming();

		if (!isBurnout && !isAiming) {
			return;
		}

		boolean isFirstPerson = client.options.getCameraType() == CameraType.FIRST_PERSON;
		Vec3 handPos = resolveRightPalmPosition(player, isFirstPerson, isAiming);

		if (isBurnout) {
			if (player.tickCount % 4 == 0) {
				double ox = (RND.nextDouble() - 0.5) * 0.06;
				double oy = (RND.nextDouble() - 0.5) * 0.06;
				double oz = (RND.nextDouble() - 0.5) * 0.06;
				client.level.addParticle(ParticleTypes.SMOKE, handPos.x + ox, handPos.y + oy, handPos.z + oz,
					0.0, 0.015 + RND.nextDouble() * 0.012, 0.0);
			}
			return;
		}

		// Shrunk 75% core radius + tiny 0.02f..0.03f sparks ONLY while holding RMB to aim Firebolt
		float charge = ClientSpellVisuals.getAimChargeProgress(1.0f);
		float coreRadius = (0.035f - charge * 0.018f);
		int count = charge >= 0.99f ? 3 : 2;

		MagmaEmberParticle.setMiniAimScale(true);
		try {
			for (int i = 0; i < count; i++) {
				double ox = (RND.nextDouble() - 0.5) * coreRadius;
				double oy = (RND.nextDouble() - 0.5) * coreRadius;
				double oz = (RND.nextDouble() - 0.5) * coreRadius;
				client.level.addParticle(ModParticles.MAGMA_EMBER.get(),
					handPos.x + ox, handPos.y + oy, handPos.z + oz,
					-ox * 0.25, -oy * 0.25 + 0.004, -oz * 0.25);
			}
		} finally {
			MagmaEmberParticle.setMiniAimScale(false);
		}
	}
}
