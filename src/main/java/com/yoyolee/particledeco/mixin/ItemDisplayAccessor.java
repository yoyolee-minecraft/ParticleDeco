package com.yoyolee.particledeco.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.Display;
import net.minecraft.world.item.ItemStack;

@Mixin(Display.ItemDisplay.class)
public interface ItemDisplayAccessor {
	@Accessor("DATA_ITEM_STACK_ID")
	static EntityDataAccessor<ItemStack> particledeco$getItemStack() {
		throw new UnsupportedOperationException();
	}

	@Accessor("DATA_ITEM_DISPLAY_ID")
	static EntityDataAccessor<Byte> particledeco$getItemDisplay() {
		throw new UnsupportedOperationException();
	}
}
