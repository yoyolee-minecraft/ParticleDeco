package com.yoyolee.particledeco.gui;

import java.util.Locale;
import java.util.function.UnaryOperator;

import eu.pb4.sgui.api.ClickType;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.yoyolee.particledeco.ParticleDeco;
import com.yoyolee.particledeco.data.Emitter;
import com.yoyolee.particledeco.data.EmitterShape;
import com.yoyolee.particledeco.data.RedstoneMode;
import com.yoyolee.particledeco.util.Messages;

/**
 * Editor using a vanilla 9x3 chest menu, rendered by sgui on the server only.
 */
public final class EditorGui extends SimpleGui {
	public static final int SLOT_INFO = 0;
	public static final int SLOT_SHAPE = 2;
	public static final int SLOT_SIZE = 3;
	public static final int SLOT_COUNT = 4;
	public static final int SLOT_INTERVAL = 5;
	public static final int SLOT_SPREAD = 6;
	public static final int SLOT_REDSTONE = 7;
	public static final int SLOT_DYE = 8;
	public static final int SLOT_OFFSET_X = 10;
	public static final int SLOT_OFFSET_Y = 12;
	public static final int SLOT_OFFSET_Z = 14;
	public static final int SLOT_RESET_OFFSET = 16;
	public static final int SLOT_CLOSE = 22;

	private final ServerLevel level;
	private final BlockPos pos;

	public EditorGui(ServerPlayer player, ServerLevel level, BlockPos pos) {
		super(MenuType.GENERIC_9x3, player, false);
		this.level = level;
		this.pos = pos.immutable();
		setTitle(Messages.text("gui.title"));
		build();
	}

	public static EditorGui open(ServerPlayer player, ServerLevel level, BlockPos pos) {
		EditorGui gui = new EditorGui(player, level, pos);
		gui.open();
		return gui;
	}

	public BlockPos pos() {
		return pos;
	}

	private Emitter current() {
		return ParticleDeco.manager().get(level, pos);
	}

	/**
	 * Applies a change to the emitter; closes the menu if the emitter disappeared meanwhile.
	 */
	public void apply(UnaryOperator<Emitter> change) {
		Emitter emitter = current();

		if (emitter == null) {
			getPlayer().sendSystemMessage(Messages.error("gui.removed"));
			close();
			return;
		}

		ParticleDeco.manager().put(level, change.apply(emitter));
		build();
	}

	private void build() {
		Emitter e = current();

		if (e == null) return;

		for (int i = 0; i < 27; i++) {
			setSlot(i, new GuiElementBuilder(item("gray_stained_glass_pane")).setName(Component.empty()).hideTooltip().build());
		}

		int maxCount = ParticleDeco.config().maxCountPerEmit;
		ItemStack infoStack = e.isWaiting() ? new ItemStack(Items.BARRIER) : e.material().copyWithCount(1);
		GuiElementBuilder info = GuiElementBuilder.from(infoStack)
				.setName(e.isWaiting() ? Messages.text("gui.waiting").withStyle(ChatFormatting.YELLOW)
						: Messages.text("gui.material", e.material().getHoverName().getString()).withStyle(ChatFormatting.AQUA));
		e.particle().ifPresent(id -> info.addLoreLine(Messages.info("gui.particle", id.toString())));
		var materialEntry = e.isWaiting() ? null : ParticleDeco.materials().find(e.material());

		if (materialEntry != null && materialEntry.pattern() != null) {
			info.addLoreLine(Messages.info("gui.pattern"));
		}
		info.addLoreLine(Component.literal(pos.getX() + " " + pos.getY() + " " + pos.getZ()).withStyle(ChatFormatting.DARK_GRAY));
		setSlot(SLOT_INFO, info.build());

		boolean patterned = materialEntry != null && materialEntry.pattern() != null;

		if (patterned) {
			// Random vanilla rhythm decides timing, count and positions; these settings would have no effect.
			setSlot(SLOT_SHAPE, inactive(Items.PRISMARINE_SHARD, Messages.text("gui.shape", Messages.raw("shape." + e.shape().serializedName())), "gui.unused.pattern"));
			setSlot(SLOT_SIZE, inactive(Items.SCAFFOLDING, Messages.text("gui.size.unused"), "gui.unused.pattern"));
			setSlot(SLOT_COUNT, inactive(Items.GLOWSTONE_DUST, Messages.text("gui.count", e.count()), "gui.unused.pattern"));
			setSlot(SLOT_INTERVAL, inactive(Items.CLOCK, Messages.text("gui.interval", e.interval()), "gui.unused.pattern"));
		} else {
			setSlot(SLOT_SHAPE, button(Items.PRISMARINE_SHARD, Messages.text("gui.shape", Messages.raw("shape." + e.shape().serializedName())), "gui.hint.cycle",
					(type) -> apply(x -> x.withShape(type.isRight ? x.shape().previous() : x.shape().next())),
					"gui.shape.help.point", "gui.shape.help.column", "gui.shape.help.ring", "gui.shape.help.area"));

			if (e.shape() == EmitterShape.POINT) {
				setSlot(SLOT_SIZE, inactive(Items.SCAFFOLDING, Messages.text("gui.size.unused"), "gui.size.point"));
			} else {
				String sizeKey = "gui.size." + e.shape().serializedName();
				setSlot(SLOT_SIZE, button(Items.SCAFFOLDING, Messages.text(sizeKey, fmt(e.shapeSize()), fmt(e.shape().minSize()), fmt(e.shape().maxSize())), "gui.hint.adjust",
						(type) -> apply(x -> x.withShapeSize(x.shapeSize() + sign(type) * (type.shift ? 1.0f : 0.25f)))));
			}

			setSlot(SLOT_COUNT, button(Items.GLOWSTONE_DUST, Messages.text("gui.count", e.count()), "gui.hint.adjust",
					(type) -> apply(x -> x.withCount(Mth.clamp(x.count() + sign(type) * (type.shift ? 4 : 1), 1, maxCount)))));
			setSlot(SLOT_INTERVAL, button(Items.CLOCK, Messages.text("gui.interval", e.interval()), "gui.hint.adjust",
					(type) -> apply(x -> x.withInterval(x.interval() + sign(type) * (type.shift ? 10 : 1)))));
		}

		setSlot(SLOT_SPREAD, button(Items.FEATHER, Messages.text("gui.spread", fmt(e.spread())), "gui.hint.adjust",
				(type) -> apply(x -> x.withSpread(x.spread() + sign(type) * (type.shift ? 0.25f : 0.05f)))));
		setSlot(SLOT_REDSTONE, button(Items.REDSTONE_TORCH, Messages.text("gui.redstone", Messages.raw("redstone." + e.redstoneMode().name().toLowerCase(Locale.ROOT))), "gui.hint.cycle",
				(type) -> apply(x -> x.withRedstoneMode(cycle(x.redstoneMode(), type.isRight)))));

		String dyeName = e.dye().map(DyeColor::getName).orElse(Messages.raw("gui.dye.none"));
		setSlot(SLOT_DYE, new GuiElementBuilder(dyeItem(e.dye().orElse(null)))
				.setName(Messages.text("gui.dye", dyeName))
				.addLoreLine(Messages.info("gui.hint.dye"))
				.addLoreLine(Messages.info("gui.hint.cycle"))
				.setCallback((index, type, action, gui) -> clickDye(type))
				.build());

		setSlot(SLOT_OFFSET_X, button(item("red_concrete"), Messages.text("gui.offset", "X", e.offsetX()), "gui.hint.adjust",
				(type) -> apply(x -> x.withOffset(x.offsetX() + sign(type) * (type.shift ? 4 : 1), x.offsetY(), x.offsetZ()))));
		setSlot(SLOT_OFFSET_Y, button(item("lime_concrete"), Messages.text("gui.offset", "Y", e.offsetY()), "gui.hint.adjust",
				(type) -> apply(x -> x.withOffset(x.offsetX(), x.offsetY() + sign(type) * (type.shift ? 4 : 1), x.offsetZ()))));
		setSlot(SLOT_OFFSET_Z, button(item("blue_concrete"), Messages.text("gui.offset", "Z", e.offsetZ()), "gui.hint.adjust",
				(type) -> apply(x -> x.withOffset(x.offsetX(), x.offsetY(), x.offsetZ() + sign(type) * (type.shift ? 4 : 1)))));
		setSlot(SLOT_RESET_OFFSET, new GuiElementBuilder(Items.WATER_BUCKET)
				.setName(Messages.text("gui.reset_offset"))
				.setCallback((index, type, action, gui) -> apply(x -> x.withOffset(0, 0, 0)))
				.build());
		setSlot(SLOT_CLOSE, new GuiElementBuilder(Items.BARRIER)
				.setName(Messages.text("gui.close").withStyle(ChatFormatting.RED))
				.setCallback((index, type, action, gui) -> close())
				.build());
	}

