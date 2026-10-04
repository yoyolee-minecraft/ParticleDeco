package com.yoyolee.particledeco.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jspecify.annotations.Nullable;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import com.yoyolee.particledeco.runtime.ParticleResolver;

/**
 * Maps vanilla items to the particle they produce, loaded from config/particledeco/materials.json.
 */
public final class MaterialTable {
	/**
	 * @param motion fixed particle velocity in blocks per tick, or null for stationary particles. Particles with motion
	 *               are sent one per packet with count 0, which makes the client use the velocity exactly.
	 */
	public record Entry(Identifier particleId, ParticleType<?> type, String rarity, @Nullable JsonObject options, @Nullable Vec3 motion, String source) {
	}

	private final Map<Item, Entry> byItem;
	private final List<Map.Entry<TagKey<Item>, Entry>> byTag;
	private final List<Entry> entries;

	private MaterialTable(Map<Item, Entry> byItem, List<Map.Entry<TagKey<Item>, Entry>> byTag, List<Entry> entries) {
		this.byItem = byItem;
		this.byTag = byTag;
		this.entries = entries;
	}

	public static MaterialTable empty() {
		return new MaterialTable(Map.of(), List.of(), List.of());
	}

	@Nullable
	public Entry find(ItemStack stack) {
		if (stack.isEmpty()) return null;

		Entry entry = byItem.get(stack.getItem());

		if (entry != null) return entry;

		for (Map.Entry<TagKey<Item>, Entry> tagEntry : byTag) {
			if (stack.is(tagEntry.getKey())) {
				return tagEntry.getValue();
			}
		}

		return null;
	}

	public int size() {
		return entries.size();
	}

	public List<Entry> entries() {
		return entries;
	}

	public static MaterialTable parse(JsonObject root, Consumer<String> warn) {
		Map<Item, Entry> byItem = new IdentityHashMap<>();
		List<Map.Entry<TagKey<Item>, Entry>> byTag = new ArrayList<>();
		List<Entry> entries = new ArrayList<>();
		JsonElement list = root.get("materials");

		if (list == null || !list.isJsonArray()) {
			warn.accept("materials.json must contain a \"materials\" array");
			return new MaterialTable(byItem, byTag, entries);
		}

		int index = 0;

		for (JsonElement el : list.getAsJsonArray()) {
			index++;

			if (!el.isJsonObject()) {
				warn.accept("materials[" + index + "] is not an object, skipped");
				continue;
			}

			JsonObject obj = el.getAsJsonObject();
			String particleStr = obj.has("particle") && obj.get("particle").isJsonPrimitive() ? obj.get("particle").getAsString() : null;
			Identifier particleId = particleStr == null ? null : Identifier.tryParse(particleStr);

			if (particleId == null || !BuiltInRegistries.PARTICLE_TYPE.containsKey(particleId)) {
				warn.accept("materials[" + index + "]: unknown particle " + particleStr + ", skipped");
				continue;
			}

			ParticleType<?> type = BuiltInRegistries.PARTICLE_TYPE.getValue(particleId);
			JsonObject options = obj.has("options") && obj.get("options").isJsonObject() ? obj.getAsJsonObject("options") : null;
			Vec3 motion = ParticleResolver.defaultMotion(type);

			if (obj.has("motion")) {
				Vec3 parsed = parseMotion(obj.get("motion"));

				if (parsed == null) {
					warn.accept("materials[" + index + "]: motion must be an array of three numbers, using default");
				} else {
					motion = parsed.lengthSqr() == 0 ? null : parsed;
				}
			}

			if (!ParticleResolver.isSupported(type, options)) {
				warn.accept("materials[" + index + "]: particle " + particleId + " needs an \"options\" object, skipped");
				continue;
			}

			String rarity = obj.has("rarity") && obj.get("rarity").isJsonPrimitive() ? obj.get("rarity").getAsString() : "common";
			List<String> itemIds = new ArrayList<>();

			if (obj.has("item") && obj.get("item").isJsonPrimitive()) {
				itemIds.add(obj.get("item").getAsString());
			}

			if (obj.has("items") && obj.get("items").isJsonArray()) {
				for (JsonElement item : obj.getAsJsonArray("items")) {
					if (item.isJsonPrimitive()) itemIds.add(item.getAsString());
				}
			}

			boolean any = false;

			for (String itemId : itemIds) {
				if (itemId.startsWith("#")) {
					Identifier tagId = Identifier.tryParse(itemId.substring(1));

					if (tagId == null) {
						warn.accept("materials[" + index + "]: invalid tag " + itemId);
						continue;
					}

					Entry entry = new Entry(particleId, type, rarity, options, motion, itemId);
					byTag.add(Map.entry(TagKey.create(Registries.ITEM, tagId), entry));
					entries.add(entry);
					any = true;
					continue;
				}

				Identifier id = Identifier.tryParse(itemId);

				if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
					warn.accept("materials[" + index + "]: unknown item " + itemId + ", skipped");
					continue;
				}

				Item item = BuiltInRegistries.ITEM.getValue(id);

				if (item == Items.AIR) {
					continue;
				}

				if (byItem.containsKey(item)) {
					warn.accept("materials[" + index + "]: item " + itemId + " is already mapped, later entry ignored");
					continue;
				}

				Entry entry = new Entry(particleId, type, rarity, options, motion, itemId);
				byItem.put(item, entry);
				entries.add(entry);
				any = true;
			}

			if (!any) {
				warn.accept("materials[" + index + "]: no valid item, skipped");
			}
		}

