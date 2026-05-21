package com.mrbysco.jeicompat.handler;

import com.mrbysco.jeicompat.JEIRecipeBridgePlugin;
import com.mrbysco.jeicompat.compat.fabric.FabricRecipeFillResponsePayload;
import com.mrbysco.jeicompat.compat.neoforge.NeoforgeRecipeFillResponsePayload;
import com.mrbysco.jeicompat.util.ContainerSlotMapper;
import com.mrbysco.jeicompat.util.InventoryMatcher;
import io.netty.buffer.Unpooled;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.DiscardedPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class RecipeFillHandler {

	public static void handleRecipeFillRequest(Player bukkitPlayer, ResourceKey<Recipe<?>> recipeId, Identifier variantId, String modLoader) {
		ServerPlayer player = ((CraftPlayer) bukkitPlayer).getHandle();
		MinecraftServer server = player.level().getServer();
		RecipeManager recipeManager = server.getRecipeManager();

		try {
			Optional<RecipeHolder<?>> recipeOpt = recipeManager.byKey(recipeId);
			if (recipeOpt.isEmpty()) {
				sendErrorResponse(player, modLoader, "Recipe not found");
				JEIRecipeBridgePlugin.LOGGER.debug("Recipe not found: " + recipeId);
				return;
			}

			Recipe<?> recipe = recipeOpt.get().value();
			ContainerSlotMapper.SlotInfo slotInfo = ContainerSlotMapper.getSlotInfo(recipe);

			if (slotInfo == null) {
				sendErrorResponse(player, modLoader, "Unsupported recipe type");
				JEIRecipeBridgePlugin.LOGGER.debug("Unsupported recipe type for: " + recipeId);
				return;
			}

			AbstractContainerMenu container = player.containerMenu;
			if (container == null) {
				sendErrorResponse(player, modLoader, "No container open");
				JEIRecipeBridgePlugin.LOGGER.debug("Player has no open container");
				return;
			}

			InventoryMatcher.MatchResult matchResult = InventoryMatcher.matchIngredientsToInventory(
				recipe,
				bukkitPlayer,
				slotInfo.inputSlots
			);

			if (matchResult.slotMatches.isEmpty()) {
				sendErrorResponse(player, modLoader, "No matching ingredients found");
				JEIRecipeBridgePlugin.LOGGER.debug("No matching ingredients found for recipe");
				return;
			}

			int filledSlots = 0;
			for (Map.Entry<Integer, ItemStack> entry : matchResult.slotMatches.entrySet()) {
				int slot = entry.getKey();
				ItemStack itemToPlace = entry.getValue();

				if (slot >= 0 && slot < container.slots.size()) {
					Slot containerSlot = container.slots.get(slot);
					ItemStack stackInSlot = containerSlot.getItem();

					if (stackInSlot.isEmpty()) {
						containerSlot.set(itemToPlace.copy());
						filledSlots++;
					}
				}
			}

			container.broadcastChanges();
			sendSuccessResponse(player, modLoader, filledSlots, slotInfo.inputSlots.size(), matchResult.missingItems);
			JEIRecipeBridgePlugin.LOGGER.debug("Recipe fill completed: " + recipeId + " (" + filledSlots + "/" + slotInfo.inputSlots.size() + " slots)");

		} catch (Exception e) {
			JEIRecipeBridgePlugin.LOGGER.error("Error handling recipe fill request", e);
			sendErrorResponse(player, modLoader, "Internal server error");
		}
	}

	private static void sendSuccessResponse(ServerPlayer player, String modLoader, int filledSlots, int totalSlots, List<String> missingItems) {
		if (modLoader.equalsIgnoreCase("fabric")) {
			FabricRecipeFillResponsePayload response = new FabricRecipeFillResponsePayload(
				true, filledSlots, totalSlots, missingItems, ""
			);
			RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
				Unpooled.buffer(),
				player.level().getServer().registryAccess()
			);
			FabricRecipeFillResponsePayload.CODEC.encode(buffer, response);
			byte[] bytes = new byte[buffer.writerIndex()];
			buffer.getBytes(0, bytes);
			sendPayload(player, Identifier.fromNamespaceAndPath("fabric", "recipe_fill_response"), bytes);

		} else if (modLoader.equalsIgnoreCase("neoforge")) {
			NeoforgeRecipeFillResponsePayload response = new NeoforgeRecipeFillResponsePayload(
				true, filledSlots, totalSlots, missingItems, ""
			);
			RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
				Unpooled.buffer(),
				player.level().getServer().registryAccess()
			);
			NeoforgeRecipeFillResponsePayload.STREAM_CODEC.encode(buffer, response);
			byte[] bytes = new byte[buffer.writerIndex()];
			buffer.getBytes(0, bytes);
			sendPayload(player, Identifier.fromNamespaceAndPath("neoforge", "recipe_fill_response"), bytes);
		}
	}

	private static void sendErrorResponse(ServerPlayer player, String modLoader, String error) {
		if (modLoader.equalsIgnoreCase("fabric")) {
			FabricRecipeFillResponsePayload response = new FabricRecipeFillResponsePayload(
				false, 0, 0, new ArrayList<>(), error
			);
			RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
				Unpooled.buffer(),
				player.level().getServer().registryAccess()
			);
			FabricRecipeFillResponsePayload.CODEC.encode(buffer, response);
			byte[] bytes = new byte[buffer.writerIndex()];
			buffer.getBytes(0, bytes);
			sendPayload(player, Identifier.fromNamespaceAndPath("fabric", "recipe_fill_response"), bytes);

		} else if (modLoader.equalsIgnoreCase("neoforge")) {
			NeoforgeRecipeFillResponsePayload response = new NeoforgeRecipeFillResponsePayload(
				false, 0, 0, new ArrayList<>(), error
			);
			RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
				Unpooled.buffer(),
				player.level().getServer().registryAccess()
			);
			NeoforgeRecipeFillResponsePayload.STREAM_CODEC.encode(buffer, response);
			byte[] bytes = new byte[buffer.writerIndex()];
			buffer.getBytes(0, bytes);
			sendPayload(player, Identifier.fromNamespaceAndPath("neoforge", "recipe_fill_response"), bytes);
		}
	}

	private static void sendPayload(ServerPlayer player, Identifier id, byte[] bytes) {
		player.connection.send(new ClientboundCustomPayloadPacket(new DiscardedPayload(id, bytes)));
	}
}
