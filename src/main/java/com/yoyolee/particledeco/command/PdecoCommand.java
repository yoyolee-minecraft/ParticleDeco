package com.yoyolee.particledeco.command;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import com.yoyolee.particledeco.ParticleDeco;
import com.yoyolee.particledeco.config.ConfigManager;
import com.yoyolee.particledeco.data.Emitter;
import com.yoyolee.particledeco.interaction.CoreItem;
import com.yoyolee.particledeco.runtime.EmitterActions;
import com.yoyolee.particledeco.runtime.EmitterStats;
import com.yoyolee.particledeco.runtime.PlayerToggles;
import com.yoyolee.particledeco.util.Messages;

public final class PdecoCommand {
	private static final int DEFAULT_NEAR_RADIUS = 16;
	private static final int MAX_RADIUS = 128;

	private PdecoCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("pdeco")
				.then(Commands.literal("toggle").executes(PdecoCommand::toggle))
				.then(Commands.literal("near")
						.executes(ctx -> near(ctx, DEFAULT_NEAR_RADIUS))
						.then(Commands.argument("radius", IntegerArgumentType.integer(1, MAX_RADIUS))
								.executes(ctx -> near(ctx, IntegerArgumentType.getInteger(ctx, "radius")))))
				.then(Commands.literal("remove")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(PdecoCommand::remove)))
				.then(Commands.literal("purge")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(Commands.argument("radius", IntegerArgumentType.integer(1, MAX_RADIUS))
								.executes(ctx -> purge(ctx, IntegerArgumentType.getInteger(ctx, "radius")))))
				.then(Commands.literal("give")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(Commands.literal("core")
								.executes(ctx -> give(ctx, ctx.getSource().getPlayerOrException(), 1))
								.then(Commands.argument("player", EntityArgument.player())
										.executes(ctx -> give(ctx, EntityArgument.getPlayer(ctx, "player"), 1))
										.then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
												.executes(ctx -> give(ctx, EntityArgument.getPlayer(ctx, "player"), IntegerArgumentType.getInteger(ctx, "count")))))))
				.then(Commands.literal("reload")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.executes(PdecoCommand::reload))
				.then(Commands.literal("stats")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.executes(PdecoCommand::stats)));
	}

	private static int toggle(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		boolean disabled = PlayerToggles.get(ctx.getSource().getServer()).toggle(player.getUUID());
		ctx.getSource().sendSuccess(() -> Messages.text(disabled ? "toggle.off" : "toggle.on"), false);
		return disabled ? 0 : 1;
	}

	private static int near(CommandContext<CommandSourceStack> ctx, int radius) {
		CommandSourceStack source = ctx.getSource();
		ServerLevel level = source.getLevel();
		BlockPos center = BlockPos.containing(source.getPosition());
		List<Emitter> emitters = ParticleDeco.manager().near(level, center, radius);

		if (emitters.isEmpty()) {
			source.sendSuccess(() -> Messages.text("near.none", radius), false);
			return 0;
		}

		emitters.sort(Comparator.comparingDouble(e -> e.pos().distSqr(center)));
		source.sendSuccess(() -> Messages.text("near.header", radius, emitters.size()), false);

		for (Emitter e : emitters) {
			String state = Messages.raw(e.isWaiting() ? "state.waiting" : "state.playing");
			String particle = e.isWaiting() ? "-" : e.particle().map(Object::toString).orElse("?");
			BlockPos p = e.pos();
			source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT, " %d %d %d  %s  %s", p.getX(), p.getY(), p.getZ(), state, particle)), false);
		}

		return emitters.size();
	}

	private static int remove(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		CommandSourceStack source = ctx.getSource();
		BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
		Emitter removed = EmitterActions.unbindAndDropAt(source.getLevel(), pos);

		if (removed == null) {
			source.sendFailure(Messages.text("remove.none"));
			return 0;
		}

		source.sendSuccess(() -> Messages.text("remove.success", pos.getX(), pos.getY(), pos.getZ()), true);
		return 1;
	}

	private static int purge(CommandContext<CommandSourceStack> ctx, int radius) {
		CommandSourceStack source = ctx.getSource();
		ServerLevel level = source.getLevel();
		BlockPos center = BlockPos.containing(source.getPosition());
		List<Emitter> emitters = ParticleDeco.manager().near(level, center, radius);

		for (Emitter e : emitters) {
			EmitterActions.unbindAndDropAt(level, e.pos());
		}

		int count = emitters.size();
		source.sendSuccess(() -> Messages.text("purge.success", count), true);
		return count;
	}

	private static int give(CommandContext<CommandSourceStack> ctx, ServerPlayer target, int count) {
		ItemStack stack = CoreItem.create(count);

		if (!target.addItem(stack)) {
			target.drop(stack, false);
		}

		ctx.getSource().sendSuccess(() -> Messages.text("give.success", count, target.getName().getString()), true);
		return count;
	}

	private static int reload(CommandContext<CommandSourceStack> ctx) {
		ConfigManager.Result result = ConfigManager.load();

		for (String warning : result.warnings()) {
			ctx.getSource().sendFailure(Component.literal(warning));
		}

		ctx.getSource().sendSuccess(() -> Messages.text("reload.success", ParticleDeco.materials().size(), result.warnings().size()), true);
		return 1;
	}

	private static int stats(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		EmitterStats stats = ParticleDeco.scheduler().stats();
		source.sendSuccess(() -> Messages.text("stats.line1", ParticleDeco.manager().loadedEmitterCount(), ParticleDeco.manager().activeChunkCount()), false);
		source.sendSuccess(() -> Messages.text("stats.line2", stats.lastTickPackets(), String.format(Locale.ROOT, "%.1f", stats.averagePacketsPerTick()), stats.totalPackets()), false);
		source.sendSuccess(() -> Messages.text("stats.line3", stats.lastDeferred(), ParticleDeco.scheduler().pendingDeferred(), stats.totalDeferred(), stats.totalDropped()), false);
		source.sendSuccess(() -> Messages.text("stats.line4", String.format(Locale.ROOT, "%.3f", stats.averageMillisPerTick())), false);
		return ParticleDeco.manager().loadedEmitterCount();
	}
}
