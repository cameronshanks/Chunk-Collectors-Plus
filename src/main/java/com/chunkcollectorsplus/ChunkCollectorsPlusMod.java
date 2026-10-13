package com.chunkcollectorsplus;

import com.chunkcollectorsplus.block.ChunkCollectorBlock;
import com.chunkcollectorsplus.block.ChunkCollectorBlockEntity;
import com.chunkcollectorsplus.config.ConfigMode;
import com.chunkcollectorsplus.menu.ChunkCollectorMenu;
import com.chunkcollectorsplus.network.CancelConfigPayload;
import com.chunkcollectorsplus.network.SelectionPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

public class ChunkCollectorsPlusMod implements ModInitializer {
	public static final String MOD_ID = "chunkcollectorsplus";
	public static final double MAX_OUTPUT_RANGE = 50.0;

	/** Mirrors whether the local player is in config mode; set by the client network handler, used to keep client prediction in step with the server. */
	public static volatile boolean clientConfigActive = false;

	public static Block CHUNK_COLLECTOR;
	public static Item CHUNK_COLLECTOR_ITEM;
	public static BlockEntityType<ChunkCollectorBlockEntity> CHUNK_COLLECTOR_BE;
	public static MenuType<ChunkCollectorMenu> HOME_MENU;
	public static MenuType<ChunkCollectorMenu> STORAGE_MENU;
	public static MenuType<ChunkCollectorMenu> FILTER_MENU;

	public static MenuType<ChunkCollectorMenu> menuType(ChunkCollectorMenu.Page page) {
		return switch (page) {
			case HOME -> HOME_MENU;
			case STORAGE -> STORAGE_MENU;
			case FILTER -> FILTER_MENU;
		};
	}

	private static MenuType<ChunkCollectorMenu> registerMenu(String name, ChunkCollectorMenu.Page page) {
		return Registry.register(BuiltInRegistries.MENU, id(name),
			new MenuType<>((containerId, inventory) -> new ChunkCollectorMenu(page, containerId, inventory, null), FeatureFlags.VANILLA_SET));
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, id("chunk_collector"));
		CHUNK_COLLECTOR = Registry.register(BuiltInRegistries.BLOCK, blockKey, new ChunkCollectorBlock(
			BlockBehaviour.Properties.of()
				.setId(blockKey)
				.mapColor(MapColor.WOOD)
				.strength(0.2F)
				.sound(SoundType.WOOD)
				.ignitedByLava()));

		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, id("chunk_collector"));
		CHUNK_COLLECTOR_ITEM = Registry.register(BuiltInRegistries.ITEM, itemKey,
			new BlockItem(CHUNK_COLLECTOR, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));

		CHUNK_COLLECTOR_BE = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, id("chunk_collector"),
			FabricBlockEntityTypeBuilder.create(ChunkCollectorBlockEntity::new, CHUNK_COLLECTOR).build());

		HOME_MENU = registerMenu("chunk_collector_home", ChunkCollectorMenu.Page.HOME);
		STORAGE_MENU = registerMenu("chunk_collector_storage", ChunkCollectorMenu.Page.STORAGE);
		FILTER_MENU = registerMenu("chunk_collector_filter", ChunkCollectorMenu.Page.FILTER);

		PayloadTypeRegistry.clientboundPlay().register(SelectionPayload.TYPE, SelectionPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(CancelConfigPayload.TYPE, CancelConfigPayload.CODEC);

		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.REDSTONE_BLOCKS)
			.register(output -> output.accept(CHUNK_COLLECTOR_ITEM));

		ConfigMode.init();
	}
}
