package com.yoyolee.particledeco.test;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

public class ParticleDecoGameTests {
	@GameTest
	public void skeletonLoads(GameTestHelper helper) {
		helper.setBlock(0, 1, 0, Blocks.STONE);
		helper.succeedWhen(() -> helper.assertBlock(new BlockPos(0, 1, 0), b -> b == Blocks.STONE, b -> Component.literal("stone")));
	}
}
