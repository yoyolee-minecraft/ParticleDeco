package com.yoyolee.particledeco.runtime;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Per-tick packet allowance, both global and per player. Reset at the start of every server tick.
 */
public final class PacketBudget {
	private int globalLimit;
	private int perPlayerLimit;
	private int globalUsed;
	private final Map<UUID, Integer> perPlayerUsed = new HashMap<>();

	public void reset(int globalLimit, int perPlayerLimit) {
		this.globalLimit = globalLimit;
		this.perPlayerLimit = perPlayerLimit;
		this.globalUsed = 0;
		this.perPlayerUsed.clear();
	}

	public boolean tryConsume(UUID player, int packets) {
		if (globalUsed + packets > globalLimit) return false;

		int used = perPlayerUsed.getOrDefault(player, 0);

		if (used + packets > perPlayerLimit) return false;

		globalUsed += packets;
		perPlayerUsed.put(player, used + packets);
		return true;
	}

	public int globalUsed() {
		return globalUsed;
	}

	public int usedBy(UUID player) {
		return perPlayerUsed.getOrDefault(player, 0);
	}
}
