package com.yoyolee.particledeco.outline;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import com.yoyolee.particledeco.ParticleDeco;
import com.yoyolee.particledeco.config.ModConfig;
import com.yoyolee.particledeco.data.Emitter;
import com.yoyolee.particledeco.interaction.CoreItem;

/**
 * Keeps, per player, the set of fake outline entities that player currently sees.
 * Playing emitters are outlined only while the player holds a core or a brush. Waiting emitters stay outlined for
 * outline.waitingShowSeconds after the player puts the tool away. Nothing here is persisted.
 */
public final class OutlineTracker {
	private static final int REFRESH_INTERVAL = 10;

	private static final class View {
		ResourceKey<Level> level;
		boolean holdingTool;
		/** Last server tick this player held a tool; far in the past until they do. */
		long lastToolTick = Long.MIN_VALUE / 2;
		final Long2ObjectMap<FakeDisplayEntity> shown = new Long2ObjectOpenHashMap<>();
	}

	private final Map<UUID, View> views = new HashMap<>();

	/**
	 * Observes every outline packet sent to a player. Used by the GameTests to replay the client's view.
	 */
	public interface PacketTap {
		void sent(ServerPlayer player, Packet<?> packet);
	}

	@Nullable
	private static volatile PacketTap tap;

	public static void setTap(@Nullable PacketTap newTap) {
		tap = newTap;
	}

	private static void sendPacket(ServerPlayer player, Packet<?> packet) {
		player.connection.send(packet);
		PacketTap current = tap;

		if (current != null) current.sent(player, packet);
	}

	public static boolean isTool(ItemStack stack) {
		return CoreItem.isCore(stack) || stack.getItem() == Items.BRUSH;
	}

	public static boolean isHoldingTool(ServerPlayer player) {
		return isTool(player.getMainHandItem()) || isTool(player.getOffhandItem());
	}

	public void tick(MinecraftServer server) {
		long tick = server.getTickCount();
		Set<UUID> online = new HashSet<>();

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			UUID uuid = player.getUUID();
			online.add(uuid);
			View view = views.computeIfAbsent(uuid, k -> new View());
			boolean holding = isHoldingTool(player);
			boolean force = holding != view.holdingTool;
			view.holdingTool = holding;

			if (holding) view.lastToolTick = tick;
			ResourceKey<Level> dimension = player.level().dimension();

			if (view.level != dimension) {
				// The client drops all entities when it changes dimension.
				view.shown.clear();
				view.level = dimension;
				force = true;
			}

			if (force || (tick + (uuid.hashCode() & 0xFFFF)) % REFRESH_INTERVAL == 0) {
				refresh(player, view);
			}
		}

