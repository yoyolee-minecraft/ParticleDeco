package com.yoyolee.particledeco.runtime;

/**
 * Rolling statistics for /pdeco stats.
 */
public final class EmitterStats {
	private static final int WINDOW = 20;

	private final int[] packets = new int[WINDOW];
	private final long[] nanos = new long[WINDOW];
	private final long[] sendNanos = new long[WINDOW];
	private int cursor;
	private int lastDeferred;
	private long totalDeferred;
	private long totalDropped;
	private long totalPackets;
	private int lastTickPackets;

	/**
	 * @param tickNanos total scheduler time this tick
	 * @param tickSendNanos part of tickNanos spent inside connection.send. On a real server this only queues the packet
	 *                      for the network thread; with in-memory test connections it also encodes the packet.
	 */
	public void record(int tickPackets, long tickNanos, long tickSendNanos, int deferred) {
		packets[cursor] = tickPackets;
		nanos[cursor] = tickNanos;
		sendNanos[cursor] = tickSendNanos;
		cursor = (cursor + 1) % WINDOW;
		lastTickPackets = tickPackets;
		lastDeferred = deferred;
		totalDeferred += deferred;
		totalPackets += tickPackets;
	}

	public void recordDropped(int dropped) {
		totalDropped += dropped;
	}

	public double averagePacketsPerTick() {
		long sum = 0;

		for (int p : packets) sum += p;

		return sum / (double) WINDOW;
	}

	public double averageMillisPerTick() {
		long sum = 0;

		for (long n : nanos) sum += n;

		return sum / (double) WINDOW / 1_000_000.0;
	}

	public double averageSendMillisPerTick() {
		long sum = 0;

		for (long n : sendNanos) sum += n;

		return sum / (double) WINDOW / 1_000_000.0;
	}

	/**
	 * Scheduler time without the time spent handing packets to the connection.
	 */
	public double averageLogicMillisPerTick() {
		return averageMillisPerTick() - averageSendMillisPerTick();
	}

	/**
	 * Median of the per-tick scheduler time without send calls. One GC pause or a busy CI machine moves the mean of a
	 * 20 tick window a lot; the median shows what a typical tick costs.
	 */
	public double medianLogicMillisPerTick() {
		long[] logic = new long[WINDOW];

		for (int i = 0; i < WINDOW; i++) logic[i] = nanos[i] - sendNanos[i];

		java.util.Arrays.sort(logic);
		return (logic[WINDOW / 2 - 1] + logic[WINDOW / 2]) / 2.0 / 1_000_000.0;
	}

	public int lastTickPackets() {
		return lastTickPackets;
	}

	public int lastDeferred() {
		return lastDeferred;
	}

	public long totalDeferred() {
		return totalDeferred;
	}

	public long totalDropped() {
		return totalDropped;
	}

	public long totalPackets() {
		return totalPackets;
	}
}
