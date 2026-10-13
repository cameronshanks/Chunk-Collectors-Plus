package com.chunkcollectorsplus.menu;

import com.chunkcollectorsplus.ChunkCollectorsPlusMod;
import com.chunkcollectorsplus.block.ChunkCollectorBlockEntity;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * One menu class, three pages (each with its own MenuType so the client opens the right screen):
 * HOME (one row: chest = storage, hopper = whitelist), STORAGE (27 take-only slots), FILTER (9 ghost slots).
 */
public class ChunkCollectorMenu extends AbstractContainerMenu {
	public enum Page {
		HOME(1), STORAGE(3), FILTER(1);

		public final int rows;

		Page(final int rows) {
			this.rows = rows;
		}
	}

	public static final int HOME_STORAGE_SLOT = 3;
	public static final int HOME_FILTER_SLOT = 5;
	public static final int BUTTON_BACK = 0;

	private final Page page;
	private final @Nullable ChunkCollectorBlockEntity owner;
	private final int containerSlots;

	/** {@code owner} is null on the client; contents arrive through normal slot syncing. */
	public ChunkCollectorMenu(final Page page, final int containerId, final Inventory inventory, final @Nullable ChunkCollectorBlockEntity owner) {
		super(ChunkCollectorsPlusMod.menuType(page), containerId);
		this.page = page;
		this.owner = owner;

		Container container;
		switch (page) {
			case HOME -> {
				SimpleContainer home = new SimpleContainer(9);
				home.setItem(HOME_STORAGE_SLOT, icon(Items.CHEST, "gui.chunkcollectorsplus.open_storage"));
				home.setItem(HOME_FILTER_SLOT, icon(Items.HOPPER, "gui.chunkcollectorsplus.open_whitelist"));
				container = home;
				this.containerSlots = 9;
				for (int i = 0; i < 9; i++) {
					this.addSlot(new LockedSlot(container, i, 8 + i * 18, 18));
				}
			}
			case STORAGE -> {
				container = owner != null ? owner : new SimpleContainer(ChunkCollectorBlockEntity.STORAGE_SIZE);
				this.containerSlots = ChunkCollectorBlockEntity.STORAGE_SIZE;
				for (int row = 0; row < 3; row++) {
					for (int col = 0; col < 9; col++) {
						this.addSlot(new Slot(container, col + row * 9, 8 + col * 18, 18 + row * 18) {
							@Override
							public boolean mayPlace(final ItemStack stack) {
								return false;
							}
						});
					}
				}
			}
			default -> {
				container = owner != null ? owner.getFilter() : new SimpleContainer(ChunkCollectorBlockEntity.FILTER_SIZE);
				this.containerSlots = ChunkCollectorBlockEntity.FILTER_SIZE;
				for (int i = 0; i < 9; i++) {
					this.addSlot(new LockedSlot(container, i, 8 + i * 18, 18));
				}
			}
		}
		this.addStandardInventorySlots(inventory, 8, 31 + page.rows * 18);
	}

	private static ItemStack icon(final net.minecraft.world.item.Item item, final String key) {
		ItemStack stack = new ItemStack(item);
		stack.set(DataComponents.CUSTOM_NAME, Component.translatable(key).withStyle(style -> style.withItalic(false)));
		return stack;
	}

	public Page getPage() {
		return this.page;
	}

	/** Slot that rejects normal insertion and pickup; whitelist and home behaviour is handled in {@link #clicked}. */
	public static class LockedSlot extends Slot {
		public LockedSlot(final Container container, final int slot, final int x, final int y) {
			super(container, slot, x, y);
		}

		@Override
		public boolean mayPlace(final ItemStack stack) {
			return false;
		}

		@Override
		public boolean mayPickup(final Player player) {
			return false;
		}
	}

	@Override
	public void clicked(final int slotIndex, final int buttonNum, final ContainerInput containerInput, final Player player) {
		if (slotIndex >= 0 && slotIndex < this.containerSlots) {
			if (this.page == Page.HOME) {
				if (this.owner != null && player instanceof ServerPlayer serverPlayer) {
					if (slotIndex == HOME_STORAGE_SLOT) {
						this.owner.openPage(serverPlayer, Page.STORAGE);
					} else if (slotIndex == HOME_FILTER_SLOT) {
						this.owner.openPage(serverPlayer, Page.FILTER);
					}
				}
				return;
			}
			if (this.page == Page.FILTER) {
				// Clicking with an item ghosts it (not consumed); clicking with an empty hand clears the slot.
				Slot slot = this.slots.get(slotIndex);
				ItemStack carried = this.getCarried();
				if (carried.isEmpty()) {
					slot.set(ItemStack.EMPTY);
				} else if (!this.filterContains(carried)) {
					slot.set(carried.copyWithCount(1));
				}
				return;
			}
		}
		super.clicked(slotIndex, buttonNum, containerInput, player);
	}

	private boolean filterContains(final ItemStack stack) {
		for (int i = 0; i < this.containerSlots; i++) {
			if (ItemStack.isSameItem(this.slots.get(i).getItem(), stack)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean clickMenuButton(final Player player, final int buttonId) {
		if (buttonId == BUTTON_BACK && this.page != Page.HOME && this.owner != null && player instanceof ServerPlayer serverPlayer) {
			this.owner.openPage(serverPlayer, Page.HOME);
			return true;
		}
		return false;
	}

	@Override
	public boolean canDragTo(final Slot slot) {
		return !(slot instanceof LockedSlot) && super.canDragTo(slot);
	}

	@Override
	public boolean canTakeItemForPickAll(final ItemStack carried, final Slot target) {
		return !(target instanceof LockedSlot) && super.canTakeItemForPickAll(carried, target);
	}

	/** Shift-click on a storage slot moves the stack to the player inventory. Nothing else shift-clicks. */
	@Override
	public ItemStack quickMoveStack(final Player player, final int slotIndex) {
		if (this.page != Page.STORAGE || slotIndex < 0 || slotIndex >= this.containerSlots) {
			return ItemStack.EMPTY;
		}
		Slot slot = this.slots.get(slotIndex);
		if (!slot.hasItem()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getItem();
		ItemStack original = stack.copy();
		if (!this.moveItemStackTo(stack, this.containerSlots, this.slots.size(), true)) {
			return ItemStack.EMPTY;
		}
		if (stack.isEmpty()) {
			slot.setByPlayer(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}
		if (stack.getCount() == original.getCount()) {
			return ItemStack.EMPTY;
		}
		slot.onTake(player, stack);
		return original;
	}

	@Override
	public boolean stillValid(final Player player) {
		return this.owner == null || this.owner.stillValid(player);
	}
}
