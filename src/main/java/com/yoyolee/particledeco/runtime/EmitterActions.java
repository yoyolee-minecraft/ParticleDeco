package com.yoyolee.particledeco.runtime;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import com.yoyolee.particledeco.ParticleDeco;
import com.yoyolee.particledeco.config.MaterialTable;
import com.yoyolee.particledeco.data.Emitter;

/**
 * State transitions shared by interactions, commands and the scheduler.
 */
public final class EmitterActions {
	private EmitterActions() {
	}

	public static Emitter bind(ServerLevel level, BlockPos pos) {
		Emitter emitter = Emitter.waiting(pos, level.getBlockState(pos).getBlock());
		ParticleDeco.manager().put(level, emitter);
		return emitter;
	}

	public static Emitter fill(ServerLevel level, Emitter emitter, ItemStack material, MaterialTable.Entry entry) {
		Emitter updated = emitter.withMaterial(material.copyWithCount(1), entry.particleId());
		ParticleDeco.manager().put(level, updated);
		return updated;
	}

	/**
	 * Returns the emitter to the waiting state and hands back the stored item.
	 */
	public static ItemStack clearMaterial(ServerLevel level, Emitter emitter) {
		ItemStack material = emitter.material();
		ParticleDeco.manager().put(level, emitter.withMaterial(ItemStack.EMPTY, null));
		return material;
	}

	/**
	 * Removes the binding and drops the stored item at the block position. Used for non-player removal and admin commands.
	 */
	@Nullable
	public static Emitter unbindAndDropAt(ServerLevel level, BlockPos pos) {
		Emitter removed = ParticleDeco.manager().remove(level, pos);

		if (removed != null && !removed.material().isEmpty()) {
			Block.popResource(level, pos, removed.material().copy());
		}

		return removed;
	}

	/**
	 * Removes the binding after a player broke the block. The block itself drops through the vanilla loot table,
	 * only the stored item is added here. Creative mode drops nothing extra.
	 */
	@Nullable
	public static Emitter unbindBrokenByPlayer(ServerLevel level, BlockPos pos, boolean creative) {
		Emitter removed = ParticleDeco.manager().remove(level, pos);

		if (removed != null && !creative && !removed.material().isEmpty()) {
			Block.popResource(level, pos, removed.material().copy());
		}

		return removed;
	}
}
