package com.joke.attunedrings.client;

import com.joke.attunedrings.AttunedRings;
import com.joke.attunedrings.client.particle.MagmaEmberParticle;
import com.joke.attunedrings.client.render.PlayerMagmaLayer;
import com.joke.attunedrings.registry.ModParticles;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@EventBusSubscriber(modid = AttunedRings.MOD_ID, value = Dist.CLIENT)
public class AttunedRingsClient {
	public static final Logger LOGGER = LoggerFactory.getLogger("attunedrings-client");

	@SubscribeEvent
	public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
		LOGGER.info("Registering Attuned Rings key mappings");
		event.register(ModKeyMappings.KEY_RING_1);
		event.register(ModKeyMappings.KEY_RING_2);
		event.register(ModKeyMappings.KEY_CAST);
	}

	@SubscribeEvent
	public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
		event.registerAbove(VanillaGuiLayers.CROSSHAIR, AttunedRings.id("crosshair_reticle"), ClientSpellVisuals::renderCrosshairReticle);
		event.registerAbove(VanillaGuiLayers.HOTBAR, AttunedRings.id("spell_hud"), SpellHudRenderer::renderHud);
		event.registerAboveAll(AttunedRings.id("burnout_overlay"), ClientBurnoutEffect::renderOverlay);
	}

	@SubscribeEvent
	public static void onRegisterParticleProviders(RegisterParticleProvidersEvent event) {
		event.registerSpriteSet(ModParticles.MAGMA_EMBER.get(), MagmaEmberParticle.Provider::new);
	}

	@SubscribeEvent
	public static void onAddPlayerLayers(EntityRenderersEvent.AddLayers event) {
		for (PlayerSkin.Model skinModel : event.getSkins()) {
			if (event.getSkin(skinModel) instanceof PlayerRenderer playerRenderer) {
				playerRenderer.addLayer(new PlayerMagmaLayer(playerRenderer));
			}
		}
	}
}
