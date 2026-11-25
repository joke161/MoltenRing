package com.joke.attunedrings.client;

import com.joke.attunedrings.AttunedRings;
import com.joke.attunedrings.client.render.PlayerMagmaLayer;
import com.joke.attunedrings.compat.epicfight.EpicFightCompat;
import com.joke.attunedrings.component.RingData;
import com.joke.attunedrings.magic.SpellType;
import com.joke.attunedrings.network.FireboltAimPayload;
import com.joke.attunedrings.registry.ModDataComponents;
import com.joke.attunedrings.registry.ModItems;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Matrix4f;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Coordinates smoothstep 1st/3rd-person spell choreography, primed casting stance,
 * 1-stack Firebolt RMB aiming/overcharge, 3D shockwave rings, damped camera shake,
 * and diegetic magma overlay state resolution.
 */
@EventBusSubscriber(modid = AttunedRings.MOD_ID, value = Dist.CLIENT)
public class ClientSpellVisuals {
	public static final ResourceLocation TEX_STACK_1 = AttunedRings.id("textures/entity/magma_overlay_stack1.png");
	public static final ResourceLocation TEX_STACK_2 = AttunedRings.id("textures/entity/magma_overlay_stack2.png");
	public static final ResourceLocation TEX_STACK_3 = AttunedRings.id("textures/entity/magma_overlay_stack3.png");
	public static final ResourceLocation TEX_STACK_4 = AttunedRings.id("textures/entity/magma_overlay_stack4.png");
	public static final ResourceLocation TEX_STACK_5 = AttunedRings.id("textures/entity/magma_overlay_stack5.png");
	public static final ResourceLocation TEX_BURNOUT = AttunedRings.id("textures/entity/magma_overlay_burnout.png");
	public static final ResourceLocation TEX_SHOCKWAVE = AttunedRings.id("textures/entity/shockwave_ring.png");

	public static final int OVERCHARGE_TICKS = 16;
	public static final int OVERCHARGED_FIREBOLT_COST = 180; // 18% energy

	private static final Map<Integer, Integer> REMOTE_STACKS = new ConcurrentHashMap<>();
	private static final Map<Integer, Boolean> REMOTE_AIMING = new ConcurrentHashMap<>();
	private static final Map<Integer, CastAnimState> ACTIVE_CASTS = new ConcurrentHashMap<>();
	private static final List<ShockwaveRing> SHOCKWAVES = new CopyOnWriteArrayList<>();

	private static int heavyShakeTicks = 0;
	private static int heavyShakeDuration = 1;
	private static float heavyShakeAmplitude = 0.0f;
	private static int pendingShakeDelayTicks = 0;
	private static int pendingShakeTicks = 0;
	private static float pendingShakeAmplitude = 0.0f;

	private static boolean localAiming = false;
	private static int localAimTicks = 0;
	private static int prevLocalAimTicks = 0;

	public static final int CAST_STANCE_HOLD_TICKS = 13; // ~0.65s sustained rapid-cast arm stance
	private static final float BASE_BLOOM_DEG = 1.5f;
	private static final float BLOOM_STEP_DEG = 2.4f;
	private static final float MAX_BLOOM_DEG = 11.5f;
	private static final float BLOOM_DECAY_PER_TICK = 0.175f; // 3.5 deg/sec
	private static float localFireboltBloom = 0.0f;
	private static float prevLocalFireboltBloom = 0.0f;

	private static final class RapidCastState {
		int holdTicks;
		float recoilImpulse;
		float prevRecoilImpulse;
	}

	private static final Map<Integer, RapidCastState> RAPID_CAST_STATES = new ConcurrentHashMap<>();
	private static final Map<Integer, SmoothedArmState> SMOOTHED_3P_STATES = new ConcurrentHashMap<>();
	private static final Map<InteractionHand, SmoothedFirstPersonState> SMOOTHED_1P_STATES = new ConcurrentHashMap<>();

	/**
	 * Per-player continuously smoothed 3rd-person joint targets & blend weights (zero instant snaps).
	 */
	public static final class SmoothedArmState {
		public float rightXRot;
		public float rightYRot;
		public float rightZRot;
		public float leftXRot;
		public float leftYRot;
		public float leftZRot;
		public float chestXDeg;
		public float chestYDeg;
		public float chestZDeg;
		public float blendWeight;
		public float leftBlendWeight;
		public boolean leftFlankCounterBalance;
		private long lastUpdateNs;
	}

	private static final class SmoothedFirstPersonState {
		float tx, ty, tz;
		float rx, ry, rz;
		float blendWeight;
		long lastUpdateNs;
	}

	public record CastAnimState(SpellType spell, long startTimeMs, int durationMs) {
		public float progress(long nowMs) {
			return Mth.clamp((float) (nowMs - startTimeMs) / (float) Math.max(1, durationMs), 0.0f, 1.0f);
		}
	}

	public record MagmaVisualState(ResourceLocation texture, int colorArgb, float pulseAlpha,
								   boolean isBurnout, boolean includeTorso, boolean includeLeftArm) {
	}

	/**
	 * Unified procedural posture in entity-aligned degrees (0 = hanging straight down,
	 * +X = pitch forward/up toward crosshair, +Y = turn inward toward chest for Right arm,
	 * +Z = spread outward away from body). Used identically by Vanilla PlayerModel and Epic Fight Armature.
	 */
	public record ProceduralPose(
		boolean hasRightArm, float rightWeight, float rXDeg, float rYDeg, float rZDeg,
		boolean hasLeftArm, float leftWeight, float lXDeg, float lYDeg, float lZDeg,
		float chestXDeg, float chestYDeg, float chestZDeg
	) {
	}

	private static final class ShockwaveRing {
		final int casterEntityId;
		final Vec3 fallbackCenter;
		final double yOffset;
		final long startTimeMs;
		final int durationMs;
		final float maxRadius;
		final boolean isSupernova;

		ShockwaveRing(int casterEntityId, Vec3 fallbackCenter, double yOffset, long startTimeMs, int durationMs, float maxRadius, boolean isSupernova) {
			this.casterEntityId = casterEntityId;
			this.fallbackCenter = fallbackCenter;
			this.yOffset = yOffset;
			this.startTimeMs = startTimeMs;
			this.durationMs = durationMs;
			this.maxRadius = maxRadius;
			this.isSupernova = isSupernova;
		}
	}

	public static float smoothstep(float t) {
		float c = Mth.clamp(t, 0.0f, 1.0f);
		return c * c * (3.0f - 2.0f * c);
	}

	public static float easeOutCubic(float t) {
		float c = 1.0f - Mth.clamp(t, 0.0f, 1.0f);
		return 1.0f - c * c * c;
	}

	public static float envelope(float u, float riseEnd, float fallStart) {
		if (u <= 0.0f || u >= 1.0f) return 0.0f;
		if (u < riseEnd) return smoothstep(u / Math.max(0.001f, riseEnd));
		if (u > fallStart) return smoothstep((1.0f - u) / Math.max(0.001f, 1.0f - fallStart));
		return 1.0f;
	}

	public static void updateStackCount(int entityId, int stackCount) {
		if (stackCount <= 0) {
			REMOTE_STACKS.remove(entityId);
		} else {
			REMOTE_STACKS.put(entityId, Math.min(5, stackCount));
			RapidCastState st = RAPID_CAST_STATES.computeIfAbsent(entityId, id -> new RapidCastState());
			st.holdTicks = CAST_STANCE_HOLD_TICKS;
		}
	}

