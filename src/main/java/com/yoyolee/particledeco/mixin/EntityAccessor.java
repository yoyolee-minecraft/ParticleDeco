package com.yoyolee.particledeco.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.Entity;

@Mixin(Entity.class)
public interface EntityAccessor {
	@Accessor("DATA_SHARED_FLAGS_ID")
	static EntityDataAccessor<Byte> particledeco$getSharedFlags() {
		throw new UnsupportedOperationException();
	}

	@Accessor("FLAG_GLOWING")
	static int particledeco$getFlagGlowing() {
		throw new UnsupportedOperationException();
	}
}
