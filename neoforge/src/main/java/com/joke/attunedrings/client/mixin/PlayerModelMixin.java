package com.joke.attunedrings.client.mixin;

import com.joke.attunedrings.client.ClientSpellVisuals;
import com.joke.attunedrings.compat.epicfight.EpicFightCompat;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerModel.class)
public abstract class PlayerModelMixin<T extends LivingEntity> {
	@Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
	private void attunedrings$applyThirdPersonSpellPose(T entity, float limbSwing, float limbSwingAmount,
														float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
		if (entity instanceof AbstractClientPlayer player && !EpicFightCompat.isEpicFightRenderingPlayer(player)) {
			ClientSpellVisuals.applyThirdPersonPose(player, (PlayerModel<?>) (Object) this);
		}
	}
}

