package com.yoyolee.particledeco.test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.DynamicOps;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import com.yoyolee.particledeco.ParticleDeco;
import com.yoyolee.particledeco.config.ConfigManager;
import com.yoyolee.particledeco.config.MaterialTable;
import com.yoyolee.particledeco.config.ModConfig;
import com.yoyolee.particledeco.data.ChunkEmitters;
import com.yoyolee.particledeco.data.Emitter;
import com.yoyolee.particledeco.data.EmitterShape;
import com.yoyolee.particledeco.data.RedstoneMode;
import com.yoyolee.particledeco.interaction.CoreItem;
import com.yoyolee.particledeco.outline.FakeDisplayEntity;
import com.yoyolee.particledeco.runtime.BlockValidator;
import com.yoyolee.particledeco.runtime.EmitterActions;
import com.yoyolee.particledeco.runtime.ParticleResolver;

public class ParticleDecoGameTests {
	/**
	 * The empty test structure is all air; a floor keeps dropped items in place.
	 */
	private static void floor(GameTestHelper helper) {
		for (int x = 0; x < 5; x++) {
			for (int z = 0; z < 5; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
			}
		}
	}

	private static ServerPlayer survivalPlayer(GameTestHelper helper) {
		floor(helper);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		return player;
	}

	private static InteractionResult use(GameTestHelper helper, ServerPlayer player, BlockPos relative, ItemStack stack) {
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		BlockPos abs = helper.absolutePos(relative);
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs).add(0, 0.5, 0), Direction.UP, abs, false);
		return player.gameMode.useItemOn(player, helper.getLevel(), stack, InteractionHand.MAIN_HAND, hit);
	}

	private static net.minecraft.world.item.Item item(String path) {
		return net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.withDefaultNamespace(path));
	}

	private static Emitter emitter(GameTestHelper helper, BlockPos relative) {
		return ParticleDeco.manager().get(helper.getLevel(), helper.absolutePos(relative));
	}

	@GameTest
	public void skeletonLoads(GameTestHelper helper) {
		helper.setBlock(0, 1, 0, Blocks.STONE);
		helper.succeedWhen(() -> helper.assertBlock(new BlockPos(0, 1, 0), b -> b == Blocks.STONE, b -> Component.literal("stone")));
	}

	@GameTest
	public void defaultMaterialsAllResolve(GameTestHelper helper) {
		List<String> warnings = new ArrayList<>();
		MaterialTable table = MaterialTable.parse(MaterialTable.defaults(), warnings::add);
		helper.assertTrue(warnings.isEmpty(), "default material warnings: " + warnings);
		helper.assertTrue(table.size() >= 58, "expected at least 58 default materials, got " + table.size());

		for (MaterialTable.Entry entry : table.entries()) {
			helper.assertTrue(ParticleResolver.resolve(entry, new ItemStack(item("white_concrete_powder")), DyeColor.BLUE) != null,
					"particle did not resolve: " + entry.particleId());
		}

		helper.assertTrue(table.find(new ItemStack(Items.TORCH)).particleId().getPath().equals("flame"), "torch -> flame");
		helper.assertTrue(table.find(new ItemStack(Items.CAMPFIRE)) != null, "campfire is a material");
		helper.assertTrue(table.find(new ItemStack(Items.DANDELION)) != null, "small flower tag maps to falling_nectar");
		helper.assertTrue(table.find(new ItemStack(item("red_concrete_powder"))).particleId().getPath().equals("falling_dust"), "concrete powder");
		helper.assertTrue(table.find(new ItemStack(Items.STONE)) == null, "stone is not a material");
		helper.succeed();
	}

	@GameTest
	public void bindFillBrushAndBreakFlow(GameTestHelper helper) {
		ServerPlayer player = survivalPlayer(helper);
		BlockPos pos = new BlockPos(1, 1, 1);
		helper.setBlock(pos, Blocks.OAK_PLANKS);

		ItemStack cores = CoreItem.create(3);
		use(helper, player, pos, cores);
		Emitter bound = emitter(helper, pos);
		helper.assertTrue(bound != null && bound.isWaiting(), "core should bind the block");
		helper.assertValueEqual(cores.getCount(), 2, "one core consumed");

		// Using the core again opens the editor instead of binding twice.
		use(helper, player, pos, cores);
		helper.assertValueEqual(cores.getCount(), 2, "second core not consumed");
		player.closeContainer();

		ItemStack campfires = new ItemStack(Items.CAMPFIRE, 2);
		InteractionResult result = use(helper, player, pos, campfires);
		helper.assertTrue(result.consumesAction(), "filling consumes the action");
		helper.assertBlockPresent(Blocks.AIR, pos.above());
		helper.assertValueEqual(campfires.getCount(), 1, "one campfire stored");
		Emitter playing = emitter(helper, pos);
		helper.assertTrue(playing != null && !playing.isWaiting() && playing.material().getItem() == Items.CAMPFIRE, "emitter is playing campfire smoke");

		// A material on a playing emitter keeps vanilla behaviour (here: the torch is placed on top).
		use(helper, player, pos, new ItemStack(Items.TORCH));
		helper.assertTrue(emitter(helper, pos).material().getItem() == Items.CAMPFIRE, "playing emitter keeps its material");

		use(helper, player, pos, new ItemStack(Items.BRUSH));
		helper.assertTrue(emitter(helper, pos).isWaiting(), "brush returns the emitter to waiting");
		helper.assertItemEntityPresent(Items.CAMPFIRE, pos, 2.0);

		use(helper, player, pos, new ItemStack(Items.TORCH));
		helper.assertTrue(emitter(helper, pos).material().getItem() == Items.TORCH, "refilled with torch");

		helper.getLevel().setBlock(helper.absolutePos(pos.above()), Blocks.AIR.defaultBlockState(), 3);
		player.gameMode.destroyBlock(helper.absolutePos(pos));
		helper.assertTrue(emitter(helper, pos) == null, "breaking unbinds");
		helper.assertItemEntityPresent(Items.OAK_PLANKS, pos, 2.0);
		helper.assertItemEntityPresent(Items.TORCH, pos, 2.0);
		helper.assertItemEntityNotPresent(Items.AMETHYST_SHARD, pos, 2.0);
		helper.succeed();
	}

	@GameTest
	public void breakingWaitingBlockDropsOnlyTheBlock(GameTestHelper helper) {
		ServerPlayer player = survivalPlayer(helper);
		BlockPos pos = new BlockPos(1, 1, 1);
		helper.setBlock(pos, Blocks.OAK_PLANKS);
		use(helper, player, pos, CoreItem.create(1));
		player.gameMode.destroyBlock(helper.absolutePos(pos));
		helper.assertTrue(emitter(helper, pos) == null, "breaking unbinds");
		helper.assertItemEntityCountIs(Items.OAK_PLANKS, pos, 2.0, 1);
		helper.assertItemEntityNotPresent(Items.AMETHYST_SHARD, pos, 2.0);
		helper.succeed();
	}

	@GameTest
	public void creativeBreakDropsNothingExtra(GameTestHelper helper) {
		floor(helper);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.CREATIVE);
		BlockPos pos = new BlockPos(1, 1, 1);
		helper.setBlock(pos, Blocks.OAK_PLANKS);
		ItemStack core = CoreItem.create(1);
		use(helper, player, pos, core);
		helper.assertValueEqual(core.getCount(), 1, "creative does not consume the core");
		use(helper, player, pos, new ItemStack(Items.TORCH));
		player.gameMode.destroyBlock(helper.absolutePos(pos));
		helper.assertTrue(emitter(helper, pos) == null, "breaking unbinds");
		helper.assertItemEntityNotPresent(Items.TORCH, pos, 2.0);
		helper.succeed();
	}

	@GameTest
	public void fillingDoesNotOpenChest(GameTestHelper helper) {
		ServerPlayer player = survivalPlayer(helper);
		BlockPos pos = new BlockPos(1, 1, 1);
		helper.setBlock(pos, Blocks.CHEST);
		use(helper, player, pos, CoreItem.create(1));
		helper.assertTrue(emitter(helper, pos) != null, "chest bound");
		use(helper, player, pos, new ItemStack(Items.CAMPFIRE));
		helper.assertTrue(player.containerMenu == player.inventoryMenu, "chest must not open while filling");
		helper.assertTrue(!emitter(helper, pos).isWaiting(), "chest emitter playing");
		helper.assertValueEqual(FakeDisplayEntity.kindFor(helper.getBlockState(pos)), FakeDisplayEntity.Kind.ITEM, "chest uses item_display outline");
		helper.succeed();
	}

	@GameTest
	public void unbindableBlocksAreRejected(GameTestHelper helper) {
		helper.assertFalse(BlockValidator.canBind(Blocks.AIR.defaultBlockState()), "air");
		helper.assertFalse(BlockValidator.canBind(Blocks.WATER.defaultBlockState()), "water");
		helper.assertFalse(BlockValidator.canBind(Blocks.LAVA.defaultBlockState()), "lava");
		helper.assertFalse(BlockValidator.canBind(Blocks.SUSPICIOUS_SAND.defaultBlockState()), "suspicious sand");
		helper.assertFalse(BlockValidator.canBind(Blocks.SUSPICIOUS_GRAVEL.defaultBlockState()), "suspicious gravel");
		helper.assertTrue(BlockValidator.canBind(Blocks.STONE.defaultBlockState()), "stone");

		ServerPlayer player = survivalPlayer(helper);
		BlockPos pos = new BlockPos(1, 1, 1);
		helper.setBlock(pos, Blocks.SUSPICIOUS_SAND);
		ItemStack core = CoreItem.create(1);
		use(helper, player, pos, core);
		helper.assertTrue(emitter(helper, pos) == null, "suspicious sand not bound");
		helper.assertValueEqual(core.getCount(), 1, "core kept");
		helper.succeed();
	}

	@GameTest(maxTicks = 60)
	public void removedBlockUnbindsAndDropsMaterial(GameTestHelper helper) {
		ServerPlayer player = survivalPlayer(helper);
		BlockPos pos = new BlockPos(1, 1, 1);
		helper.setBlock(pos, Blocks.OAK_PLANKS);
		use(helper, player, pos, CoreItem.create(1));
		use(helper, player, pos, new ItemStack(Items.TORCH));
		// Simulates an explosion, piston or fire removing the block without a player.
		helper.setBlock(pos, Blocks.AIR);
		helper.succeedWhen(() -> {
			helper.assertTrue(emitter(helper, pos) == null, "emitter should be unbound by the scheduler");
			helper.assertItemEntityPresent(Items.TORCH, pos, 1.5);
		});
	}

	@GameTest(maxTicks = 60)
	public void replacedBlockUnbinds(GameTestHelper helper) {
		ServerPlayer player = survivalPlayer(helper);
		BlockPos pos = new BlockPos(1, 1, 1);
		helper.setBlock(pos, Blocks.OAK_PLANKS);
		use(helper, player, pos, CoreItem.create(1));
		helper.setBlock(pos, Blocks.STONE);
		helper.succeedWhen(() -> helper.assertTrue(emitter(helper, pos) == null, "different block type unbinds"));
	}

	@GameTest(maxTicks = 60)
	public void blockStateChangeKeepsBinding(GameTestHelper helper) {
		ServerPlayer player = survivalPlayer(helper);
		BlockPos pos = new BlockPos(1, 1, 1);
		helper.setBlock(pos, Blocks.OAK_FENCE_GATE);
		use(helper, player, pos, CoreItem.create(1));
		helper.assertTrue(emitter(helper, pos) != null, "gate bound");
		BlockState open = helper.getBlockState(pos).setValue(FenceGateBlock.OPEN, true);
		helper.setBlock(pos, open);
		helper.runAfterDelay(40, () -> {
			helper.assertTrue(emitter(helper, pos) != null, "state change must not unbind");
			helper.succeed();
		});
	}

	@GameTest
	public void anvilRenameCreatesCore(GameTestHelper helper) {
		ServerPlayer player = survivalPlayer(helper);
		AnvilMenu menu = new AnvilMenu(0, player.getInventory());
		menu.getSlot(AnvilMenu.INPUT_SLOT).set(new ItemStack(Items.AMETHYST_SHARD, 4));
		menu.setItemName(ParticleDeco.config().coreAnvilName);
		ItemStack result = menu.getSlot(AnvilMenu.RESULT_SLOT).getItem();
		helper.assertTrue(CoreItem.isCore(result), "renamed amethyst shard becomes a core");
		helper.assertTrue(Boolean.TRUE.equals(result.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE)), "core has glint");

		AnvilMenu other = new AnvilMenu(0, player.getInventory());
		other.getSlot(AnvilMenu.INPUT_SLOT).set(new ItemStack(Items.AMETHYST_SHARD));
		other.setItemName("something else");
		helper.assertFalse(CoreItem.isCore(other.getSlot(AnvilMenu.RESULT_SLOT).getItem()), "other names are not cores");

		ItemStack renamedCore = CoreItem.create(1);
		renamedCore.set(DataComponents.CUSTOM_NAME, Component.literal("renamed"));
		helper.assertTrue(CoreItem.isCore(renamedCore), "renaming a core keeps it a core");
		ItemStack sameName = new ItemStack(Items.AMETHYST_SHARD);
		sameName.set(DataComponents.CUSTOM_NAME, Component.literal(ParticleDeco.config().coreAnvilName));
		helper.assertFalse(CoreItem.isCore(sameName), "same name without marker is not a core");
		helper.succeed();
	}

	@GameTest
	public void emitterCodecRoundTrip(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
		Emitter original = Emitter.waiting(pos, Blocks.OAK_PLANKS)
				.withMaterial(new ItemStack(Items.REDSTONE), ParticleDeco.id("dummy"))
				.withOffset(3, -5, 32)
				.withShape(EmitterShape.RING)
				.withShapeSize(2.5f)
				.withCount(5)
				.withInterval(20)
				.withSpread(0.3f)
				.withRedstoneMode(RedstoneMode.ON_SIGNAL)
				.withDye(DyeColor.LIME);
		ChunkEmitters chunk = ChunkEmitters.EMPTY.with(original);
		DynamicOps<Tag> ops = level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
		Tag encoded = ChunkEmitters.CODEC.encodeStart(ops, chunk).getOrThrow();
		ChunkEmitters decoded = ChunkEmitters.CODEC.parse(ops, encoded).getOrThrow();
		Emitter copy = decoded.get(pos);
		helper.assertTrue(copy != null, "decoded emitter present");
		helper.assertTrue(ItemStack.matches(copy.material(), original.material()), "material survives");
		helper.assertValueEqual(copy.block(), original.block(), "block");
		helper.assertValueEqual(copy.offsetX(), 3, "offset x");
		helper.assertValueEqual(copy.offsetY(), -5, "offset y");
		helper.assertValueEqual(copy.offsetZ(), 32, "offset z");
		helper.assertValueEqual(copy.shape(), EmitterShape.RING, "shape");
		helper.assertValueEqual(copy.shapeSize(), 2.5f, "size");
		helper.assertValueEqual(copy.count(), 5, "count");
		helper.assertValueEqual(copy.interval(), 20, "interval");
		helper.assertValueEqual(copy.redstoneMode(), RedstoneMode.ON_SIGNAL, "redstone");
		helper.assertValueEqual(copy.dye(), Optional.of(DyeColor.LIME), "dye");
		helper.assertValueEqual(copy.dataVersion(), Emitter.CURRENT_DATA_VERSION, "data version");
		helper.assertValueEqual(copy.particle(), original.particle(), "particle id");

		Emitter clamped = original.withOffset(99, -99, 0).withInterval(1).withShapeSize(100f);
		helper.assertValueEqual(clamped.offsetX(), Emitter.MAX_OFFSET, "offset clamped");
		helper.assertValueEqual(clamped.offsetY(), -Emitter.MAX_OFFSET, "offset clamped");
		helper.assertValueEqual(clamped.interval(), Emitter.MIN_INTERVAL, "interval clamped");
		helper.assertValueEqual(clamped.shapeSize(), EmitterShape.RING.maxSize(), "size clamped");
		helper.succeed();
	}

	@GameTest
	public void attachmentPersistsInChunk(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos rel = new BlockPos(1, 1, 1);
		helper.setBlock(rel, Blocks.OAK_PLANKS);
		BlockPos pos = helper.absolutePos(rel);
		EmitterActions.bind(level, pos);
		var chunk = level.getChunkAt(pos);
		ChunkEmitters stored = chunk.getAttached(com.yoyolee.particledeco.runtime.EmitterManager.EMITTERS);
		helper.assertTrue(stored != null && stored.get(pos) != null, "attachment written to chunk");
		helper.assertTrue(chunk.isUnsaved(), "chunk marked for saving");
		EmitterActions.unbindAndDropAt(level, pos);
		helper.assertTrue(emitter(helper, rel) == null, "removed");
		helper.succeed();
	}

	@GameTest
	public void outlineVisibilityFollowsHeldItem(GameTestHelper helper) {
		ServerPlayer player = survivalPlayer(helper);
		Vec3 standAt = helper.absoluteVec(new Vec3(1.5, 1, 3.5));
		player.teleportTo(standAt.x, standAt.y, standAt.z);
		BlockPos waiting = new BlockPos(0, 1, 1);
		BlockPos playing = new BlockPos(2, 1, 1);
		helper.setBlock(waiting, Blocks.OAK_PLANKS);
		helper.setBlock(playing, Blocks.OAK_FENCE);
		use(helper, player, waiting, CoreItem.create(1));
		use(helper, player, playing, CoreItem.create(1));
		use(helper, player, playing, new ItemStack(Items.TORCH));

		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		ParticleDeco.outlines().refreshNow(player);
		helper.assertTrue(ParticleDeco.outlines().isShown(player, helper.absolutePos(waiting)), "waiting always outlined");
		helper.assertFalse(ParticleDeco.outlines().isShown(player, helper.absolutePos(playing)), "playing hidden without tool");

		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BRUSH));
		ParticleDeco.outlines().refreshNow(player);
		helper.assertTrue(ParticleDeco.outlines().isShown(player, helper.absolutePos(playing)), "playing shown with brush");

		player.setItemInHand(InteractionHand.MAIN_HAND, CoreItem.create(1));
		ParticleDeco.outlines().refreshNow(player);
		helper.assertTrue(ParticleDeco.outlines().isShown(player, helper.absolutePos(playing)), "playing shown with core");

		FakeDisplayEntity fence = new FakeDisplayEntity(helper.absolutePos(playing), helper.getBlockState(playing), 0x4AD9FF);
		helper.assertValueEqual(fence.kind(), FakeDisplayEntity.Kind.BLOCK, "fence uses block_display");
		helper.assertValueEqual(fence.spawnPackets().size(), 2, "add entity + entity data");
		helper.assertTrue(fence.dataPacket().packedItems().size() >= 6, "glow, color, scale, brightness, translation, block state");
		helper.succeed();
	}

	@GameTest
	public void packetBudgetDefersOverflow(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = survivalPlayer(helper);
		BlockPos rel = new BlockPos(1, 1, 1);
		helper.setBlock(rel, Blocks.OAK_PLANKS);
		BlockPos pos = helper.absolutePos(rel);
		player.teleportTo(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5);
		Emitter emitter = EmitterActions.bind(level, pos)
				.withMaterial(new ItemStack(Items.TORCH), ParticleDeco.id("flame"))
				.withShape(EmitterShape.COLUMN)
				.withCount(4);
		ParticleDeco.manager().put(level, emitter);

		ModConfig saved = ParticleDeco.config();
		ModConfig tight = ModConfig.parse(saved.toJson(), w -> { });
		tight.globalPacketsPerTick = 2;
		ParticleDeco.setConfig(tight);

		try {
			int before = ParticleDeco.scheduler().pendingDeferred();
			ParticleDeco.scheduler().resetBudget();
			ParticleDeco.scheduler().emitNow(level, emitter, List.of(player));
			helper.assertTrue(ParticleDeco.scheduler().pendingDeferred() > before, "4 packets over a budget of 2 must be deferred");
		} finally {
			ParticleDeco.setConfig(saved);
			ParticleDeco.scheduler().resetBudget();
		}

		helper.succeed();
	}

	@GameTest
	public void invalidConfigFallsBackToDefaults(GameTestHelper helper) throws Exception {
		List<String> warnings = new ArrayList<>();
		JsonObject bad = JsonParser.parseString("""
				{"core": "nope", "viewDistance": -5, "brushDurabilityCost": "x",
				 "outline": {"waitingColor": "#GGGGGG", "showDistance": 1000},
				 "limits": {"perChunk": 0, "maxCountPerEmit": 3.5}, "bindBlacklist": 7, "language": "fr_fr"}
				""").getAsJsonObject();
		ModConfig parsed = ModConfig.parse(bad, warnings::add);
		ModConfig defaults = new ModConfig();
		helper.assertValueEqual(parsed.viewDistance, defaults.viewDistance, "viewDistance default");
		helper.assertValueEqual(parsed.outlineWaitingColor, defaults.outlineWaitingColor, "color default");
		helper.assertValueEqual(parsed.outlineShowDistance, defaults.outlineShowDistance, "showDistance default");
		helper.assertValueEqual(parsed.perChunk, defaults.perChunk, "perChunk default");
		helper.assertValueEqual(parsed.maxCountPerEmit, defaults.maxCountPerEmit, "maxCount default");
		helper.assertValueEqual(parsed.language, defaults.language, "language default");
		helper.assertTrue(warnings.size() >= 8, "every bad value warns, got " + warnings);

		ModConfig saved = ParticleDeco.config();
		MaterialTable savedMaterials = ParticleDeco.materials();
		Path dir = Files.createTempDirectory("pdeco-config");

		try {
			Files.writeString(dir.resolve("config.json"), "{ this is not json");
			Files.writeString(dir.resolve("materials.json"), "{\"materials\": [{\"items\": [\"minecraft:not_an_item\"], \"particle\": \"minecraft:flame\"}]}");
			ConfigManager.Result result = ConfigManager.load(dir);
			helper.assertTrue(!result.warnings().isEmpty(), "broken files produce warnings");
			helper.assertValueEqual(result.config().viewDistance, defaults.viewDistance, "broken config uses defaults");
			helper.assertTrue(result.materials().size() >= 58, "empty material table falls back to defaults");
		} finally {
			ParticleDeco.setConfig(saved);
			ParticleDeco.setMaterials(savedMaterials);
		}

		helper.succeed();
	}

	@GameTest
	public void redstoneModes(GameTestHelper helper) {
		helper.assertTrue(RedstoneMode.IGNORE.allows(false) && RedstoneMode.IGNORE.allows(true), "ignore");
		helper.assertTrue(RedstoneMode.ON_SIGNAL.allows(true) && !RedstoneMode.ON_SIGNAL.allows(false), "on signal");
		helper.assertTrue(!RedstoneMode.NO_SIGNAL.allows(true) && RedstoneMode.NO_SIGNAL.allows(false), "no signal");
		helper.succeed();
	}

	@GameTest
	public void commandsRun(GameTestHelper helper) {
		var server = helper.getLevel().getServer();
		var source = server.createCommandSourceStack().withLevel(helper.getLevel()).withPosition(helper.absoluteVec(new Vec3(1, 1, 1))).withSuppressedOutput();
		server.getCommands().performPrefixedCommand(source, "pdeco stats");
		server.getCommands().performPrefixedCommand(source, "pdeco near 8");
		helper.setBlock(new BlockPos(1, 1, 1), Blocks.OAK_PLANKS);
		BlockPos abs = helper.absolutePos(new BlockPos(1, 1, 1));
		EmitterActions.bind(helper.getLevel(), abs);
		server.getCommands().performPrefixedCommand(source, "pdeco remove " + abs.getX() + " " + abs.getY() + " " + abs.getZ());
		helper.assertTrue(emitter(helper, new BlockPos(1, 1, 1)) == null, "pdeco remove unbinds");
		helper.succeed();
	}
}
