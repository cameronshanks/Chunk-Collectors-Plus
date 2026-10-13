package com.chunkcollectorsplus.block;

import com.chunkcollectorsplus.ChunkCollectorsPlusMod;
import com.chunkcollectorsplus.menu.ChunkCollectorMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ChunkCollectorBlockEntity extends BaseContainerBlockEntity {
	public static final int STORAGE_SIZE = 27;
	public static final int FILTER_SIZE = 9;
	private static final int COLLECT_INTERVAL = 10;
	private static final int OUTPUT_INTERVAL = 8;
	/** Ticks to wait before scanning again after a scan found matching items but could store none of them. */
	private static final int FULL_BACKOFF = 20;

	private NonNullList<ItemStack> items = NonNullList.withSize(STORAGE_SIZE, ItemStack.EMPTY);
	private final SimpleContainer filter = new SimpleContainer(FILTER_SIZE) {
		@Override
		public void setChanged() {
			super.setChanged();
			ChunkCollectorBlockEntity.this.filterDirty = true;
			ChunkCollectorBlockEntity.this.setChanged();
		}
	};
	private @Nullable BlockPos output;

	/** Cached set of whitelisted items; rebuilt lazily after the filter changes. */
	private final Set<Item> filterItems = new HashSet<>();
	private boolean filterDirty = true;
	/** Game time before which collection is skipped because storage was full on the last scan. */
	private long collectResumeTime;
	private final int collectPhase;
	private final int outputPhase;

	public ChunkCollectorBlockEntity(final BlockPos pos, final BlockState state) {
		super(ChunkCollectorsPlusMod.CHUNK_COLLECTOR_BE, pos, state);
		// Spread collectors across every slot of each interval so their work doesn't bunch into the same ticks.
		int hash = pos.hashCode() * 0x9E3779B1;
		this.collectPhase = Math.floorMod(hash >>> 8, COLLECT_INTERVAL);
		this.outputPhase = Math.floorMod(hash >>> 8, OUTPUT_INTERVAL);
	}

	public Container getFilter() {
		return this.filter;
	}

	public @Nullable BlockPos getOutput() {
		return this.output;
	}

	public void setOutput(final @Nullable BlockPos output) {
		this.output = output;
		this.setChanged();
		this.syncConnectedState();
	}

	// ---- storage ----

	@Override
	protected NonNullList<ItemStack> getItems() {
		return this.items;
	}

	@Override
	protected void setItems(final NonNullList<ItemStack> items) {
		this.items = items;
	}

	@Override
	public int getContainerSize() {
		return STORAGE_SIZE;
	}

	@Override
	protected Component getDefaultName() {
		return Component.translatable("container.chunkcollectorsplus.chunk_collector");
	}

	@Override
	protected AbstractContainerMenu createMenu(final int containerId, final Inventory inventory) {
		return new ChunkCollectorMenu(ChunkCollectorMenu.Page.HOME, containerId, inventory, this);
	}

	public void openPage(final ServerPlayer player, final ChunkCollectorMenu.Page page) {
		String titleKey = switch (page) {
			case HOME -> "container.chunkcollectorsplus.chunk_collector";
			case STORAGE -> "container.chunkcollectorsplus.storage";
			case FILTER -> "container.chunkcollectorsplus.whitelist";
		};
		player.openMenu(new SimpleMenuProvider((id, inv, p) -> new ChunkCollectorMenu(page, id, inv, this), Component.translatable(titleKey)));
	}

	@Override
	protected void loadAdditional(final ValueInput input) {
		super.loadAdditional(input);
		this.items = NonNullList.withSize(STORAGE_SIZE, ItemStack.EMPTY);
		ContainerHelper.loadAllItems(input, this.items);

		NonNullList<ItemStack> filterItems = NonNullList.withSize(FILTER_SIZE, ItemStack.EMPTY);
		ContainerHelper.loadAllItems(input.childOrEmpty("Filter"), filterItems);
		for (int i = 0; i < FILTER_SIZE; i++) {
			this.filter.setItem(i, filterItems.get(i));
		}

		this.filterDirty = true;
		this.output = input.read("Output", BlockPos.CODEC).orElse(null);
	}

	@Override
	protected void saveAdditional(final ValueOutput out) {
		super.saveAdditional(out);
		ContainerHelper.saveAllItems(out, this.items);

		NonNullList<ItemStack> filterItems = NonNullList.withSize(FILTER_SIZE, ItemStack.EMPTY);
		for (int i = 0; i < FILTER_SIZE; i++) {
			filterItems.set(i, this.filter.getItem(i));
		}
		ContainerHelper.saveAllItems(out.child("Filter"), filterItems);

		out.storeNullable("Output", BlockPos.CODEC, this.output);
	}

	// ---- logic ----

	private Set<Item> filterSet() {
		if (this.filterDirty) {
			this.filterItems.clear();
			for (int i = 0; i < FILTER_SIZE; i++) {
				ItemStack ghost = this.filter.getItem(i);
				if (!ghost.isEmpty()) {
					this.filterItems.add(ghost.getItem());
				}
			}
			this.filterDirty = false;
		}
		return this.filterItems;
	}

	public boolean matchesFilter(final ItemStack stack) {
		return this.filterSet().contains(stack.getItem());
	}

	private void syncConnectedState() {
		if (this.level == null || this.level.isClientSide()) {
			return;
		}
		BlockState state = this.getBlockState();
		boolean connected = this.output != null;
		if (state.getBlock() instanceof ChunkCollectorBlock && state.getValue(ChunkCollectorBlock.CONNECTED) != connected) {
			this.level.setBlock(this.worldPosition, state.setValue(ChunkCollectorBlock.CONNECTED, connected), 3);
		}
	}

	public static void serverTick(final Level level, final BlockPos pos, final BlockState state, final ChunkCollectorBlockEntity be) {
		long gameTime = level.getGameTime();
		if ((gameTime + be.collectPhase) % COLLECT_INTERVAL == 0) {
			be.collect(level, gameTime);
		}
		if ((gameTime + be.outputPhase) % OUTPUT_INTERVAL == 0) {
			be.pushOutput(level);
		}
		if (state.getValue(ChunkCollectorBlock.CONNECTED) != (be.output != null)) {
			be.syncConnectedState();
		}
	}

	/** Pulls every matching item entity in this block's chunk into internal storage. An empty whitelist collects nothing. */
	private void collect(final Level level, final long gameTime) {
		Set<Item> wanted = this.filterSet();
		if (wanted.isEmpty() || gameTime < this.collectResumeTime) {
			return;
		}
		ChunkPos chunk = ChunkPos.containing(this.worldPosition);
		AABB box = new AABB(chunk.getMinBlockX(), level.getMinY(), chunk.getMinBlockZ(),
			chunk.getMaxBlockX() + 1, level.getMaxY() + 1, chunk.getMaxBlockZ() + 1);
		// The filter runs inside the query, so non-matching items never make it into the list.
		List<ItemEntity> entities = level.getEntitiesOfClass(ItemEntity.class, box,
			e -> e.isAlive() && !e.getItem().isEmpty() && wanted.contains(e.getItem().getItem()));
		boolean storedAny = false;
		for (ItemEntity entity : entities) {
			ItemStack stack = entity.getItem();
			int before = stack.getCount();
			ItemStack remainder = HopperBlockEntity.addItem(null, this, stack.copy(), null);
			if (remainder.isEmpty()) {
				entity.discard();
				storedAny = true;
			} else if (remainder.getCount() != before) {
				entity.setItem(remainder);
				storedAny = true;
			}
		}
		if (!entities.isEmpty() && !storedAny) {
			this.collectResumeTime = gameTime + FULL_BACKOFF;
		}
	}

	/** Moves one stack out to the linked inventory. Output is paused (not cleared) while the target chunk is unloaded or the target is full. */
	private void pushOutput(final Level level) {
		if (this.output == null || this.isEmpty() || !level.hasChunkAt(this.output)) {
			return;
		}
		Container target = HopperBlockEntity.getContainerAt(level, this.output);
		if (target == null) {
			this.setOutput(null);
			return;
		}
		for (int i = 0; i < STORAGE_SIZE; i++) {
			ItemStack stack = this.items.get(i);
			if (stack.isEmpty()) {
				continue;
			}
			int before = stack.getCount();
			ItemStack remainder = HopperBlockEntity.addItem(null, target, stack.copy(), null);
			if (remainder.getCount() != before) {
				this.items.set(i, remainder.isEmpty() ? ItemStack.EMPTY : remainder);
				this.collectResumeTime = 0L;
				this.setChanged();
				return;
			}
		}
	}
}
