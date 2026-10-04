package com.yoyolee.particledeco.data;

import java.util.Locale;

import com.mojang.serialization.Codec;

public enum RedstoneMode {
	IGNORE,
	ON_SIGNAL,
	NO_SIGNAL;

	public static final Codec<RedstoneMode> CODEC = Codec.STRING.xmap(RedstoneMode::byName, mode -> mode.name());

	public boolean allows(boolean powered) {
		return switch (this) {
			case IGNORE -> true;
			case ON_SIGNAL -> powered;
			case NO_SIGNAL -> !powered;
		};
	}

	public RedstoneMode next() {
		return values()[(ordinal() + 1) % values().length];
	}

	public static RedstoneMode byName(String name) {
		try {
			return valueOf(name.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			return IGNORE;
		}
	}
}
