package com.yoyolee.particledeco.outline;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.joml.Vector3f;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import com.yoyolee.particledeco.mixin.BlockDisplayAccessor;
import com.yoyolee.particledeco.mixin.DisplayAccessor;
import com.yoyolee.particledeco.mixin.EntityAccessor;
import com.yoyolee.particledeco.mixin.ItemDisplayAccessor;
import com.yoyolee.particledeco.mixin.ServerLevelAccessor;

/**
 * Packet-only glowing display entity used as an outline. It never exists in the server world.
 * Entity ids are taken from the vanilla entity counter so they can never collide with real entities.
 */
public final class FakeDisplayEntity {
	public enum Kind {
		BLOCK,
		ITEM,
		MARKER
	}

	private static final float SCALE = 1.002f;
	private static final float OFFSET = (SCALE - 1.0f) / 2.0f;

	private final int id;
	private final UUID uuid = UUID.randomUUID();
	private final BlockPos pos;
	private Kind kind;
	private BlockState state;
	private int color;

	public FakeDisplayEntity(BlockPos pos, BlockState state, int color) {
		this.id = nextId();
		this.pos = pos.immutable();
		this.state = state;
		this.kind = kindFor(state);
		this.color = color;
	}

	public static int nextId() {
		int id = ServerLevelAccessor.particledeco$getEntityCounter().incrementAndGet();

		if (id == 0) id = ServerLevelAccessor.particledeco$getEntityCounter().incrementAndGet();

		return id;
	}

	/**
	 * Blocks drawn by a block entity renderer (chests, signs, banners...) are not visible as block_display,
	 * so they use an item_display of the block item. Blocks without item fall back to corner particles.
	 */
	public static Kind kindFor(BlockState state) {
		if (state.getRenderShape() == RenderShape.MODEL) return Kind.BLOCK;

		return state.getBlock().asItem() == Items.AIR ? Kind.MARKER : Kind.ITEM;
	}

	public int id() {
		return id;
	}

	public BlockPos pos() {
		return pos;
	}

	public Kind kind() {
		return kind;
	}

	public BlockState state() {
		return state;
	}

	public int color() {
		return color;
	}

	/**
	 * Updates the tracked state and color.
	 *
	 * @return true when the kind changed and the entity must be re-created
	 */
	public boolean update(BlockState newState, int newColor) {
		Kind newKind = kindFor(newState);
		boolean recreate = newKind != kind;
		this.state = newState;
		this.kind = newKind;
		this.color = newColor;
		return recreate;
	}

	public List<Packet<? super ClientGamePacketListener>> spawnPackets() {
		List<Packet<? super ClientGamePacketListener>> packets = new ArrayList<>();

		if (kind == Kind.MARKER) return packets;

		EntityType<?> type = kind == Kind.BLOCK ? EntityType.BLOCK_DISPLAY : EntityType.ITEM_DISPLAY;
		Vec3 at = kind == Kind.BLOCK ? Vec3.atLowerCornerOf(pos) : Vec3.atCenterOf(pos);
		packets.add(new ClientboundAddEntityPacket(id, uuid, at.x, at.y, at.z, 0.0f, 0.0f, type, 0, Vec3.ZERO, 0.0));
		packets.add(dataPacket());
		return packets;
	}

	public ClientboundSetEntityDataPacket dataPacket() {
		List<SynchedEntityData.DataValue<?>> values = new ArrayList<>();
		values.add(SynchedEntityData.DataValue.create(EntityAccessor.particledeco$getSharedFlags(), (byte) (1 << EntityAccessor.particledeco$getFlagGlowing())));
		values.add(SynchedEntityData.DataValue.create(DisplayAccessor.particledeco$getGlowColor(), color & 0xFFFFFF));
		values.add(SynchedEntityData.DataValue.create(DisplayAccessor.particledeco$getScale(), new Vector3f(SCALE, SCALE, SCALE)));
		values.add(SynchedEntityData.DataValue.create(DisplayAccessor.particledeco$getBrightness(), Brightness.FULL_BRIGHT.pack()));

		if (kind == Kind.BLOCK) {
			values.add(SynchedEntityData.DataValue.create(DisplayAccessor.particledeco$getTranslation(), new Vector3f(-OFFSET, -OFFSET, -OFFSET)));
			values.add(SynchedEntityData.DataValue.create(BlockDisplayAccessor.particledeco$getBlockState(), state));
		} else if (kind == Kind.ITEM) {
			values.add(SynchedEntityData.DataValue.create(ItemDisplayAccessor.particledeco$getItemStack(), new ItemStack(state.getBlock().asItem())));
			values.add(SynchedEntityData.DataValue.create(ItemDisplayAccessor.particledeco$getItemDisplay(), ItemDisplayContext.NONE.getId()));
		}

		return new ClientboundSetEntityDataPacket(id, values);
	}

	public static ClientboundRemoveEntitiesPacket removePacket(int... ids) {
		return new ClientboundRemoveEntitiesPacket(ids);
	}
}
