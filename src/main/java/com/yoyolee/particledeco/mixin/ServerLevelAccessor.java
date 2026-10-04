package com.yoyolee.particledeco.mixin;

import java.util.concurrent.atomic.AtomicInteger;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.server.level.ServerLevel;

@Mixin(ServerLevel.class)
public interface ServerLevelAccessor {
	@Accessor("ENTITY_COUNTER")
	static AtomicInteger particledeco$getEntityCounter() {
		throw new UnsupportedOperationException();
	}
}
