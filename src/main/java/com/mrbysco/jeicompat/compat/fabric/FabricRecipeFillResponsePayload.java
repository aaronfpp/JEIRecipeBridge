package com.mrbysco.jeicompat.compat.fabric;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

public record FabricRecipeFillResponsePayload(
	boolean success,
	int filledSlots,
	int totalSlots,
	List<String> missingItems,
	String errorMessage
) implements CustomPacketPayload {

	public static final StreamCodec<RegistryFriendlyByteBuf, FabricRecipeFillResponsePayload> CODEC =
		StreamCodec.ofMember(
			FabricRecipeFillResponsePayload::write,
			FabricRecipeFillResponsePayload::read
		);

	public static final Type<FabricRecipeFillResponsePayload> TYPE = new Type<>(
		Identifier.fromNamespaceAndPath("fabric", "recipe_fill_response")
	);

	private static FabricRecipeFillResponsePayload read(RegistryFriendlyByteBuf buf) {
		boolean success = buf.readBoolean();
		int filledSlots = buf.readVarInt();
		int totalSlots = buf.readVarInt();
		int missingCount = buf.readVarInt();
		List<String> missingItems = new ArrayList<>();
		for (int i = 0; i < missingCount; i++) {
			missingItems.add(buf.readUtf(256));
		}
		String errorMessage = buf.readBoolean() ? buf.readUtf(256) : "";
		return new FabricRecipeFillResponsePayload(success, filledSlots, totalSlots, missingItems, errorMessage);
	}

	private void write(RegistryFriendlyByteBuf buf) {
		buf.writeBoolean(this.success);
		buf.writeVarInt(this.filledSlots);
		buf.writeVarInt(this.totalSlots);
		buf.writeVarInt(this.missingItems.size());
		for (String item : this.missingItems) {
			buf.writeUtf(item);
		}
		buf.writeBoolean(!this.errorMessage.isEmpty());
		if (!this.errorMessage.isEmpty()) {
			buf.writeUtf(this.errorMessage);
		}
	}

	@NonNull
	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
