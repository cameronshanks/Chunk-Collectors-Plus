package com.chunkcollectorsplus.client;

import com.chunkcollectorsplus.ChunkCollectorsPlusMod;
import com.chunkcollectorsplus.network.CancelConfigPayload;
import com.chunkcollectorsplus.network.SelectionPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.phys.AABB;

import java.util.List;

public class ChunkCollectorsPlusClient implements ClientModInitializer {
	private static final int STROKE = 0xFF55FFFF;
	private static final int FILL = 0x3055FFFF;
	private static volatile List<BlockPos> selected = List.of();

	@Override
	public void onInitializeClient() {
		MenuScreens.register(ChunkCollectorsPlusMod.HOME_MENU, ChunkCollectorScreen::new);
		MenuScreens.register(ChunkCollectorsPlusMod.STORAGE_MENU, ChunkCollectorScreen::new);
		MenuScreens.register(ChunkCollectorsPlusMod.FILTER_MENU, ChunkCollectorScreen::new);

		// Esc leaves config mode: swallow the pause menu it would open and tell the server.
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (ChunkCollectorsPlusMod.clientConfigActive && client.screen instanceof PauseScreen) {
				client.setScreen(null);
				ClientPlayNetworking.send(new CancelConfigPayload());
				selected = List.of();
				ChunkCollectorsPlusMod.clientConfigActive = false;
			}
		});

		ClientPlayNetworking.registerGlobalReceiver(SelectionPayload.TYPE, (payload, context) -> context.client().execute(() -> {
			selected = List.copyOf(payload.positions());
			ChunkCollectorsPlusMod.clientConfigActive = !selected.isEmpty();
		}));

		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			selected = List.of();
			ChunkCollectorsPlusMod.clientConfigActive = false;
		});

		// Runs inside Minecraft's per-frame gizmo scope; "always on top" makes the outline glow through walls.
		LevelRenderEvents.END_EXTRACTION.register(context -> {
			for (BlockPos pos : selected) {
				Gizmos.cuboid(new AABB(pos).inflate(0.01), GizmoStyle.strokeAndFill(STROKE, 4.0F, FILL)).setAlwaysOnTop();
			}
		});
	}
}
