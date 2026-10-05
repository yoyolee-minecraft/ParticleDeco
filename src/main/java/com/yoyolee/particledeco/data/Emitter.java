package com.yoyolee.particledeco.data;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/**
 * One particle emitter bound to a block. Immutable; use the {@code with*} methods to derive updated copies.
 *
 * @param pos          bound block position, also the key inside the chunk
 * @param block        block type at bind time; informational, the block may change type later without unbinding
 * @param material     stored item, empty while waiting for a material
 * @param particle     particle id derived from the material, informational
 * @param offsetX      offset in 1/16 block, -32..32
 * @param shape        emission shape
 * @param shapeSize    column height, ring radius or area edge length
 * @param count        particles per emit
 * @param interval     ticks between emits
 * @param spread       random spread in blocks
 * @param redstoneMode redstone gating
 * @param dye          optional dye color for colored particles
 * @param dataVersion  schema version for future migration
 */
public record Emitter(
		BlockPos pos,
		Block block,
		ItemStack material,
		Optional<Identifier> particle,
		int offsetX,
		int offsetY,
		int offsetZ,
		EmitterShape shape,
		float shapeSize,
		int count,
		int interval,
		float spread,
		RedstoneMode redstoneMode,
		Optional<DyeColor> dye,
		int dataVersion
) {
	public static final int CURRENT_DATA_VERSION = 1;
	public static final int MAX_OFFSET = 32;
	public static final int MIN_INTERVAL = 2;
	public static final int MAX_INTERVAL = 100;
	public static final int DEFAULT_INTERVAL = 10;
	public static final int DEFAULT_COUNT = 2;
	public static final float MAX_SPREAD = 2.0f;

	private static final Codec<Emitter> BASE_CODEC = RecordCodecBuilder.create(instance -> instance.group(
			BlockPos.CODEC.fieldOf("pos").forGetter(Emitter::pos),
			BuiltInRegistries.BLOCK.byNameCodec().fieldOf("block").forGetter(Emitter::block),
			ItemStack.OPTIONAL_CODEC.optionalFieldOf("material", ItemStack.EMPTY).forGetter(Emitter::material),
			Identifier.CODEC.optionalFieldOf("particle").forGetter(Emitter::particle),
			Codec.INT.listOf().optionalFieldOf("offset", java.util.List.of(0, 0, 0)).forGetter(e -> java.util.List.of(e.offsetX, e.offsetY, e.offsetZ)),
			EmitterShape.CODEC.optionalFieldOf("shape", EmitterShape.POINT).forGetter(Emitter::shape),
			Codec.FLOAT.optionalFieldOf("shapeSize", 1.0f).forGetter(Emitter::shapeSize),
			Codec.INT.optionalFieldOf("count", DEFAULT_COUNT).forGetter(Emitter::count),
			Codec.INT.optionalFieldOf("interval", DEFAULT_INTERVAL).forGetter(Emitter::interval),
			Codec.FLOAT.optionalFieldOf("spread", 0.0f).forGetter(Emitter::spread),
			RedstoneMode.CODEC.optionalFieldOf("redstoneMode", RedstoneMode.IGNORE).forGetter(Emitter::redstoneMode),
			DyeColor.CODEC.optionalFieldOf("dye").forGetter(Emitter::dye),
			Codec.INT.optionalFieldOf("dataVersion", CURRENT_DATA_VERSION).forGetter(Emitter::dataVersion)
	).apply(instance, (pos, block, material, particle, offset, shape, size, count, interval, spread, redstone, dye, version) -> new Emitter(
			pos, block, material, particle,
			offset.size() > 0 ? offset.get(0) : 0,
			offset.size() > 1 ? offset.get(1) : 0,
			offset.size() > 2 ? offset.get(2) : 0,
			shape, size, count, interval, spread, redstone, dye, version).sanitized()));

	public static final Codec<Emitter> CODEC = BASE_CODEC;

	public static Emitter waiting(BlockPos pos, Block block) {
		return new Emitter(pos.immutable(), block, ItemStack.EMPTY, Optional.empty(), 0, 0, 0, EmitterShape.POINT, 1.0f,
				DEFAULT_COUNT, DEFAULT_INTERVAL, 0.0f, RedstoneMode.IGNORE, Optional.empty(), CURRENT_DATA_VERSION);
	}

	public boolean isWaiting() {
		return material.isEmpty();
	}

	public Emitter sanitized() {
		return new Emitter(pos, block, material, particle,
				Mth.clamp(offsetX, -MAX_OFFSET, MAX_OFFSET),
				Mth.clamp(offsetY, -MAX_OFFSET, MAX_OFFSET),
				Mth.clamp(offsetZ, -MAX_OFFSET, MAX_OFFSET),
				shape,
				Mth.clamp(shapeSize, shape.minSize(), shape.maxSize()),
				Math.max(1, count),
				Mth.clamp(interval, MIN_INTERVAL, MAX_INTERVAL),
				Mth.clamp(spread, 0.0f, MAX_SPREAD),
				redstoneMode, dye, dataVersion);
	}

	public Emitter withMaterial(ItemStack stack, @Nullable Identifier particleId) {
		return new Emitter(pos, block, stack, Optional.ofNullable(particleId), offsetX, offsetY, offsetZ, shape, shapeSize, count, interval, spread, redstoneMode, dye, dataVersion);
	}

	public Emitter withOffset(int x, int y, int z) {
		return new Emitter(pos, block, material, particle, x, y, z, shape, shapeSize, count, interval, spread, redstoneMode, dye, dataVersion).sanitized();
	}

	public Emitter withShape(EmitterShape newShape) {
		return new Emitter(pos, block, material, particle, offsetX, offsetY, offsetZ, newShape, shapeSize, count, interval, spread, redstoneMode, dye, dataVersion).sanitized();
	}

	public Emitter withShapeSize(float size) {
		return new Emitter(pos, block, material, particle, offsetX, offsetY, offsetZ, shape, size, count, interval, spread, redstoneMode, dye, dataVersion).sanitized();
	}

	public Emitter withCount(int newCount) {
		return new Emitter(pos, block, material, particle, offsetX, offsetY, offsetZ, shape, shapeSize, newCount, interval, spread, redstoneMode, dye, dataVersion).sanitized();
	}

	public Emitter withInterval(int newInterval) {
		return new Emitter(pos, block, material, particle, offsetX, offsetY, offsetZ, shape, shapeSize, count, newInterval, spread, redstoneMode, dye, dataVersion).sanitized();
	}

	public Emitter withSpread(float newSpread) {
		return new Emitter(pos, block, material, particle, offsetX, offsetY, offsetZ, shape, shapeSize, count, interval, newSpread, redstoneMode, dye, dataVersion).sanitized();
	}

	public Emitter withRedstoneMode(RedstoneMode mode) {
		return new Emitter(pos, block, material, particle, offsetX, offsetY, offsetZ, shape, shapeSize, count, interval, spread, mode, dye, dataVersion);
	}

	public Emitter withDye(@Nullable DyeColor color) {
		return new Emitter(pos, block, material, particle, offsetX, offsetY, offsetZ, shape, shapeSize, count, interval, spread, redstoneMode, Optional.ofNullable(color), dataVersion);
	}

	/**
	 * Tick phase derived from the position hash so emitters with the same interval do not fire on the same tick.
	 */
	public int phase() {
		long h = pos.asLong() * 0x9E3779B97F4A7C15L;
		return (int) ((h ^ (h >>> 32)) & 0x7FFFFFFF);
	}

	public boolean isDue(long tick) {
		return (tick + phase()) % interval == 0;
	}
}
