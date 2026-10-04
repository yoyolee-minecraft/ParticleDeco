package com.yoyolee.particledeco.interaction;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import com.yoyolee.particledeco.ParticleDeco;
import com.yoyolee.particledeco.config.MaterialTable;
import com.yoyolee.particledeco.data.Emitter;
import com.yoyolee.particledeco.gui.EditorGui;
import com.yoyolee.particledeco.runtime.BlockValidator;
import com.yoyolee.particledeco.runtime.EmitterActions;
import com.yoyolee.particledeco.util.Messages;

/**
 * Right click handling. Runs before vanilla block use and item placement, so returning a non-PASS result
 * prevents a campfire from being placed or a chest from opening while filling an emitter.
 */
public final class UseBlockHandler {
	private UseBlockHandler() {
	}

	public static InteractionResult onUseBlock(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
		if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer) || !(level instanceof ServerLevel serverLevel)) {
			return InteractionResult.PASS;
		}

		if (serverPlayer.isSpectator()) return InteractionResult.PASS;

		ItemStack stack = player.getItemInHand(hand);
		BlockPos pos = hit.getBlockPos();
		Emitter emitter = ParticleDeco.manager().get(serverLevel, pos);

		if (CoreItem.isCore(stack)) {
			return useCore(serverPlayer, serverLevel, pos, stack, emitter);
		}

		if (stack.getItem() == Items.BRUSH) {
			return useBrush(serverPlayer, serverLevel, hand, stack, emitter, hit);
		}

		if (emitter != null && emitter.isWaiting()) {
			MaterialTable.Entry entry = ParticleDeco.materials().find(stack);

			if (entry != null) {
				return fill(serverPlayer, serverLevel, stack, emitter, entry);
			}
		}

		return InteractionResult.PASS;
	}

	private static InteractionResult useCore(ServerPlayer player, ServerLevel level, BlockPos pos, ItemStack stack, Emitter emitter) {
		if (emitter != null) {
			EditorGui.open(player, level, pos);
			return InteractionResult.SUCCESS;
		}

		BlockState state = level.getBlockState(pos);

		if (!BlockValidator.canBind(state)) {
			player.sendOverlayMessage(Messages.error("bind.invalid"));
			resync(player);
			return InteractionResult.FAIL;
		}

		int limit = ParticleDeco.config().perChunk;

		if (ParticleDeco.manager().countInChunk(level, pos) >= limit) {
			player.sendOverlayMessage(Messages.error("bind.chunk_limit", limit));
			resync(player);
			return InteractionResult.FAIL;
		}

		EmitterActions.bind(level, pos);

		if (!isCreative(player)) {
			stack.shrink(1);
		}

		player.sendOverlayMessage(Messages.info("bind.success"));
		ParticleDeco.outlines().refreshNow(player);
		resync(player);
		return InteractionResult.SUCCESS;
	}

	private static InteractionResult useBrush(ServerPlayer player, ServerLevel level, InteractionHand hand, ItemStack stack, Emitter emitter, BlockHitResult hit) {
		if (emitter == null || emitter.isWaiting()) {
			// No effect on waiting emitters; plain blocks keep vanilla brush behaviour.
			return InteractionResult.PASS;
		}

		ItemStack material = EmitterActions.clearMaterial(level, emitter);

		if (!material.isEmpty()) {
			dropInFront(player, level, material, hit);
		}

		int cost = ParticleDeco.config().brushDurabilityCost;

		if (cost > 0 && !isCreative(player)) {
			stack.hurtAndBreak(cost, player, hand);
		}

		player.sendOverlayMessage(Messages.info("brush.success"));
		ParticleDeco.outlines().refreshNow(player);
		resync(player);
		return InteractionResult.SUCCESS;
	}

	private static InteractionResult fill(ServerPlayer player, ServerLevel level, ItemStack stack, Emitter emitter, MaterialTable.Entry entry) {
		EmitterActions.fill(level, emitter, stack, entry);

		if (!isCreative(player) && ParticleDeco.config().consumeMaterial) {
			stack.shrink(1);
		}

		player.sendOverlayMessage(Messages.info("fill.success", entry.particleId().getPath()));
		ParticleDeco.outlines().refreshNow(player);
		resync(player);
		return InteractionResult.SUCCESS;
	}

	private static void dropInFront(ServerPlayer player, ServerLevel level, ItemStack stack, BlockHitResult hit) {
		Direction face = hit.getDirection();
		BlockPos pos = hit.getBlockPos();
		Vec3 at = Vec3.atCenterOf(pos).add(face.getStepX() * 0.7, face.getStepY() * 0.7, face.getStepZ() * 0.7);
		net.minecraft.world.entity.item.ItemEntity item = new net.minecraft.world.entity.item.ItemEntity(level, at.x, at.y, at.z, stack.copy());
		item.setDefaultPickUpDelay();
		level.addFreshEntity(item);
	}

	/**
	 * Server-authoritative game mode. Same as {@link ServerPlayer#isCreative()} in normal play, but does not depend on
	 * {@code Player#gameMode()}, which test mock players override.
	 */
	public static boolean isCreative(ServerPlayer player) {
		return player.gameMode.isCreative();
	}

	/**
	 * The vanilla client may have predicted a block placement or an item count change; force a full inventory sync.
	 */
	private static void resync(ServerPlayer player) {
		player.containerMenu.sendAllDataToRemote();
		player.inventoryMenu.sendAllDataToRemote();
	}
}
