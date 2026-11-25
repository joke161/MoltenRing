package com.joke.attunedrings.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

public class MagmaEmberParticle extends TextureSheetParticle {
	private static boolean miniAimScale = false;
	private final float baseQuadSize;

	public static void setMiniAimScale(boolean mini) {
		miniAimScale = mini;
	}

	protected MagmaEmberParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, SpriteSet sprites) {
		super(level, x, y, z, xd, yd, zd);
		this.friction = 0.94f;
		this.gravity = -0.02f; // Subtle rising heat buoyancy
		this.xd = xd;
		this.yd = yd;
		this.zd = zd;
		if (miniAimScale) {
			this.lifetime = 8 + this.random.nextInt(6);
			this.baseQuadSize = 0.020f + this.random.nextFloat() * 0.010f; // 0.02f..0.03f during RMB aim
		} else {
			this.lifetime = 16 + this.random.nextInt(14);
			this.baseQuadSize = 0.085f + this.random.nextFloat() * 0.065f;
		}
		this.quadSize = this.baseQuadSize;
		this.hasPhysics = true;
		this.pickSprite(sprites);
		updateColorAndScale();
	}

	@Override
	public void tick() {
		super.tick();
		this.xd += (this.random.nextDouble() - 0.5) * 0.004;
		this.zd += (this.random.nextDouble() - 0.5) * 0.004;
		updateColorAndScale();
	}

	private void updateColorAndScale() {
		float t = Mth.clamp((float) this.age / (float) this.lifetime, 0.0f, 1.0f);

		// Transition: bright gold-white core -> glowing orange flame -> dark cooling ash
		if (t < 0.25f) {
			float k = t / 0.25f;
			this.rCol = 1.0f;
			this.gCol = Mth.lerp(k, 0.96f, 0.68f);
			this.bCol = Mth.lerp(k, 0.72f, 0.14f);
			this.alpha = 1.0f;
			this.quadSize = this.baseQuadSize * (0.85f + 0.30f * k);
		} else if (t < 0.70f) {
			float k = (t - 0.25f) / 0.45f;
			this.rCol = Mth.lerp(k, 1.0f, 0.85f);
			this.gCol = Mth.lerp(k, 0.68f, 0.24f);
			this.bCol = Mth.lerp(k, 0.14f, 0.03f);
			this.alpha = Mth.lerp(k, 1.0f, 0.82f);
			this.quadSize = this.baseQuadSize * Mth.lerp(k, 1.15f, 0.78f);
		} else {
			float k = (t - 0.70f) / 0.30f;
			this.rCol = Mth.lerp(k, 0.85f, 0.16f);
			this.gCol = Mth.lerp(k, 0.24f, 0.14f);
			this.bCol = Mth.lerp(k, 0.03f, 0.16f);
			this.alpha = Mth.lerp(k, 0.82f, 0.0f);
			this.quadSize = this.baseQuadSize * Mth.lerp(k, 0.78f, 0.25f);
		}
	}

	@Override
	public ParticleRenderType getRenderType() {
		return ParticleRenderType.PARTICLE_SHEET_LIT;
	}

	@Override
	protected int getLightColor(float partialTick) {
		float t = Mth.clamp((float) this.age / (float) this.lifetime, 0.0f, 1.0f);
		if (t < 0.65f) {
			return LightTexture.FULL_BRIGHT;
		}
		int block = (int) Mth.lerp((t - 0.65f) / 0.35f, 15.0f, 3.0f);
		int base = super.getLightColor(partialTick);
		int sky = LightTexture.sky(base);
		return LightTexture.pack(Math.max(block, LightTexture.block(base)), sky);
	}

	public static class Provider implements ParticleProvider<SimpleParticleType> {
		private final SpriteSet sprites;

		public Provider(SpriteSet sprites) {
			this.sprites = sprites;
		}

		@Override
		public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xd, double yd, double zd) {
			return new MagmaEmberParticle(level, x, y, z, xd, yd, zd, this.sprites);
		}
	}
}

