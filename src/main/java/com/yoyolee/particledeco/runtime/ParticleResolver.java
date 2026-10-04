package com.yoyolee.particledeco.runtime;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import org.jspecify.annotations.Nullable;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.PowerParticleOption;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import com.yoyolee.particledeco.ParticleDeco;
import com.yoyolee.particledeco.config.MaterialTable;

/**
 * Turns a material table entry plus the stored item into concrete vanilla particle options.
 */
public final class ParticleResolver {
	private static final int DEFAULT_POTION_COLOR = 0xFF385DC6;

	private ParticleResolver() {
	}

	/**
	 * Built-in velocity for particles that vanilla always spawns moving. Campfire smoke only rises because the campfire
	 * gives it an upward velocity of 0.07; without it the smoke hangs in place. Entries in materials.json can override
	 * this with "motion", so existing config files get the fix without editing.
	 */
	@Nullable
	public static Vec3 defaultMotion(ParticleType<?> type) {
		if (type == ParticleTypes.CAMPFIRE_COSY_SMOKE || type == ParticleTypes.CAMPFIRE_SIGNAL_SMOKE) {
			return new Vec3(0.0, 0.07, 0.0);
		}

		return null;
	}

	public static boolean isSupported(ParticleType<?> type, @Nullable JsonObject options) {
		if (type instanceof SimpleParticleType) return true;

		if (type == ParticleTypes.DUST || type == ParticleTypes.DUST_COLOR_TRANSITION || type == ParticleTypes.FALLING_DUST
				|| type == ParticleTypes.ENTITY_EFFECT || type == ParticleTypes.DRAGON_BREATH) {
			return true;
		}

		return options != null && decode(type, options) != null;
	}

	@Nullable
	public static ParticleOptions resolve(MaterialTable.Entry entry, ItemStack material, @Nullable DyeColor dye) {
		ParticleType<?> type = entry.type();

		if (type == ParticleTypes.DUST) {
			if (dye == null) return DustParticleOptions.REDSTONE;

			return new DustParticleOptions(dye.getTextureDiffuseColor() & 0xFFFFFF, 1.0f);
		}

		if (type == ParticleTypes.DUST_COLOR_TRANSITION) {
			if (dye == null) return DustColorTransitionOptions.SCULK_TO_REDSTONE;

			return new DustColorTransitionOptions(DustColorTransitionOptions.SCULK_PARTICLE_COLOR, dye.getTextureDiffuseColor() & 0xFFFFFF, 1.0f);
		}

		if (type == ParticleTypes.FALLING_DUST) {
			Block block = Block.byItem(material.getItem());

			if (block == Blocks.AIR) block = Blocks.SAND;

			return new BlockParticleOption(ParticleTypes.FALLING_DUST, block.defaultBlockState());
		}

		if (type == ParticleTypes.ENTITY_EFFECT) {
			PotionContents contents = material.get(DataComponents.POTION_CONTENTS);
			int color = contents != null ? contents.getColorOr(DEFAULT_POTION_COLOR) : DEFAULT_POTION_COLOR;
			return ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, color | 0xFF000000);
		}

		if (type == ParticleTypes.DRAGON_BREATH) {
			return PowerParticleOption.create(ParticleTypes.DRAGON_BREATH, 1.0f);
		}

		if (entry.options() != null) {
			ParticleOptions decoded = decode(type, entry.options());

			if (decoded != null) return decoded;
		}

		if (type instanceof SimpleParticleType simple) return simple;

		return null;
	}

	@Nullable
	private static <T extends ParticleOptions> T decode(ParticleType<T> type, JsonObject options) {
		return type.codec().codec().parse(JsonOps.INSTANCE, options)
				.resultOrPartial(error -> ParticleDeco.LOGGER.warn("Invalid particle options {}: {}", options, error))
				.orElse(null);
	}
}
