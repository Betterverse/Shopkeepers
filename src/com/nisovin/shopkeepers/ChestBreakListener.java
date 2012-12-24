package com.nisovin.shopkeepers;

import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

public class ChestBreakListener implements Listener {

	private ShopkeepersPlugin plugin;
	
	public ChestBreakListener(ShopkeepersPlugin plugin) {
		this.plugin = plugin;
	}
	
	@EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
	public void onBlockBreak(BlockBreakEvent event) {
		if (event.getBlock().getType() == Material.CHEST) {
			Shopkeeper shopkeeper = plugin.getShopkeeperOwnerOfChest(event.getBlock());
			if (shopkeeper != null) {
				plugin.closeTradingForShopkeeper(shopkeeper.getId());
				plugin.activeShopkeepers.remove(shopkeeper.getId());
				plugin.allShopkeepersByChunk.get(shopkeeper.getChunk()).remove(shopkeeper);
				plugin.save();
				shopkeeper.remove();
			}
		}
	}
	
}
