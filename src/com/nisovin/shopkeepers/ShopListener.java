package com.nisovin.shopkeepers;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import com.nisovin.shopkeepers.shoptypes.PlayerShopkeeper;

class ShopListener implements Listener {

	ShopkeepersPlugin plugin;
	
	public ShopListener(ShopkeepersPlugin plugin) {
		this.plugin = plugin;
	}
	
	@EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
	void onBlockPlace(BlockPlaceEvent event) {
		if (event.getBlock().getType() == Material.CHEST) {
			Block b = event.getBlock();
			List<String> list = plugin.recentlyPlacedChests.get(event.getPlayer().getName());
			if (list == null) {
				list = new LinkedList<String>();
				plugin.recentlyPlacedChests.put(event.getPlayer().getName(), list);
			}
			list.add(b.getWorld().getName() + "," + b.getX() + "," + b.getY() + "," + b.getZ());
			if (list.size() > 5) {
				list.remove(0);
			}
		}
	}
	
	@EventHandler
	void onInventoryClose(InventoryCloseEvent event) {
		String name = event.getPlayer().getName();
		if (plugin.editing.containsKey(name)) {
			ShopkeepersPlugin.debug("Player " + name + " closed editor window");
			String id = plugin.editing.remove(name);
			Shopkeeper shopkeeper = plugin.activeShopkeepers.get(id);
			if (shopkeeper != null) {
				if (event.getInventory().getTitle().equals(Settings.editorTitle)) {
					shopkeeper.onEditorClose(event);
					plugin.closeTradingForShopkeeper(id);
					plugin.save();
				}
			}
		} else if (plugin.purchasing.containsKey(name)) {
			ShopkeepersPlugin.debug("Player " + name + " closed trade window");
			plugin.purchasing.remove(name);
		}
	}
	
	@EventHandler
	void onInventoryClick(InventoryClickEvent event) {
		// shopkeeper editor click
		if (event.getInventory().getTitle().equals(Settings.editorTitle)) {
			if (plugin.editing.containsKey(event.getWhoClicked().getName())) {
				// get the shopkeeper being edited
				String id = plugin.editing.get(event.getWhoClicked().getName());
				Shopkeeper shopkeeper = plugin.activeShopkeepers.get(id);
				if (shopkeeper != null) {
					// editor click
					EditorClickResult result = shopkeeper.onEditorClick(event);
					if (result == EditorClickResult.DELETE_SHOPKEEPER) {
						// close inventories
						plugin.closeTradingForShopkeeper(id);
						
						// return egg
						if (Settings.deletingPlayerShopReturnsEgg && shopkeeper instanceof PlayerShopkeeper) {
							event.getWhoClicked().getInventory().addItem(new ItemStack(Material.MONSTER_EGG, 1, (short)120));
						}
						
						// remove shopkeeper
						plugin.activeShopkeepers.remove(id);
						plugin.allShopkeepersByChunk.get(shopkeeper.getChunk()).remove(shopkeeper);
						plugin.save();
					} else if (result == EditorClickResult.DONE_EDITING) {
						// end the editing session
						plugin.closeTradingForShopkeeper(id);
						plugin.save();
					} else if (result == EditorClickResult.SAVE_AND_CONTINUE) {
						plugin.save();
					}
				} else {
					event.setCancelled(true);
					plugin.closeInventory(event.getWhoClicked());
				}
			} else {
				event.setCancelled(true);
				plugin.closeInventory(event.getWhoClicked());
			}
		}
		// purchase click
		if (event.getInventory().getName().equals("mob.villager") && event.getRawSlot() == 2 && plugin.purchasing.containsKey(event.getWhoClicked().getName())) {
			String id = plugin.purchasing.get(event.getWhoClicked().getName());
			Shopkeeper shopkeeper = plugin.activeShopkeepers.get(id);
			if (shopkeeper != null) {
				// verify purchase
				ItemStack item = event.getCurrentItem();
				ItemStack item1 = event.getInventory().getItem(0);
				ItemStack item2 = event.getInventory().getItem(1);
				boolean ok = false;
				List<ItemStack[]> recipes = shopkeeper.getRecipes();
				for (ItemStack[] recipe : recipes) {
					if (itemEquals(item1, recipe[0]) && itemEquals(item2, recipe[1]) && itemEquals(item, recipe[2])) {
						ok = true;
						break;
					}
				}
				if (!ok) {
					event.setCancelled(true);
					return;
				}
				
				// send purchase click to shopkeeper
				shopkeeper.onPurchaseClick(event);
				
				// log purchase
				if (Settings.enablePurchaseLogging && !event.isCancelled()) {
					try {
						String owner = (shopkeeper instanceof PlayerShopkeeper ? ((PlayerShopkeeper)shopkeeper).getOwner() : "[Admin]");
						File file = new File(plugin.getDataFolder(), "purchases-" + new SimpleDateFormat("yyyy-MM-dd").format(new Date()) + ".csv");
						boolean isNew = !file.exists();
						BufferedWriter writer = new BufferedWriter(new FileWriter(file, true));
						if (isNew) writer.append("TIME,PLAYER,SHOP TYPE,SHOP POS,OWNER,ITEM TYPE,DATA,QUANTITY,CURRENCY 1,CURRENCY 2\n");
						writer.append("\"" + 
								new SimpleDateFormat("HH:mm:ss").format(new Date()) + "\",\"" + 
								event.getWhoClicked().getName() + "\",\"" + 
								shopkeeper.getType().name() + "\",\"" + 
								shopkeeper.getPositionString() + "\",\"" + 
								owner + "\",\"" + 
								item.getType().name() + "\",\"" + 
								item.getDurability() + "\",\"" + 
								item.getAmount() + "\",\"" +
								(item1 != null ? item1.getType().name() + ":" + item1.getDurability() : "") + "\",\"" +
								(item2 != null ? item2.getType().name() + ":" + item2.getDurability() : "") + "\"\n");
						writer.close();
					} catch (IOException e) {
						plugin.getLogger().severe("IO exception while trying to log purchase");
					}
				}
			}
		}
	}
	
