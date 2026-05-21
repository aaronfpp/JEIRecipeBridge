package com.mrbysco.jeicompat.util;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.entity.HumanEntity;
import org.bukkit.inventory.Inventory;

import java.lang.reflect.Method;
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
		Map<Integer, ItemStack> slotMatches = new HashMap<>();
		List<String> missingItems = new ArrayList<>();

		List<Ingredient> ingredients = extractIngredients(recipe);
		if (ingredients == null || ingredients.isEmpty()) {
			return new MatchResult(false, slotMatches, missingItems);
		}

		List<ItemStack> availableItems = getAvailableInventoryItems(player.getInventory());

		for (int i = 0; i < Math.min(inputSlots.size(), ingredients.size()); i++) {
			int containerSlot = inputSlots.get(i);
			Ingredient ingredient = ingredients.get(i);

			ItemStack matched = findMatchingItem(ingredient, availableItems);
			if (matched.isEmpty()) {
				missingItems.add(getIngredientName(ingredient));
			} else {
				ItemStack toPlace = matched.copy();
				toPlace.setCount(1);
				slotMatches.put(containerSlot, toPlace);
				matched.shrink(1);
			}
		}

		boolean canFillCompletely = missingItems.isEmpty();
		return new MatchResult(canFillCompletely, slotMatches, missingItems);
	}

	private static List<Ingredient> extractIngredients(Recipe<?> recipe) {
		try {
			Method getIngredientsMethod = recipe.getClass().getMethod("getIngredients");
			Object result = getIngredientsMethod.invoke(recipe);
			if (result instanceof List) {
				return (List<Ingredient>) result;
			}
		} catch (Exception e) {
			// Fallback: recipe type doesn't have getIngredients()
		}
		return new ArrayList<>();
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

	private static ItemStack findMatchingItem(Ingredient ingredient, List<ItemStack> availableItems) {
		for (ItemStack item : availableItems) {
			if (!item.isEmpty() && ingredient.test(item)) {
				return item;
			}
		}
		return ItemStack.EMPTY;
	}

	private static String getIngredientName(Ingredient ingredient) {
		if (ingredient == null) return "Unknown";
		return ingredient.toString();
	}
}
