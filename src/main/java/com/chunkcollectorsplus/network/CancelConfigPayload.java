package com.chunkcollectorsplus.network;

import com.chunkcollectorsplus.ChunkCollectorsPlusMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client -> server: the player pressed Esc and wants to leave config mode. */
public record CancelConfigPayload() implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<CancelConfigPayload> TYPE = new CustomPacketPayload.Type<>(ChunkCollectorsPlusMod.id("cancel_config"));
	public static final StreamCodec<RegistryFriendlyByteBuf, CancelConfigPayload> CODEC = StreamCodec.unit(new CancelConfigPayload());

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
