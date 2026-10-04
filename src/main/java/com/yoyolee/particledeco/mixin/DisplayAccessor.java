package com.yoyolee.particledeco.mixin;

import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.Display;

@Mixin(Display.class)
public interface DisplayAccessor {
	@Accessor("DATA_SCALE_ID")
	static EntityDataAccessor<Vector3fc> particledeco$getScale() {
		throw new UnsupportedOperationException();
	}

	@Accessor("DATA_TRANSLATION_ID")
	static EntityDataAccessor<Vector3fc> particledeco$getTranslation() {
		throw new UnsupportedOperationException();
	}

	@Accessor("DATA_GLOW_COLOR_OVERRIDE_ID")
	static EntityDataAccessor<Integer> particledeco$getGlowColor() {
		throw new UnsupportedOperationException();
	}

	@Accessor("DATA_BRIGHTNESS_OVERRIDE_ID")
	static EntityDataAccessor<Integer> particledeco$getBrightness() {
		throw new UnsupportedOperationException();
	}
}
