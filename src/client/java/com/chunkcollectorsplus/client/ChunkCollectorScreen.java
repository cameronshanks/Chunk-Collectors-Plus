package com.chunkcollectorsplus.client;

import com.chunkcollectorsplus.menu.ChunkCollectorMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

public class ChunkCollectorScreen extends AbstractContainerScreen<ChunkCollectorMenu> {
	private static final Identifier BACKGROUND = Identifier.withDefaultNamespace("textures/gui/container/generic_54.png");
	private static final int GHOST_TINT = 0x6055AAFF;
	private final int rows;

	public ChunkCollectorScreen(final ChunkCollectorMenu menu, final Inventory inventory, final Component title) {
		super(menu, inventory, title, 176, 114 + menu.getPage().rows * 18);
		this.rows = menu.getPage().rows;
		this.inventoryLabelY = this.imageHeight - 94;
	}

	@Override
	protected void init() {
		super.init();
		if (this.menu.getPage() != ChunkCollectorMenu.Page.HOME) {
			this.addRenderableWidget(Button.builder(Component.translatable("gui.chunkcollectorsplus.back"),
					button -> this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, ChunkCollectorMenu.BUTTON_BACK))
				.bounds(this.leftPos + this.imageWidth - 46, this.topPos + 3, 40, 12)
				.build());
		}
	}

	@Override
	public void extractBackground(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a) {
		super.extractBackground(graphics, mouseX, mouseY, a);
		int xo = (this.width - this.imageWidth) / 2;
		int yo = (this.height - this.imageHeight) / 2;
		graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, xo, yo, 0.0F, 0.0F, this.imageWidth, this.rows * 18 + 17, 256, 256);
		graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, xo, yo + this.rows * 18 + 17, 0.0F, 126.0F, this.imageWidth, 96, 256, 256);
		if (this.menu.getPage() == ChunkCollectorMenu.Page.FILTER) {
			// Tint the ghost slots so the whitelist reads differently from storage.
			for (int i = 0; i < 9; i++) {
				graphics.fill(xo + 8 + i * 18, yo + 18, xo + 24 + i * 18, yo + 34, GHOST_TINT);
			}
		}
	}
}
