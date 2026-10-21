package com.joke.attunedrings.magic;

import com.joke.attunedrings.AttunedRings;
import net.minecraft.resources.ResourceLocation;

public enum ElementType {
	FIRE("fire", 0xFF6600, "🔥", AttunedRings.id("textures/gui/rune_fire.png"));

	private final String id;
	private final int color;
	private final String symbol;
	private final ResourceLocation texture;

	ElementType(String id, int color, String symbol, ResourceLocation texture) {
		this.id = id;
		this.color = color;
		this.symbol = symbol;
		this.texture = texture;
	}

	public String getId() {
		return id;
	}

	public int getColor() {
		return color;
	}

	public String getSymbol() {
		return symbol;
	}

	public ResourceLocation getTexture() {
		return texture;
	}
}
