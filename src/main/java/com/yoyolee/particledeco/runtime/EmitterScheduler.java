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
import com.yoyolee.particledeco.config.SpawnPattern;
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
	private long tickSendNanos;

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
		tickSendNanos = 0;
		long tick = server.getTickCount();
		PlayerToggles toggles = PlayerToggles.get(server);

		processDeferred(server, toggles);

		for (ServerLevel level : server.getAllLevels()) {
			if (!manager.hasActive(level)) continue;

			List<ServerPlayer> players = level.players();
			manager.forEachLoaded(level, emitter -> {
				if (emitter.isDue(tick) || hasPattern(emitter)) {
					process(level, emitter, players, toggles, null);
				}
			});
		}

		stats.record(tickPackets, System.nanoTime() - start, tickSendNanos, tickDeferred);
	}

	/**
	 * Plays one emitter immediately against the current budget, outside of its regular schedule.
	 * Used by /pdeco and the GameTests.
	 */
	public void emitNow(ServerLevel level, Emitter emitter, List<ServerPlayer> players) {
		process(level, emitter, players, PlayerToggles.get(level.getServer()), null);
	}

	/**
	 * Starts a fresh budget with the current config limits.
	 */
	public void resetBudget() {
		ModConfig config = ParticleDeco.config();
		budget.reset(config.globalPacketsPerTick, config.perPlayerPacketsPerTick);
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
	 * Emitters whose material uses a random spawn pattern are evaluated every tick instead of on their interval.
	 */
	private static boolean hasPattern(Emitter emitter) {
		if (emitter.isWaiting()) return false;

		MaterialTable.Entry entry = ParticleDeco.materials().find(emitter.material());
		return entry != null && entry.pattern() != null;
	}

	/**
	 * Validates and plays one emitter.
	 *
	 * @param onlyPlayer when not null this is a deferred replay for a single player, block validation is skipped
	 */
	private void process(ServerLevel level, Emitter emitter, List<ServerPlayer> players, PlayerToggles toggles, @Nullable UUID onlyPlayer) {
		BlockPos pos = emitter.pos();
		BlockState state = level.getBlockState(pos);

		if (onlyPlayer == null && BlockValidator.isDestroyed(state)) {
			EmitterActions.unbindAndDropAt(level, pos);
			return;
		}

		if (emitter.isWaiting()) return;

		if (!emitter.redstoneMode().allows(level.hasNeighborSignal(pos))) return;

		MaterialTable.Entry entry = ParticleDeco.materials().find(emitter.material());

		if (entry == null) return;

		ParticleOptions options = ParticleResolver.resolve(entry, emitter.material(), emitter.dye().orElse(null));

		if (options == null) return;

		ModConfig config = ParticleDeco.config();
		Vec3 origin = origin(emitter).add(state.getOffset(pos));
		SpawnPattern pattern = entry.pattern();
		int patternCount = 0;

		if (pattern != null) {
			// A deferred replay re-sends without a new roll; otherwise roll vanilla's per-tick chance.
			patternCount = onlyPlayer != null ? pattern.min() : pattern.roll(random);

			if (patternCount == 0) return;

			patternCount = Math.min(patternCount, config.maxCountPerEmit);
		}
		double maxDistSq = (double) config.viewDistance * config.viewDistance;
		List<ClientboundLevelParticlesPacket> packets = null;

		for (ServerPlayer player : players) {
			if (toggles.isDisabled(player.getUUID())) continue;

			if (player.position().distanceToSqr(origin) > maxDistSq) continue;

			if (packets == null) {
				packets = pattern != null
						? buildPatternPackets(options, origin, pattern, patternCount, entry.motion())
						: buildPackets(emitter, options, origin, Math.min(emitter.count(), config.maxCountPerEmit), entry.motion());
			}

			if (budget.tryConsume(player.getUUID(), packets.size())) {
				long sendStart = System.nanoTime();

				for (ClientboundLevelParticlesPacket packet : packets) {
					player.connection.send(packet);
				}

				tickSendNanos += System.nanoTime() - sendStart;

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
	 *
	 * @param motion fixed velocity, or null. With motion every particle needs its own packet (count 0) so the client
	 *               applies the velocity exactly; the spread is then applied here instead of by the client.
	 */
	public List<ClientboundLevelParticlesPacket> buildPackets(Emitter emitter, ParticleOptions options, Vec3 origin, int count, @Nullable Vec3 motion) {
		List<ClientboundLevelParticlesPacket> packets = new ArrayList<>();
		float spread = emitter.spread();
		float size = emitter.shapeSize();

		switch (emitter.shape()) {
			case POINT -> {
				if (motion == null) {
					packets.add(packet(options, origin.x, origin.y, origin.z, spread, count, null));
				} else {
					for (int i = 0; i < count; i++) {
						packets.add(packet(options, origin.x, origin.y, origin.z, spread, 1, motion));
					}
				}
			}
			case COLUMN -> {
				for (int i = 0; i < count; i++) {
					double y = count == 1 ? 0 : size * i / (count - 1);
					packets.add(packet(options, origin.x, origin.y + y, origin.z, spread, 1, motion));
				}
			}
			case RING -> {
				double phase = random.nextDouble() * (Math.PI * 2) / Math.max(1, count);

				for (int i = 0; i < count; i++) {
					double angle = phase + (Math.PI * 2) * i / count;
					packets.add(packet(options, origin.x + Math.cos(angle) * size, origin.y, origin.z + Math.sin(angle) * size, spread, 1, motion));
				}
			}
			case AREA -> {
				double half = size / 2.0;

				for (int i = 0; i < count; i++) {
					double x = origin.x + (random.nextDouble() * 2 - 1) * half;
					double z = origin.z + (random.nextDouble() * 2 - 1) * half;
					packets.add(packet(options, x, origin.y, z, spread, 1, motion));
				}
			}
		}

		return packets;
	}

	/**
	 * Packets for one hit of a random spawn pattern: each particle at its own sampled position.
	 */
	public List<ClientboundLevelParticlesPacket> buildPatternPackets(ParticleOptions options, Vec3 origin, SpawnPattern pattern, int count, @Nullable Vec3 motion) {
		List<ClientboundLevelParticlesPacket> packets = new ArrayList<>(count);

		for (int i = 0; i < count; i++) {
			Vec3 at = pattern.sample(origin, random);
			packets.add(packet(options, at.x, at.y, at.z, 0.0f, 1, motion));
		}

		return packets;
	}

	private ClientboundLevelParticlesPacket packet(ParticleOptions options, double x, double y, double z, float spread, int count, @Nullable Vec3 motion) {
		if (motion == null) {
			return new ClientboundLevelParticlesPacket(options, false, false, x, y, z, spread, spread, spread, 0.0f, count);
		}

		// count 0: the client spawns exactly one particle and uses (dx, dy, dz) * speed as its velocity.
		double jx = x + (random.nextDouble() * 2 - 1) * spread;
		double jy = y + (random.nextDouble() * 2 - 1) * spread;
		double jz = z + (random.nextDouble() * 2 - 1) * spread;
		return new ClientboundLevelParticlesPacket(options, false, false, jx, jy, jz, (float) motion.x, (float) motion.y, (float) motion.z, 1.0f, 0);
	}
}
