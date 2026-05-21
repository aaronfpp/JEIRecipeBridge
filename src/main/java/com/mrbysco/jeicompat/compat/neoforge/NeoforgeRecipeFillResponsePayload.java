package com.mrbysco.jeicompat.compat.neoforge;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

public record NeoforgeRecipeFillResponsePayload(
	boolean success,
	int filledSlots,
	int totalSlots,
	List<String> missingItems,
	String errorMessage
) {

	public static final StreamCodec<RegistryFriendlyByteBuf, NeoforgeRecipeFillResponsePayload> STREAM_CODEC =
		StreamCodec.ofMember(
			NeoforgeRecipeFillResponsePayload::write,
			NeoforgeRecipeFillResponsePayload::read
		);

	private static NeoforgeRecipeFillResponsePayload read(RegistryFriendlyByteBuf buf) {
		boolean success = buf.readBoolean();
		int filledSlots = buf.readVarInt();
		int totalSlots = buf.readVarInt();
		int missingCount = buf.readVarInt();
		List<String> missingItems = new ArrayList<>();
		for (int i = 0; i < missingCount; i++) {
			missingItems.add(buf.readUtf(256));
		}
		String errorMessage = buf.readBoolean() ? buf.readUtf(256) : "";
		return new NeoforgeRecipeFillResponsePayload(success, filledSlots, totalSlots, missingItems, errorMessage);
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
}
