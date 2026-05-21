package com.mrbysco.jeicompat.util;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;

import java.util.ArrayList;
import java.util.List;

public class ContainerSlotMapper {

	public static class SlotInfo {
		public final List<Integer> inputSlots;
		public final int outputSlot;

		public SlotInfo(List<Integer> inputSlots, int outputSlot) {
			this.inputSlots = inputSlots;
			this.outputSlot = outputSlot;
		}
	}

	public static SlotInfo getSlotInfo(Recipe<?> recipe) {
		RecipeType<?> type = recipe.getType();
		Identifier typeId = BuiltInRegistries.RECIPE_TYPE.getKey(type);

		if (typeId == null) {
			return null;
		}

		String typeName = typeId.getPath();

		return switch (typeName) {
			case "crafting" -> getCraftingSlots();
			case "smelting", "blasting", "smoking", "campfire_cooking" -> getFurnaceSlots();
			case "smithing" -> getSmithingSlots();
			case "stonecutting" -> getStonecutterSlots();
			default -> null;
		};
	}

	private static SlotInfo getCraftingSlots() {
		List<Integer> inputs = new ArrayList<>();
		for (int i = 0; i < 9; i++) {
			inputs.add(i);
		}
		return new SlotInfo(inputs, 9);
	}

	private static SlotInfo getFurnaceSlots() {
		List<Integer> inputs = new ArrayList<>();
		inputs.add(0); // smelting input slot
		inputs.add(1); // fuel slot
		return new SlotInfo(inputs, 2);
	}

	private static SlotInfo getSmithingSlots() {
		List<Integer> inputs = new ArrayList<>();
		inputs.add(0); // template slot
		inputs.add(1); // base slot
		inputs.add(2); // addition slot
		return new SlotInfo(inputs, 3);
	}

	private static SlotInfo getStonecutterSlots() {
		List<Integer> inputs = new ArrayList<>();
		inputs.add(0); // input slot
		return new SlotInfo(inputs, 1);
	}
}
