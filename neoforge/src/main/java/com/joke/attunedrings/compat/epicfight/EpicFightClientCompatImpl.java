package com.joke.attunedrings.compat.epicfight;

import com.joke.attunedrings.client.ClientSpellVisuals;
import com.joke.attunedrings.client.ClientSpellVisuals.SmoothedArmState;
import com.joke.attunedrings.client.render.AnimatedEntityTexture;
import com.joke.attunedrings.client.render.NormalExtrudedVertexConsumer;
import com.joke.attunedrings.client.render.PlayerMagmaLayer;
import com.joke.attunedrings.client.render.RenderBufferFlusher;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.FastColor;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Quaternionf;
import yesman.epicfight.api.animation.Joint;
import yesman.epicfight.api.animation.JointTransform;
import yesman.epicfight.api.animation.Pose;
import yesman.epicfight.api.client.event.EpicFightClientEventHooks;
import yesman.epicfight.api.client.event.types.registry.RegisterPatchedRenderersEvent;
import yesman.epicfight.api.client.event.types.render.PrepareModelEvent;
import yesman.epicfight.api.client.input.InputManager;
import yesman.epicfight.api.client.input.action.MinecraftInputAction;
import yesman.epicfight.api.client.model.Mesh;
import yesman.epicfight.api.client.model.Meshes;
import yesman.epicfight.api.utils.math.OpenMatrix4f;
import yesman.epicfight.api.utils.math.Vec3f;
import yesman.epicfight.client.events.engine.RenderEngine;
import yesman.epicfight.client.mesh.HumanoidMesh;
import yesman.epicfight.client.renderer.EpicFightRenderTypes;
import yesman.epicfight.client.renderer.FirstPersonRenderer;
import yesman.epicfight.client.renderer.patched.entity.PatchedLivingEntityRenderer;
import yesman.epicfight.client.renderer.patched.layer.PatchedLayer;
import yesman.epicfight.client.world.capabilites.entitypatch.player.LocalPlayerPatch;
import yesman.epicfight.model.armature.HumanoidArmature;
import yesman.epicfight.world.capabilities.entitypatch.player.PlayerPatch;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Isolated Epic Fight client compat:
 * - Mirrors PlayerMagmaLayer onto both 3rd-person HumanoidMesh and 1st-person FirstPersonRenderer (arm-only)
 *   using NormalExtrudedVertexConsumer (+0.0045F along vertex normals) so veins sit cleanly above skin and jackets
 * - Tracks per-frame 1st-person & 3rd-person render handshakes so vanilla fallbacks never miss or double-render
 * - Hooks PREPARE_MODEL_TO_RENDER to slerp SmoothedArmState cleanly onto Shoulder_R/Arm_R & Shoulder_L/Arm_L
 * - Reads physical USE input so Battle Mode never blocks 1-stack Firebolt RMB aiming
 */
public final class EpicFightClientCompatImpl {
	private static long currentRenderFrame = 1L;
	private static boolean epfDrewFirstPersonThisPass = false;
	private static final Map<Integer, Long> EPF_3P_RENDERED_FRAME = new ConcurrentHashMap<>();

	private EpicFightClientCompatImpl() {
	}

