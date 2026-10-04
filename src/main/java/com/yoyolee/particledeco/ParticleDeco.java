package com.yoyolee.particledeco;

import net.fabricmc.api.ModInitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ParticleDeco implements ModInitializer {
	public static final String MOD_ID = "particledeco";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Particle Deco loaded");
	}
}
