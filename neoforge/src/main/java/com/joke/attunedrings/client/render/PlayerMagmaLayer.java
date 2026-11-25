package com.joke.attunedrings.client.render;

import com.joke.attunedrings.client.ClientSpellVisuals;
import com.joke.attunedrings.compat.epicfight.EpicFightCompat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * Diegetic skin-hugging magma vein overlay for 3rd-person PlayerRenderer and post-arm 1st-person rendering:
 * animated flipbook magma veins (Stacks 1-5) and shaded cooling ash/obsidian cracks (Stage 6 Burnout).
 * Extrudes inner body/arm vertices +0.0185F in 3rd person (above outer 0.015625F jacket/sleeves) and
 * +0.0035F in 1st person so veins sit cleanly without floating sleeve blocks or Z-fighting.
 */
public class PlayerMagmaLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

	public PlayerMagmaLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> renderer) {
		super(renderer);
	}

	@Override
	public void render(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
					   AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
					   float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
		if (player.isInvisible() || EpicFightCompat.didEpicFightRender3pThisFrame(player)) {
			return;
		}

		ClientSpellVisuals.MagmaVisualState state = ClientSpellVisuals.resolvePlayerMagmaState(player);
		if (state == null) {
			return;
		}

		if (!state.isBurnout()) {
			AnimatedEntityTexture.ensureRegistered(state.texture());
		}

		PlayerModel<AbstractClientPlayer> model = this.getParentModel();
		RenderType renderType = RenderType.entityTranslucentCull(state.texture());
		int light = state.isBurnout() ? packedLight : LightTexture.FULL_BRIGHT;

		VertexConsumer vc = new NormalExtrudedVertexConsumer(
			bufferSource.getBuffer(renderType),
			NormalExtrudedVertexConsumer.THIRD_PERSON_OFFSET,
			false
		);

		// Render strictly inner cubes extruded past outer jacket/sleeves; skip Head/Hat, Legs, and outer sleeves
		model.rightArm.render(poseStack, vc, light, OverlayTexture.NO_OVERLAY, state.colorArgb());
		if (state.includeTorso()) {
			model.body.render(poseStack, vc, light, OverlayTexture.NO_OVERLAY, state.colorArgb());
		}
		if (state.includeLeftArm()) {
			model.leftArm.render(poseStack, vc, light, OverlayTexture.NO_OVERLAY, state.colorArgb());
		}

		// Commit dynamic emissive/translucent batch immediately after draw (handles Vanilla & Iris BufferSourceWrapper)
		RenderBufferFlusher.flush(bufferSource, renderType);
	}

	/**
	 * Renders the first-person rightArm overlay AFTER the base skin arm is drawn,
	 * extruding vertices +0.0035F along their surface normals so veins stay flush on the arm.
	 */
	public static void renderFirstPersonOverlay(AbstractClientPlayer player, PoseStack poseStack,
												MultiBufferSource bufferSource, int packedLight) {
		if (player == null || player.isInvisible()) {
			return;
		}

		ClientSpellVisuals.MagmaVisualState state = ClientSpellVisuals.resolvePlayerMagmaState(player);
		if (state == null) {
			return;
		}

		if (!state.isBurnout()) {
			AnimatedEntityTexture.ensureRegistered(state.texture());
		}

		if (!(Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(player) instanceof PlayerRenderer playerRenderer)) {
			return;
		}

		PlayerModel<AbstractClientPlayer> model = playerRenderer.getModel();
		model.attackTime = 0.0f;
		model.crouching = false;
		model.swimAmount = 0.0f;
		model.setupAnim(player, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
		model.rightArm.xRot = 0.0f;

		RenderType renderType = RenderType.entityTranslucentCull(state.texture());
		int light = state.isBurnout() ? packedLight : LightTexture.FULL_BRIGHT;

		VertexConsumer vc = new NormalExtrudedVertexConsumer(
			bufferSource.getBuffer(renderType),
			NormalExtrudedVertexConsumer.FIRST_PERSON_OFFSET,
			false
		);
		model.rightArm.render(poseStack, vc, light, OverlayTexture.NO_OVERLAY, state.colorArgb());

		// Flush immediately after drawing so Sodium/Iris BufferSourceWrapper commits the batch
		RenderBufferFlusher.flush(bufferSource, renderType);
	}
}
