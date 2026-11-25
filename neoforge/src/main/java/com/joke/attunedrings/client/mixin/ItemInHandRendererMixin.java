package com.joke.attunedrings.client.mixin;

import com.joke.attunedrings.client.ClientSpellVisuals;
import com.joke.attunedrings.client.render.PlayerMagmaLayer;
import com.joke.attunedrings.compat.epicfight.EpicFightCompat;
import com.joke.attunedrings.registry.ModItems;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {
	@Shadow
	@Final
	private Minecraft minecraft;

	@Shadow
	private ItemStack mainHandItem;

	@Shadow
	private ItemStack offHandItem;

	@Shadow
	protected abstract void renderPlayerArm(PoseStack poseStack, MultiBufferSource buffer, int combinedLight,
											float equippedProgress, float swingProgress, HumanoidArm side);

	@Inject(method = "tick", at = @At("HEAD"))
	private void attunedrings$preventReequipBobbing(CallbackInfo ci) {
		if (this.minecraft.player == null) {
			return;
		}

		ItemStack currentMain = this.minecraft.player.getMainHandItem();
		if (currentMain.is(ModItems.MOLTEN_RING.get()) && this.mainHandItem.is(ModItems.MOLTEN_RING.get())) {
			this.mainHandItem = currentMain;
		}

		ItemStack currentOff = this.minecraft.player.getOffhandItem();
		if (currentOff.is(ModItems.MOLTEN_RING.get()) && this.offHandItem.is(ModItems.MOLTEN_RING.get())) {
			this.offHandItem = currentOff;
		}
	}

	@Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
	private void attunedrings$pushFirstPersonSpellPose(AbstractClientPlayer player, float partialTick, float pitch,
													   InteractionHand hand, float swingProgress, ItemStack stack,
													   float equippedProgress, PoseStack poseStack,
													   MultiBufferSource buffer, int combinedLight, CallbackInfo ci) {
		poseStack.pushPose();
		ClientSpellVisuals.applyFirstPersonTransform(player, poseStack, hand, partialTick, pitch);
		if (hand == InteractionHand.MAIN_HAND && ClientSpellVisuals.shouldSuppressHeldMainHandItem(player)) {
			this.renderPlayerArm(poseStack, buffer, combinedLight, 0.0F, swingProgress, HumanoidArm.RIGHT);
			poseStack.popPose();
			ci.cancel();
		}
	}

	@Inject(method = "renderArmWithItem", at = @At("RETURN"))
	private void attunedrings$popFirstPersonSpellPose(AbstractClientPlayer player, float partialTick, float pitch,
													  InteractionHand hand, float swingProgress, ItemStack stack,
													  float equippedProgress, PoseStack poseStack,
													  MultiBufferSource buffer, int combinedLight, CallbackInfo ci) {
		poseStack.popPose();
	}

	@Inject(method = "renderPlayerArm", at = @At("TAIL"))
	private void attunedrings$renderFirstPersonMagmaAfterArm(PoseStack poseStack, MultiBufferSource buffer,
															 int combinedLight, float equippedProgress,
															 float swingProgress, HumanoidArm side, CallbackInfo ci) {
		if (side == HumanoidArm.RIGHT && this.minecraft.player != null
			&& !EpicFightCompat.consumeFirstPersonRenderedThisPass()) {
			PlayerMagmaLayer.renderFirstPersonOverlay(this.minecraft.player, poseStack, buffer, combinedLight);
		}
	}
}
