package com.chunkcollectorsplus.config;

import com.chunkcollectorsplus.ChunkCollectorsPlusMod;
import com.chunkcollectorsplus.block.ChunkCollectorBlock;
import com.chunkcollectorsplus.block.ChunkCollectorBlockEntity;
import com.chunkcollectorsplus.network.CancelConfigPayload;
import com.chunkcollectorsplus.network.SelectionPayload;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Server-side config mode.
 * <ul>
 *   <li>Shift + right click a collector: enter config mode with it selected.</li>
 *   <li>Right click collectors (shift or not): toggle them in the selection (they glow for you).</li>
 *   <li>Right click an inventory (shift or not) within range of every selected collector: link them all to it and leave config mode.</li>
 *   <li>Esc: cancel (the client sends a {@link CancelConfigPayload}).</li>
 * </ul>
 */
public final class ConfigMode {
	private static final class Selection {
		final ResourceKey<Level> dimension;
		final Set<BlockPos> collectors = new LinkedHashSet<>();

		Selection(final ResourceKey<Level> dimension) {
			this.dimension = dimension;
		}
	}

	private static final Map<UUID, Selection> SESSIONS = new HashMap<>();

	private ConfigMode() {
	}

	public static void init() {
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (hand != InteractionHand.MAIN_HAND || player.isSpectator()) {
				return InteractionResult.PASS;
			}
			if (level.isClientSide()) {
				// Keep client prediction aligned with the server so no block gets placed while configuring.
				BlockState state = level.getBlockState(hit.getBlockPos());
				boolean enters = state.getBlock() instanceof ChunkCollectorBlock && player.isShiftKeyDown();
				return ChunkCollectorsPlusMod.clientConfigActive || enters ? InteractionResult.SUCCESS : InteractionResult.PASS;
			}
			return handleUseBlock((ServerPlayer) player, level, hit.getBlockPos());
		});

		ServerPlayNetworking.registerGlobalReceiver(CancelConfigPayload.TYPE, (payload, context) -> {
			if (SESSIONS.containsKey(context.player().getUUID())) {
				cancel(context.player(), "message.chunkcollectorsplus.config_cancelled");
			}
		});

		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> SESSIONS.remove(handler.getPlayer().getUUID()));
	}

	private static InteractionResult handleUseBlock(final ServerPlayer player, final Level level, final BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		boolean isCollector = state.getBlock() instanceof ChunkCollectorBlock;
		Selection selection = SESSIONS.get(player.getUUID());

		if (selection == null) {
			if (isCollector && player.isShiftKeyDown()) {
				selection = new Selection(level.dimension());
				selection.collectors.add(pos.immutable());
				SESSIONS.put(player.getUUID(), selection);
				sync(player, selection);
				player.sendOverlayMessage(Component.translatable("message.chunkcollectorsplus.config_entered"));
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		}

		if (level.dimension() != selection.dimension) {
			cancel(player, "message.chunkcollectorsplus.config_wrong_dimension");
			return InteractionResult.SUCCESS;
		}

		if (isCollector) {
			BlockPos key = pos.immutable();
			if (!selection.collectors.remove(key)) {
				selection.collectors.add(key);
			}
			sync(player, selection);
			player.sendOverlayMessage(Component.translatable("message.chunkcollectorsplus.selected", selection.collectors.size()));
			return InteractionResult.SUCCESS;
		}

		Container target = HopperBlockEntity.getContainerAt(level, pos);
		if (target == null) {
			player.sendOverlayMessage(Component.translatable("message.chunkcollectorsplus.not_inventory"));
			return InteractionResult.SUCCESS;
		}
		if (selection.collectors.isEmpty()) {
			cancel(player, "message.chunkcollectorsplus.config_cancelled");
			return InteractionResult.SUCCESS;
		}

		int tooFar = 0;
		double limitSq = ChunkCollectorsPlusMod.MAX_OUTPUT_RANGE * ChunkCollectorsPlusMod.MAX_OUTPUT_RANGE;
		for (BlockPos collector : selection.collectors) {
			if (collector.distSqr(pos) > limitSq) {
				tooFar++;
			}
		}
		if (tooFar > 0) {
			player.sendOverlayMessage(Component.translatable("message.chunkcollectorsplus.too_far", tooFar, (int) ChunkCollectorsPlusMod.MAX_OUTPUT_RANGE));
			return InteractionResult.SUCCESS;
		}

		int linked = 0;
		for (BlockPos collector : new ArrayList<>(selection.collectors)) {
			BlockEntity be = level.getBlockEntity(collector);
			if (be instanceof ChunkCollectorBlockEntity cc) {
				cc.setOutput(pos.immutable());
				linked++;
			}
		}
		SESSIONS.remove(player.getUUID());
		ServerPlayNetworking.send(player, new SelectionPayload(java.util.List.of()));
		player.sendOverlayMessage(Component.translatable("message.chunkcollectorsplus.linked", linked));
		return InteractionResult.SUCCESS;
	}

	private static void cancel(final ServerPlayer player, final String messageKey) {
		SESSIONS.remove(player.getUUID());
		ServerPlayNetworking.send(player, new SelectionPayload(java.util.List.of()));
		player.sendOverlayMessage(Component.translatable(messageKey));
	}

	private static void sync(final ServerPlayer player, final Selection selection) {
		ServerPlayNetworking.send(player, new SelectionPayload(new ArrayList<>(selection.collectors)));
	}
}
