package com.yoyolee.particledeco.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.Display;
import net.minecraft.world.level.block.state.BlockState;

@Mixin(Display.BlockDisplay.class)
public interface BlockDisplayAccessor {
	@Accessor("DATA_BLOCK_STATE_ID")
	static EntityDataAccessor<BlockState> particledeco$getBlockState() {
		throw new UnsupportedOperationException();
	}
}
