package com.yoyolee.particledeco.runtime;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import com.yoyolee.particledeco.ParticleDeco;

/**
 * Decides which blocks can carry an emitter and whether a bound emitter still matches its block.
 */
public final class BlockValidator {
	private BlockValidator() {
	}

	public static boolean canBind(BlockState state) {
		if (state.isAir() || state.liquid()) return false;

		if (!state.getFluidState().isEmpty() && state.getBlock() == Blocks.BUBBLE_COLUMN) return false;

		Block block = state.getBlock();

		if (block == Blocks.SUSPICIOUS_SAND || block == Blocks.SUSPICIOUS_GRAVEL) return false;

		String id = BuiltInRegistries.BLOCK.getKey(block).toString();
		return !ParticleDeco.config().bindBlacklist.contains(id);
	}

	/**
	 * Only the block type is compared, never the block state, so doors, furnaces and crops keep their emitter.
	 */
	public static boolean stillBound(BlockState current, Block boundBlock) {
		return current.getBlock() == boundBlock;
	}
}
