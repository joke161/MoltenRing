package com.joke.attunedrings.client;

import com.joke.attunedrings.magic.ElementType;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Random;

public class ClientHandEffect {
	private static final Random RND = new Random();

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(ClientHandEffect::tick);
	}

	private static void tick(Minecraft client) {
		if (client.player == null || client.level == null || client.isPaused()) {
			return;
		}

		List<ElementType> queue = ClientSpellQueue.getQueue();
		if (queue.isEmpty()) {
			return;
		}

		LocalPlayer player = client.player;
		int stackCount = queue.size(); // 1 to 5

		Vec3 handPos;
		boolean isFirstPerson = client.options.getCameraType() == CameraType.FIRST_PERSON;

		if (isFirstPerson) {
			// Offset along player's right shoulder/hand in camera space
			Vec3 eye = player.getEyePosition();
			Vec3 look = player.getViewVector(1.0f);
			Vec3 up = new Vec3(0, 1, 0);
			Vec3 right = look.cross(up).normalize();
			Vec3 actualUp = right.cross(look).normalize();

			// Hand position in first person: down and right, safely away from crosshair
			handPos = eye.add(look.scale(0.38))
				.add(right.scale(0.32))
				.add(actualUp.scale(-0.20));
		} else {
			// Third person: right hand in world coordinates based on body yaw
			float yaw = player.yBodyRot;
			double rad = Math.toRadians(yaw + 90);
			handPos = player.position().add(Math.cos(rad) * -0.40, 0.85, Math.sin(rad) * -0.40);
		}

		// Spawn particles based on stack count
		int count = Math.min(stackCount, 4);
		for (int i = 0; i < count; i++) {
			double ox = (RND.nextDouble() - 0.5) * 0.08;
			double oy = (RND.nextDouble() - 0.5) * 0.08;
			double oz = (RND.nextDouble() - 0.5) * 0.08;

			double vx = (RND.nextDouble() - 0.5) * 0.01;
			double vy = 0.01 + RND.nextDouble() * 0.02;
			double vz = (RND.nextDouble() - 0.5) * 0.01;

			if (stackCount == 5 && RND.nextFloat() < 0.35f) {
				client.level.addParticle(ParticleTypes.LAVA, handPos.x + ox, handPos.y + oy, handPos.z + oz, vx, vy, vz);
			} else if (stackCount >= 4 && RND.nextFloat() < 0.25f) {
				client.level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, handPos.x + ox, handPos.y + oy, handPos.z + oz, vx, vy, vz);
			} else if (stackCount >= 2 || RND.nextBoolean()) {
				client.level.addParticle(ParticleTypes.FLAME, handPos.x + ox, handPos.y + oy, handPos.z + oz, vx, vy, vz);
			} else {
				client.level.addParticle(ParticleTypes.SMOKE, handPos.x + ox, handPos.y + oy, handPos.z + oz, vx, vy, vz);
			}
		}

		// Subtle crackle sound at high stacks
		if (stackCount >= 4 && player.tickCount % 25 == 0) {
			client.level.playLocalSound(handPos.x, handPos.y, handPos.z,
				SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, 0.25f, 1.2f + RND.nextFloat() * 0.4f, false);
		}
	}
}