	public static void onLocalStackPrimed(Player player) {
		if (player != null) {
			RapidCastState st = RAPID_CAST_STATES.computeIfAbsent(player.getId(), id -> new RapidCastState());
			st.holdTicks = CAST_STANCE_HOLD_TICKS;
		}
	}

	private static boolean hasCastStanceHold(Player player) {
		RapidCastState st = RAPID_CAST_STATES.get(player.getId());
		return st != null && st.holdTicks > 0;
	}

	private static float getLerpedRecoil(Player player, float partialTick) {
		RapidCastState st = RAPID_CAST_STATES.get(player.getId());
		if (st == null) {
			return 0.0f;
		}
		return Mth.lerp(partialTick, st.prevRecoilImpulse, st.recoilImpulse);
	}

	public static void setRemoteAiming(int entityId, boolean aiming) {
		if (!aiming) {
			REMOTE_AIMING.remove(entityId);
		} else {
			REMOTE_AIMING.put(entityId, Boolean.TRUE);
		}
	}

	public static boolean isLocalAiming() {
		return localAiming;
	}

	public static float getAimChargeProgress(float partialTick) {
		if (!localAiming) {
			return 0.0f;
		}
		float lerped = Mth.lerp(partialTick, prevLocalAimTicks, localAimTicks);
		return Mth.clamp(lerped / (float) OVERCHARGE_TICKS, 0.0f, 1.0f);
	}

