package com.mrbysco.jeicompat.handler;

import com.mrbysco.jeicompat.JEIRecipeBridgePlugin;
import com.mrbysco.jeicompat.util.ContainerSlotMapper;
import com.mrbysco.jeicompat.util.InventoryMatcher;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class RecipeFillHandler {

	public static void handleRecipeFillRequest(Player bukkitPlayer, ResourceKey<Recipe<?>> recipeId, Identifier variantId) {
		ServerPlayer player = ((CraftPlayer) bukkitPlayer).getHandle();
		MinecraftServer server = player.level().getServer();
		RecipeManager recipeManager = server.getRecipeManager();

		Optional<RecipeHolder<?>> recipeOpt = recipeManager.byKey(recipeId);
		if (recipeOpt.isEmpty()) {
			JEIRecipeBridgePlugin.LOGGER.debug("Recipe not found: " + recipeId);
			return;
		}

		Recipe<?> recipe = recipeOpt.get().value();
		ContainerSlotMapper.SlotInfo slotInfo = ContainerSlotMapper.getSlotInfo(recipe);

		if (slotInfo == null) {
			JEIRecipeBridgePlugin.LOGGER.debug("Unsupported recipe type for: " + recipeId);
			return;
		}

		AbstractContainerMenu container = player.containerMenu;
		if (container == null) {
			JEIRecipeBridgePlugin.LOGGER.debug("Player has no open container");
			return;
		}

		InventoryMatcher.MatchResult matchResult = InventoryMatcher.matchIngredientsToInventory(
			recipe,
			bukkitPlayer,
			slotInfo.inputSlots
		);

		if (matchResult.slotMatches.isEmpty()) {
			JEIRecipeBridgePlugin.LOGGER.debug("No matching ingredients found for recipe");
			return;
		}

		for (Map.Entry<Integer, ItemStack> entry : matchResult.slotMatches.entrySet()) {
			int slot = entry.getKey();
			ItemStack item = entry.getValue();

			if (slot >= 0 && slot < container.slots.size()) {
				ItemStack stackInSlot = container.slots.get(slot).getItem();
				if (stackInSlot.isEmpty()) {
					container.slots.get(slot).set(item.split(1));
				}
			}
		}

		container.broadcastChanges();
		JEIRecipeBridgePlugin.LOGGER.debug("Recipe fill completed: " + recipeId);
	}
}
