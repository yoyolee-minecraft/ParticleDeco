package com.yoyolee.particledeco.config;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/**
 * Global settings loaded from config/particledeco/config.json.
 * Every field is validated on load; invalid values fall back to the default and produce a warning.
 */
public final class ModConfig {
	public String coreItem = "minecraft:amethyst_shard";
	public String coreAnvilName = "粒子核心";
	public boolean coreRecipeMode = false;
	public int brushDurabilityCost = 0;
	public boolean consumeMaterial = true;
	public List<String> bindBlacklist = new ArrayList<>(List.of("minecraft:suspicious_sand", "minecraft:suspicious_gravel"));
	public int outlineWaitingColor = 0xFFD54A;
	public int outlinePlayingColor = 0x4AD9FF;
	public int outlineShowDistance = 16;
	public int viewDistance = 32;
	public int perChunk = 32;
	public int maxCountPerEmit = 8;
	public int globalPacketsPerTick = 400;
	public int perPlayerPacketsPerTick = 60;
	public String language = "zh_tw";

	public static ModConfig parse(JsonObject root, Consumer<String> warn) {
		ModConfig c = new ModConfig();
		JsonObject core = object(root, "core", warn);
		c.coreItem = string(core, "core.item", "item", c.coreItem, warn);
		c.coreAnvilName = string(core, "core.anvilName", "anvilName", c.coreAnvilName, warn);
		c.coreRecipeMode = bool(core, "core.recipeMode", "recipeMode", c.coreRecipeMode, warn);
		c.brushDurabilityCost = integer(root, "brushDurabilityCost", "brushDurabilityCost", c.brushDurabilityCost, 0, 64, warn);
		c.consumeMaterial = bool(root, "consumeMaterial", "consumeMaterial", c.consumeMaterial, warn);

		if (root.has("bindBlacklist")) {
			JsonElement el = root.get("bindBlacklist");

			if (el.isJsonArray()) {
				List<String> list = new ArrayList<>();

				for (JsonElement e : el.getAsJsonArray()) {
					if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isString()) {
						list.add(e.getAsString());
					} else {
						warn.accept("bindBlacklist contains a non-string entry, ignored: " + e);
					}
				}

				c.bindBlacklist = list;
			} else {
				warn.accept("bindBlacklist must be an array, using default");
			}
		}

		JsonObject outline = object(root, "outline", warn);
		c.outlineWaitingColor = color(outline, "outline.waitingColor", "waitingColor", c.outlineWaitingColor, warn);
		c.outlinePlayingColor = color(outline, "outline.playingColor", "playingColor", c.outlinePlayingColor, warn);
		c.outlineShowDistance = integer(outline, "outline.showDistance", "showDistance", c.outlineShowDistance, 1, 64, warn);
		c.viewDistance = integer(root, "viewDistance", "viewDistance", c.viewDistance, 1, 128, warn);

		JsonObject limits = object(root, "limits", warn);
		c.perChunk = integer(limits, "limits.perChunk", "perChunk", c.perChunk, 1, 4096, warn);
		c.maxCountPerEmit = integer(limits, "limits.maxCountPerEmit", "maxCountPerEmit", c.maxCountPerEmit, 1, 64, warn);
		c.globalPacketsPerTick = integer(limits, "limits.globalPacketsPerTick", "globalPacketsPerTick", c.globalPacketsPerTick, 1, 100000, warn);
		c.perPlayerPacketsPerTick = integer(limits, "limits.perPlayerPacketsPerTick", "perPlayerPacketsPerTick", c.perPlayerPacketsPerTick, 1, 10000, warn);

		String lang = string(root, "language", "language", c.language, warn);

		if (lang.equals("zh_tw") || lang.equals("en_us")) {
			c.language = lang;
		} else {
			warn.accept("language must be zh_tw or en_us, got " + lang);
		}

		return c;
	}

	public JsonObject toJson() {
		JsonObject root = new JsonObject();
		JsonObject core = new JsonObject();
		core.addProperty("item", coreItem);
		core.addProperty("anvilName", coreAnvilName);
		core.addProperty("recipeMode", coreRecipeMode);
		root.add("core", core);
		root.addProperty("brushDurabilityCost", brushDurabilityCost);
		root.addProperty("consumeMaterial", consumeMaterial);
		JsonArray blacklist = new JsonArray();
		bindBlacklist.forEach(blacklist::add);
		root.add("bindBlacklist", blacklist);
		JsonObject outline = new JsonObject();
		outline.addProperty("waitingColor", String.format("#%06X", outlineWaitingColor));
		outline.addProperty("playingColor", String.format("#%06X", outlinePlayingColor));
		outline.addProperty("showDistance", outlineShowDistance);
		root.add("outline", outline);
		root.addProperty("viewDistance", viewDistance);
		JsonObject limits = new JsonObject();
		limits.addProperty("perChunk", perChunk);
		limits.addProperty("maxCountPerEmit", maxCountPerEmit);
		limits.addProperty("globalPacketsPerTick", globalPacketsPerTick);
		limits.addProperty("perPlayerPacketsPerTick", perPlayerPacketsPerTick);
		root.add("limits", limits);
		root.addProperty("language", language);
		return root;
	}

	private static JsonObject object(JsonObject parent, String key, Consumer<String> warn) {
		JsonElement el = parent.get(key);

		if (el == null) {
			return new JsonObject();
		}

		if (!el.isJsonObject()) {
			warn.accept(key + " must be an object, using defaults");
			return new JsonObject();
		}

		return el.getAsJsonObject();
	}

	private static String string(JsonObject obj, String path, String key, String def, Consumer<String> warn) {
		JsonElement el = obj.get(key);

		if (el == null) return def;

		if (el instanceof JsonPrimitive p && p.isString()) {
			return p.getAsString();
		}

		warn.accept(path + " must be a string, using default " + def);
		return def;
	}

	private static boolean bool(JsonObject obj, String path, String key, boolean def, Consumer<String> warn) {
		JsonElement el = obj.get(key);

		if (el == null) return def;

		if (el instanceof JsonPrimitive p && p.isBoolean()) {
			return p.getAsBoolean();
		}

		warn.accept(path + " must be true or false, using default " + def);
		return def;
	}

	private static int integer(JsonObject obj, String path, String key, int def, int min, int max, Consumer<String> warn) {
		JsonElement el = obj.get(key);

		if (el == null) return def;

		if (el instanceof JsonPrimitive p && p.isNumber()) {
			double d = p.getAsDouble();

			if (d == Math.floor(d) && d >= min && d <= max) {
				return (int) d;
			}
		}

		warn.accept(path + " must be an integer between " + min + " and " + max + ", using default " + def);
		return def;
	}

	private static int color(JsonObject obj, String path, String key, int def, Consumer<String> warn) {
		JsonElement el = obj.get(key);

		if (el == null) return def;

		Integer parsed = parseColor(el);

		if (parsed != null) return parsed;

		warn.accept(path + " must be a color like \"#RRGGBB\", using default");
		return def;
	}

	public static Integer parseColor(JsonElement el) {
		if (el instanceof JsonPrimitive p) {
			if (p.isNumber()) {
				return p.getAsInt() & 0xFFFFFF;
			}

			if (p.isString()) {
				String s = p.getAsString().trim();

				if (s.startsWith("#")) s = s.substring(1);

				if (s.length() == 6) {
					try {
						return Integer.parseInt(s, 16);
					} catch (NumberFormatException ignored) {
						return null;
					}
				}
			}
		}

		return null;
	}
}
