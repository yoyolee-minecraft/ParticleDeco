package com.yoyolee.particledeco.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.fabricmc.loader.api.FabricLoader;

import com.yoyolee.particledeco.ParticleDeco;

/**
 * Loads config.json and materials.json. Missing files are created with defaults; malformed files never crash the
 * server, they log a warning and fall back to defaults.
 */
public final class ConfigManager {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	public record Result(ModConfig config, MaterialTable materials, List<String> warnings) {
	}

	private ConfigManager() {
	}

	public static Path directory() {
		return FabricLoader.getInstance().getConfigDir().resolve(ParticleDeco.MOD_ID);
	}

	public static Result load() {
		return load(directory());
	}

	public static Result load(Path dir) {
		List<String> warnings = new ArrayList<>();
		JsonObject configJson = readOrCreate(dir.resolve("config.json"), new ModConfig().toJson(), warnings);
		ModConfig config = ModConfig.parse(configJson, w -> warnings.add("config.json: " + w));
		// The material table reads the blacklist and other settings through ParticleDeco.config(), so install first.
		ParticleDeco.setConfig(config);
		JsonObject materialsJson = readOrCreate(dir.resolve("materials.json"), MaterialTable.defaults(), warnings);
		List<String> added = MaterialTable.upgrade(materialsJson);

		if (!added.isEmpty()) {
			try {
				Files.writeString(dir.resolve("materials.json"), GSON.toJson(materialsJson), StandardCharsets.UTF_8);
				ParticleDeco.LOGGER.info("Added new default materials to materials.json: {}", added);
			} catch (IOException e) {
				warnings.add("Could not update materials.json with new defaults: " + e.getMessage());
			}
		}

		MaterialTable materials = MaterialTable.parse(materialsJson, w -> warnings.add("materials.json: " + w));

		if (materials.size() == 0) {
			warnings.add("materials.json: no usable entries, falling back to the default table");
			materials = MaterialTable.parse(MaterialTable.defaults(), w -> warnings.add("default materials: " + w));
		}

		ParticleDeco.setMaterials(materials);

		for (String warning : warnings) {
			ParticleDeco.LOGGER.warn(warning);
		}

		ParticleDeco.LOGGER.info("Loaded {} particle materials", materials.size());
		return new Result(config, materials, warnings);
	}

	private static JsonObject readOrCreate(Path file, JsonObject defaults, List<String> warnings) {
		if (Files.notExists(file)) {
			try {
				Files.createDirectories(file.getParent());
				Files.writeString(file, GSON.toJson(defaults), StandardCharsets.UTF_8);
			} catch (IOException e) {
				warnings.add("Could not write default " + file.getFileName() + ": " + e.getMessage());
			}

			return defaults;
		}

		try {
			JsonElement parsed = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));

			if (parsed.isJsonObject()) {
				return parsed.getAsJsonObject();
			}

			warnings.add(file.getFileName() + " is not a JSON object, using defaults");
		} catch (Exception e) {
			warnings.add(file.getFileName() + " could not be parsed (" + e.getMessage() + "), using defaults");
		}

		return defaults;
	}
}
