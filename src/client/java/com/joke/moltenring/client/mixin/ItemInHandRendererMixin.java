package com.joke.moltenring.client.mixin;

import com.joke.moltenring.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemInHandRenderer;
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

	@Inject(method = "tick", at = @At("HEAD"))
	private void moltenring$preventReequipBobbing(CallbackInfo ci) {
		if (this.minecraft.player == null) {
			return;
		}

		ItemStack currentMain = this.minecraft.player.getMainHandItem();
		if (currentMain.is(ModItems.MOLTEN_RING) && this.mainHandItem.is(ModItems.MOLTEN_RING)) {
			this.mainHandItem = currentMain;
		}

		ItemStack currentOff = this.minecraft.player.getOffhandItem();
		if (currentOff.is(ModItems.MOLTEN_RING) && this.offHandItem.is(ModItems.MOLTEN_RING)) {
			this.offHandItem = currentOff;
		}
	}
}
