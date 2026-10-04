package com.yoyolee.particledeco.interaction;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

import com.yoyolee.particledeco.ParticleDeco;

/**
 * The particle core is a plain vanilla item marked with {@code minecraft:custom_data} {particledeco_core: 1b}.
 * No custom item or data component is registered, so vanilla clients can join.
 */
public final class CoreItem {
	public static final String TAG_KEY = "particledeco_core";
	private static final CompoundTag MARKER = new CompoundTag();

	static {
		MARKER.putByte(TAG_KEY, (byte) 1);
	}

	private CoreItem() {
	}

	public static Item baseItem() {
		Identifier id = Identifier.tryParse(ParticleDeco.config().coreItem);
		Item item = id == null ? Items.AIR : BuiltInRegistries.ITEM.getValue(id);
		return item == Items.AIR ? Items.AMETHYST_SHARD : item;
	}

	public static boolean isCore(ItemStack stack) {
		if (stack.isEmpty()) return false;

		CustomData data = stack.get(DataComponents.CUSTOM_DATA);

		if (data == null || data.isEmpty()) return false;

		// Exact byte match first; any numeric 1 is also accepted so datapack recipes written in JSON still count.
		return data.matchedBy(MARKER) || data.copyTag().getByteOr(TAG_KEY, (byte) 0) == 1;
	}

	/**
	 * Writes the core marker and glint onto an existing stack in place.
	 */
	public static void markAsCore(ItemStack stack) {
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putByte(TAG_KEY, (byte) 1));
		stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
	}

	public static ItemStack create(int count) {
		ItemStack stack = new ItemStack(baseItem(), count);
		markAsCore(stack);
		stack.set(DataComponents.CUSTOM_NAME, Component.literal(ParticleDeco.config().coreAnvilName));
		return stack;
	}

	/**
	 * Anvil conversion check: the renamed result must be the configured base item, carry exactly the configured name
	 * and not yet be a core.
	 */
	public static boolean shouldConvert(ItemStack result) {
		if (ParticleDeco.config().coreRecipeMode) return false;

		if (result.isEmpty() || result.getItem() != baseItem() || isCore(result)) return false;

		Component name = result.get(DataComponents.CUSTOM_NAME);
		return name != null && name.getString().equals(ParticleDeco.config().coreAnvilName);
	}
}
