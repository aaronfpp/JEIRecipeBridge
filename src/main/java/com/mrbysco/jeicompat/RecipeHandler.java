package com.mrbysco.jeicompat;

import com.mrbysco.jeicompat.compat.fabric.FabricRecipeSyncPayload;
import com.mrbysco.jeicompat.compat.neoforge.NeoforgeRecipeSyncPayload;
import com.mrbysco.jeicompat.handler.JeiRecipeTransferHandler;
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
import org.bukkit.event.player.PlayerRegisterChannelEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;

import net.minecraft.network.codec.ByteBufCodecs;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RecipeHandler implements Listener, PluginMessageListener {

	@EventHandler
	public void onChannelRegister(PlayerRegisterChannelEvent event) {
		// Only log JEI-relevant channel registrations to keep the console clean
		String channel = event.getChannel();
		if (channel.startsWith("jei:") || channel.startsWith("fabric:recipe") || channel.startsWith("neoforge:recipe")) {
			JEIRecipeBridgePlugin.LOGGER.info("[JEIRecipeBridge] {} registered channel: {}", event.getPlayer().getName(), channel);
		}
	}

	@EventHandler
	public void onJoin(PlayerJoinEvent event) {
		final Player originalPlayer = event.getPlayer();
		final ServerPlayer player = ((CraftPlayer) originalPlayer).getHandle();
		final MinecraftServer server = player.level().getServer();
		final RecipeManager recipeManager = server.getRecipeManager();
		final RecipeMap recipeMap = recipeManager.recipes;

		String brand = originalPlayer.getClientBrandName();
		JEIRecipeBridgePlugin.LOGGER.info("[JEIRecipeBridge] {} connected (client: {})",
			originalPlayer.getName(), brand != null ? brand : "unknown");

		// Delay sending recipe payloads to allow standard/custom client handshake channel registrations to complete
		org.bukkit.Bukkit.getScheduler().runTaskLater(JEIRecipeBridgePlugin.Plugin, () -> {
			if (!originalPlayer.isOnline()) {
				return;
			}
			RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess());
			String delayedBrand = originalPlayer.getClientBrandName();
			if (delayedBrand == null) {
				JEIRecipeBridgePlugin.LOGGER.warn("[JEIRecipeBridge] Recipe sync aborted for {} — unknown client brand", originalPlayer.getName());
				return;
			}

			Set<String> listeningChannels = originalPlayer.getListeningPluginChannels();
			boolean supportsFill = listeningChannels.contains("fabric:recipe_fill_response") ||
					listeningChannels.contains("fabric:recipe_fill_request") ||
					listeningChannels.contains("neoforge:recipe_fill_response") ||
					listeningChannels.contains("neoforge:recipe_fill_request");

			JEIRecipeBridgePlugin.LOGGER.info("[JEIRecipeBridge] Sending recipe sync to {} (brand: {}, fill-capable: {})",
				originalPlayer.getName(), delayedBrand, supportsFill);

			if (delayedBrand.equalsIgnoreCase("fabric")) {
				sendFabricPayload(player, recipeMap, buffer, supportsFill);
				JEIRecipeBridgePlugin.LOGGER.info("[JEIRecipeBridge] ✓ Fabric recipe sync sent to {}", originalPlayer.getName());
			} else if (delayedBrand.equalsIgnoreCase("neoforge")) {
				sendNeoForgePayload(player, server, recipeMap, buffer, supportsFill);
				JEIRecipeBridgePlugin.LOGGER.info("[JEIRecipeBridge] ✓ NeoForge recipe sync sent to {}", originalPlayer.getName());
			} else {
				JEIRecipeBridgePlugin.LOGGER.info("[JEIRecipeBridge] Skipping recipe sync for {} — unsupported client brand: {}",
					originalPlayer.getName(), delayedBrand);
			}
		}, 10L);
	}

	private static void sendNeoForgePayload(ServerPlayer player, MinecraftServer server, RecipeMap recipeMap, RegistryFriendlyByteBuf buffer, boolean supportsFill) {
		List<RecipeType<?>> allRecipeTypes = BuiltInRegistries.RECIPE_TYPE.stream().toList();
		var recipeTypeSet = Set.copyOf(allRecipeTypes);
		
		if (supportsFill) {
			var payload = NeoforgeRecipeSyncPayload.create(allRecipeTypes, recipeMap);
			NeoforgeRecipeSyncPayload.STREAM_CODEC.encode(buffer, payload);
		} else {
			var recipeSubset = recipeMap.values().stream().filter(h -> recipeTypeSet.contains(h.value().getType())).toList();
			NeoforgeRecipeSyncPayload.RECIPE_TYPE_CODEC.encode(buffer, recipeTypeSet);
			NeoforgeRecipeSyncPayload.RECIPES_CODEC.encode(buffer, recipeSubset);
		}

		byte[] bytes = new byte[buffer.writerIndex()];
		buffer.getBytes(0, bytes);

		sendPayload(player, Identifier.fromNamespaceAndPath("neoforge", "recipe_content"), bytes);

		player.connection.send(new ClientboundUpdateTagsPacket(TagNetworkSerialization.serializeTagsToNetwork(server.registries())));
	}

	private static void sendFabricPayload(ServerPlayer player, RecipeMap recipeMap, RegistryFriendlyByteBuf buffer, boolean supportsFill) {
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

		if (supportsFill) {
			var payload = new FabricRecipeSyncPayload(list, true);
			FabricRecipeSyncPayload.CODEC.encode(buffer, payload);
		} else {
			FabricRecipeSyncPayload.Entry.CODEC.apply(ByteBufCodecs.list()).encode(buffer, list);
		}

		byte[] bytes = new byte[buffer.writerIndex()];
		buffer.getBytes(0, bytes);

		sendPayload(player, Identifier.fromNamespaceAndPath("fabric", "recipe_sync"), bytes);
	}

	private static void sendPayload(ServerPlayer player, Identifier id, byte[] bytes) {
		player.connection.send(new ClientboundCustomPayloadPacket(new DiscardedPayload(id, bytes)));
	}

	@Override
	public void onPluginMessageReceived(String channel, Player player, byte[] message) {
		switch (channel) {

			// ── Primary transfer path: JEI's native recipe transfer packet ──────
			case "jei:recipe_transfer" ->
				JeiRecipeTransferHandler.handle(player, message);

			// ── Custom fill-request paths (mod-loader specific) ───────────────
			case "fabric:recipe_fill_request" -> {
				JEIRecipeBridgePlugin.LOGGER.info("[JEIRecipeBridge] {} sent fabric:recipe_fill_request", player.getName());
				handleFabricRecipeFillRequest(player, message);
			}
			case "neoforge:recipe_fill_request" -> {
				JEIRecipeBridgePlugin.LOGGER.info("[JEIRecipeBridge] {} sent neoforge:recipe_fill_request", player.getName());
				handleNeoforgeRecipeFillRequest(player, message);
			}

			// ── Other jei: channels are registered for the handshake only ─────
			default -> {
				if (channel.startsWith("jei:")) {
					JEIRecipeBridgePlugin.LOGGER.debug(
						"[JEIRecipeBridge] Received unhandled JEI packet '{}' from {} ({} bytes)",
						channel, player.getName(), message.length);
				}
			}
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
