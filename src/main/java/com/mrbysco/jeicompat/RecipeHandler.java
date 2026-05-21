package com.mrbysco.jeicompat;

import com.mrbysco.jeicompat.compat.fabric.FabricRecipeSyncPayload;
import com.mrbysco.jeicompat.compat.neoforge.NeoforgeRecipeSyncPayload;
import com.mrbysco.jeicompat.handler.RecipeFillHandler;
import io.netty.buffer.Unpooled;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ClientboundUpdateTagsPacket;
import net.minecraft.network.protocol.common.custom.DiscardedPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagNetworkSerialization;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class RecipeHandler implements Listener, PluginMessageListener {

	@EventHandler
	public void onJoin(PlayerJoinEvent event) {
		final Player originalPlayer = event.getPlayer();
		final ServerPlayer player = ((CraftPlayer) originalPlayer).getHandle();
		final MinecraftServer server = player.level().getServer();
		final RecipeManager recipeManager = server.getRecipeManager();

		originalPlayer.sendMessage("§6PaperMC JEI Compat: Syncing Recipes...§r");

		RecipeMap recipeMap = recipeManager.recipes;

		RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess());
		String brand = originalPlayer.getClientBrandName();
		if (brand == null) {
			return; // Unknown brand, do not send any custom payload
		}
		if (brand.equalsIgnoreCase("fabric")) {
			sendFabricPayload(player, recipeMap, buffer);
		} else if (brand.equalsIgnoreCase("neoforge")) {
			sendNeoForgePayload(player, server, recipeMap, buffer);
		}
	}

	private static void sendNeoForgePayload(ServerPlayer player, MinecraftServer server, RecipeMap recipeMap, RegistryFriendlyByteBuf buffer) {
		List<RecipeType<?>> allRecipeTypes = BuiltInRegistries.RECIPE_TYPE.stream().toList();
		var payload = NeoforgeRecipeSyncPayload.create(allRecipeTypes, recipeMap);
		NeoforgeRecipeSyncPayload.STREAM_CODEC.encode(buffer, payload);

		byte[] bytes = new byte[buffer.writerIndex()];
		buffer.getBytes(0, bytes);

		sendPayload(player, Identifier.fromNamespaceAndPath("neoforge", "recipe_content"), bytes);

		player.connection.send(new ClientboundUpdateTagsPacket(TagNetworkSerialization.serializeTagsToNetwork(server.registries())));
	}

	private static void sendFabricPayload(ServerPlayer player, RecipeMap recipeMap, RegistryFriendlyByteBuf buffer) {
		var list = new ArrayList<FabricRecipeSyncPayload.Entry>();
		var seen = new HashSet<RecipeSerializer<?>>();

		for (RecipeSerializer<?> serializer : BuiltInRegistries.RECIPE_SERIALIZER) {
			if (!seen.add(serializer)) continue; // skip duplicates

			List<RecipeHolder<?>> recipes = new ArrayList<>();
			for (RecipeHolder<?> holder : recipeMap.values()) {
				if (holder.value().getSerializer() == serializer) {
					recipes.add(holder);
				}
			}

			if (!recipes.isEmpty()) {
				RecipeSerializer<?> entrySerializer = recipes.get(0).value().getSerializer();
				list.add(new FabricRecipeSyncPayload.Entry(entrySerializer, recipes));
			}
		}

		var payload = new FabricRecipeSyncPayload(list);
		FabricRecipeSyncPayload.CODEC.encode(buffer, payload);

		byte[] bytes = new byte[buffer.writerIndex()];
		buffer.getBytes(0, bytes);

		sendPayload(player, Identifier.fromNamespaceAndPath("fabric", "recipe_sync"), bytes);
	}

	private static void sendPayload(ServerPlayer player, Identifier id, byte[] bytes) {
		player.connection.send(new ClientboundCustomPayloadPacket(new DiscardedPayload(id, bytes)));
	}

	@Override
	public void onPluginMessageReceived(String channel, Player player, byte[] message) {
		if (channel.equals("fabric:recipe_fill_request")) {
			handleFabricRecipeFillRequest(player, message);
		} else if (channel.equals("neoforge:recipe_fill_request")) {
			handleNeoforgeRecipeFillRequest(player, message);
		}
	}

	private void handleFabricRecipeFillRequest(Player player, byte[] message) {
		try {
			RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
				io.netty.buffer.Unpooled.wrappedBuffer(message),
				((CraftPlayer) player).getHandle().level().getServer().registryAccess()
			);

			com.mrbysco.jeicompat.compat.fabric.FabricRecipeFillRequestPayload payload =
				com.mrbysco.jeicompat.compat.fabric.FabricRecipeFillRequestPayload.CODEC.decode(buffer);

			RecipeFillHandler.handleRecipeFillRequest(player, payload.recipeId(), payload.variantId(), "fabric");
		} catch (Exception e) {
			JEIRecipeBridgePlugin.LOGGER.error("Error handling Fabric recipe fill request", e);
		}
	}

	private void handleNeoforgeRecipeFillRequest(Player player, byte[] message) {
		try {
			RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
				io.netty.buffer.Unpooled.wrappedBuffer(message),
				((CraftPlayer) player).getHandle().level().getServer().registryAccess()
			);

			com.mrbysco.jeicompat.compat.neoforge.NeoforgeRecipeFillRequestPayload payload =
				com.mrbysco.jeicompat.compat.neoforge.NeoforgeRecipeFillRequestPayload.STREAM_CODEC.decode(buffer);

			RecipeFillHandler.handleRecipeFillRequest(player, payload.recipeId(), payload.variantId(), "neoforge");
		} catch (Exception e) {
			JEIRecipeBridgePlugin.LOGGER.error("Error handling NeoForge recipe fill request", e);
		}
	}
}
