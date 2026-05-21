package com.mrbysco.jeicompat.util;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.entity.HumanEntity;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InventoryMatcher {

	public static class MatchResult {
		public boolean canFillCompletely;
		public Map<Integer, ItemStack> slotMatches;
		public List<String> missingItems;

		public MatchResult(boolean canFillCompletely, Map<Integer, ItemStack> slotMatches, List<String> missingItems) {
			this.canFillCompletely = canFillCompletely;
			this.slotMatches = slotMatches;
			this.missingItems = missingItems;
		}
	}

	public static MatchResult matchIngredientsToInventory(Recipe<?> recipe, HumanEntity player, List<Integer> inputSlots) {
		// Placeholder - will be expanded to handle specific recipe types
		Map<Integer, ItemStack> slotMatches = new HashMap<>();
		List<String> missingItems = new ArrayList<>();
		return new MatchResult(false, slotMatches, missingItems);
	}

	private static List<ItemStack> getAvailableInventoryItems(Inventory inventory) {
		List<ItemStack> items = new ArrayList<>();

		for (org.bukkit.inventory.ItemStack bukitStack : inventory.getContents()) {
			if (bukitStack != null && !bukitStack.isEmpty() && isPlainItem(bukitStack)) {
				ItemStack nmsStack = CraftItemStack.asNMSCopy(bukitStack);
				if (!nmsStack.isEmpty()) {
					items.add(nmsStack);
				}
			}
		}

		return items;
	}

	private static boolean isPlainItem(org.bukkit.inventory.ItemStack stack) {
		return !stack.hasItemMeta();
	}

	private static ItemStack findMatchingItem(Ingredient ingredient, List<ItemStack> availableItems, Map<ItemStack, Integer> usedCounts) {
		for (ItemStack item : availableItems) {
			if (ingredient.test(item)) {
				int alreadyUsed = usedCounts.getOrDefault(item, 0);
				if (alreadyUsed < item.getMaxStackSize()) {
					return item;
				}
			}
		}
		return ItemStack.EMPTY;
	}
}
