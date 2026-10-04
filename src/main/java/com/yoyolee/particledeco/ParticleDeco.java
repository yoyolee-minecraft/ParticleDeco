package com.yoyolee.particledeco;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.resources.Identifier;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;

import com.yoyolee.particledeco.command.PdecoCommand;
import com.yoyolee.particledeco.config.ConfigManager;
import com.yoyolee.particledeco.config.MaterialTable;
import com.yoyolee.particledeco.config.ModConfig;
import com.yoyolee.particledeco.config.RecipeModeCondition;
import com.yoyolee.particledeco.interaction.BreakHandler;
import com.yoyolee.particledeco.interaction.UseBlockHandler;
import com.yoyolee.particledeco.outline.OutlineTracker;
import com.yoyolee.particledeco.runtime.EmitterManager;
import com.yoyolee.particledeco.runtime.EmitterScheduler;

public class ParticleDeco implements ModInitializer {
	public static final String MOD_ID = "particledeco";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static ModConfig config = new ModConfig();
	private static MaterialTable materials = MaterialTable.empty();
	private static final EmitterManager MANAGER = new EmitterManager();
	private static final EmitterScheduler SCHEDULER = new EmitterScheduler(MANAGER);
	private static final OutlineTracker OUTLINES = new OutlineTracker();

	@Override
	public void onInitialize() {
		EmitterManager.init();
		RecipeModeCondition.register();
		ConfigManager.load();

		ServerChunkEvents.CHUNK_LOAD.register((level, chunk, generated) -> MANAGER.onChunkLoad(level, chunk));
		ServerChunkEvents.CHUNK_UNLOAD.register(MANAGER::onChunkUnload);
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			SCHEDULER.tick(server);
			OUTLINES.tick(server);
		});
		UseBlockCallback.EVENT.register(UseBlockHandler::onUseBlock);
		PlayerBlockBreakEvents.AFTER.register(BreakHandler::afterBreak);
		CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) -> PdecoCommand.register(dispatcher));
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> OUTLINES.reset(newPlayer));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			MANAGER.clear();
			SCHEDULER.clear();
			OUTLINES.clear();
		});

		LOGGER.info("Particle Deco loaded");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	public static ModConfig config() {
		return config;
	}

	public static void setConfig(ModConfig newConfig) {
		config = newConfig;
	}

	public static MaterialTable materials() {
		return materials;
	}

	public static void setMaterials(MaterialTable table) {
		materials = table;
	}

	public static EmitterManager manager() {
		return MANAGER;
	}

	public static EmitterScheduler scheduler() {
		return SCHEDULER;
	}

	public static OutlineTracker outlines() {
		return OUTLINES;
	}
}