	public static void register(IEventBus modEventBus) {
		EpicFightClientEventHooks.Registry.MODIFY_PATCHED_ENTITY.registerEvent(EpicFightClientCompatImpl::onModifyPatchedRenderers);
		EpicFightClientEventHooks.Render.PREPARE_MODEL_TO_RENDER.registerEvent(EpicFightClientCompatImpl::onPrepareModelToRender);
		NeoForge.EVENT_BUS.addListener(EpicFightClientCompatImpl::onRenderFramePre);
		NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, EpicFightClientCompatImpl::onRenderHandPre);
	}

	public static boolean isUseKeyHeld(boolean vanillaKeyDown) {
		return vanillaKeyDown || InputManager.isActionPhysicallyActive(MinecraftInputAction.USE);
	}

	public static boolean isEpicFightRenderingPlayer(Player player) {
		return player instanceof AbstractClientPlayer
			&& RenderEngine.getInstance().hasRendererFor(player)
			&& !RenderEngine.getInstance().shouldRenderVanillaModel();
	}

	public static boolean consumeFirstPersonRenderedThisPass() {
		boolean drew = epfDrewFirstPersonThisPass;
		epfDrewFirstPersonThisPass = false;
		return drew;
	}

	public static boolean didEpicFightRender3pThisFrame(Player player) {
		if (player == null) {
			return false;
		}
		Long renderedFrame = EPF_3P_RENDERED_FRAME.get(player.getId());
		return renderedFrame != null && renderedFrame == currentRenderFrame;
	}

	private static void onRenderFramePre(RenderFrameEvent.Pre event) {
		currentRenderFrame++;
		epfDrewFirstPersonThisPass = false;
	}

	private static void onRenderHandPre(RenderHandEvent event) {
		if (event.getHand() == InteractionHand.MAIN_HAND) {
			epfDrewFirstPersonThisPass = false;
		}
	}

	private static void onPrepareModelToRender(PrepareModelEvent event) {
		if (!(event.getEntityPatch() instanceof PlayerPatch<?> patch) || !patch.overrideRender()) {
			return;
		}
		if (!(patch.getOriginal() instanceof Player player) || !(patch.getArmature() instanceof HumanoidArmature armature)) {
			return;
		}
		if (patch.getEntityState().inaction()) {
			return;
		}

		SmoothedArmState s = ClientSpellVisuals.updateAndGetSmoothed3pState(player, event.getPartialTicks());
		if (s.blendWeight <= 0.004f && s.leftBlendWeight <= 0.004f) {
			return;
		}

		Pose basePose = patch.getAnimator().getPose(event.getPartialTicks());
		Pose workingPose = new Pose();
		workingPose.load(basePose, Pose.LoadOperation.SET);

		if (s.blendWeight > 0.004f && (Math.abs(s.chestXDeg) > 0.05f || Math.abs(s.chestYDeg) > 0.05f || Math.abs(s.chestZDeg) > 0.05f)) {
			applyAdditiveJointRotation(armature, workingPose, armature.chest, "Chest",
				s.chestXDeg * s.blendWeight, s.chestYDeg * s.blendWeight, s.chestZDeg * s.blendWeight);
		}

		if (s.blendWeight > 0.004f) {
			float rxDeg = (float) Math.toDegrees(-s.rightXRot);
			float ryDeg = (float) Math.toDegrees(s.rightYRot);
			float rzDeg = (float) Math.toDegrees(s.rightZRot);
			blendArmJointPose(armature, workingPose, armature.armR, "Arm_R", "Shoulder_R", "Hand_R",
				rxDeg, ryDeg, rzDeg, s.blendWeight);
		}

		if (s.leftBlendWeight > 0.004f) {
			float lxDeg = (float) Math.toDegrees(-s.leftXRot);
			// Epic Fight Arm_L Y-axis points up: invert shield counter-balance Y so it opens outward to the left hip
			float lyDeg = (float) Math.toDegrees(s.leftFlankCounterBalance ? -s.leftYRot : s.leftYRot);
			float lzDeg = (float) Math.toDegrees(s.leftZRot);
			blendArmJointPose(armature, workingPose, armature.armL, "Arm_L", "Shoulder_L", "Hand_L",
				lxDeg, lyDeg, lzDeg, s.leftBlendWeight);
		}

		armature.setPose(workingPose);
	}

	private static void applyAdditiveJointRotation(HumanoidArmature armature, Pose pose, Joint joint, String jointName,
												   float xDeg, float yDeg, float zDeg) {
		JointTransform jt = pose.hasTransform(jointName) ? pose.get(jointName).copy() : JointTransform.empty();
		OpenMatrix4f toParent = new OpenMatrix4f(armature.getBoundTransformFor(pose, joint))
			.removeScale()
			.removeTranslation()
			.invert();
		Vec3f xAxis = OpenMatrix4f.transform3v(toParent, Vec3f.X_AXIS, null);
		Vec3f yAxis = OpenMatrix4f.transform3v(toParent, Vec3f.Y_AXIS, null);
		Vec3f zAxis = OpenMatrix4f.transform3v(toParent, Vec3f.Z_AXIS, null);
		OpenMatrix4f rot = OpenMatrix4f.createRotatorDeg(yDeg, yAxis)
			.rotateDeg(xDeg, xAxis)
			.rotateDeg(zDeg, zAxis);
		jt.frontResult(JointTransform.fromMatrix(rot), OpenMatrix4f::mul);
		pose.putJointData(jointName, jt);
	}

	private static void blendArmJointPose(HumanoidArmature armature, Pose pose, Joint armJoint,
										  String armName, String shoulderName, String handName,
										  float xDeg, float yDeg, float zDeg, float blend) {
		// Neutralize Shoulder swing smoothly on workingPose itself so idle/walk transforms cannot twist the arm
		if (pose.hasTransform(shoulderName)) {
			JointTransform sj = pose.get(shoulderName).copy();
			Quaternionf sRot = new Quaternionf(sj.rotation()).slerp(new Quaternionf(), blend);
			pose.putJointData(shoulderName, JointTransform.translationRotation(sj.translation(), sRot));
		}

		JointTransform baseArm = pose.hasTransform(armName) ? pose.get(armName).copy() : JointTransform.empty();

		Pose refPose = new Pose();
		refPose.load(pose, Pose.LoadOperation.SET);
		refPose.putJointData(armName, JointTransform.empty());

		OpenMatrix4f toParent = new OpenMatrix4f(armature.getBoundTransformFor(refPose, armJoint))
			.removeScale()
			.removeTranslation()
			.invert();

		Vec3f xAxis = OpenMatrix4f.transform3v(toParent, Vec3f.X_AXIS, null);
		Vec3f yAxis = OpenMatrix4f.transform3v(toParent, Vec3f.Y_AXIS, null);
		Vec3f zAxis = OpenMatrix4f.transform3v(toParent, Vec3f.Z_AXIS, null);

		OpenMatrix4f targetRot = OpenMatrix4f.createRotatorDeg(yDeg, yAxis)
			.rotateDeg(xDeg, xAxis)
			.rotateDeg(zDeg, zAxis);
		Quaternionf targetQuat = JointTransform.fromMatrix(targetRot).rotation();

		Quaternionf blendedRot = new Quaternionf(baseArm.rotation()).slerp(targetQuat, blend);
		pose.putJointData(armName, JointTransform.translationRotation(baseArm.translation(), blendedRot));

		if (pose.hasTransform(handName)) {
			JointTransform hj = pose.get(handName).copy();
			Quaternionf hRot = new Quaternionf(hj.rotation()).slerp(new Quaternionf(), blend);
			pose.putJointData(handName, JointTransform.translationRotation(hj.translation(), hRot));
		}
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static void onModifyPatchedRenderers(RegisterPatchedRenderersEvent.ModifyEntity event) {
		if (event.get(EntityType.PLAYER) instanceof PatchedLivingEntityRenderer patchedPlayer) {
			patchedPlayer.addPatchedLayer(PlayerMagmaLayer.class, new EpicFightMagmaLayer(false));
		}
		FirstPersonRenderer fpRenderer = RenderEngine.getInstance().getFirstPersonRenderer();
		if (fpRenderer != null) {
			fpRenderer.addPatchedLayer(PlayerMagmaLayer.class, new EpicFightMagmaLayer<>(true));
		}
	}

	private static class EpicFightMagmaLayer<E extends AbstractClientPlayer, T extends PlayerPatch<E>>
		extends PatchedLayer<E, T, PlayerModel<E>, RenderLayer<E, PlayerModel<E>>> {

		private final boolean firstPersonArmOnly;

		EpicFightMagmaLayer(boolean firstPersonArmOnly) {
			this.firstPersonArmOnly = firstPersonArmOnly;
		}

		@Override
		protected void renderLayer(T patch,
								   E player,
								   RenderLayer<E, PlayerModel<E>> vanillaLayer,
								   PoseStack poseStack,
								   MultiBufferSource bufferSource,
								   int packedLight,
								   OpenMatrix4f[] poses,
								   float bob,
								   float netHeadYaw,
								   float headPitch,
								   float partialTick) {
			if (player.isInvisible()) {
				return;
			}
			// When FirstPersonRenderer lacks an animated POV pose (e.g. IDLE/WALK/RUN sprinting) and the player
			// has an empty main-hand spell stance, defer 1st-person rendering to ClientSpellVisuals' cast-arm pass
			if (firstPersonArmOnly
				&& patch instanceof LocalPlayerPatch lp
				&& lp.getPovSettings() == null
				&& player.getMainHandItem().isEmpty()
				&& ClientSpellVisuals.shouldShowFirstPersonCastArm(player)) {
				return;
			}

			ClientSpellVisuals.MagmaVisualState state = ClientSpellVisuals.resolvePlayerMagmaState(player);
			if (state == null) {
				return;
			}

			if (!state.isBurnout()) {
				AnimatedEntityTexture.ensureRegistered(state.texture());
			}

			boolean slim = "slim".equals(player.getSkin().model().id());
			HumanoidMesh mesh = (slim ? Meshes.ALEX : Meshes.BIPED).get();

			boolean prevHead = mesh.head.isHidden();
			boolean prevHat = mesh.hat.isHidden();
			boolean prevLeftLeg = mesh.leftLeg.isHidden();
			boolean prevRightLeg = mesh.rightLeg.isHidden();
			boolean prevLeftPants = mesh.leftPants.isHidden();
			boolean prevRightPants = mesh.rightPants.isHidden();
			boolean prevTorso = mesh.torso.isHidden();
			boolean prevJacket = mesh.jacket.isHidden();
			boolean prevLeftArm = mesh.leftArm.isHidden();
			boolean prevLeftSleeve = mesh.leftSleeve.isHidden();
			boolean prevRightArm = mesh.rightArm.isHidden();
			boolean prevRightSleeve = mesh.rightSleeve.isHidden();

			// Strictly hide Head/Hat, Legs, and outer clothing cubes (jacket/sleeves) so only inner body/arm extrude cleanly
			mesh.head.setHidden(true);
			mesh.hat.setHidden(true);
			mesh.leftLeg.setHidden(true);
			mesh.rightLeg.setHidden(true);
			mesh.leftPants.setHidden(true);
			mesh.rightPants.setHidden(true);
			mesh.jacket.setHidden(true);
			mesh.rightSleeve.setHidden(true);
			mesh.leftSleeve.setHidden(true);
			mesh.rightArm.setHidden(false);
			mesh.torso.setHidden(firstPersonArmOnly || !state.includeTorso());
			mesh.leftArm.setHidden(firstPersonArmOnly || !state.includeLeftArm());

			float r = FastColor.ARGB32.red(state.colorArgb()) / 255.0f;
			float g = FastColor.ARGB32.green(state.colorArgb()) / 255.0f;
			float b = FastColor.ARGB32.blue(state.colorArgb()) / 255.0f;
			float a = FastColor.ARGB32.alpha(state.colorArgb()) / 255.0f;
			int light = state.isBurnout() ? packedLight : LightTexture.FULL_BRIGHT;

			RenderType layerType = EpicFightRenderTypes.getTriangulated(
				RenderType.entityTranslucentCull(state.texture())
			);

			float extrudeDistance = firstPersonArmOnly
				? NormalExtrudedVertexConsumer.FIRST_PERSON_OFFSET
				: NormalExtrudedVertexConsumer.THIRD_PERSON_OFFSET;
			MultiBufferSource extrudedSource = type -> new NormalExtrudedVertexConsumer(
				bufferSource.getBuffer(type),
				extrudeDistance,
				true
			);

			try {
				mesh.draw(poseStack, extrudedSource, layerType, Mesh.DrawingFunction.NEW_ENTITY, light, r, g, b, a,
					OverlayTexture.NO_OVERLAY, patch.getArmature(), poses);
				// Commit EpicFight's dynamic triangulated RenderType immediately AFTER drawing (handles Vanilla & Iris wrappers)
				RenderBufferFlusher.flush(bufferSource, layerType);
				if (firstPersonArmOnly) {
					epfDrewFirstPersonThisPass = true;
				} else {
					EPF_3P_RENDERED_FRAME.put(player.getId(), currentRenderFrame);
				}
			} finally {
				mesh.head.setHidden(prevHead);
				mesh.hat.setHidden(prevHat);
				mesh.leftLeg.setHidden(prevLeftLeg);
				mesh.rightLeg.setHidden(prevRightLeg);
				mesh.leftPants.setHidden(prevLeftPants);
				mesh.rightPants.setHidden(prevRightPants);
				mesh.torso.setHidden(prevTorso);
				mesh.jacket.setHidden(prevJacket);
				mesh.leftArm.setHidden(prevLeftArm);
				mesh.leftSleeve.setHidden(prevLeftSleeve);
				mesh.rightArm.setHidden(prevRightArm);
				mesh.rightSleeve.setHidden(prevRightSleeve);
			}
		}
	}
}