		return new MaterialTable(byItem, Collections.unmodifiableList(byTag), Collections.unmodifiableList(entries));
	}

	@Nullable
	private static Vec3 parseMotion(JsonElement el) {
		if (!el.isJsonArray()) return null;

		JsonArray arr = el.getAsJsonArray();

		if (arr.size() != 3) return null;

		double[] v = new double[3];

		for (int i = 0; i < 3; i++) {
			JsonElement e = arr.get(i);

			if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) return null;

			v[i] = Math.max(-1.0, Math.min(1.0, e.getAsDouble()));
		}

		return new Vec3(v[0], v[1], v[2]);
	}

	/**
	 * Default table from docs/SPEC.md section 5.
	 */
	public static JsonObject defaults() {
		JsonArray list = new JsonArray();
		// common
		add(list, "common", "minecraft:flame", "minecraft:torch");
		add(list, "common", "minecraft:small_flame", "minecraft:candle", "#minecraft:candles");
		add(list, "common", "minecraft:copper_fire_flame", "minecraft:copper_torch");
		add(list, "common", "minecraft:campfire_cosy_smoke", "minecraft:campfire");
		add(list, "common", "minecraft:campfire_signal_smoke", "minecraft:hay_block");
		add(list, "common", "minecraft:smoke", "minecraft:charcoal");
		add(list, "common", "minecraft:large_smoke", "minecraft:coal");
		add(list, "common", "minecraft:dripping_water", "minecraft:water_bucket");
		add(list, "common", "minecraft:dripping_dripstone_water", "minecraft:pointed_dripstone");
		add(list, "common", "minecraft:dripping_honey", "minecraft:honey_bottle");
		add(list, "common", "minecraft:happy_villager", "minecraft:bone_meal");
		add(list, "common", "minecraft:heart", "minecraft:wheat");
		add(list, "common", "minecraft:note", "minecraft:note_block");
		add(list, "common", "minecraft:enchant", "minecraft:bookshelf");
		add(list, "common", "minecraft:falling_nectar", "#minecraft:small_flowers");
		add(list, "common", "minecraft:item_snowball", "minecraft:snowball");
		add(list, "common", "minecraft:squid_ink", "minecraft:ink_sac");
		add(list, "common", "minecraft:dust", "minecraft:redstone");
		add(list, "common", "minecraft:falling_dust",
				"minecraft:white_concrete_powder", "minecraft:orange_concrete_powder", "minecraft:magenta_concrete_powder",
				"minecraft:light_blue_concrete_powder", "minecraft:yellow_concrete_powder", "minecraft:lime_concrete_powder",
				"minecraft:pink_concrete_powder", "minecraft:gray_concrete_powder", "minecraft:light_gray_concrete_powder",
				"minecraft:cyan_concrete_powder", "minecraft:purple_concrete_powder", "minecraft:blue_concrete_powder",
				"minecraft:brown_concrete_powder", "minecraft:green_concrete_powder", "minecraft:red_concrete_powder",
				"minecraft:black_concrete_powder");
		add(list, "common", "minecraft:wax_on", "minecraft:honeycomb");
		add(list, "common", "minecraft:firework", "minecraft:firework_rocket");
		// medium
		add(list, "medium", "minecraft:soul_fire_flame", "minecraft:soul_torch");
		add(list, "medium", "minecraft:dripping_lava", "minecraft:lava_bucket");
		add(list, "medium", "minecraft:lava", "minecraft:magma_cream");
		add(list, "medium", "minecraft:cherry_leaves", "minecraft:cherry_leaves");
		add(list, "medium", "minecraft:pale_oak_leaves", "minecraft:pale_oak_leaves");
		add(list, "medium", "minecraft:crimson_spore", "minecraft:crimson_fungus");
		add(list, "medium", "minecraft:warped_spore", "minecraft:warped_fungus");
		add(list, "medium", "minecraft:ash", "minecraft:soul_sand");
		add(list, "medium", "minecraft:white_ash", "minecraft:basalt");
		add(list, "medium", "minecraft:mycelium", "minecraft:mycelium");
		add(list, "medium", "minecraft:snowflake", "minecraft:powder_snow_bucket");
		add(list, "medium", "minecraft:glow", "minecraft:glow_ink_sac");
		add(list, "medium", "minecraft:spore_blossom_air", "minecraft:spore_blossom");
		add(list, "medium", "minecraft:firefly", "minecraft:firefly_bush");
		add(list, "medium", "minecraft:electric_spark", "minecraft:lightning_rod");
		add(list, "medium", "minecraft:portal", "minecraft:ender_pearl");
		add(list, "medium", "minecraft:dripping_obsidian_tear", "minecraft:crying_obsidian");
		add(list, "medium", "minecraft:entity_effect", "minecraft:potion", "minecraft:splash_potion", "minecraft:lingering_potion");
		add(list, "medium", "minecraft:small_gust", "minecraft:wind_charge");
		add(list, "medium", "minecraft:nautilus", "minecraft:nautilus_shell");
		add(list, "medium", "minecraft:dust_color_transition", "minecraft:sculk_sensor");
		add(list, "medium", "minecraft:explosion", "minecraft:tnt");
		addWithOptions(list, "medium", "minecraft:geyser_base", geyserBase(), "minecraft:magma_block");
		addWithOptions(list, "medium", "minecraft:geyser_poof", geyserBase(), "minecraft:sulfur_spike");
		add(list, "medium", "minecraft:noxious_gas", "minecraft:sulfur");
		// rare
		add(list, "rare", "minecraft:end_rod", "minecraft:end_rod");
		add(list, "rare", "minecraft:reverse_portal", "minecraft:respawn_anchor");
		add(list, "rare", "minecraft:vault_connection", "minecraft:trial_key");
		add(list, "rare", "minecraft:trial_spawner_detection_ominous", "minecraft:ominous_trial_key");
		add(list, "rare", "minecraft:ominous_spawning", "minecraft:ominous_bottle");
		add(list, "rare", "minecraft:sculk_soul", "minecraft:sculk_catalyst");
		add(list, "rare", "minecraft:dragon_breath", "minecraft:dragon_breath");
		add(list, "rare", "minecraft:totem_of_undying", "minecraft:totem_of_undying");
		JsonObject plume = new JsonObject();
		plume.addProperty("water_blocks", 3);
		addWithOptions(list, "rare", "minecraft:geyser_plume", plume, "minecraft:potent_sulfur");
		JsonObject root = new JsonObject();
		root.add("materials", list);
		return root;
	}

	private static JsonObject geyserBase() {
		JsonObject options = new JsonObject();
		options.addProperty("water_blocks", 3);
		options.addProperty("burst_impulse_base", 0.5);
		return options;
	}

	private static void addWithOptions(JsonArray list, String rarity, String particle, JsonObject options, String... items) {
		add(list, rarity, particle, items);
		list.get(list.size() - 1).getAsJsonObject().add("options", options);
	}

	private static void add(JsonArray list, String rarity, String particle, String... items) {
		JsonObject obj = new JsonObject();
		JsonArray arr = new JsonArray();

		for (String item : items) arr.add(item);

		obj.add("items", arr);
		obj.addProperty("particle", particle);
		obj.addProperty("rarity", rarity);
		list.add(obj);
	}
}
