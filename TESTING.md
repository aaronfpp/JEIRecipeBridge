# Recipe Fill Feature - Testing Guide

## Overview
This document outlines how to test the recipe fill (auto-fill) feature for the JEI Recipe Bridge plugin.

## Setup Requirements
- Paper server running Minecraft 1.26.1+
- JEI Recipe Bridge plugin installed
- Fabric or NeoForge client with JEI installed
- Test recipes available (vanilla recipes are fine)

## Test Scenarios

### 0. Recipe Sync Handshake
**Objective**: Verify the server advertises recipe fill capability during recipe sync.

**Steps**:
1. Connect to server with Fabric/NeoForge + JEI
2. Confirm the client receives the recipe sync payload
3. Verify the client sees the [+] button enabled for supported recipes

**Expected Result**: Server sends recipe sync payload with `pluginCapabilities = true`, and JEI shows the fill button active.

### 1. Basic Crafting Table Fill
**Objective**: Verify recipe filling works for basic crafting table recipes

**Steps**:
1. Connect to server with Fabric/NeoForge + JEI
2. Open crafting table
3. Have required ingredients in inventory (e.g., 2 oak planks for sticks)
4. Click [+] button in JEI for a crafting recipe
5. **Expected Result**: Items should appear in crafting grid

**Verification Checklist**:
- [ ] Items moved from inventory to crafting grid
- [ ] Only the required amount moved
- [ ] Output slot remains empty
- [ ] Partial fills work (some slots filled if missing ingredients)

### 2. Furnace/Smelting Recipe Fill
**Objective**: Verify recipe filling works for furnace smelting

**Steps**:
1. Open furnace
2. Have required smelting ingredients (ore + fuel)
3. Click [+] for a smelting recipe
4. **Expected Result**: Input in left slot, fuel in bottom slot

**Verification Checklist**:
- [ ] Ore appears in input slot
- [ ] Fuel appears in fuel slot
- [ ] Output slot untouched
- [ ] Response feedback appears to client

### 3. Smithing Table Fill
**Objective**: Verify recipe filling for smithing table recipes

**Steps**:
1. Open smithing table
2. Have template, base, and addition items
3. Click [+] for a smithing recipe
4. **Expected Result**: Items in correct slots (template, base, addition)

**Verification Checklist**:
- [ ] Template in correct slot
- [ ] Base item in correct slot
- [ ] Addition item in correct slot

### 4. Stonecutter Fill
**Objective**: Verify recipe filling for stonecutter

**Steps**:
1. Open stonecutter
2. Have stone blocks
3. Click [+] for a stonecutting recipe
4. **Expected Result**: Stone block appears in input slot

**Verification Checklist**:
- [ ] Input block in left slot
- [ ] Output slots remain available for recipes

### 5. Edge Cases

#### Missing Ingredients
**Steps**:
1. Open crafting table
2. Inventory empty
3. Click [+] for any recipe
4. **Expected Result**: Error response, no items moved

**Verification**:
- [ ] No items moved to grid
- [ ] Server logs show "No matching ingredients"
- [ ] Client receives error feedback

#### Inventory with Enchanted Items
**Steps**:
1. Have enchanted sword in inventory
2. Recipe requires plain wood
3. Click [+] 
4. **Expected Result**: Plain items used, enchanted items skipped

**Verification**:
- [ ] Only plain items considered
- [ ] Enchanted items untouched

#### Partial Fill
**Steps**:
1. Have 1/3 of required ingredients
2. Click [+] for recipe
3. **Expected Result**: Available slots filled, rest empty

**Verification**:
- [ ] Available items moved to grid
- [ ] Missing items reported in response
- [ ] Partial grid is usable (player can manually add rest)

## Monitoring & Debugging

### Server Log Output
Expected log messages:

```
[DEBUG] Received Fabric recipe fill request for: minecraft:crafting_shaped
[DEBUG] Recipe fill completed: minecraft:crafting_shaped (2/3 slots)
```

### Error Log Examples
```
[ERROR] Recipe not found: invalid:recipe:id
[ERROR] Error handling Fabric recipe fill request: java.lang.NullPointerException
```

### Enable Debug Logging
Edit plugin config or use Paper commands to enable DEBUG level logs:
```
/loglevel debug
```

## Test Data

### Simple Test Recipes (Vanilla)
1. **Sticks** - 2 oak planks in a column
2. **Crafting Table** - 4 oak planks in 2x2
3. **Stone** - Smelt cobblestone in furnace (needs fuel)
4. **Netherite Tools** - Use smithing table with netherite upgrade

## Known Limitations

1. **Recipe Variants**: Only first variant supported currently (no variant selection)
2. **Container Types**: Only supports vanilla containers (can add mods later)
3. **NBT Matching**: Ignores enchantments/durability/names as designed
4. **Stack Limits**: Respects max stack sizes for ingredients

## Success Criteria

Feature is working correctly when:
- [ ] Basic crafting table recipes auto-fill successfully
- [ ] All container types (furnace, smithing, stonecutter) work
- [ ] Partial fills work gracefully
- [ ] Enchanted/special items are skipped
- [ ] Error responses sent for invalid recipes
- [ ] No inventory desync occurs
- [ ] Client receives detailed response feedback

## Troubleshooting

### Items Not Moving
**Possible Causes**:
1. Recipe not found in server recipe manager
2. Container type mismatch
3. Ingredients not matching due to NBT data
4. **Fix**: Check server logs for specific error

### Desync (client sees items, server doesn't)
**Possible Causes**:
1. Container slot indices wrong
2. broadcastChanges() not being called
3. **Fix**: Verify slot indices match container type in ContainerSlotMapper

### Client Receives No Response
**Possible Causes**:
1. Outgoing channels not registered properly
2. Payload encoding error
3. **Fix**: Verify channels registered in JEIRecipeBridgePlugin.onEnable()

