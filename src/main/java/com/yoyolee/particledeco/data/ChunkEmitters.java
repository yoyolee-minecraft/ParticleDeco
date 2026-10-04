package com.yoyolee.particledeco.data;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;

/**
 * Immutable set of emitters inside one chunk, stored as the chunk attachment {@code particledeco:emitters}.
 * Every mutation returns a new instance so that the attachment API sees a new value and marks the chunk dirty.
 */
public final class ChunkEmitters {
	public static final ChunkEmitters EMPTY = new ChunkEmitters(new Long2ObjectLinkedOpenHashMap<>());

	public static final Codec<ChunkEmitters> CODEC = Emitter.CODEC.listOf().xmap(ChunkEmitters::fromList, ChunkEmitters::toList);

	private final Long2ObjectMap<Emitter> emitters;

	private ChunkEmitters(Long2ObjectMap<Emitter> emitters) {
		this.emitters = emitters;
	}

	private static ChunkEmitters fromList(List<Emitter> list) {
		Long2ObjectLinkedOpenHashMap<Emitter> map = new Long2ObjectLinkedOpenHashMap<>();

		for (Emitter emitter : list) {
			map.put(emitter.pos().asLong(), emitter);
		}

		return new ChunkEmitters(map);
	}

	private List<Emitter> toList() {
		return List.copyOf(emitters.values());
	}

	@Nullable
	public Emitter get(BlockPos pos) {
		return emitters.get(pos.asLong());
	}

	public ChunkEmitters with(Emitter emitter) {
		Long2ObjectLinkedOpenHashMap<Emitter> map = new Long2ObjectLinkedOpenHashMap<>(emitters);
		map.put(emitter.pos().asLong(), emitter);
		return new ChunkEmitters(map);
	}

	public ChunkEmitters without(BlockPos pos) {
		if (!emitters.containsKey(pos.asLong())) return this;

		Long2ObjectLinkedOpenHashMap<Emitter> map = new Long2ObjectLinkedOpenHashMap<>(emitters);
		map.remove(pos.asLong());
		return new ChunkEmitters(map);
	}

	public Collection<Emitter> all() {
		return Collections.unmodifiableCollection(emitters.values());
	}

	public int size() {
		return emitters.size();
	}

	public boolean isEmpty() {
		return emitters.isEmpty();
	}

	public Map<Long, Emitter> asMap() {
		return Collections.unmodifiableMap(emitters);
	}
}