		views.keySet().removeIf(uuid -> !online.contains(uuid));
	}

	/**
	 * Called after respawn or when a player otherwise needs a full resend.
	 */
	public void reset(ServerPlayer player) {
		View view = views.remove(player.getUUID());

		if (view != null && !view.shown.isEmpty()) {
			IntArrayList ids = new IntArrayList();
			view.shown.values().forEach(e -> ids.add(e.id()));
			sendPacket(player, new ClientboundRemoveEntitiesPacket(ids));
		}
	}

	public void clear() {
		views.clear();
	}

	/**
	 * Binding or brushing counts as holding a tool even when it used up the last core or broke the brush.
	 */
	public void markToolUsed(ServerPlayer player) {
		setLastToolTick(player, player.level().getServer().getTickCount());
	}

	/**
	 * Test hook: pretend the player last held a tool at the given server tick.
	 */
	public void setLastToolTick(ServerPlayer player, long tick) {
		views.computeIfAbsent(player.getUUID(), k -> new View()).lastToolTick = tick;
	}

	/**
	 * Whether waiting emitters are still outlined for this player without a tool in hand.
	 */
	private static boolean waitingVisible(ServerPlayer player, View view) {
		int seconds = ParticleDeco.config().outlineWaitingShowSeconds;

		if (seconds < 0) return true;

		return player.level().getServer().getTickCount() - view.lastToolTick < seconds * 20L;
	}

	public int shownCount(ServerPlayer player) {
		View view = views.get(player.getUUID());
		return view == null ? 0 : view.shown.size();
	}

	public boolean isShown(ServerPlayer player, BlockPos pos) {
		View view = views.get(player.getUUID());
		return view != null && view.shown.containsKey(pos.asLong());
	}

	public void refreshNow(ServerPlayer player) {
		View view = views.computeIfAbsent(player.getUUID(), k -> new View());
		view.holdingTool = isHoldingTool(player);

		if (view.holdingTool) view.lastToolTick = player.level().getServer().getTickCount();

		view.level = player.level().dimension();
		refresh(player, view);
	}

	private void refresh(ServerPlayer player, View view) {
		ModConfig config = ParticleDeco.config();
		ServerLevel level = player.level();
		List<Emitter> nearby = ParticleDeco.manager().near(level, player.blockPosition(), config.outlineShowDistance);
		Long2ObjectMap<Emitter> desired = new Long2ObjectOpenHashMap<>();
		boolean showWaiting = view.holdingTool || waitingVisible(player, view);

		for (Emitter emitter : nearby) {
			if (view.holdingTool || (emitter.isWaiting() && showWaiting)) {
				desired.put(emitter.pos().asLong(), emitter);
			}
		}

		IntArrayList removed = new IntArrayList();
		Iterator<Long2ObjectMap.Entry<FakeDisplayEntity>> it = view.shown.long2ObjectEntrySet().iterator();

		while (it.hasNext()) {
			Long2ObjectMap.Entry<FakeDisplayEntity> entry = it.next();

			if (!desired.containsKey(entry.getLongKey())) {
				removed.add(entry.getValue().id());
				it.remove();
			}
		}

		if (!removed.isEmpty()) {
			sendPacket(player, new ClientboundRemoveEntitiesPacket(removed));
		}

		for (Emitter emitter : desired.values()) {
			BlockState state = level.getBlockState(emitter.pos());
			int color = emitter.isWaiting() ? config.outlineWaitingColor : config.outlinePlayingColor;
			FakeDisplayEntity existing = view.shown.get(emitter.pos().asLong());

			if (existing == null) {
				FakeDisplayEntity created = new FakeDisplayEntity(emitter.pos(), state, color);
				view.shown.put(emitter.pos().asLong(), created);
				send(player, created.spawnPackets());
			} else if (existing.state() != state || existing.color() != color) {
				if (existing.update(state, color)) {
					sendPacket(player, new ClientboundRemoveEntitiesPacket(existing.id()));
					FakeDisplayEntity created = new FakeDisplayEntity(emitter.pos(), state, color);
					view.shown.put(emitter.pos().asLong(), created);
					send(player, created.spawnPackets());
				} else if (existing.kind() != FakeDisplayEntity.Kind.MARKER) {
					// Block state changed (door opened, fence connected...): resend so the outline shape follows.
					sendPacket(player, existing.dataPacket());
				}
			}

			FakeDisplayEntity current = view.shown.get(emitter.pos().asLong());

			if (current != null && current.kind() == FakeDisplayEntity.Kind.MARKER) {
				sendCornerMarkers(player, emitter.pos(), color);
			}
		}
	}

	private static void send(ServerPlayer player, List<Packet<? super ClientGamePacketListener>> packets) {
		for (Packet<? super ClientGamePacketListener> packet : packets) {
			sendPacket(player, packet);
		}
	}

	private static void sendCornerMarkers(ServerPlayer player, BlockPos pos, int color) {
		DustParticleOptions dust = new DustParticleOptions(color & 0xFFFFFF, 0.8f);

		for (int i = 0; i < 8; i++) {
			double x = pos.getX() + ((i & 1) == 0 ? 0.0 : 1.0);
			double y = pos.getY() + ((i & 2) == 0 ? 0.0 : 1.0);
			double z = pos.getZ() + ((i & 4) == 0 ? 0.0 : 1.0);
			sendPacket(player, new ClientboundLevelParticlesPacket(dust, false, false, x, y, z, 0.0f, 0.0f, 0.0f, 0.0f, 1));
		}
	}
}