	private void clickDye(ClickType type) {
		ItemStack carried = getPlayer().containerMenu.getCarried();
		DyeColor fromCursor = carried.get(DataComponents.DYE);

		if (fromCursor != null) {
			apply(x -> x.withDye(fromCursor));
			return;
		}

		if (type.shift) {
			apply(x -> x.withDye(null));
			return;
		}

		apply(x -> x.withDye(cycleDye(x.dye().orElse(null), type.isRight)));
	}

	public static DyeColor cycleDye(DyeColor current, boolean backwards) {
		DyeColor[] values = DyeColor.values();

		if (current == null) return backwards ? values[values.length - 1] : values[0];

		int next = current.ordinal() + (backwards ? -1 : 1);

		if (next < 0 || next >= values.length) return null;

		return values[next];
	}

	private static RedstoneMode cycle(RedstoneMode mode, boolean backwards) {
		RedstoneMode[] values = RedstoneMode.values();
		return values[(mode.ordinal() + (backwards ? values.length - 1 : 1)) % values.length];
	}

	private static Item dyeItem(DyeColor color) {
		return item((color == null ? "white" : color.getName()) + "_dye");
	}

	/**
	 * Colored items are color collections in 26.x, so look them up by id.
	 */
	private static Item item(String path) {
		Item item = BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(path));
		return item == Items.AIR ? Items.PAPER : item;
	}

	private interface Click {
		void click(ClickType type);
	}

	private static eu.pb4.sgui.api.elements.GuiElement button(Item icon, Component name, String hintKey, Click click, String... helpKeys) {
		GuiElementBuilder builder = new GuiElementBuilder(icon).setName(name);

		for (String helpKey : helpKeys) {
			builder.addLoreLine(Messages.info(helpKey));
		}

		return builder
				.addLoreLine(Messages.info(hintKey))
				.setCallback((index, type, action, gui) -> {
					if (type.isLeft || type.isRight) click.click(type);
				})
				.build();
	}

	/**
	 * A setting that does not apply right now: greyed out, explains why, ignores clicks.
	 */
	private static eu.pb4.sgui.api.elements.GuiElement inactive(Item icon, MutableComponent name, String reasonKey) {
		return new GuiElementBuilder(icon)
				.setName(name.withStyle(ChatFormatting.DARK_GRAY))
				.addLoreLine(Messages.info(reasonKey))
				.build();
	}

	private static int sign(ClickType type) {
		return type.isRight ? -1 : 1;
	}

	private static String fmt(float value) {
		return String.format(Locale.ROOT, "%.2f", value);
	}
}
