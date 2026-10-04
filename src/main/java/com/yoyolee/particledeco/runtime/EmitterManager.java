package com.yoyolee.particledeco.runtime;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

import com.yoyolee.particledeco.ParticleDeco;
import com.yoyolee.particledeco.data.ChunkEmitters;
import com.yoyolee.particledeco.data.Emitter;

/**
 * Owns the chunk attachment and an in-memory index of loaded chunks that contain emitters.
 * The attachment is the source of truth; the index only speeds up per-tick iteration.
 */
public final class EmitterManager {
	public static final AttachmentType<ChunkEmitters> EMITTERS = AttachmentRegistry.create(ParticleDeco.id("emitters"),
			builder -> builder.persistent(ChunkEmitters.CODEC));

	private final Map<ResourceKey<Level>, Long2ObjectMap<LevelChunk>> active = new HashMap<>();

	public static void init() {
		// Loads the class so the attachment type is registered during mod initialisation.
	}

	public void onChunkLoad(ServerLevel level, LevelChunk chunk) {
		ChunkEmitters emitters = chunk.getAttached(EMITTERS);

		if (emitters != null && !emitters.isEmpty()) {
			active.computeIfAbsent(level.dimension(), k -> new Long2ObjectOpenHashMap<>()).put(chunk.getPos().pack(), chunk);
		}
	}

	public void onChunkUnload(ServerLevel level, LevelChunk chunk) {
		Long2ObjectMap<LevelChunk> map = active.get(level.dimension());

		if (map != null) {
			map.remove(chunk.getPos().pack());
		}
	}

	public void clear() {
		active.clear();
	}

	@Nullable
	public Emitter get(ServerLevel level, BlockPos pos) {
		if (!level.isLoaded(pos)) return null;

		ChunkEmitters emitters = level.getChunkAt(pos).getAttached(EMITTERS);
		return emitters == null ? null : emitters.get(pos);
	}

	public int countInChunk(ServerLevel level, BlockPos pos) {
		ChunkEmitters emitters = level.getChunkAt(pos).getAttached(EMITTERS);
		return emitters == null ? 0 : emitters.size();
	}

	public void put(ServerLevel level, Emitter emitter) {
		LevelChunk chunk = level.getChunkAt(emitter.pos());
		ChunkEmitters current = chunk.getAttachedOrElse(EMITTERS, ChunkEmitters.EMPTY);
		chunk.setAttached(EMITTERS, current.with(emitter));
		chunk.markUnsaved();
		active.computeIfAbsent(level.dimension(), k -> new Long2ObjectOpenHashMap<>()).put(chunk.getPos().pack(), chunk);
	}

	@Nullable
	public Emitter remove(ServerLevel level, BlockPos pos) {
		LevelChunk chunk = level.getChunkAt(pos);
		ChunkEmitters current = chunk.getAttached(EMITTERS);

		if (current == null) return null;

		Emitter removed = current.get(pos);

		if (removed == null) return null;

		ChunkEmitters updated = current.without(pos);

		if (updated.isEmpty()) {
			chunk.removeAttached(EMITTERS);
			Long2ObjectMap<LevelChunk> map = active.get(level.dimension());

			if (map != null) map.remove(chunk.getPos().pack());
		} else {
			chunk.setAttached(EMITTERS, updated);
		}

		chunk.markUnsaved();
		return removed;
	}

	/**
	 * Iterates every emitter in loaded chunks of a level. The consumer may modify emitters; iteration works on a snapshot.
	 */
	public void forEachLoaded(ServerLevel level, Consumer<Emitter> consumer) {
		Long2ObjectMap<LevelChunk> map = active.get(level.dimension());

		if (map == null || map.isEmpty()) return;

		List<Emitter> snapshot = new ArrayList<>();

		for (LevelChunk chunk : map.values()) {
			ChunkEmitters emitters = chunk.getAttached(EMITTERS);

			if (emitters != null) snapshot.addAll(emitters.all());
		}

		snapshot.forEach(consumer);
	}

	/**
	 * Emitters in loaded chunks within a square chunk radius around a position.
	 */
	public List<Emitter> near(ServerLevel level, BlockPos center, int blockRadius) {
		List<Emitter> result = new ArrayList<>();
		Long2ObjectMap<LevelChunk> map = active.get(level.dimension());

		if (map == null || map.isEmpty()) return result;

		ChunkPos centerChunk = ChunkPos.containing(center);
		int chunkRadius = (blockRadius >> 4) + 1;
		long radiusSq = (long) blockRadius * blockRadius;

		for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
			for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
				LevelChunk chunk = map.get(ChunkPos.pack(centerChunk.x() + dx, centerChunk.z() + dz));

				if (chunk == null) continue;

				ChunkEmitters emitters = chunk.getAttached(EMITTERS);

				if (emitters == null) continue;

				for (Emitter emitter : emitters.all()) {
					if (emitter.pos().distSqr(center) <= radiusSq) {
						result.add(emitter);
					}
				}
			}
		}

		return result;
	}

	public boolean hasActive(ServerLevel level) {
		Long2ObjectMap<LevelChunk> map = active.get(level.dimension());
		return map != null && !map.isEmpty();
	}

	public int activeChunkCount() {
		int total = 0;

		for (Long2ObjectMap<LevelChunk> map : active.values()) total += map.size();

		return total;
	}

	public int loadedEmitterCount() {
		int total = 0;

		for (Long2ObjectMap<LevelChunk> map : active.values()) {
			for (LevelChunk chunk : map.values()) {
				ChunkEmitters emitters = chunk.getAttached(EMITTERS);

				if (emitters != null) total += emitters.size();
			}
		}

		return total;
	}

	public Collection<ResourceKey<Level>> activeLevels() {
		return active.keySet();
	}
}
