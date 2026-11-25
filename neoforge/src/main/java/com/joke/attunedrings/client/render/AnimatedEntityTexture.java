package com.joke.attunedrings.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.texture.Tickable;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Mth;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Loads vertical flipbook entity textures (e.g. 64x256, 4 frames) with .png.mcmeta
 * animation metadata and uploads smoothly interpolated 64x64 frames each tick.
 */
public class AnimatedEntityTexture extends AbstractTexture implements Tickable {
	private static final Set<ResourceLocation> REGISTERED = new HashSet<>();

	private final ResourceLocation location;
	private NativeImage sheet;
	private NativeImage frameBuffer;
	private int frameWidth = 64;
	private int frameHeight = 64;
	private int frameCount = 1;
	private int frameTime = 3;
	private boolean interpolate = true;
	private int currentFrame = 0;
	private int subTick = 0;

	public AnimatedEntityTexture(ResourceLocation location) {
		this.location = location;
	}

	public static void ensureRegistered(ResourceLocation location) {
		if (REGISTERED.contains(location)) {
			return;
		}
		TextureManager manager = Minecraft.getInstance().getTextureManager();
		AbstractTexture existing = manager.getTexture(location, null);
		if (!(existing instanceof AnimatedEntityTexture)) {
			manager.register(location, new AnimatedEntityTexture(location));
		}
		REGISTERED.add(location);
	}

	@Override
	public void load(ResourceManager resourceManager) throws IOException {
		closeImages();
		Resource resource = resourceManager.getResourceOrThrow(this.location);
		try (InputStream stream = resource.open()) {
			this.sheet = NativeImage.read(stream);
		}

		Optional<AnimationMetadataSection> metaOpt = resource.metadata().getSection(AnimationMetadataSection.SERIALIZER);
		this.frameWidth = this.sheet.getWidth();
		this.frameHeight = this.frameWidth; // Square frames in vertical strip (64x256 -> 64x64)
		this.frameCount = Math.max(1, this.sheet.getHeight() / this.frameHeight);
		this.frameTime = metaOpt.map(m -> Math.max(1, m.getDefaultFrameTime())).orElse(3);
		this.interpolate = metaOpt.map(AnimationMetadataSection::isInterpolatedFrames).orElse(true);

		this.frameBuffer = new NativeImage(this.frameWidth, this.frameHeight, false);
		this.currentFrame = 0;
		this.subTick = 0;

		copyFrame(0);
		if (!RenderSystem.isOnRenderThreadOrInit()) {
			RenderSystem.recordRenderCall(this::prepareAndUpload);
		} else {
			prepareAndUpload();
		}
	}

	private void prepareAndUpload() {
		if (this.frameBuffer == null) {
			return;
		}
		TextureUtil.prepareImage(this.getId(), 0, this.frameWidth, this.frameHeight);
		this.frameBuffer.upload(0, 0, 0, false);
	}

	@Override
	public void tick() {
		if (this.sheet == null || this.frameBuffer == null || this.frameCount <= 1) {
			return;
		}

		this.subTick++;
		if (this.subTick >= this.frameTime) {
			this.subTick = 0;
			this.currentFrame = (this.currentFrame + 1) % this.frameCount;
		}

		if (this.interpolate) {
			int nextFrame = (this.currentFrame + 1) % this.frameCount;
			float blend = (float) this.subTick / (float) this.frameTime;
			interpolateFrames(this.currentFrame, nextFrame, blend);
		} else if (this.subTick == 0) {
			copyFrame(this.currentFrame);
		} else {
			return;
		}

		if (RenderSystem.isOnRenderThread()) {
			bind();
			this.frameBuffer.upload(0, 0, 0, false);
		}
	}

	private void copyFrame(int frameIndex) {
		int ySrc = frameIndex * this.frameHeight;
		for (int y = 0; y < this.frameHeight; y++) {
			for (int x = 0; x < this.frameWidth; x++) {
				this.frameBuffer.setPixelRGBA(x, y, this.sheet.getPixelRGBA(x, ySrc + y));
			}
		}
	}

	private void interpolateFrames(int f0, int f1, float blend) {
		int y0 = f0 * this.frameHeight;
		int y1 = f1 * this.frameHeight;
		for (int y = 0; y < this.frameHeight; y++) {
			for (int x = 0; x < this.frameWidth; x++) {
				int c0 = this.sheet.getPixelRGBA(x, y0 + y);
				int c1 = this.sheet.getPixelRGBA(x, y1 + y);
				if (c0 == c1) {
					this.frameBuffer.setPixelRGBA(x, y, c0);
					continue;
				}
				int a = (int) Mth.lerp(blend, (c0 >>> 24) & 0xFF, (c1 >>> 24) & 0xFF);
				int b = (int) Mth.lerp(blend, (c0 >>> 16) & 0xFF, (c1 >>> 16) & 0xFF);
				int g = (int) Mth.lerp(blend, (c0 >>> 8) & 0xFF, (c1 >>> 8) & 0xFF);
				int r = (int) Mth.lerp(blend, c0 & 0xFF, c1 & 0xFF);
				this.frameBuffer.setPixelRGBA(x, y, (a << 24) | (b << 16) | (g << 8) | r);
			}
		}
	}

	private void closeImages() {
		if (this.sheet != null) {
			this.sheet.close();
			this.sheet = null;
		}
		if (this.frameBuffer != null) {
			this.frameBuffer.close();
			this.frameBuffer = null;
		}
	}

	@Override
	public void close() {
		closeImages();
		super.close();
	}
}

