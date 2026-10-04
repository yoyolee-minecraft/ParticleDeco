package com.yoyolee.particledeco.runtime;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import com.yoyolee.particledeco.ParticleDeco;
import com.yoyolee.particledeco.config.MaterialTable;
import com.yoyolee.particledeco.config.ModConfig;
import com.yoyolee.particledeco.data.Emitter;

/**
 * Runs once per server tick: validates bound blocks, applies redstone gating, builds vanilla particle packets and
 * sends them to nearby players within the packet budget. Over-budget sends are deferred to the next tick.
 */
public final class EmitterScheduler {
	private static final int MAX_DEFERRED = 20_000;

	private record Deferred(ResourceKey<Level> level, BlockPos pos, UUID player) {
	}

	private final EmitterManager manager;
	private final PacketBudget budget = new PacketBudget();
	private final EmitterStats stats = new EmitterStats();
	private final RandomSource random = RandomSource.create();
	private Deque<Deferred> deferred = new ArrayDeque<>();
	private final Set<Deferred> deferredSet = new LinkedHashSet<>();
	private int tickPackets;
	private int tickDeferred;

	public EmitterScheduler(EmitterManager manager) {
		this.manager = manager;
	}

	public EmitterStats stats() {
		return stats;
	}

	public int pendingDeferred() {
		return deferred.size();
	}

	public void clear() {
		deferred.clear();
		deferredSet.clear();
	}

	public void tick(MinecraftServer server) {
		long start = System.nanoTime();
		ModConfig config = ParticleDeco.config();
		budget.reset(config.globalPacketsPerTick, config.perPlayerPacketsPerTick);
		tickPackets = 0;
		tickDeferred = 0;
		long tick = server.getTickCount();
		PlayerToggles toggles = PlayerToggles.get(server);

		processDeferred(server, toggles);

		for (ServerLevel level : server.getAllLevels()) {
			if (!manager.hasActive(level)) continue;

			List<ServerPlayer> players = level.players();
			manager.forEachLoaded(level, emitter -> {
				if (emitter.isDue(tick)) {
					process(level, emitter, players, toggles, null);
				}
			});
		}

		stats.record(tickPackets, System.nanoTime() - start, tickDeferred);
	}

	private void processDeferred(MinecraftServer server, PlayerToggles toggles) {
		if (deferred.isEmpty()) return;

		Deque<Deferred> current = deferred;
		deferred = new ArrayDeque<>();
		deferredSet.clear();

		for (Deferred entry : current) {
			ServerLevel level = server.getLevel(entry.level());
			ServerPlayer player = server.getPlayerList().getPlayer(entry.player());

			if (level == null || player == null || player.level() != level) continue;

			Emitter emitter = manager.get(level, entry.pos());

			if (emitter == null) continue;

			process(level, emitter, List.of(player), toggles, entry.player());
		}
	}

	/**
	 * Validates and plays one emitter.
	 *
	 * @param onlyPlayer when not null this is a deferred replay for a single player, block validation is skipped
	 */
	private void process(ServerLevel level, Emitter emitter, List<ServerPlayer> players, PlayerToggles toggles, @Nullable UUID onlyPlayer) {
		BlockPos pos = emitter.pos();

		if (onlyPlayer == null) {
			BlockState state = level.getBlockState(pos);

			if (!BlockValidator.stillBound(state, emitter.block())) {
				EmitterActions.unbindAndDropAt(level, pos);
				return;
			}
		}

		if (emitter.isWaiting()) return;

		if (!emitter.redstoneMode().allows(level.hasNeighborSignal(pos))) return;

		MaterialTable.Entry entry = ParticleDeco.materials().find(emitter.material());

		if (entry == null) return;

		ParticleOptions options = ParticleResolver.resolve(entry, emitter.material(), emitter.dye().orElse(null));

		if (options == null) return;

		ModConfig config = ParticleDeco.config();
		Vec3 origin = origin(emitter);
		double maxDistSq = (double) config.viewDistance * config.viewDistance;
		List<ClientboundLevelParticlesPacket> packets = null;

		for (ServerPlayer player : players) {
			if (onlyPlayer == null && toggles.isDisabled(player.getUUID())) continue;

			if (player.position().distanceToSqr(origin) > maxDistSq) continue;

			if (packets == null) packets = buildPackets(emitter, options, origin, Math.min(emitter.count(), config.maxCountPerEmit));

			if (budget.tryConsume(player.getUUID(), packets.size())) {
				for (ClientboundLevelParticlesPacket packet : packets) {
					player.connection.send(packet);
				}

				tickPackets += packets.size();
			} else {
				defer(new Deferred(level.dimension(), pos, player.getUUID()));
			}
		}
	}

	private void defer(Deferred entry) {
		tickDeferred++;

		if (!deferredSet.add(entry)) return;

		deferred.addLast(entry);

		if (deferred.size() > MAX_DEFERRED) {
			Deferred dropped = deferred.pollFirst();
			deferredSet.remove(dropped);
			stats.recordDropped(1);
		}
	}

	public static Vec3 origin(Emitter emitter) {
		BlockPos pos = emitter.pos();
		return new Vec3(pos.getX() + 0.5 + emitter.offsetX() / 16.0,
				pos.getY() + 1.0 + emitter.offsetY() / 16.0,
				pos.getZ() + 0.5 + emitter.offsetZ() / 16.0);
	}

	/**
	 * Server-side shape sampling. Each returned packet counts once against the budget per receiving player.
	 */
	public List<ClientboundLevelParticlesPacket> buildPackets(Emitter emitter, ParticleOptions options, Vec3 origin, int count) {
		List<ClientboundLevelParticlesPacket> packets = new ArrayList<>();
		float spread = emitter.spread();
		float size = emitter.shapeSize();

		switch (emitter.shape()) {
			case POINT -> packets.add(packet(options, origin.x, origin.y, origin.z, spread, count));
			case COLUMN -> {
				for (int i = 0; i < count; i++) {
					double y = count == 1 ? 0 : size * i / (count - 1);
					packets.add(packet(options, origin.x, origin.y + y, origin.z, spread, 1));
				}
			}
			case RING -> {
				double phase = random.nextDouble() * (Math.PI * 2) / Math.max(1, count);

				for (int i = 0; i < count; i++) {
					double angle = phase + (Math.PI * 2) * i / count;
					packets.add(packet(options, origin.x + Math.cos(angle) * size, origin.y, origin.z + Math.sin(angle) * size, spread, 1));
				}
			}
			case AREA -> {
				double half = size / 2.0;

				for (int i = 0; i < count; i++) {
					double x = origin.x + (random.nextDouble() * 2 - 1) * half;
					double z = origin.z + (random.nextDouble() * 2 - 1) * half;
					packets.add(packet(options, x, origin.y, z, spread, 1));
				}
			}
		}

		return packets;
	}

	private static ClientboundLevelParticlesPacket packet(ParticleOptions options, double x, double y, double z, float spread, int count) {
		return new ClientboundLevelParticlesPacket(options, false, false, x, y, z, spread, spread, spread, 0.0f, count);
	}
}
