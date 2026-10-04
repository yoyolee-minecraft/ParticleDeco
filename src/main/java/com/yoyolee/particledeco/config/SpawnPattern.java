package com.yoyolee.particledeco.config;

import java.util.function.Consumer;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.jspecify.annotations.Nullable;

import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * Random emission rule copied from vanilla blocks, used instead of the emitter's fixed interval and count.
 * <p>
 * Every tick there is a {@code chance} to emit; a hit emits between {@code min} and {@code max} particles. Each particle
 * lands up to {@code horizontal} blocks away on x and z, and {@code vertical * (r1 + r2) / 2} blocks above the origin,
 * which for vertical = 2 is exactly vanilla's {@code y + random + random}.
 *
 * @param chance     probability per tick, 0..1
 * @param min        minimum particles per hit
 * @param max        maximum particles per hit
 * @param horizontal half width of the horizontal spawn area in blocks
 * @param vertical   height of the vertical spawn range in blocks
 */
public record SpawnPattern(float chance, int min, int max, double horizontal, double vertical) {
	/**
	 * CampfireBlockEntity.particleTick: 11% per tick, 2 to 3 smoke particles. CampfireBlock.makeParticles: x and z within
	 * +-1/3 block of the centre, y + random + random.
	 */
	public static final SpawnPattern CAMPFIRE = new SpawnPattern(0.11f, 2, 3, 1.0 / 3.0, 2.0);

	/**
	 * @return number of particles to emit this tick, 0 when the roll fails
	 */
	public int roll(RandomSource random) {
		if (random.nextFloat() >= chance) return 0;

		return min + random.nextInt(max - min + 1);
	}

	public Vec3 sample(Vec3 origin, RandomSource random) {
		double x = origin.x + (random.nextDouble() * 2 - 1) * horizontal;
		double y = origin.y + (random.nextDouble() + random.nextDouble()) * vertical / 2.0;
		double z = origin.z + (random.nextDouble() * 2 - 1) * horizontal;
		return new Vec3(x, y, z);
	}

	/**
	 * Average particles per tick, used for documentation and tests.
	 */
	public double expectedPerTick() {
		return chance * (min + max) / 2.0;
	}

	/**
	 * Parses {@code "pattern": {...}}. {@code "pattern": false} disables a built-in default.
	 *
	 * @return the parsed pattern, {@code fallback} when the value is invalid, or null when disabled
	 */
	@Nullable
	public static SpawnPattern parse(JsonElement el, @Nullable SpawnPattern fallback, Consumer<String> warn) {
		if (el instanceof JsonPrimitive p && p.isBoolean() && !p.getAsBoolean()) return null;

		if (el.isJsonNull()) return null;

		if (!el.isJsonObject()) {
			warn.accept("pattern must be an object or false, using default");
			return fallback;
		}

		JsonObject obj = el.getAsJsonObject();
		SpawnPattern base = fallback != null ? fallback : CAMPFIRE;
		float chance = (float) number(obj, "chance", base.chance(), 0.0, 1.0, warn);
		int min = (int) number(obj, "min", base.min(), 1, 64, warn);
		int max = (int) number(obj, "max", Math.max(min, base.max()), 1, 64, warn);

		if (max < min) {
			warn.accept("pattern.max is smaller than pattern.min, using min");
			max = min;
		}

		double horizontal = number(obj, "horizontal", base.horizontal(), 0.0, 4.0, warn);
		double vertical = number(obj, "vertical", base.vertical(), 0.0, 8.0, warn);
		return new SpawnPattern(chance, min, max, horizontal, vertical);
	}

	private static double number(JsonObject obj, String key, double def, double min, double max, Consumer<String> warn) {
		if (!obj.has(key)) return def;

		JsonElement el = obj.get(key);

		if (el instanceof JsonPrimitive p && p.isNumber()) {
			double v = p.getAsDouble();

			if (v >= min && v <= max) return v;
		}

		warn.accept("pattern." + key + " must be a number between " + min + " and " + max + ", using " + def);
		return def;
	}
}
