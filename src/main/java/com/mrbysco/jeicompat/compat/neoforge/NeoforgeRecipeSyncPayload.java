package com.mrbysco.jeicompat.compat.neoforge;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.RecipeType;
import org.jspecify.annotations.NonNull;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record NeoforgeRecipeSyncPayload(
		Set<RecipeType<?>> recipeTypes,
		List<RecipeHolder<?>> recipes,
		boolean pluginCapabilities) implements CustomPacketPayload {
	public static final Type<NeoforgeRecipeSyncPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("neoforge", "recipe_content"));

	public static final StreamCodec<RegistryFriendlyByteBuf, Set<RecipeType<?>>> RECIPE_TYPE_CODEC =
			ByteBufCodecs.registry(Registries.RECIPE_TYPE).apply(ByteBufCodecs.collection(HashSet::new));
	public static final StreamCodec<RegistryFriendlyByteBuf, List<RecipeHolder<?>>> RECIPES_CODEC =
			RecipeHolder.STREAM_CODEC.apply(ByteBufCodecs.list());

	public static final StreamCodec<RegistryFriendlyByteBuf, NeoforgeRecipeSyncPayload> STREAM_CODEC = StreamCodec.ofMember(
			NeoforgeRecipeSyncPayload::write,
			NeoforgeRecipeSyncPayload::read
		);

	public static NeoforgeRecipeSyncPayload create(Collection<RecipeType<?>> recipeTypes, RecipeMap recipes) {
		var recipeTypeSet = Set.copyOf(recipeTypes);
		// Fast-path for empty recipe type set (if no mod wants to sync anything)
		if (recipeTypeSet.isEmpty()) {
			return new NeoforgeRecipeSyncPayload(recipeTypeSet, List.of(), true);
		} else {
			var recipeSubset = recipes.values().stream().filter(h -> recipeTypeSet.contains(h.value().getType())).toList();
			return new NeoforgeRecipeSyncPayload(recipeTypeSet, recipeSubset, true);
		}
	}

	private static NeoforgeRecipeSyncPayload read(RegistryFriendlyByteBuf buf) {
		var recipeTypes = RECIPE_TYPE_CODEC.decode(buf);
		var recipes = RECIPES_CODEC.decode(buf);
		boolean pluginCapabilities = buf.readBoolean();
		return new NeoforgeRecipeSyncPayload(recipeTypes, recipes, pluginCapabilities);
	}

	private void write(RegistryFriendlyByteBuf buf) {
		RECIPE_TYPE_CODEC.encode(buf, this.recipeTypes);
		RECIPES_CODEC.encode(buf, this.recipes);
		buf.writeBoolean(this.pluginCapabilities);
	}

	@NonNull
	@Override
	public Type<NeoforgeRecipeSyncPayload> type() {
		return TYPE;
	}
}