	private boolean itemEquals(ItemStack item1, ItemStack item2) {
		if ((item1 == null || item1.getTypeId() == 0) && (item2 == null || item2.getTypeId() == 0)) return true;
		if (item1 == null || item2 == null) return false;
		return item1.getTypeId() == item2.getTypeId() && item1.getDurability() == item2.getDurability() && itemNamesEqual(item1, item2);
	}

	private static String getNameOfItem(ItemStack item) {
		if (item.hasItemMeta()) {
			ItemMeta meta = item.getItemMeta();
			if (meta.hasDisplayName()) {
				return meta.getDisplayName();
			}
		}
		return "";
	}

	private static boolean itemNamesEqual(ItemStack item1, ItemStack item2) {
		String name1 = getNameOfItem(item1);
		String name2 = getNameOfItem(item2);
		return (name1.equals(name2));
	}

	@EventHandler(priority=EventPriority.LOW)
	void onPlayerInteract1(PlayerInteractEvent event) {		
		// prevent opening shop chests
		if (event.hasBlock() && event.getClickedBlock().getType() == Material.CHEST) {
			Player player = event.getPlayer();
			Block block = event.getClickedBlock();
			
			// check for protected chest
			if (!event.getPlayer().hasPermission("shopkeeper.bypass")) {
				if (plugin.isChestProtected(player, block)) {
					event.setCancelled(true);
					return;
				}
				for (BlockFace face : plugin.faces) {
					if (block.getRelative(face).getType() == Material.CHEST) {
						if (plugin.isChestProtected(player, block.getRelative(face))) {
							event.setCancelled(true);
							return;
						}				
					}
				}
			}
		}
		
	}
	
	@EventHandler
	void onChunkLoad(ChunkLoadEvent event) {
		final Chunk chunk = event.getChunk();
		Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
			public void run() {
				if (chunk.isLoaded()) {
					plugin.loadShopkeepersInChunk(chunk);
				}
			}
		}, 2);
	}

	@EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
	void onChunkUnload(ChunkUnloadEvent event) {
		List<Shopkeeper> shopkeepers = plugin.allShopkeepersByChunk.get(event.getWorld().getName() + "," + event.getChunk().getX() + "," + event.getChunk().getZ());
		if (shopkeepers != null) {
			ShopkeepersPlugin.debug("Unloading " + shopkeepers.size() + " shopkeepers in chunk " + event.getChunk().getX() + "," + event.getChunk().getZ());
			for (Shopkeeper shopkeeper : shopkeepers) {
				plugin.activeShopkeepers.remove(shopkeeper.getId());
				shopkeeper.remove();
			}
		}
	}
	
	@EventHandler(priority=EventPriority.LOWEST)
	void onWorldLoad(WorldLoadEvent event) {
		for (Chunk chunk : event.getWorld().getLoadedChunks()) {
			plugin.loadShopkeepersInChunk(chunk);
		}
	}
	
	@EventHandler(priority=EventPriority.LOWEST)
	void onWorldUnload(WorldUnloadEvent event) {
		String worldName = event.getWorld().getName();
		Iterator<Shopkeeper> iter = plugin.activeShopkeepers.values().iterator();
		int count = 0;
		while (iter.hasNext()) {
			Shopkeeper shopkeeper = iter.next();
			if (shopkeeper.getWorldName().equals(worldName)) {
				shopkeeper.remove();
				iter.remove();
				count++;
			}
		}
		ShopkeepersPlugin.debug("Unloaded " + count + " shopkeepers in unloaded world " + worldName);
	}
	
	@EventHandler
	void onPlayerQuit(PlayerQuitEvent event) {
		String name = event.getPlayer().getName();
		plugin.editing.remove(name);
		plugin.purchasing.remove(name);
		plugin.selectedShopType.remove(name);
		plugin.selectedChest.remove(name);
		plugin.recentlyPlacedChests.remove(name);
	}
	
}
