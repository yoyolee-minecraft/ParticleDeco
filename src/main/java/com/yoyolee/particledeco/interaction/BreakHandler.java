package com.yoyolee.particledeco.interaction;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import com.yoyolee.particledeco.ParticleDeco;
import com.yoyolee.particledeco.runtime.EmitterActions;

/**
 * Breaking a bound block unbinds it. The block drops through its vanilla loot table; only the stored material is
 * added. Creative mode adds nothing. The core is not returned.
 */
public final class BreakHandler {
	private BreakHandler() {
	}

	public static void afterBreak(Level level, Player player, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity) {
		if (!(level instanceof ServerLevel serverLevel)) return;

		if (ParticleDeco.manager().get(serverLevel, pos) == null) return;

		boolean creative = player instanceof ServerPlayer serverPlayer ? UseBlockHandler.isCreative(serverPlayer) : player.isCreative();
		EmitterActions.unbindBrokenByPlayer(serverLevel, pos, creative);
	}
}
