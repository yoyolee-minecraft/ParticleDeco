package com.yoyolee.particledeco.config;

import com.mojang.serialization.MapCodec;
import org.jspecify.annotations.Nullable;

import net.minecraft.resources.RegistryOps;

import net.fabricmc.fabric.api.resource.conditions.v1.ResourceCondition;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceConditionType;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceConditions;

import com.yoyolee.particledeco.ParticleDeco;

/**
 * Datapack load condition {@code particledeco:recipe_mode}: true when config core.recipeMode is enabled.
 * The optional core crafting recipe uses it, so it only exists while recipe mode is on.
 */
public final class RecipeModeCondition implements ResourceCondition {
	public static final MapCodec<RecipeModeCondition> CODEC = MapCodec.unit(RecipeModeCondition::new);
	public static final ResourceConditionType<RecipeModeCondition> TYPE = ResourceConditionType.create(ParticleDeco.id("recipe_mode"), CODEC);

	public static void register() {
		ResourceConditions.register(TYPE);
	}

	@Override
	public ResourceConditionType<?> getType() {
		return TYPE;
	}

	@Override
	public boolean test(RegistryOps.@Nullable RegistryInfoLookup registryInfo) {
		return ParticleDeco.config().coreRecipeMode;
	}
}
