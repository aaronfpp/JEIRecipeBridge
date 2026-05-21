package com.mrbysco.jeicompat.compat.neoforge;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.crafting.Recipe;

public record NeoforgeRecipeFillRequestPayload(
	ResourceKey<Recipe<?>> recipeId,
	Identifier variantId,
	int quantity
) {

	public static final StreamCodec<RegistryFriendlyByteBuf, NeoforgeRecipeFillRequestPayload> STREAM_CODEC =
		StreamCodec.ofMember(
			NeoforgeRecipeFillRequestPayload::write,
			NeoforgeRecipeFillRequestPayload::read
		);

	private static NeoforgeRecipeFillRequestPayload read(RegistryFriendlyByteBuf buf) {
		ResourceKey<Recipe<?>> recipeId = buf.readResourceKey(Registries.RECIPE);
		Identifier variantId = buf.readIdentifier();
		int quantity = buf.readVarInt();
		return new NeoforgeRecipeFillRequestPayload(recipeId, variantId, quantity);
	}

	private void write(RegistryFriendlyByteBuf buf) {
		buf.writeResourceKey(this.recipeId);
		buf.writeIdentifier(this.variantId);
		buf.writeVarInt(this.quantity);
	}
}

