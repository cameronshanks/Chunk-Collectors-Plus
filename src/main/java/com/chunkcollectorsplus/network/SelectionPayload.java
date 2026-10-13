package com.chunkcollectorsplus.network;

import com.chunkcollectorsplus.ChunkCollectorsPlusMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;

/** Server -> client: the collectors currently selected by the receiving player (empty = config mode ended). */
public record SelectionPayload(List<BlockPos> positions) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<SelectionPayload> TYPE = new CustomPacketPayload.Type<>(ChunkCollectorsPlusMod.id("selection"));
	public static final StreamCodec<RegistryFriendlyByteBuf, SelectionPayload> CODEC = StreamCodec.composite(
		BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list()), SelectionPayload::positions,
		SelectionPayload::new);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