	public static boolean isAimingFirebolt(Player player) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null && player.getId() == mc.player.getId()) {
			return localAiming;
		}
		return REMOTE_AIMING.getOrDefault(player.getId(), Boolean.FALSE);
	}

	public static int getStackCount(Player player) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null && player.getId() == mc.player.getId()) {
			return ClientSpellQueue.getQueue().size();
		}
		return REMOTE_STACKS.getOrDefault(player.getId(), 0);
	}

	public static void triggerCastVisual(int entityId, SpellType spell, int durationTicks) {
		long now = System.currentTimeMillis();
		RapidCastState rapid = RAPID_CAST_STATES.computeIfAbsent(entityId, id -> new RapidCastState());
		boolean armAlreadyRaised = rapid.holdTicks > 0 || ACTIVE_CASTS.containsKey(entityId);
		rapid.holdTicks = CAST_STANCE_HOLD_TICKS;

		Minecraft mc = Minecraft.getInstance();
		if (spell == SpellType.FIRE_1) {
			if (mc.player != null && entityId == mc.player.getId()) {
				if (durationTicks == 12) {
					localFireboltBloom = 0.0f; // Overcharged sniper resets quick-fire bloom
				} else {
					localFireboltBloom = Math.min(
						MAX_BLOOM_DEG,
						(localFireboltBloom <= 0.01f ? BASE_BLOOM_DEG : localFireboltBloom) + BLOOM_STEP_DEG
					);
				}
			}

			if (armAlreadyRaised) {
				// Sustain raised channeling stance and trigger crisp additive recoil instead of restarting lift
				ACTIVE_CASTS.remove(entityId);
				rapid.recoilImpulse = 1.0f;
				rapid.prevRecoilImpulse = 1.0f;
			} else {
				ACTIVE_CASTS.put(entityId, new CastAnimState(spell, now, durationTicks * 50));
				rapid.recoilImpulse = 0.55f;
				rapid.prevRecoilImpulse = 0.55f;
			}
		} else {
			ACTIVE_CASTS.put(entityId, new CastAnimState(spell, now, durationTicks * 50));
		}

		if (mc.level != null && mc.level.getEntity(entityId) instanceof Player caster) {
			boolean battleMode = EpicFightCompat.isBattleMode(caster);
			if (spell == SpellType.FIRE_4) {
				long impactDelayMs = battleMode ? 600L : 0L; // Tick 12 impact in Epic Fight Battle Mode
				SHOCKWAVES.add(new ShockwaveRing(entityId, caster.position().add(0, 0.15, 0), 0.15, now + impactDelayMs, 800, 5.2f, false));
			} else if (spell == SpellType.FIRE_5) {
				long slamDelayMs = battleMode ? 500L : 350L;
				SHOCKWAVES.add(new ShockwaveRing(entityId, caster.position().add(0, 0.15, 0), 0.15, now + slamDelayMs, 1050, 8.6f, true));
			}

			if (mc.player != null && entityId == mc.player.getId()) {
				if (spell == SpellType.FIRE_4) {
					if (battleMode) {
						pendingShakeDelayTicks = 12;
						pendingShakeTicks = 12;
						pendingShakeAmplitude = 2.6f;
					} else {
						heavyShakeDuration = 12;
						heavyShakeTicks = 12;
						heavyShakeAmplitude = 2.4f;
					}
				} else if (spell == SpellType.FIRE_5) {
					if (battleMode) {
						pendingShakeDelayTicks = 10;
						pendingShakeTicks = 22;
						pendingShakeAmplitude = 4.6f;
					} else {
						heavyShakeDuration = 22;
						heavyShakeTicks = 22;
						heavyShakeAmplitude = 4.6f;
					}
				}
			}
		}
	}

	public static CastAnimState getActiveCast(Player player) {
		CastAnimState state = ACTIVE_CASTS.get(player.getId());
		if (state == null) {
			return null;
		}
		if (System.currentTimeMillis() - state.startTimeMs() >= state.durationMs()) {
			ACTIVE_CASTS.remove(player.getId());
			return null;
		}
		return state;
	}

	public static boolean isPlayerInBurnout(Player player) {
		if (player.isCreative()) {
			return false;
		}
		ItemStack moltenRing = findPlayerMoltenRingClient(player);
		if (!moltenRing.isEmpty()) {
			RingData data = moltenRing.getOrDefault(ModDataComponents.RING_DATA.get(), RingData.DEFAULT);
			return data.penaltyCooldown() > 0;
		}
		return false;
	}

	public static MagmaVisualState resolvePlayerMagmaState(Player player) {
		ItemStack moltenRing = findPlayerMoltenRingClient(player);
		if (moltenRing.isEmpty()) {
			return null;
		}

		// Stage 6 Burnout takes priority (bypassed in Creative mode)
		if (!player.isCreative()) {
			RingData data = moltenRing.getOrDefault(ModDataComponents.RING_DATA.get(), RingData.DEFAULT);
			if (data.penaltyCooldown() > 0) {
				float cooldownRatio = Mth.clamp((float) data.penaltyCooldown() / (float) RingData.PENALTY_COOLDOWN, 0.0f, 1.0f);
				float alpha = smoothstep(cooldownRatio) * 0.85f;
				if (alpha > 0.02f) {
					int argb = FastColor.ARGB32.color((int) (alpha * 255), 255, 255, 255);
					return new MagmaVisualState(TEX_BURNOUT, argb, alpha, true, true, false);
				}
				return null;
			}
		}

		int stacks = getStackCount(player);
		if (stacks > 0) {
			float time = (System.currentTimeMillis() % 100000L) * 0.004f;
			float pulse = (float) (0.5 + 0.5 * Math.sin(time * (stacks >= 5 ? 3.5f : 1.8f)));

			ResourceLocation tex = switch (stacks) {
				case 1 -> TEX_STACK_1;
				case 2 -> TEX_STACK_2;
				case 3 -> TEX_STACK_3;
				case 4 -> TEX_STACK_4;
				default -> TEX_STACK_5;
			};
			float alpha = 1.0f;
			boolean includeTorso = stacks >= 3;
			boolean includeLeftArm = stacks >= 5;

			float green = stacks == 5 ? (0.90f + 0.10f * pulse) : 1.0f;
			int argb = FastColor.ARGB32.color(255, 255, (int) (green * 255), 255);
			return new MagmaVisualState(tex, argb, alpha, false, includeTorso, includeLeftArm);
		}
		return null;
	}

	public static ItemStack findPlayerMoltenRingClient(Player player) {
		var curioOpt = CuriosApi.getCuriosInventory(player)
			.flatMap(inv -> inv.findFirstCurio(ModItems.MOLTEN_RING.get()));
		if (curioOpt.isPresent()) {
			return curioOpt.get().stack();
		}
		if (player.getMainHandItem().is(ModItems.MOLTEN_RING.get())) return player.getMainHandItem();
		if (player.getOffhandItem().is(ModItems.MOLTEN_RING.get())) return player.getOffhandItem();
		return ItemStack.EMPTY;
	}

	@SubscribeEvent
	public static void onClientTick(ClientTickEvent.Post event) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.isPaused()) {
			return;
		}
		prevLocalAimTicks = localAimTicks;
		prevLocalFireboltBloom = localFireboltBloom;
		if (localFireboltBloom > 0.0f) {
			localFireboltBloom = Math.max(0.0f, localFireboltBloom - BLOOM_DECAY_PER_TICK);
		}

		Iterator<Map.Entry<Integer, RapidCastState>> rapidIt = RAPID_CAST_STATES.entrySet().iterator();
		while (rapidIt.hasNext()) {
			Map.Entry<Integer, RapidCastState> entry = rapidIt.next();
			RapidCastState st = entry.getValue();
			st.prevRecoilImpulse = st.recoilImpulse;
			st.recoilImpulse = Math.max(0.0f, st.recoilImpulse - 0.24f);
			if (!ACTIVE_CASTS.containsKey(entry.getKey()) && st.holdTicks > 0) {
				st.holdTicks--;
			}
			if (st.holdTicks <= 0 && st.recoilImpulse <= 0.001f && st.prevRecoilImpulse <= 0.001f) {
				rapidIt.remove();
			}
		}

		if (mc.player != null && mc.level != null) {
			ItemStack ring = findPlayerMoltenRingClient(mc.player);
			if (ring.isEmpty() || mc.player.isInWaterOrBubble()) {
				if (!ClientSpellQueue.getQueue().isEmpty()) {
					ClientSpellQueue.forceClear();
				}
				if (localAiming) {
					localAiming = false;
					localAimTicks = 0;
					prevLocalAimTicks = 0;
					PacketDistributor.sendToServer(new FireboltAimPayload(0, false));
				}
				ACTIVE_CASTS.remove(mc.player.getId());
				RAPID_CAST_STATES.remove(mc.player.getId());
				localFireboltBloom = 0.0f;
				prevLocalFireboltBloom = 0.0f;
			} else {
				boolean hasOneStack = ClientSpellQueue.getQueue().size() == 1;
				boolean rmbEligible = isRmbAimEligible(mc.player);
				RingData ringData = ring.getOrDefault(ModDataComponents.RING_DATA.get(), RingData.DEFAULT);
				boolean ringReadyForSniper = mc.player.isCreative() || (ringData.penaltyCooldown() <= 0 && ringData.energy() >= OVERCHARGED_FIREBOLT_COST);
				boolean rmbDown = EpicFightCompat.isUseKeyHeld(mc.options.keyUse.isDown());
				boolean canAim = mc.screen == null && hasOneStack && rmbEligible && ringReadyForSniper && getActiveCast(mc.player) == null && EpicFightCompat.canCastSpell(mc.player);
				boolean wantAim = canAim && rmbDown;

				if (!localAiming && wantAim) {
					localAiming = true;
					localAimTicks = 0;
					prevLocalAimTicks = 0;
					PacketDistributor.sendToServer(new FireboltAimPayload(0, true));
					mc.level.playLocalSound(mc.player.getX(), mc.player.getY() + 1.2, mc.player.getZ(),
						SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.35f, 0.78f, false);
				} else if (localAiming && wantAim) {
					localAimTicks++;
					if (localAimTicks == 4 || localAimTicks == 9 || localAimTicks == 13) {
						float pitch = 0.90f + (localAimTicks / (float) OVERCHARGE_TICKS) * 0.75f;
						mc.level.playLocalSound(mc.player.getX(), mc.player.getY() + 1.2, mc.player.getZ(),
							SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.25f, pitch, false);
					} else if (localAimTicks == OVERCHARGE_TICKS) {
						mc.level.playLocalSound(mc.player.getX(), mc.player.getY() + 1.2, mc.player.getZ(),
							SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.35f, 1.90f, false);
						mc.level.playLocalSound(mc.player.getX(), mc.player.getY() + 1.2, mc.player.getZ(),
							SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.35f, 1.65f, false);
					}
				} else if (localAiming) {
					boolean releasedAfterFullCharge = !rmbDown && canAim && localAimTicks >= OVERCHARGE_TICKS;
					localAiming = false;
					localAimTicks = 0;
					prevLocalAimTicks = 0;
					PacketDistributor.sendToServer(new FireboltAimPayload(0, false));
					if (releasedAfterFullCharge) {
						ClientSpellQueue.castOverchargedFirebolt(mc.player);
					}
				}
			}
		} else {
			localAiming = false;
			localAimTicks = 0;
			localFireboltBloom = 0.0f;
			prevLocalFireboltBloom = 0.0f;
			RAPID_CAST_STATES.clear();
			SMOOTHED_3P_STATES.clear();
			SMOOTHED_1P_STATES.clear();
		}

		if (pendingShakeDelayTicks > 0) {
			pendingShakeDelayTicks--;
			if (pendingShakeDelayTicks == 0 && pendingShakeTicks > 0) {
				heavyShakeDuration = pendingShakeTicks;
				heavyShakeTicks = pendingShakeTicks;
				heavyShakeAmplitude = pendingShakeAmplitude;
				pendingShakeTicks = 0;
			}
		}
		if (heavyShakeTicks > 0) {
			heavyShakeTicks--;
		}
		long now = System.currentTimeMillis();
		Iterator<ShockwaveRing> it = SHOCKWAVES.iterator();
		while (it.hasNext()) {
			ShockwaveRing ring = it.next();
			if (now - ring.startTimeMs > ring.durationMs) {
				SHOCKWAVES.remove(ring);
			}
		}
	}

	public static boolean isRmbAimEligible(Player player) {
		if (player == null) {
			return false;
		}
		return !isRmbBlockingItem(player.getMainHandItem(), true)
			&& !isRmbBlockingItem(player.getOffhandItem(), false);
	}

	private static boolean isRmbBlockingItem(ItemStack stack, boolean mainHand) {
		if (stack.isEmpty() || stack.is(ModItems.MOLTEN_RING.get())) {
			return false;
		}
		if (stack.getItem() instanceof ShieldItem) {
			return true;
		}
		if (mainHand && stack.getItem() instanceof BlockItem) {
			return true;
		}
		return stack.getUseAnimation() != UseAnim.NONE;
	}

	public static boolean shouldSuppressHeldMainHandItem(Player player) {
		if (player == null || isPlayerInBurnout(player) || findPlayerMoltenRingClient(player).isEmpty()) {
			return false;
		}
		if (player.getMainHandItem().isEmpty()) {
			return false;
		}
		return hasCastStanceHold(player) || getActiveCast(player) != null || isAimingFirebolt(player);
	}

	@SubscribeEvent
	public static void onInteractionTriggered(InputEvent.InteractionKeyMappingTriggered event) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null && event.isUseItem() && localAiming && localAimTicks >= 2 && isRmbAimEligible(mc.player)) {
			event.setCanceled(true);
			event.setSwingHand(false);
		}
	}

	private static boolean shouldShowDynamicCrosshair(Minecraft mc, DeltaTracker deltaTracker) {
		if (mc.player == null || mc.options.hideGui || !mc.options.getCameraType().isFirstPerson()) {
			return false;
		}
		if (findPlayerMoltenRingClient(mc.player).isEmpty()) {
			return false;
		}
		if (localAiming) {
			return true;
		}
		float partial = deltaTracker.getGameTimeDeltaPartialTick(true);
		float bloom = Mth.lerp(partial, prevLocalFireboltBloom, localFireboltBloom);
		return bloom > 1.55f;
	}

	@SubscribeEvent
	public static void onRenderGuiLayerPre(RenderGuiLayerEvent.Pre event) {
		if (!VanillaGuiLayers.CROSSHAIR.equals(event.getName())) {
			return;
		}
		if (shouldShowDynamicCrosshair(Minecraft.getInstance(), event.getPartialTick())) {
			event.setCanceled(true);
		}
	}

	public static void renderCrosshairReticle(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		if (!shouldShowDynamicCrosshair(mc, deltaTracker)) {
			return;
		}

		// Vanilla draws its 15x15 crosshair at (w - 15) / 2, placing the 1x1 center pixel at offset +7
		int cx = (guiGraphics.guiWidth() - 15) / 2 + 7;
		int cy = (guiGraphics.guiHeight() - 15) / 2 + 7;
		float partial = deltaTracker.getGameTimeDeltaPartialTick(true);

		if (localAiming) {
			float charge = getAimChargeProgress(partial);
			boolean ready = charge >= 0.999f || localAimTicks >= OVERCHARGE_TICKS;
			if (ready) {
				// Fully charged sniper lock: compact 7x7 cross in bright gold
				RenderSystem.enableBlend();
				RenderSystem.defaultBlendFunc();
				int gold = 0xFFFFD600;
				guiGraphics.fill(cx - 3, cy, cx + 4, cy + 1, gold);
				guiGraphics.fill(cx, cy - 3, cx + 1, cy, gold);
				guiGraphics.fill(cx, cy + 1, cx + 1, cy + 4, gold);
				return;
			}

			int gap = Math.round(Mth.lerp(charge, 6.0f, 0.0f));
			int armLen = Math.round(Mth.lerp(charge, 5.0f, 3.0f));
			drawInverseContrastArms(guiGraphics, cx, cy, gap, armLen, gap == 0);
		} else {
			float bloom = Mth.lerp(partial, prevLocalFireboltBloom, localFireboltBloom);
			float t = Mth.clamp((bloom - BASE_BLOOM_DEG) / (MAX_BLOOM_DEG - BASE_BLOOM_DEG), 0.0f, 1.0f);
			int gap = Math.round(Mth.lerp(t, 0.0f, 8.0f));
			int armLen = 4;
			drawInverseContrastArms(guiGraphics, cx, cy, gap, armLen, true);
		}
	}

	private static void drawInverseContrastArms(GuiGraphics gg, int cx, int cy, int gap, int armLen, boolean drawCenterDot) {
		// Flush queued GUI quads first so RenderType.gui() cannot override our inverse blend state
		gg.flush();
		RenderSystem.enableBlend();
		RenderSystem.blendFuncSeparate(
			GlStateManager.SourceFactor.ONE_MINUS_DST_COLOR,
			GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR,
			GlStateManager.SourceFactor.ONE,
			GlStateManager.DestFactor.ZERO
		);
		RenderSystem.setShader(GameRenderer::getPositionColorShader);
		try {
			Matrix4f mat = gg.pose().last().pose();
			BufferBuilder buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
			if (drawCenterDot) {
				fillInverseQuad(buf, mat, cx, cy, cx + 1, cy + 1);
			}
			fillInverseQuad(buf, mat, cx, cy - gap - armLen, cx + 1, cy - gap);
			fillInverseQuad(buf, mat, cx, cy + 1 + gap, cx + 1, cy + 1 + gap + armLen);
			fillInverseQuad(buf, mat, cx - gap - armLen, cy, cx - gap, cy + 1);
			fillInverseQuad(buf, mat, cx + 1 + gap, cy, cx + 1 + gap + armLen, cy + 1);
			BufferUploader.drawWithShader(buf.buildOrThrow());
		} finally {
			RenderSystem.defaultBlendFunc();
		}
	}

	private static void fillInverseQuad(BufferBuilder buf, Matrix4f mat, int x0, int y0, int x1, int y1) {
		buf.addVertex(mat, (float) x0, (float) y0, 0.0F).setColor(255, 255, 255, 255);
		buf.addVertex(mat, (float) x0, (float) y1, 0.0F).setColor(255, 255, 255, 255);
		buf.addVertex(mat, (float) x1, (float) y1, 0.0F).setColor(255, 255, 255, 255);
		buf.addVertex(mat, (float) x1, (float) y0, 0.0F).setColor(255, 255, 255, 255);
	}

	@SubscribeEvent(priority = EventPriority.HIGH)
	public static void onRenderHandOverride(RenderHandEvent event) {
		if (event.getHand() != InteractionHand.MAIN_HAND) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null && shouldSuppressHeldMainHandItem(mc.player)) {
			// Cancel Vanilla & Epic Fight MAIN_HAND item pass so onRenderHandFallback draws the cast arm instead
			event.setCanceled(true);
		}
	}

	@SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
	public static void onRenderHandFallback(RenderHandEvent event) {
		if (!event.isCanceled() || event.getHand() != InteractionHand.MAIN_HAND) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		AbstractClientPlayer player = mc.player;
		if (player == null || player.isInvisible()) {
			return;
		}
		boolean suppressHeld = shouldSuppressHeldMainHandItem(player);
		if (!player.getMainHandItem().isEmpty() && !suppressHeld) {
			return;
		}
		if ((!suppressHeld && !shouldShowFirstPersonCastArm(player)) || EpicFightCompat.consumeFirstPersonRenderedThisPass()) {
			return;
		}
		if (!(mc.getEntityRenderDispatcher().getRenderer(player) instanceof PlayerRenderer playerRenderer)) {
			return;
		}

		// Explicitly render the right casting arm + normal-extruded magma veins (empty-hand Epic Fight sprint OR suppressed MainHand item)
		PoseStack poseStack = event.getPoseStack();
		MultiBufferSource bufferSource = event.getMultiBufferSource();
		int light = event.getPackedLight();
		float equipProgress = suppressHeld ? 0.0f : event.getEquipProgress();
		float swingProgress = event.getSwingProgress();

		poseStack.pushPose();
		applyFirstPersonTransform(player, poseStack, InteractionHand.MAIN_HAND, event.getPartialTick(), event.getInterpolatedPitch());

		float f = Mth.sqrt(swingProgress);
		float f1 = -0.3F * Mth.sin(f * (float) Math.PI);
		float f2 = 0.4F * Mth.sin(f * ((float) Math.PI * 2.0F));
		float f3 = -0.4F * Mth.sin(swingProgress * (float) Math.PI);
		poseStack.translate(f1 + 0.64000005F, f2 - 0.6F + equipProgress * -0.6F, f3 - 0.71999997F);
		poseStack.mulPose(Axis.YP.rotationDegrees(45.0F));
		float f5 = Mth.sin(swingProgress * swingProgress * (float) Math.PI);
		float f6 = Mth.sin(f * (float) Math.PI);
		poseStack.mulPose(Axis.YP.rotationDegrees(f6 * 70.0F));
		poseStack.mulPose(Axis.ZP.rotationDegrees(f5 * -20.0F));
		poseStack.translate(-1.0F, 3.6F, 3.5F);
		poseStack.mulPose(Axis.ZP.rotationDegrees(120.0F));
		poseStack.mulPose(Axis.XP.rotationDegrees(200.0F));
		poseStack.mulPose(Axis.YP.rotationDegrees(-135.0F));
		poseStack.translate(5.6F, 0.0F, 0.0F);

		playerRenderer.renderRightHand(poseStack, bufferSource, light, player);
		PlayerMagmaLayer.renderFirstPersonOverlay(player, poseStack, bufferSource, light);
		poseStack.popPose();
	}

	@SubscribeEvent
	public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
		if (heavyShakeTicks <= 0) {
			return;
		}
		float progress = (float) heavyShakeTicks / Math.max(1, heavyShakeDuration);
		float damp = progress * progress;
		double t = System.currentTimeMillis() * 0.055;
		float shakeYaw = (float) Math.sin(t * 1.3) * heavyShakeAmplitude * damp;
		float shakePitch = (float) Math.cos(t * 1.7) * heavyShakeAmplitude * damp;
		float shakeRoll = (float) Math.sin(t * 2.1) * (heavyShakeAmplitude * 0.45f) * damp;

		event.setYaw(event.getYaw() + shakeYaw);
		event.setPitch(event.getPitch() + shakePitch);
		event.setRoll(event.getRoll() + shakeRoll);
	}

	public static boolean shouldShowFirstPersonCastArm(Player player) {
		if (isPlayerInBurnout(player) || findPlayerMoltenRingClient(player).isEmpty()) {
			return false;
		}
		if (getStackCount(player) > 0 || isAimingFirebolt(player) || getActiveCast(player) != null || hasCastStanceHold(player)) {
			return true;
		}
		SmoothedFirstPersonState fp = SMOOTHED_1P_STATES.get(InteractionHand.MAIN_HAND);
		return fp != null && fp.blendWeight > 0.01f;
	}

	private static float computeSpellEnvelope(CastAnimState state, long nowMs) {
		float durationTicks = Math.max(8.0f, state.durationMs() / 50.0f);
		float elapsedTicks = Mth.clamp((nowMs - state.startTimeMs()) / 50.0f, 0.0f, durationTicks);
		float fadeIn = smoothstep(elapsedTicks / 3.0f);
		float fadeOut = smoothstep((durationTicks - elapsedTicks) / 5.0f);
		return Math.min(fadeIn, fadeOut);
	}

	/**
	 * Smoothly interpolated first-person PoseStack choreography strictly bounded inside
	 * the lower-right viewport: x in [-0.12, 0.05], y in [-0.08, 0.10], z in [-0.18, 0.05], rot in +/-30 deg.
	 */
	public static void applyFirstPersonTransform(Player player, PoseStack poseStack, InteractionHand hand, float partialTick, float pitch) {
		SmoothedFirstPersonState fp = SMOOTHED_1P_STATES.computeIfAbsent(hand, h -> new SmoothedFirstPersonState());
		long nowNs = System.nanoTime();
		if (fp.lastUpdateNs == 0L || nowNs - fp.lastUpdateNs >= 1_000_000L) {
			float deltaSeconds = fp.lastUpdateNs == 0L ? 0.016f : Mth.clamp((nowNs - fp.lastUpdateNs) * 1e-9f, 0.001f, 0.05f);
			fp.lastUpdateNs = nowNs;
			float recoil = hand == InteractionHand.MAIN_HAND ? getLerpedRecoil(player, partialTick) : 0.0f;
			float speed = recoil > 0.02f ? 28.0f : 16.0f;
			float alpha = 1.0f - (float) Math.exp(-deltaSeconds * speed);

			float tX = 0.0f, tY = 0.0f, tZ = 0.0f;
			float rX = 0.0f, rY = 0.0f, rZ = 0.0f;
			float targetWeight = 0.0f;

			boolean isMain = hand == InteractionHand.MAIN_HAND;
			boolean hasRing = !findPlayerMoltenRingClient(player).isEmpty();
			CastAnimState state = getActiveCast(player);
			if (state == null) {
				if (hasRing && !isPlayerInBurnout(player)) {
					if (isMain) {
						if (isAimingFirebolt(player)) {
							float charge = getAimChargeProgress(partialTick);
							float pitchTilt = Mth.clamp(pitch, -65.0f, 65.0f) * 0.16f;
							tX = -0.09f;
							tY = 0.045f + charge * 0.015f;
							tZ = -0.14f;
							rX = -16.0f + pitchTilt;
							rY = 10.0f;
							targetWeight = 1.0f;
						} else if (hasCastStanceHold(player)) {
							// Sustained forward rapid-cast channeling stance + crisp additive recoil impulse
							tX = -0.09f;
							tY = 0.05f + recoil * 0.045f;
							tZ = -0.15f + recoil * 0.09f;
							rX = -18.0f - recoil * 12.0f;
							rY = 10.0f;
							targetWeight = 1.0f;
						} else if (getStackCount(player) > 0) {
							tX = 0.0f;
							tY = 0.03f;
							tZ = -0.04f;
							rX = -4.0f;
							targetWeight = 1.0f;
						}
					}
				}
			} else {
				long nowMs = System.currentTimeMillis();
				float u = state.progress(nowMs);
				float env = computeSpellEnvelope(state, nowMs);
				float side = isMain ? 1.0f : -1.0f;

				switch (state.spell()) {
					case FIRE_1 -> {
						if (isMain) {
							if (u < 0.25f) {
								float lift = smoothstep(u / 0.25f);
								tX = -0.12f * lift;
								tY = 0.06f * lift + recoil * 0.03f;
								tZ = -0.18f * lift + recoil * 0.04f;
								rX = -24.0f * lift - recoil * 6.0f;
								rY = 12.0f * lift;
								targetWeight = Math.max(env, lift);
							} else {
								float settle = smoothstep((u - 0.25f) / 0.55f);
								tX = Mth.lerp(settle, -0.12f, -0.09f);
								tY = Mth.lerp(settle, 0.06f, 0.05f) + recoil * 0.045f;
								tZ = Mth.lerp(settle, -0.18f, -0.15f) + recoil * 0.09f;
								rX = Mth.lerp(settle, -24.0f, -18.0f) - recoil * 12.0f;
								rY = Mth.lerp(settle, 12.0f, 10.0f);
								targetWeight = 1.0f;
							}
						}
					}
					case FIRE_2 -> {
						if (isMain) {
							float rhythm = (float) Math.sin(u * Math.PI * 14.0f) * 0.022f;
							tX = -0.09f + rhythm * 0.45f;
							tY = 0.07f + Math.abs(rhythm) * 0.6f;
							tZ = -0.15f + rhythm;
							rX = -20.0f - rhythm * 160.0f;
							rY = 10.0f;
							targetWeight = env;
						}
					}
					case FIRE_3 -> {
						if (isMain) {
							if (u < 0.30f) {
								float w = smoothstep(u / 0.30f);
								tX = 0.05f * w;
								tY = -0.05f * w;
								tZ = -0.09f * w;
								rX = -10.0f * w;
								rY = -20.0f * w;
							} else {
								float s = smoothstep((u - 0.30f) / 0.42f);
								tX = Mth.lerp(s, 0.05f, -0.12f);
								tY = Mth.lerp(s, -0.05f, -0.08f);
								tZ = Mth.lerp(s, -0.09f, -0.16f);
								rX = -16.0f;
								rY = Mth.lerp(s, -20.0f, 26.0f);
								rZ = 14.0f * s;
							}
							targetWeight = env;
						}
					}
					case FIRE_4 -> {
						// Flame Bastion (28 ticks): 0..12 gather (u < 0.43), 12..18 outward thrust & hold (0.43..0.643), 18..28 return
						if (u < 0.43f) {
							float cross = smoothstep(u / 0.43f);
							tX = -0.12f * side * cross;
							tY = 0.05f * cross;
							tZ = -0.11f * cross;
							rY = 26.0f * side * cross;
							rZ = 16.0f * side * cross;
							targetWeight = env;
						} else if (u < 0.643f) {
							float push = smoothstep((u - 0.43f) / 0.075f);
							tX = Mth.lerp(push, -0.12f, 0.05f) * side;
							tY = 0.05f;
							tZ = Mth.lerp(push, -0.11f, -0.18f);
							rX = -22.0f * push;
							rY = Mth.lerp(push, 26.0f, -22.0f) * side;
							rZ = Mth.lerp(push, 16.0f, -6.0f) * side;
							targetWeight = env;
						} else {
							float back = 1.0f - smoothstep((u - 0.643f) / 0.357f);
							tX = 0.05f * side * back;
							tY = 0.05f * back;
							tZ = -0.18f * back;
							rX = -22.0f * back;
							rY = -22.0f * side * back;
							rZ = -6.0f * side * back;
							targetWeight = env;
						}
					}
					case FIRE_5 -> {
						if (u < 0.385f) {
							float gather = smoothstep(u / 0.385f);
							tX = -0.07f * side * gather;
							tY = 0.10f * gather;
							tZ = -0.11f * gather;
							rX = 22.0f * gather;
							rZ = -14.0f * side * gather;
						} else {
							float slam = smoothstep((u - 0.385f) / 0.155f);
							tX = Mth.lerp(slam, -0.07f, -0.04f) * side;
							tY = Mth.lerp(slam, 0.10f, -0.08f);
							tZ = Mth.lerp(slam, -0.11f, -0.18f);
							rX = Mth.lerp(slam, 22.0f, -30.0f);
							rZ = Mth.lerp(slam, -14.0f, 0.0f) * side;
						}
						targetWeight = env;
					}
				}
			}

			// Enforce strict visibility bounds inside the lower-right viewport
			tX = Mth.clamp(tX * targetWeight, -0.12f, 0.05f);
			tY = Mth.clamp(tY * targetWeight, -0.08f, 0.10f);
			tZ = Mth.clamp(tZ * targetWeight, -0.18f, 0.05f);
			rX = Mth.clamp(rX * targetWeight, -30.0f, 30.0f);
			rY = Mth.clamp(rY * targetWeight, -30.0f, 30.0f);
			rZ = Mth.clamp(rZ * targetWeight, -30.0f, 30.0f);

			fp.tx = Mth.lerp(alpha, fp.tx, tX);
			fp.ty = Mth.lerp(alpha, fp.ty, tY);
			fp.tz = Mth.lerp(alpha, fp.tz, tZ);
			fp.rx = Mth.lerp(alpha, fp.rx, rX);
			fp.ry = Mth.lerp(alpha, fp.ry, rY);
			fp.rz = Mth.lerp(alpha, fp.rz, rZ);
			fp.blendWeight = Mth.lerp(alpha, fp.blendWeight, targetWeight);
		}

		if (fp.blendWeight > 0.004f || Math.abs(fp.tx) > 0.001f || Math.abs(fp.ty) > 0.001f || Math.abs(fp.tz) > 0.001f) {
			poseStack.translate(
				Mth.clamp(fp.tx, -0.12f, 0.05f),
				Mth.clamp(fp.ty, -0.08f, 0.10f),
				Mth.clamp(fp.tz, -0.18f, 0.05f)
			);
			poseStack.mulPose(Axis.XP.rotationDegrees(Mth.clamp(fp.rx, -30.0f, 30.0f)));
			poseStack.mulPose(Axis.YP.rotationDegrees(Mth.clamp(fp.ry, -30.0f, 30.0f)));
			poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.clamp(fp.rz, -30.0f, 30.0f)));
		}
	}

	/**
	 * Computes unified procedural arm and torso keyframes with 3-tick fade-in & 5-tick fade-out
	 * shared across Vanilla PlayerModel and Epic Fight HumanoidArmature.
	 */
	public static ProceduralPose computeProceduralPose(Player player, float partialTick) {
		CastAnimState state = getActiveCast(player);
		if (state == null) {
			if (isPlayerInBurnout(player) || findPlayerMoltenRingClient(player).isEmpty()) {
				return null;
			}
			if (isAimingFirebolt(player)) {
				float pitchDeg = Mth.clamp(player.getViewXRot(partialTick), -85.0f, 85.0f);
				return new ProceduralPose(
					true, 1.0f, 90.0f - pitchDeg, 0.0f, 0.0f,
					false, 0.0f, 0.0f, 0.0f, 0.0f,
					0.0f, 0.0f, 0.0f
				);
			}
			if (hasCastStanceHold(player)) {
				float pitchDeg = Mth.clamp(player.getViewXRot(partialTick), -75.0f, 75.0f) * 0.55f;
				float recoil = getLerpedRecoil(player, partialTick);
				return new ProceduralPose(
					true, 1.0f, 90.0f - pitchDeg + recoil * 18.0f, 6.0f, 0.0f,
					false, 0.0f, 0.0f, 0.0f, 0.0f,
					6.0f + recoil * 4.0f, 0.0f, 0.0f
				);
			}
			int stacks = getStackCount(player);
			if (stacks > 0) {
				// Subtle +17.2 deg (0.30 rad) forward poise without twisting across chest
				return new ProceduralPose(
					true, 0.72f, (float) Math.toDegrees(0.30f), 0.0f, 0.0f,
					false, 0.0f, 0.0f, 0.0f, 0.0f,
					0.0f, 0.0f, 0.0f
				);
			}
			return null;
		}

		long nowMs = System.currentTimeMillis();
		float u = state.progress(nowMs);
		float env = computeSpellEnvelope(state, nowMs);

		switch (state.spell()) {
			case FIRE_1 -> {
				// Cold cast raises smoothly then settles seamlessly into sustained channeling hold (zero idle dip)
				float pitchFull = Mth.clamp(player.getViewXRot(partialTick), -75.0f, 75.0f);
				float holdTargetDeg = 90.0f - pitchFull * 0.55f;
				float recoil = getLerpedRecoil(player, partialTick);
				if (u < 0.25f) {
					float lift = smoothstep(u / 0.25f);
					float peakDeg = 94.0f - pitchFull;
					float rX = Mth.lerp(lift, 22.0f, peakDeg) + recoil * 14.0f;
					return new ProceduralPose(
						true, Math.max(env, lift), rX, 6.0f * lift, 0.0f,
						false, 0.0f, 0.0f, 0.0f, 0.0f,
						6.0f * lift, 0.0f, 0.0f
					);
				} else {
					float settle = smoothstep((u - 0.25f) / 0.55f);
					float peakDeg = 94.0f - pitchFull;
					float rX = Mth.lerp(settle, peakDeg, holdTargetDeg) + recoil * 18.0f;
					return new ProceduralPose(
						true, 1.0f, rX, 6.0f, 0.0f,
						false, 0.0f, 0.0f, 0.0f, 0.0f,
						6.0f + recoil * 4.0f, 0.0f, 0.0f
					);
				}
			}
			case FIRE_2 -> {
				// Dragon's Breath (50 ticks): 4-tick raise -> 2.5s forward channeling palm with rhythmic recoil
				float rhythm = (float) Math.sin(u * Math.PI * 14.0f) * 6.0f;
				float pitchAdj = -Mth.clamp(player.getViewXRot(partialTick), -60.0f, 60.0f) * 0.48f;
				float rX = 90.0f + pitchAdj + rhythm;
				return new ProceduralPose(
					true, env, rX, 6.0f, 0.0f,
					false, 0.0f, 0.0f, 0.0f, 0.0f,
					8.0f * env, 0.0f, 0.0f
				);
			}
			case FIRE_3 -> {
				// Lava Strider (20 ticks): 6-tick wind-up (0.30) + fluid wide low ground sweep
				if (u < 0.30f) {
					float w = smoothstep(u / 0.30f);
					return new ProceduralPose(
						true, env, 56.0f * w, -48.0f * w, 26.0f * w,
						false, 0.0f, 0.0f, 0.0f, 0.0f,
						6.0f * w * env, -16.0f * w * env, 0.0f
					);
				} else {
					float s = smoothstep((u - 0.30f) / 0.42f);
					return new ProceduralPose(
						true, env, Mth.lerp(s, 56.0f, 40.0f), Mth.lerp(s, -48.0f, 56.0f), Mth.lerp(s, 26.0f, -12.0f),
						false, 0.0f, 0.0f, 0.0f, 0.0f,
						8.0f * env, Mth.lerp(s, -16.0f, 18.0f) * env, 0.0f
					);
				}
			}
			case FIRE_4 -> {
				// Flame Bastion (28 ticks): 0..12 gather (u=0..0.43), 12..18 outward shockwave hold (0.43..0.643), 18..28 smooth return
				if (u < 0.43f) {
					float g = smoothstep(u / 0.43f);
					return new ProceduralPose(
						true, env, 76.0f * g, 54.0f * g, -22.0f * g,
						true, env, 76.0f * g, -54.0f * g, 22.0f * g,
						12.0f * g * env, 0.0f, 0.0f
					);
				} else if (u < 0.643f) {
					float p = smoothstep((u - 0.43f) / 0.075f);
					return new ProceduralPose(
						true, env, Mth.lerp(p, 76.0f, 92.0f), Mth.lerp(p, 54.0f, -64.0f), Mth.lerp(p, -22.0f, 34.0f),
						true, env, Mth.lerp(p, 76.0f, 92.0f), Mth.lerp(p, -54.0f, 64.0f), Mth.lerp(p, 22.0f, -34.0f),
						Mth.lerp(p, 12.0f, -12.0f) * env, 0.0f, 0.0f
					);
				} else {
					float ret = 1.0f - smoothstep((u - 0.643f) / 0.357f);
					float w = env * ret;
					return new ProceduralPose(
						true, w, 92.0f, -64.0f, 34.0f,
						true, w, 92.0f, 64.0f, -34.0f,
						-12.0f * w, 0.0f, 0.0f
					);
				}
			}
			case FIRE_5 -> {
				// Supernova (26 ticks): 10-tick overhead energy gather (0.385) + decisive downward slam
				if (u < 0.385f) {
					float gather = smoothstep(u / 0.385f);
					return new ProceduralPose(
						true, env, 168.0f * gather, 0.0f, 26.0f * gather,
						true, env, 168.0f * gather, 0.0f, -26.0f * gather,
						-16.0f * gather * env, 0.0f, 0.0f
					);
				} else {
					float slam = smoothstep((u - 0.385f) / 0.155f);
					return new ProceduralPose(
						true, env, Mth.lerp(slam, 168.0f, 26.0f), 0.0f, Mth.lerp(slam, 26.0f, -8.0f),
						true, env, Mth.lerp(slam, 168.0f, 26.0f), 0.0f, Mth.lerp(slam, -26.0f, 8.0f),
						Mth.lerp(slam, -16.0f, 24.0f) * env, 0.0f, 0.0f
					);
				}
			}
		}
		return null;
	}

	/**
	 * Updates and returns the continuously exponentially smoothed 3rd-person arm/torso state for a player.
	 */
	public static SmoothedArmState updateAndGetSmoothed3pState(Player player, float partialTick) {
		SmoothedArmState s = SMOOTHED_3P_STATES.computeIfAbsent(player.getId(), id -> new SmoothedArmState());
		long nowNs = System.nanoTime();
		if (s.lastUpdateNs == 0L || nowNs - s.lastUpdateNs >= 1_000_000L) {
			float deltaSeconds = s.lastUpdateNs == 0L ? 0.016f : Mth.clamp((nowNs - s.lastUpdateNs) * 1e-9f, 0.001f, 0.05f);
			s.lastUpdateNs = nowNs;
			float alpha = 1.0f - (float) Math.exp(-deltaSeconds * 16.0f);

			ProceduralPose proc = computeProceduralPose(player, partialTick);
			float tRX = 0.0f, tRY = 0.0f, tRZ = 0.0f, tRightBlend = 0.0f;
			float tLX = 0.0f, tLY = 0.0f, tLZ = 0.0f, tLeftBlend = 0.0f;
			float tCX = 0.0f, tCY = 0.0f, tCZ = 0.0f;

			boolean nextCounterBalance = false;
			if (proc != null) {
				if (proc.hasRightArm()) {
					tRX = (float) Math.toRadians(-proc.rXDeg());
					tRY = (float) Math.toRadians(proc.rYDeg());
					tRZ = (float) Math.toRadians(proc.rZDeg());
					tRightBlend = proc.rightWeight();
				}
				if (proc.hasLeftArm()) {
					tLX = (float) Math.toRadians(-proc.lXDeg());
					tLY = (float) Math.toRadians(proc.lYDeg());
					tLZ = (float) Math.toRadians(proc.lZDeg());
					tLeftBlend = proc.leftWeight();
				} else if (proc.hasRightArm() && proc.rightWeight() > 0.05f && proc.rXDeg() >= 30.0f && !player.isUsingItem()) {
					// Open left-arm counter-balance stance while right casting arm is raised
					float u = proc.rightWeight();
					tLX = 0.20f * u;
					tLY = -0.35f * u;
					tLZ = -0.28f * u;
					tLeftBlend = u;
					nextCounterBalance = true;
				}
				tCX = proc.chestXDeg();
				tCY = proc.chestYDeg();
				tCZ = proc.chestZDeg();
			}
			s.leftFlankCounterBalance = nextCounterBalance || (s.leftFlankCounterBalance && s.leftBlendWeight > 0.004f);

			s.rightXRot = Mth.lerp(alpha, s.rightXRot, tRX);
			s.rightYRot = Mth.lerp(alpha, s.rightYRot, tRY);
			s.rightZRot = Mth.lerp(alpha, s.rightZRot, tRZ);
			s.leftXRot = Mth.lerp(alpha, s.leftXRot, tLX);
			s.leftYRot = Mth.lerp(alpha, s.leftYRot, tLY);
			s.leftZRot = Mth.lerp(alpha, s.leftZRot, tLZ);
			s.chestXDeg = Mth.lerp(alpha, s.chestXDeg, tCX);
			s.chestYDeg = Mth.lerp(alpha, s.chestYDeg, tCY);
			s.chestZDeg = Mth.lerp(alpha, s.chestZDeg, tCZ);
			s.blendWeight = Mth.lerp(alpha, s.blendWeight, tRightBlend);
			s.leftBlendWeight = Mth.lerp(alpha, s.leftBlendWeight, tLeftBlend);
		}
		return s;
	}

	/**
	 * Continuously smoothed third-person PlayerModel choreography with zero snapping on release/end.
	 * Left arm stays untouched unless explicitly driven (Flame Bastion & Supernova).
	 */
	public static void applyThirdPersonPose(AbstractClientPlayer player, PlayerModel<?> model) {
		float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
		SmoothedArmState s = updateAndGetSmoothed3pState(player, partial);

		if (s.blendWeight > 0.004f) {
			model.rightArm.xRot = Mth.lerp(s.blendWeight, model.rightArm.xRot, s.rightXRot);
			model.rightArm.yRot = Mth.lerp(s.blendWeight, model.rightArm.yRot, s.rightYRot);
			model.rightArm.zRot = Mth.lerp(s.blendWeight, model.rightArm.zRot, s.rightZRot);
			model.rightSleeve.copyFrom(model.rightArm);
		}

		if (s.leftBlendWeight > 0.004f) {
			model.leftArm.xRot = Mth.lerp(s.leftBlendWeight, model.leftArm.xRot, s.leftXRot);
			model.leftArm.yRot = Mth.lerp(s.leftBlendWeight, model.leftArm.yRot, s.leftYRot);
			model.leftArm.zRot = Mth.lerp(s.leftBlendWeight, model.leftArm.zRot, s.leftZRot);
			model.leftSleeve.copyFrom(model.leftArm);
		}
	}

	@SubscribeEvent
	public static void onRenderLevelStage(RenderLevelStageEvent event) {
		if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || SHOCKWAVES.isEmpty()) {
			return;
		}

		Minecraft mc = Minecraft.getInstance();
		long now = System.currentTimeMillis();
		float partialTick = mc.getTimer().getGameTimeDeltaPartialTick(true);
		Vec3 camPos = event.getCamera().getPosition();
		PoseStack poseStack = event.getPoseStack();
		MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();

		poseStack.pushPose();
		poseStack.translate(-camPos.x, -camPos.y, -camPos.z);

		for (ShockwaveRing ring : SHOCKWAVES) {
			if (now < ring.startTimeMs) {
				continue;
			}
			float t = Mth.clamp((float) (now - ring.startTimeMs) / (float) ring.durationMs, 0.0f, 1.0f);
			if (t >= 1.0f) {
				continue;
			}

			Vec3 liveCenter = ring.fallbackCenter;
			if (mc.level != null) {
				Entity caster = mc.level.getEntity(ring.casterEntityId);
				if (caster != null) {
					liveCenter = caster.getPosition(partialTick).add(0.0, ring.yOffset, 0.0);
				}
			}

			float radius = easeOutCubic(t) * ring.maxRadius;
			float alpha = (1.0f - t) * (1.0f - t);
			drawShockwaveTorus(poseStack, bufferSource, liveCenter, radius, ring.isSupernova ? 0.55f : 0.36f, alpha, ring.isSupernova);
			if (ring.isSupernova && t > 0.10f) {
				float t2 = Mth.clamp((t - 0.10f) / 0.90f, 0.0f, 1.0f);
				drawShockwaveTorus(poseStack, bufferSource, liveCenter.add(0, 0.25, 0), easeOutCubic(t2) * (ring.maxRadius * 0.72f), 0.35f, (1.0f - t2) * 0.8f, false);
			}
		}

		poseStack.popPose();
		bufferSource.endBatch(RenderType.entityTranslucentEmissive(TEX_SHOCKWAVE));
	}

	private static void drawShockwaveTorus(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 center, float radius, float bandWidth, float alpha, boolean supernova) {
		if (radius <= 0.1f || alpha <= 0.02f) {
			return;
		}
		VertexConsumer vc = bufferSource.getBuffer(RenderType.entityTranslucentEmissive(TEX_SHOCKWAVE));
		Matrix4f mat = poseStack.last().pose();

		int segments = 48;
		float innerR = Math.max(0.05f, radius - bandWidth);
		int r = 255;
		int gInner = supernova ? 235 : 195;
		int bInner = supernova ? 150 : 60;
		int gOuter = supernova ? 115 : 85;
		int bOuter = 12;
		int aCore = Mth.clamp((int) (alpha * 220), 0, 255);
		int aEdge = Mth.clamp((int) (alpha * 60), 0, 255);

		float cx = (float) center.x;
		float cy = (float) center.y;
		float cz = (float) center.z;
		float rimHeight = bandWidth * 0.85f;

		for (int i = 0; i < segments; i++) {
			double a0 = (i / (double) segments) * Math.PI * 2.0;
			double a1 = ((i + 1) / (double) segments) * Math.PI * 2.0;
			float c0 = (float) Math.cos(a0);
			float s0 = (float) Math.sin(a0);
			float c1 = (float) Math.cos(a1);
			float s1 = (float) Math.sin(a1);

			vc.addVertex(mat, cx + c0 * innerR, cy, cz + s0 * innerR)
				.setColor(r, gOuter, bOuter, aEdge).setUv(0.5f, 0.5f)
				.setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0, 1, 0);
			vc.addVertex(mat, cx + c0 * radius, cy, cz + s0 * radius)
				.setColor(r, gInner, bInner, aCore).setUv(0.5f, 0.5f)
				.setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0, 1, 0);
			vc.addVertex(mat, cx + c1 * radius, cy, cz + s1 * radius)
				.setColor(r, gInner, bInner, aCore).setUv(0.5f, 0.5f)
				.setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0, 1, 0);
			vc.addVertex(mat, cx + c1 * innerR, cy, cz + s1 * innerR)
				.setColor(r, gOuter, bOuter, aEdge).setUv(0.5f, 0.5f)
				.setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0, 1, 0);

			vc.addVertex(mat, cx + c0 * radius, cy, cz + s0 * radius)
				.setColor(r, gInner, bInner, aCore).setUv(0.5f, 0.5f)
				.setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(c0, 0, s0);
			vc.addVertex(mat, cx + c0 * radius, cy + rimHeight, cz + s0 * radius)
				.setColor(r, gOuter, bOuter, 0).setUv(0.5f, 0.5f)
				.setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(c0, 0, s0);
			vc.addVertex(mat, cx + c1 * radius, cy + rimHeight, cz + s1 * radius)
				.setColor(r, gOuter, bOuter, 0).setUv(0.5f, 0.5f)
				.setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(c1, 0, s1);
			vc.addVertex(mat, cx + c1 * radius, cy, cz + s1 * radius)
				.setColor(r, gInner, bInner, aCore).setUv(0.5f, 0.5f)
				.setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(c1, 0, s1);
		}
	}
}

