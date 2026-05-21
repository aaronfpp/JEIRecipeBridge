package com.mrbysco.jeicompat.compat.fabric;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.crafting.Recipe;
import org.jspecify.annotations.NonNull;

public record FabricRecipeFillRequestPayload(
	ResourceKey<Recipe<?>> recipeId,
	Identifier variantId,
	int quantity
) implements CustomPacketPayload {

	public static final StreamCodec<RegistryFriendlyByteBuf, FabricRecipeFillRequestPayload> CODEC =
		StreamCodec.ofMember(
			FabricRecipeFillRequestPayload::write,
			FabricRecipeFillRequestPayload::read
		);

	public static final Type<FabricRecipeFillRequestPayload> TYPE = new Type<>(
		Identifier.fromNamespaceAndPath("fabric", "recipe_fill_request")
	);

	private static FabricRecipeFillRequestPayload read(RegistryFriendlyByteBuf buf) {
		ResourceKey<Recipe<?>> recipeId = buf.readResourceKey(Registries.RECIPE);
		Identifier variantId = buf.readIdentifier();
		int quantity = buf.readVarInt();
		return new FabricRecipeFillRequestPayload(recipeId, variantId, quantity);
	}

	private void write(RegistryFriendlyByteBuf buf) {
		buf.writeResourceKey(this.recipeId);
		buf.writeIdentifier(this.variantId);
		buf.writeVarInt(this.quantity);
	}

	@NonNull
	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

