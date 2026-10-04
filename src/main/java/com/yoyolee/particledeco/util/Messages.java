package com.yoyolee.particledeco.util;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import com.yoyolee.particledeco.ParticleDeco;

/**
 * Server-side translations. Vanilla clients do not have this mod's language files, so every text is sent as a literal.
 */
public final class Messages {
	private static final Map<String, String> ZH = new HashMap<>();
	private static final Map<String, String> EN = new HashMap<>();

	static {
		put("bind.success", "已綁定，放入材料即可開始播放粒子", "Bound. Use a material on the block to start particles");
		put("bind.invalid", "這個方塊不能綁定粒子", "This block cannot hold a particle emitter");
		put("bind.chunk_limit", "這個區塊的發射點已達上限（%s）", "This chunk already has the maximum of %s emitters");
		put("fill.success", "開始播放：%s", "Now playing: %s");
		put("brush.success", "已刷除粒子，材料已歸還", "Particles brushed off, material returned");
		put("toggle.off", "已關閉裝飾粒子顯示", "Decoration particles hidden");
		put("toggle.on", "已開啟裝飾粒子顯示", "Decoration particles shown");
		put("near.none", "附近 %s 格內沒有發射點", "No emitters within %s blocks");
		put("near.header", "附近 %s 格內有 %s 個發射點：", "%2$s emitters within %1$s blocks:");
		put("near.entry", "%s %s %s  %s  %s", "%s %s %s  %s  %s");
		put("state.waiting", "等待填入", "waiting");
		put("state.playing", "播放中", "playing");
		put("remove.success", "已解除 %s %s %s 的綁定", "Unbound %s %s %s");
		put("remove.none", "該位置沒有發射點", "No emitter at that position");
		put("purge.success", "已解除 %s 個發射點", "Unbound %s emitters");
		put("give.success", "已給予 %s 個粒子核心給 %s", "Gave %s particle cores to %s");
		put("reload.success", "已重新載入設定（材料 %s 筆，警告 %s 則）", "Reloaded config (%s materials, %s warnings)");
		put("stats.line1", "已載入發射點 %s 個，分布於 %s 個區塊", "%s loaded emitters in %s chunks");
		put("stats.line2", "封包：上一 tick %s，近 20 tick 平均 %s / tick，累計 %s", "Packets: last tick %s, average %s / tick over 20 ticks, total %s");
		put("stats.line3", "延後：上一 tick %s，等待中 %s，累計 %s，丟棄 %s", "Deferred: last tick %s, pending %s, total %s, dropped %s");
		put("stats.line4", "耗時：平均 %s ms / tick（其中交給連線送出 %s ms）", "Time: %s ms / tick on average (%s ms handing packets to connections)");
		put("gui.title", "粒子設定", "Particle settings");
		put("gui.waiting", "等待填入材料", "Waiting for material");
		put("gui.material", "材料：%s", "Material: %s");
		put("gui.particle", "粒子：%s", "Particle: %s");
		put("gui.shape", "形狀：%s", "Shape: %s");
		put("gui.size", "大小：%s 格", "Size: %s blocks");
		put("gui.count", "每次數量：%s", "Count per emit: %s");
		put("gui.interval", "間隔：%s tick", "Interval: %s ticks");
		put("gui.spread", "擴散：%s 格", "Spread: %s blocks");
		put("gui.redstone", "紅石模式：%s", "Redstone mode: %s");
		put("gui.dye", "染料顏色：%s", "Dye color: %s");
		put("gui.pattern", "隨機節奏（照原版）：間隔、數量與形狀不套用", "Random vanilla rhythm: interval, count and shape are not used");
		put("gui.dye.none", "預設", "default");
		put("gui.offset", "%s 軸偏移：%s / 16 格", "%s offset: %s / 16 block");
		put("gui.reset_offset", "重設偏移", "Reset offset");
		put("gui.close", "關閉", "Close");
		put("gui.hint.adjust", "左鍵增加，右鍵減少，Shift 加大步進", "Left click +, right click -, shift for larger steps");
		put("gui.hint.cycle", "左鍵下一個，右鍵上一個", "Left click next, right click previous");
		put("gui.hint.dye", "拿染料點擊可直接套用，Shift 點擊清除", "Click with a dye to apply it, shift click to clear");
		put("gui.removed", "這個發射點已不存在", "This emitter no longer exists");
		put("shape.point", "單點", "point");
		put("shape.column", "垂直柱", "column");
		put("shape.ring", "水平圓環", "ring");
		put("shape.area", "方形區域", "area");
		put("redstone.ignore", "無視", "ignore");
		put("redstone.on_signal", "有訊號才播", "only when powered");
		put("redstone.no_signal", "無訊號才播", "only when unpowered");
		put("core.name", "粒子核心", "Particle Core");
	}

	private Messages() {
	}

	private static void put(String key, String zh, String en) {
		ZH.put(key, zh);
		EN.put(key, en);
	}

	public static String raw(String key) {
		Map<String, String> map = "en_us".equals(ParticleDeco.config().language) ? EN : ZH;
		return map.getOrDefault(key, key);
	}

	public static String format(String key, Object... args) {
		return String.format(raw(key), args);
	}

	public static MutableComponent text(String key, Object... args) {
		return Component.literal(format(key, args));
	}

	public static MutableComponent info(String key, Object... args) {
		return text(key, args).withStyle(ChatFormatting.GRAY);
	}

	public static MutableComponent error(String key, Object... args) {
		return text(key, args).withStyle(ChatFormatting.RED);
	}
}
