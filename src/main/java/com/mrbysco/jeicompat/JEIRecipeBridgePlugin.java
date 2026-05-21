package com.mrbysco.jeicompat;

import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.Messenger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class JEIRecipeBridgePlugin extends JavaPlugin {
	public static final Logger LOGGER = LoggerFactory.getLogger("JEIRecipeBridge");
	public static Plugin Plugin;

	@Override
	public void onEnable() {
		Plugin = this;

		getServer().getPluginManager().registerEvents(new RecipeHandler(), this);

		final Server server = getServer();
		final Messenger messenger = server.getMessenger();

		// ── Outgoing channels (server → client) ────────────────────────────────
		// Recipe sync payloads for Fabric and NeoForge JEI clients
		messenger.registerOutgoingPluginChannel(this, "neoforge:recipe_content");
		messenger.registerOutgoingPluginChannel(this, "fabric:recipe_sync");
		// Recipe fill responses
		messenger.registerOutgoingPluginChannel(this, "neoforge:recipe_fill_response");
		messenger.registerOutgoingPluginChannel(this, "fabric:recipe_fill_response");

		// ── Incoming channels (client → server) ────────────────────────────────
		// Recipe fill requests from Fabric and NeoForge JEI clients
		messenger.registerIncomingPluginChannel(this, "neoforge:recipe_fill_request", new RecipeHandler());
		messenger.registerIncomingPluginChannel(this, "fabric:recipe_fill_request", new RecipeHandler());

		// ── JEI protocol channels ──────────────────────────────────────────────
		// JEI clients check for "jei:delete_player_item" (and other jei: channels)
		// being registered on the server to determine if JEI is present.
		// Without these, isJeiOnServer() returns false and the [+] button stays grey.
		RecipeHandler jeiChannelHandler = new RecipeHandler();
		messenger.registerIncomingPluginChannel(this, "jei:delete_player_item", jeiChannelHandler);
		messenger.registerIncomingPluginChannel(this, "jei:recipe_transfer", jeiChannelHandler);
		messenger.registerIncomingPluginChannel(this, "jei:request_cheat_permission", jeiChannelHandler);
		messenger.registerIncomingPluginChannel(this, "jei:give_item_stack", jeiChannelHandler);
		messenger.registerIncomingPluginChannel(this, "jei:set_hotbar_item_stack", jeiChannelHandler);

		LOGGER.info("[JEIRecipeBridge] Plugin enabled — all JEI compatibility channels registered.");
		LOGGER.info("[JEIRecipeBridge] Outgoing: neoforge:recipe_content, fabric:recipe_sync, *:recipe_fill_response");
		LOGGER.info("[JEIRecipeBridge] Incoming: *:recipe_fill_request, jei:delete_player_item, jei:recipe_transfer, jei:request_cheat_permission, jei:give_item_stack, jei:set_hotbar_item_stack");
	}

	@Override
	public void onDisable() {
		LOGGER.info("[JEIRecipeBridge] Plugin disabled.");
	}
}
