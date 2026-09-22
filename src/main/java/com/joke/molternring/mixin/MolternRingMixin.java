package com.joke.molternring.mixin;

import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
public class MolternRingMixin {
	@Inject(at = @At("HEAD"), method = "loadLevel")
	private void init(CallbackInfo info) {
		// Example injection into server level loading
	}
}

