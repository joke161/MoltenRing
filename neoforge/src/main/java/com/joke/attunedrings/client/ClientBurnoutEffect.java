package com.joke.attunedrings.client;

import com.joke.attunedrings.AttunedRings;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

import java.util.Random;

/**
 * Handles Stage 6 Burnout "cyberpsychosis" instability during the first 8 seconds (160 ticks):
 * periodic +-15 degree random camera twitching, overload audio glitches, and screen edge pulse.
 */
@EventBusSubscriber(modid = AttunedRings.MOD_ID, value = Dist.CLIENT)
public class ClientBurnoutEffect {
	private static final Random RND = new Random();

	private static int instabilityTicks = 0;
	private static int totalDuration = 160;
	private static int nextTwitchDelay = 0;
	private static float yawOffset = 0.0f;
	private static float pitchOffset = 0.0f;
	private static float rollOffset = 0.0f;
	private static float pulseFlash = 0.0f;

	public static void trigger(int durationTicks) {
		instabilityTicks = Math.max(1, durationTicks);
		totalDuration = instabilityTicks;
		nextTwitchDelay = 1;
		pulseFlash = 1.0f;
		ClientSpellQueue.forceClear();
	}

	@SubscribeEvent
	public static void onClientTick(ClientTickEvent.Post event) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.level == null || client.isPaused()) {
			return;
		}

		if (instabilityTicks <= 0) {
			yawOffset = 0.0f;
			pitchOffset = 0.0f;
			rollOffset = 0.0f;
			pulseFlash = 0.0f;
			return;
		}

		instabilityTicks--;
		nextTwitchDelay--;
		pulseFlash *= 0.84f;

		// Smooth decay back toward center between sudden twitches
		yawOffset *= 0.65f;
		pitchOffset *= 0.65f;
		rollOffset *= 0.65f;

		if (nextTwitchDelay <= 0) {
			float fadeFactor = Math.min(1.0f, (float) instabilityTicks / 40.0f);
			// Periodic +-15 degree random camera twitch impulse
			yawOffset = (RND.nextFloat() * 2.0f - 1.0f) * 15.0f * (0.45f + 0.55f * fadeFactor);
			pitchOffset = (RND.nextFloat() * 2.0f - 1.0f) * 15.0f * (0.45f + 0.55f * fadeFactor);
			rollOffset = (RND.nextFloat() * 2.0f - 1.0f) * 6.0f * fadeFactor;
			pulseFlash = 0.9f * fadeFactor;

			nextTwitchDelay = 9 + RND.nextInt(10); // every ~0.45s - 0.95s

			// Overload glitch audio
			client.level.playLocalSound(
				client.player.getX(), client.player.getY(), client.player.getZ(),
				RND.nextBoolean() ? SoundEvents.BEACON_DEACTIVATE : SoundEvents.FIRE_EXTINGUISH,
				SoundSource.PLAYERS,
				0.55f * fadeFactor,
				0.55f + RND.nextFloat() * 1.1f,
				false
			);
		}
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
		if (instabilityTicks <= 0) {
			return;
		}

		event.setYaw(event.getYaw() + yawOffset);
		event.setPitch(event.getPitch() + pitchOffset);
		event.setRoll(event.getRoll() + rollOffset);
	}

	public static void renderOverlay(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
		if (instabilityTicks <= 0) {
			return;
		}

		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.options.hideGui) {
			return;
		}

		int w = guiGraphics.guiWidth();
		int h = guiGraphics.guiHeight();
		float progress = (float) instabilityTicks / Math.max(1, totalDuration);
		float heartbeat = (float) (0.5 + 0.5 * Math.sin(System.currentTimeMillis() * 0.018));
		float intensity = Math.min(1.0f, (0.28f * progress + 0.40f * pulseFlash) * (0.65f + 0.35f * heartbeat));

		int alpha = Math.min(130, Math.max(0, (int) (intensity * 130)));
		if (alpha <= 2) {
			return;
		}

		int edgeColor = (alpha << 24) | 0xFF2800; // Fiery crimson overload pulse
		int thickness = 10;

		// Top, bottom, left, right overload vignette borders
		guiGraphics.fill(0, 0, w, thickness, edgeColor);
		guiGraphics.fill(0, h - thickness, w, h, edgeColor);
		guiGraphics.fill(0, thickness, thickness, h - thickness, edgeColor);
		guiGraphics.fill(w - thickness, thickness, w, h - thickness, edgeColor);
	}
}
