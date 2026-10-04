package com.yoyolee.particledeco.data;

import java.util.Locale;

import com.mojang.serialization.Codec;

/**
 * Emission shapes. Sizes are column height, ring radius or area edge length in blocks.
 */
public enum EmitterShape {
	POINT(0.0f, 0.0f),
	COLUMN(1.0f, 4.0f),
	RING(0.25f, 3.0f),
	AREA(1.0f, 5.0f);

	public static final Codec<EmitterShape> CODEC = Codec.STRING.xmap(EmitterShape::byName, EmitterShape::serializedName);

	private final float minSize;
	private final float maxSize;

	EmitterShape(float minSize, float maxSize) {
		this.minSize = minSize;
		this.maxSize = maxSize;
	}

	public float minSize() {
		return minSize;
	}

	public float maxSize() {
		return maxSize;
	}

	public String serializedName() {
		return name().toLowerCase(Locale.ROOT);
	}

	public EmitterShape next() {
		return values()[(ordinal() + 1) % values().length];
	}

	public EmitterShape previous() {
		return values()[(ordinal() + values().length - 1) % values().length];
	}

	public static EmitterShape byName(String name) {
		for (EmitterShape shape : values()) {
			if (shape.serializedName().equalsIgnoreCase(name)) return shape;
		}

		return POINT;
	}
}
