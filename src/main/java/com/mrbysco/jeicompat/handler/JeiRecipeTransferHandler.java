package com.mrbysco.jeicompat.handler;

import com.mrbysco.jeicompat.JEIRecipeBridgePlugin;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Handles the {@code jei:recipe_transfer} plugin-message packet.
 *
 * <p>The JEI client pre-computes which inventory slot to move to which crafting slot and sends
 * that list here. We simply execute those moves server-side, mirroring the logic of
 * {@code mezz.jei.common.transfer.BasicRecipeTransferHandlerServer}.</p>
 *
 * <p>Packet format (all encoded as VarInts / booleans via Minecraft's FriendlyByteBuf):</p>
 * <pre>
 *   List&lt;TransferOperation&gt;  (inventorySlotId, craftingSlotId pairs)
 *   List&lt;Integer&gt;            craftingSlotIds
 *   List&lt;Integer&gt;            inventorySlotIds
 *   boolean                  maxTransfer
 *   boolean                  requireCompleteSets
 * </pre>
 */
public final class JeiRecipeTransferHandler {

    private JeiRecipeTransferHandler() {}

    public static void handle(Player bukkitPlayer, byte[] message) {
        ServerPlayer player = ((CraftPlayer) bukkitPlayer).getHandle();
        try {
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(message));

            // --- Decode packet (matches PacketRecipeTransfer.STREAM_CODEC in JEI) ---
            List<int[]> transferOps = readVarIntPairList(buf);   // (inventorySlotId, craftingSlotId)
            List<Integer> craftingSlotIds  = readVarIntList(buf);
            List<Integer> inventorySlotIds = readVarIntList(buf);
            boolean maxTransfer           = buf.readBoolean();
            boolean requireCompleteSets   = buf.readBoolean();

            JEIRecipeBridgePlugin.LOGGER.info(
                "[JEIRecipeBridge] {} recipe transfer: {} ops, {} crafting slots, {} inv slots, max={}, complete={}",
                bukkitPlayer.getName(), transferOps.size(), craftingSlotIds.size(),
                inventorySlotIds.size(), maxTransfer, requireCompleteSets);

            // --- Resolve slot objects from the player's open container ---
            AbstractContainerMenu container = player.containerMenu;
            int totalSlots = container.slots.size();

            List<Slot> craftingSlots  = resolveSlots(container, craftingSlotIds,  totalSlots, "crafting");
            List<Slot> inventorySlots = resolveSlots(container, inventorySlotIds, totalSlots, "inventory");

            if (craftingSlots == null || inventorySlots == null) {
                JEIRecipeBridgePlugin.LOGGER.warn(
                    "[JEIRecipeBridge] Recipe transfer aborted for {} — invalid slot indices", bukkitPlayer.getName());
                return;
            }

            // --- Validate transfer operations ---
            if (!validateOps(transferOps, craftingSlotIds, inventorySlotIds, totalSlots, player)) {
                JEIRecipeBridgePlugin.LOGGER.warn(
                    "[JEIRecipeBridge] Recipe transfer aborted for {} — slot validation failed", bukkitPlayer.getName());
                return;
            }

            // --- Execute the transfer (same algorithm as BasicRecipeTransferHandlerServer) ---
            executeTransfer(player, transferOps, craftingSlots, inventorySlots, maxTransfer, requireCompleteSets);

            container.broadcastChanges();
            JEIRecipeBridgePlugin.LOGGER.info(
                "[JEIRecipeBridge] ✓ Recipe transfer complete for {}", bukkitPlayer.getName());

        } catch (Exception e) {
            JEIRecipeBridgePlugin.LOGGER.error(
                "[JEIRecipeBridge] Error handling jei:recipe_transfer for {}", bukkitPlayer.getName(), e);
        }
    }

    // -------------------------------------------------------------------------
    // Transfer logic (mirrors BasicRecipeTransferHandlerServer)
    // -------------------------------------------------------------------------

    private static void executeTransfer(
            ServerPlayer player,
            List<int[]> ops,
            List<Slot> craftingSlots,
            List<Slot> inventorySlots,
            boolean maxTransfer,
            boolean requireCompleteSets) {

        boolean transferAsCompleteSets = requireCompleteSets || !maxTransfer;

        Map<Slot, ItemStack> craftingSlotToTaken =
            takeItemsFromInventory(player, ops, craftingSlots, inventorySlots, transferAsCompleteSets, maxTransfer);

        if (craftingSlotToTaken.isEmpty()) {
            JEIRecipeBridgePlugin.LOGGER.warn("[JEIRecipeBridge] Transfer found no items to move.");
            return;
        }

        // Clear crafting grid → stow displaced items back to inventory
        List<ItemStack> displaced = clearCraftingSlots(craftingSlots, player);

        // Fill crafting grid
        int slotLimit = getSlotStackLimit(craftingSlotToTaken, requireCompleteSets);
        List<ItemStack> remainders = new ArrayList<>();
        craftingSlotToTaken.forEach((slot, stack) -> {
            ItemStack leftover = slot.safeInsert(stack, slotLimit);
            if (!leftover.isEmpty()) remainders.add(leftover);
        });

        // Put everything that didn't fit back into the player inventory
        stowItems(player, inventorySlots, displaced);
        stowItems(player, inventorySlots, remainders);
    }

    private static Map<Slot, ItemStack> takeItemsFromInventory(
            ServerPlayer player,
            List<int[]> ops,
            List<Slot> craftingSlots,
            List<Slot> inventorySlots,
            boolean transferAsCompleteSets,
            boolean maxTransfer) {

        Map<Slot, ItemStack> result = new HashMap<>();

        if (!maxTransfer) {
            // Single-set transfer
            Map<Slot, ItemStack> found = removeOneSet(player, ops, craftingSlots, inventorySlots, transferAsCompleteSets);
            result.putAll(found);
        } else {
            // Keep taking sets until inventory is empty
            while (true) {
                Map<Slot, ItemStack> found = removeOneSet(player, ops, craftingSlots, inventorySlots, transferAsCompleteSets);
                if (found.isEmpty()) break;
                mergeInto(result, found);
            }
        }
        return result;
    }

    private static Map<Slot, ItemStack> removeOneSet(
            ServerPlayer player,
            List<int[]> ops,
            List<Slot> craftingSlots,
            List<Slot> inventorySlots,
            boolean requireComplete) {

        Map<Slot, ItemStack> backup   = requireComplete ? new HashMap<>() : null;
        Map<Slot, ItemStack> foundSet = new HashMap<>();
        AbstractContainerMenu container = player.containerMenu;

        for (int[] op : ops) {
            int inventorySlotId = op[0];
            int craftingSlotId  = op[1];
            Slot craftingSlot = container.getSlot(craftingSlotId);

            // Try the hinted slot first, then crafting slots, then full inventory slots
            Slot source = findSourceSlot(player, inventorySlotId, craftingSlots, inventorySlots, container);

            if (source != null) {
                if (backup != null && !backup.containsKey(source)) {
                    backup.put(source, source.getItem().copy());
                }
                ItemStack taken = source.safeTake(1, Integer.MAX_VALUE, player);
                foundSet.put(craftingSlot, taken);
            } else if (requireComplete) {
                // Roll back everything we took in this set
                if (backup != null) {
                    backup.forEach(Slot::set);
                }
                return Map.of();
            }
            // If not requireComplete we simply skip the missing item slot
        }
        return foundSet;
    }

    /** Find a source slot: prefer the hint (inventorySlotId), fall back to crafting slots, then inventory slots. */
    private static Slot findSourceSlot(
            ServerPlayer player,
            int hintSlotId,
            List<Slot> craftingSlots,
            List<Slot> inventorySlots,
            AbstractContainerMenu container) {

        if (hintSlotId >= 0 && hintSlotId < container.slots.size()) {
            Slot hint = container.getSlot(hintSlotId);
            if (isUsable(player, hint)) return hint;
        }
        // Fallback: any crafting slot with the right item
        for (Slot s : craftingSlots) {
            if (isUsable(player, s)) return s;
        }
        // Fallback: any inventory slot
        for (Slot s : inventorySlots) {
            if (isUsable(player, s)) return s;
        }
        return null;
    }

    private static boolean isUsable(ServerPlayer player, Slot slot) {
        ItemStack stack = slot.getItem();
        return !stack.isEmpty() && slot.allowModification(player);
    }

    // -------------------------------------------------------------------------
    // Helper methods
    // -------------------------------------------------------------------------

    private static int getSlotStackLimit(Map<Slot, ItemStack> slotToItem, boolean requireCompleteSets) {
        if (!requireCompleteSets) return Integer.MAX_VALUE;
        return slotToItem.entrySet().stream()
            .mapToInt(e -> e.getKey().mayPlace(e.getValue()) ? e.getKey().getMaxStackSize(e.getValue()) : Integer.MAX_VALUE)
            .min().orElse(Integer.MAX_VALUE);
    }

    private static List<ItemStack> clearCraftingSlots(List<Slot> craftingSlots, ServerPlayer player) {
        List<ItemStack> cleared = new ArrayList<>();
        for (Slot slot : craftingSlots) {
            if (!slot.mayPickup(player)) continue;
            ItemStack item = slot.getItem();
            if (!item.isEmpty() && slot.mayPlace(item)) {
                cleared.add(slot.safeTake(Integer.MAX_VALUE, Integer.MAX_VALUE, player));
            }
        }
        return cleared;
    }

    private static void stowItems(ServerPlayer player, List<Slot> inventorySlots, List<ItemStack> stacks) {
        for (ItemStack stack : stacks) {
            ItemStack remainder = stowItem(player, inventorySlots, stack);
            if (!remainder.isEmpty()) {
                if (!player.getInventory().add(remainder)) {
                    player.drop(remainder, false);
                }
            }
        }
    }

    private static ItemStack stowItem(ServerPlayer player, List<Slot> slots, ItemStack stack) {
        if (stack.isEmpty()) return ItemStack.EMPTY;
        ItemStack remainder = stack.copy();
        // Fill existing stacks first
        for (Slot slot : slots) {
            if (!slot.mayPickup(player)) continue;
            ItemStack existing = slot.getItem();
            if (!existing.isEmpty() && existing.isStackable()) {
                remainder = slot.safeInsert(remainder);
                if (remainder.isEmpty()) return ItemStack.EMPTY;
            }
        }
        // Then empty slots
        for (Slot slot : slots) {
            if (slot.getItem().isEmpty()) {
                remainder = slot.safeInsert(remainder);
                if (remainder.isEmpty()) return ItemStack.EMPTY;
            }
        }
        return remainder;
    }

    private static void mergeInto(Map<Slot, ItemStack> result, Map<Slot, ItemStack> addition) {
        addition.forEach((slot, stack) -> {
            result.merge(slot, stack, (existing, incoming) -> {
                existing.grow(incoming.getCount());
                return existing;
            });
        });
    }

    // -------------------------------------------------------------------------
    // Validation
    // -------------------------------------------------------------------------

    private static boolean validateOps(
            List<int[]> ops,
            List<Integer> craftingSlotIds,
            List<Integer> inventorySlotIds,
            int totalSlots,
            ServerPlayer player) {

        for (int[] op : ops) {
            int invId  = op[0];
            int crtId  = op[1];
            if (invId < 0 || invId >= totalSlots || crtId < 0 || crtId >= totalSlots) {
                JEIRecipeBridgePlugin.LOGGER.error(
                    "[JEIRecipeBridge] Slot index out of bounds: invSlot={}, craftSlot={}, total={}",
                    invId, crtId, totalSlots);
                return false;
            }
            if (!craftingSlotIds.contains(crtId)) {
                JEIRecipeBridgePlugin.LOGGER.error(
                    "[JEIRecipeBridge] craftingSlot {} not in declared crafting slot list", crtId);
                return false;
            }
        }
        return true;
    }

    // -------------------------------------------------------------------------
    // Packet deserialization helpers
    // -------------------------------------------------------------------------

    /** Reads a VarInt-prefixed list of (inventorySlotId, craftingSlotId) pairs. */
    private static List<int[]> readVarIntPairList(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<int[]> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            int inv = buf.readVarInt();
            int crt = buf.readVarInt();
            list.add(new int[]{inv, crt});
        }
        return list;
    }

    /** Reads a VarInt-prefixed list of VarInts. */
    private static List<Integer> readVarIntList(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<Integer> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(buf.readVarInt());
        }
        return list;
    }

    /** Resolves slot IDs to Slot objects, returning null on any out-of-bounds ID. */
    private static List<Slot> resolveSlots(
            AbstractContainerMenu container,
            List<Integer> ids,
            int totalSlots,
            String label) {

        List<Slot> slots = new ArrayList<>(ids.size());
        for (int id : ids) {
            if (id < 0 || id >= totalSlots) {
                JEIRecipeBridgePlugin.LOGGER.error(
                    "[JEIRecipeBridge] {} slot id {} out of range (container has {} slots)", label, id, totalSlots);
                return null;
            }
            slots.add(container.getSlot(id));
        }
        return slots;
    }
}
