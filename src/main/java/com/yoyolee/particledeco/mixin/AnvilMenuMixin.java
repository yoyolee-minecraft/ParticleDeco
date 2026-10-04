package com.yoyolee.particledeco.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;

import com.yoyolee.particledeco.interaction.CoreItem;

/**
 * Turns an anvil-renamed base item into a particle core by writing the custom_data marker on the result.
 */
@Mixin(AnvilMenu.class)
public abstract class AnvilMenuMixin {
	@Inject(method = "createResult", at = @At("TAIL"))
	private void particledeco$convertCore(CallbackInfo ci) {
		AnvilMenu self = (AnvilMenu) (Object) this;
		ItemStack result = self.getSlot(AnvilMenu.RESULT_SLOT).getItem();

		if (CoreItem.shouldConvert(result)) {
			CoreItem.markAsCore(result);
		}
	}
}
