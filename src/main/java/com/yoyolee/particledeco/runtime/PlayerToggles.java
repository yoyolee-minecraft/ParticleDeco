package com.yoyolee.particledeco.runtime;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import com.yoyolee.particledeco.ParticleDeco;

/**
 * Overworld SavedData holding the UUIDs of players who turned decoration particles off.
 */
public final class PlayerToggles extends SavedData {
	private static final Codec<PlayerToggles> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			UUIDUtil.STRING_CODEC.listOf().optionalFieldOf("disabled", List.of()).forGetter(t -> List.copyOf(t.disabled))
	).apply(instance, PlayerToggles::new));

	private static final SavedDataType<PlayerToggles> TYPE = new SavedDataType<>(ParticleDeco.id("player_toggles"), PlayerToggles::new, CODEC, null);

	private final Set<UUID> disabled;

	private PlayerToggles() {
		this.disabled = new HashSet<>();
	}

	private PlayerToggles(List<UUID> disabled) {
		this.disabled = new HashSet<>(disabled);
	}

	public static PlayerToggles get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	public boolean isDisabled(UUID uuid) {
		return disabled.contains(uuid);
	}

	/**
	 * @return true when particles are now disabled for the player
	 */
	public boolean toggle(UUID uuid) {
		boolean nowDisabled;

		if (disabled.remove(uuid)) {
			nowDisabled = false;
		} else {
			disabled.add(uuid);
			nowDisabled = true;
		}

		setDirty();
		return nowDisabled;
	}
}
