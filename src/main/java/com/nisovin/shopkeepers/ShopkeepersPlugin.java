package com.nisovin.shopkeepers;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import com.nisovin.shopkeepers.events.*;
import com.nisovin.shopkeepers.pluginhandlers.*;
import com.nisovin.shopkeepers.shopobjects.*;
import com.nisovin.shopkeepers.shoptypes.*;
import com.nisovin.shopkeepers.volatilecode.VolatileCodeHandle;
import com.nisovin.shopkeepers.volatilecode.VolatileCode_1_4_5;
import com.nisovin.shopkeepers.volatilecode.VolatileCode_1_4_6;

public class ShopkeepersPlugin extends JavaPlugin {

	static ShopkeepersPlugin plugin;
	static VolatileCodeHandle volatileCodeHandle;

	private boolean debug = false;
	
	Map<String, List<Shopkeeper>> allShopkeepersByChunk = new HashMap<String, List<Shopkeeper>>();
	Map<String, Shopkeeper> activeShopkeepers = new HashMap<String, Shopkeeper>();
	Map<String, String> editing = new HashMap<String, String>();
	Map<String, String> purchasing = new HashMap<String, String>();
	Map<String, List<String>> recentlyPlacedChests = new HashMap<String, List<String>>();
	Map<String, ShopkeeperType> selectedShopType = new HashMap<String, ShopkeeperType>();
	Map<String, Block> selectedChest = new HashMap<String, Block>();
	
	private boolean dirty = false;
	private int chunkLoadSaveTask = -1;
		
	BlockFace[] faces = {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};
	
	@Override
	public void onEnable() {
		plugin = this;
		
		// load volatile code handler
		String version = getServer().getVersion();
		if (version.contains("(MC: 1.4.5)")) {
			volatileCodeHandle = new VolatileCode_1_4_5();
		} else if (version.contains("(MC: 1.4.6)")) {
			volatileCodeHandle = new VolatileCode_1_4_6();
		} else {
			getLogger().severe("Incompatible server version: Shopkeepers plugin cannot be enabled.");
			this.setEnabled(false);
			return;
		}
		
		// get config
		File file = new File(getDataFolder(), "config.yml");
		if (!file.exists()) {
			saveDefaultConfig();
		}
		reloadConfig();
		Configuration config = getConfig();
		Settings.loadConfiguration(config);
		debug = config.getBoolean("debug", debug);

		// get lang config
		String lang = config.getString("language", "en");
		File langFile = new File(getDataFolder(), "language-" + lang + ".yml");
		if (!langFile.exists() && this.getResource("language-" + lang + ".yml") != null) {
			saveResource("language-" + lang + ".yml", false);
		}
		if (langFile.exists()) {
			try {
				YamlConfiguration langConfig = new YamlConfiguration();
				langConfig.load(langFile);
				Settings.loadLanguageConfiguration(langConfig);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		
		// load shopkeeper saved data
		load();
		
		// spawn villagers in loaded chunks
		for (World world : Bukkit.getWorlds()) {
			for (Chunk chunk : world.getLoadedChunks()) {
				loadShopkeepersInChunk(chunk);
			}
		}
		
		// register events
		PluginManager pm = getServer().getPluginManager();
		pm.registerEvents(new ShopListener(this), this);
		if (Settings.enableVillagerShops) {
			pm.registerEvents(new VillagerListener(this), this);
		}
		if (Settings.enableSignShops) {
			pm.registerEvents(new BlockListener(this), this);
		}
		if (Settings.blockVillagerSpawns) {
			pm.registerEvents(new BlockSpawnListener(), this);
		}
		if (Settings.protectChests) {
			pm.registerEvents(new ChestProtectListener(this), this);
		} else if (Settings.deleteShopkeeperOnBreakChest) {
			pm.registerEvents(new ChestBreakListener(this), this);
		}
		
		// start teleporter
		Bukkit.getScheduler().scheduleSyncRepeatingTask(this, new Runnable() {
			public void run() {
				List<Shopkeeper> readd = new ArrayList<Shopkeeper>();
				Iterator<Map.Entry<String, Shopkeeper>> iter = activeShopkeepers.entrySet().iterator();
				while (iter.hasNext()) {
					Shopkeeper shopkeeper = iter.next().getValue();
					boolean update = shopkeeper.teleport();
					if (update) {
						readd.add(shopkeeper);
						iter.remove();
					}
				}
				for (Shopkeeper shopkeeper : readd) {
					if (shopkeeper.isActive()) {
						activeShopkeepers.put(shopkeeper.getId(), shopkeeper);
					}
				}
			}
		}, 200, 200);
		
		// start verifier
		if (Settings.enableSpawnVerifier) {
			Bukkit.getScheduler().scheduleSyncRepeatingTask(this, new Runnable() {
				public void run() {
					int count = 0;
					for (String chunkStr : allShopkeepersByChunk.keySet()) {
						if (isChunkLoaded(chunkStr)) {
							List<Shopkeeper> shopkeepers = allShopkeepersByChunk.get(chunkStr);
							for (Shopkeeper shopkeeper : shopkeepers) {
								if (!shopkeeper.isActive()) {
									boolean spawned = shopkeeper.spawn();
									if (spawned) {
										activeShopkeepers.put(shopkeeper.getId(), shopkeeper);
										count++;
									} else {
										debug("Failed to spawn shopkeeper at " + shopkeeper.getPositionString());
									}
								}
							}
						}
					}
					if (count > 0) {
						debug("Spawn verifier: " + count + " shopkeepers respawned");
					}
				}
			}, 600, 1200);
		}
		
		// start saver
		if (!Settings.saveInstantly) {
			Bukkit.getScheduler().scheduleSyncRepeatingTask(this, new Runnable() {
				public void run() {
					if (dirty) {
						saveReal();
						dirty = false;
					}
				}
			}, 6000, 6000);
		}
		
	}
	
	@Override
	public void onDisable() {
		if (dirty) {
			saveReal();
			dirty = false;
		}
		
		for (String playerName : editing.keySet()) {
			Player player = Bukkit.getPlayerExact(playerName);
			if (player != null) {
				player.closeInventory();
			}
		}
		editing.clear();
		
		for (String playerName : purchasing.keySet()) {
			Player player = Bukkit.getPlayerExact(playerName);
			if (player != null) {
				player.closeInventory();
			}
		}
		purchasing.clear();
		
		for (Shopkeeper shopkeeper : activeShopkeepers.values()) {
			shopkeeper.remove();
		}
		activeShopkeepers.clear();
		allShopkeepersByChunk.clear();
		
		selectedShopType.clear();
		selectedChest.clear();
		
		HandlerList.unregisterAll((Plugin)this);		
		Bukkit.getScheduler().cancelTasks(this);
		
		plugin = null;
	}
	
	/**
	 * Reloads the plugin.
	 */
	public void reload() {
		onDisable();
		onEnable();
	}
	
	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (args.length > 0 && args[0].equalsIgnoreCase("reload") && sender.hasPermission("shopkeeper.reload")) {
			// reload command
			reload();
			sender.sendMessage(ChatColor.GREEN + "Shopkeepers plugin reloaded!");
			return true;
		} else if (args.length == 1 && args[0].equalsIgnoreCase("debug") && sender.isOp()) {
			// toggle debug command
			debug = !debug;
			sender.sendMessage(ChatColor.GREEN + "Debug mode " + (debug?"enabled":"disabled"));
			return true;
			
		} else if (args.length == 1 && args[0].equals("check") && sender.isOp()) {
			for (Shopkeeper shopkeeper : activeShopkeepers.values()) {
				if (shopkeeper.isActive()) {
					Location loc = shopkeeper.getActualLocation();
					sender.sendMessage("Shopkeeper at " + shopkeeper.getPositionString() + ": active (" + (loc != null ? loc.toString() : "maybe not?!?") + ")");
				} else {
					sender.sendMessage("Shopkeeper at " + shopkeeper.getPositionString() + ": INACTIVE!");
				}
			}
			return true;
			
		} else if (sender instanceof Player) {
			Player player = (Player)sender;
						
			// get the spawn location for the shopkeeper
			Block block = player.getTargetBlock(null, 10);
			if (block != null && block.getType() != Material.AIR) {
				if (Settings.createPlayerShopWithCommand && block.getType() == Material.CHEST) {
					// check if already a chest
					if (isChestProtected(null, block)) {
						return true;
					}
					// check for recently placed
					List<String> list = plugin.recentlyPlacedChests.get(player.getName());
					if (list == null || !list.contains(block.getWorld().getName() + "," + block.getX() + "," + block.getY() + "," + block.getZ())) {
						sendMessage(player, Settings.msgChestNotPlaced);
						return true;
					}
					// check for permission
					if (Settings.simulateRightClickOnCommand) {
						PlayerInteractEvent event = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, new ItemStack(Material.AIR), block, BlockFace.UP);
						Bukkit.getPluginManager().callEvent(event);
						if (event.isCancelled()) {
							return true;
						}
					}
					// create the player shopkeeper
					ShopkeeperType shopType = null;
					if (args == null || args.length == 0) {
						shopType = ShopkeeperType.next(player, null);
					} else {
						if ((args[0].toLowerCase().startsWith("norm") || args[0].toLowerCase().startsWith("sell"))) {
							shopType = ShopkeeperType.PLAYER_NORMAL;
						} else if (args[0].toLowerCase().startsWith("book")) {
							shopType = ShopkeeperType.PLAYER_BOOK;
						} else if (args[0].toLowerCase().startsWith("buy")) {
							shopType = ShopkeeperType.PLAYER_BUY;
						} else if (args[0].toLowerCase().startsWith("trad")) {
							shopType = ShopkeeperType.PLAYER_TRADE;
						}
						if (!shopType.hasPermission(player)) {
							shopType = null;
						}
					}
					if (shopType != null) {
						Shopkeeper shopkeeper = createNewPlayerShopkeeper(player, block, block.getLocation().add(0, 1.5, 0), shopType, new VillagerShop());
						if (shopkeeper != null) {
							sendCreatedMessage(player, shopType);
						}
					}
				} else if (player.hasPermission("shopkeeper.admin")) {
					// create the admin shopkeeper
					Shopkeeper shopkeeper = createNewAdminShopkeeper(block.getLocation().add(0, 1.5, 0), 0);
					if (shopkeeper != null) {
						sendMessage(player, Settings.msgAdminShopCreated);
					}
				}
			} else {
				sendMessage(player, Settings.msgShopCreateFail);
			}
			
			return true;
		} else {
			sender.sendMessage("You must be a player to create a shopkeeper.");
			sender.sendMessage("Use 'shopkeeper reload' to reload the plugin.");
			return true;
		}
	}
	
	/**
	 * Creates a new admin shopkeeper and spawns it into the world.
	 * @param location the block location the shopkeeper should spawn
	 * @param profession the shopkeeper's profession, a number from 0 to 5
	 * @return the shopkeeper created
	 */
	public Shopkeeper createNewAdminShopkeeper(Location location, int profession) {
		// make sure profession is valid
		if (profession < 0 || profession > 5) {
			profession = 0;
		}
		// create the shopkeeper (and spawn it)
		Shopkeeper shopkeeper = new AdminShopkeeper(location, new VillagerShop());
		shopkeeper.spawn();
		activeShopkeepers.put(shopkeeper.getId(), shopkeeper);
		addShopkeeper(shopkeeper);
		
		return shopkeeper;
	}

	/**
	 * Creates a new player-based shopkeeper and spawns it into the world.
	 * @param player the player who created the shopkeeper
	 * @param chest the backing chest for the shop
	 * @param location the block location the shopkeeper should spawn
	 * @param profession the shopkeeper's profession, a number from 0 to 5
	 * @param type the player shop type (0=normal, 1=book, 2=buy)
	 * @return the shopkeeper created
	 */
	public Shopkeeper createNewPlayerShopkeeper(Player player, Block chest, Location location, ShopkeeperType shopType, ShopObject shopObject) {
		if (shopType == null || shopObject == null) {
			return null;
		}
		
		// check worldguard
		if (Settings.enableWorldGuardRestrictions) {
			if (!WorldGuardHandler.canBuild(player, location)) {
				plugin.sendMessage(player, Settings.msgShopCreateFail);
				return null;
			}
		}
		
		// check towny
		if (Settings.enableTownyRestrictions) {
			if (!TownyHandler.isCommercialArea(location)) {
				plugin.sendMessage(player, Settings.msgShopCreateFail);
				return null;
			}
		}
		
		int maxShops = Settings.maxShopsPerPlayer;
		
		// call event
		CreatePlayerShopkeeperEvent event = new CreatePlayerShopkeeperEvent(player, chest, location, shopType, maxShops);
		Bukkit.getPluginManager().callEvent(event);
		if (event.isCancelled()) {
			return null;
		} else {
			location = event.getSpawnLocation();
			shopType = event.getType();
			maxShops = event.getMaxShopsForPlayer();
		}
		
		// count owned shops
		if (maxShops > 0) {
			int count = 0;
			for (List<Shopkeeper> list : allShopkeepersByChunk.values()) {
				for (Shopkeeper shopkeeper : list) {
					if (shopkeeper instanceof PlayerShopkeeper && ((PlayerShopkeeper)shopkeeper).getOwner().equalsIgnoreCase(player.getName())) {
						count++;
					}
				}
			}
			if (count >= maxShops) {
				sendMessage(player, Settings.msgTooManyShops);
				return null;
			}
		}
		
		// create the shopkeeper
		Shopkeeper shopkeeper = null;
		if (shopType == ShopkeeperType.PLAYER_NORMAL) {
			if (Settings.allowCustomQuantities) {
				shopkeeper = new CustomQuantityPlayerShopkeeper(player, chest, location, shopObject);
			} else {
				shopkeeper = new FixedQuantityPlayerShopkeeper(player, chest, location, shopObject);
			}
		} else if (shopType == ShopkeeperType.PLAYER_BOOK) {
			shopkeeper = new WrittenBookPlayerShopkeeper(player, chest, location, shopObject);
		} else if (shopType == ShopkeeperType.PLAYER_BUY) {
			shopkeeper = new BuyingPlayerShopkeeper(player, chest, location, shopObject);
		} else if (shopType == ShopkeeperType.PLAYER_TRADE) {
			shopkeeper = new TradingPlayerShopkeeper(player, chest, location, shopObject);
		}

		// spawn and save the shopkeeper
		if (shopkeeper != null) {
			shopkeeper.spawn();
			activeShopkeepers.put(shopkeeper.getId(), shopkeeper);
			addShopkeeper(shopkeeper);
		}
		
		return shopkeeper;
	}
	
	/**
	 * Gets the shopkeeper by the villager's entity id.
	 * @param entityId the entity id of the villager
	 * @return the Shopkeeper, or null if the enitity with the given id is not a shopkeeper
	 */
	public Shopkeeper getShopkeeperByEntityId(int entityId) {
		return activeShopkeepers.get(entityId);
	}
	
	/**
	 * Gets all shopkeepers from a given chunk. Returns null if there are no shopkeepers in that chunk.
	 * @param world the world
	 * @param x chunk x-coordinate
	 * @param z chunk z-coordinate
	 * @return a list of shopkeepers, or null if there are none
	 */
	public List<Shopkeeper> getShopkeepersInChunk(String world, int x, int z) {
		return allShopkeepersByChunk.get(world + "," + x + "," + z);
	}
	
	/**
	 * Checks if a given entity is a Shopkeeper.
	 * @param entity the entity to check
	 * @return whether the entity is a Shopkeeper
	 */
	public boolean isShopkeeper(Entity entity) {
		return activeShopkeepers.containsKey("entity" + entity.getEntityId());
	}	

	void addShopkeeper(Shopkeeper shopkeeper) {
		// add to chunk list
		List<Shopkeeper> list = allShopkeepersByChunk.get(shopkeeper.getChunk());
		if (list == null) {
			list = new ArrayList<Shopkeeper>();
			allShopkeepersByChunk.put(shopkeeper.getChunk(), list);
		}
		list.add(shopkeeper);
		// save all data
		save();
	}

	boolean sendCreatedMessage(Player player, ShopkeeperType shopType) {
		if (shopType == ShopkeeperType.PLAYER_NORMAL) {
			plugin.sendMessage(player, Settings.msgPlayerShopCreated);
			return true;
		} else if (shopType == ShopkeeperType.PLAYER_BOOK) {
			plugin.sendMessage(player, Settings.msgBookShopCreated);
			return true;
		} else if (shopType == ShopkeeperType.PLAYER_BUY) {
			plugin.sendMessage(player, Settings.msgBuyShopCreated);
			return true;
		} else if (shopType == ShopkeeperType.PLAYER_TRADE) {
			plugin.sendMessage(player, Settings.msgTradeShopCreated);
			return true;
		}
		return false;
	}
	
	void closeTradingForShopkeeper(final String id) {
		Bukkit.getScheduler().scheduleSyncDelayedTask(this, new Runnable() {
			public void run() {
				Iterator<String> editors = editing.keySet().iterator();
				while (editors.hasNext()) {
					String name = editors.next();
					if (editing.get(name).equals(id)) {
						Player player = Bukkit.getPlayerExact(name);
						if (player != null) {
							player.closeInventory();
						}
						editors.remove();
					}
				}
				Iterator<String> purchasers = purchasing.keySet().iterator();
				while (purchasers.hasNext()) {
					String name = purchasers.next();
					if (purchasing.get(name).equals(id)) {
						Player player = Bukkit.getPlayerExact(name);
						if (player != null) {
							player.closeInventory();
						}
						purchasers.remove();
					}
				}
			}
		}, 1);
	}
	
	void closeInventory(final HumanEntity player) {
		Bukkit.getScheduler().scheduleSyncDelayedTask(ShopkeepersPlugin.plugin, new Runnable() {
			public void run() {
				player.closeInventory();
			}
		}, 1);
	}
	
	boolean openTradeWindow(Shopkeeper shopkeeper, Player player) {
		return volatileCodeHandle.openTradeWindow(shopkeeper, player);
	}
	
	boolean isChestProtected(Player player, Block block) {
		for (Shopkeeper shopkeeper : activeShopkeepers.values()) {
			if (shopkeeper instanceof PlayerShopkeeper) {
				PlayerShopkeeper pshop = (PlayerShopkeeper)shopkeeper;
				if ((player == null || !pshop.getOwner().equalsIgnoreCase(player.getName())) && pshop.usesChest(block)) {
					return true;
				}
			}
		}
		return false;
	}
	
	Shopkeeper getShopkeeperOwnerOfChest(Block block) {
		for (Shopkeeper shopkeeper : activeShopkeepers.values()) {
			if (shopkeeper instanceof PlayerShopkeeper) {
				PlayerShopkeeper pshop = (PlayerShopkeeper)shopkeeper;
				if (pshop.usesChest(block)) {
					return pshop;
				}
			}
		}
		return null;
	}
	
	void sendMessage(Player player, String message) {
		message = ChatColor.translateAlternateColorCodes('&', message);
		String[] msgs = message.split("\n");
		for (String msg : msgs) {
			player.sendMessage(msg);
		}
	}
	
	void loadShopkeepersInChunk(Chunk chunk) {
		List<Shopkeeper> shopkeepers = allShopkeepersByChunk.get(chunk.getWorld().getName() + "," + chunk.getX() + "," + chunk.getZ());
		if (shopkeepers != null) {
			debug("Loading " + shopkeepers.size() + " shopkeepers in chunk " + chunk.getX() + "," + chunk.getZ());
			for (Shopkeeper shopkeeper : shopkeepers) {
				if (!shopkeeper.isActive() && shopkeeper.needsSpawned()) {
					boolean spawned = shopkeeper.spawn();
					if (spawned) {
						activeShopkeepers.put(shopkeeper.getId(), shopkeeper);
					} else {
						getLogger().warning("Failed to spawn shopkeeper at " + shopkeeper.getPositionString());
					}
				}
			}
			// save
			dirty = true;
			if (Settings.saveInstantly) {
				if (chunkLoadSaveTask < 0) {
					chunkLoadSaveTask = Bukkit.getScheduler().scheduleSyncDelayedTask(this, new Runnable() {
						public void run() {
							if (dirty) {
								saveReal();
								dirty = false;
							}
							chunkLoadSaveTask = -1;
						}
					}, 600);
				}
			}
		}
	}
	
	private boolean isChunkLoaded(String chunkStr) {
		String[] chunkData = chunkStr.split(",");
		World w = getServer().getWorld(chunkData[0]);
		if (w != null) {
			int x = Integer.parseInt(chunkData[1]);
			int z = Integer.parseInt(chunkData[2]);
			return w.isChunkLoaded(x, z);
		}
		return false;
	}
	
	private void load() {
		File file = new File(getDataFolder(), "save.yml");
		if (!file.exists()) return;
		
		YamlConfiguration config = new YamlConfiguration();
		try {
			config.load(file);
		} catch (Exception e) {
			e.printStackTrace();
			return;
		}
		
		Set<String> keys = config.getKeys(false);
		for (String key : keys) {
			ConfigurationSection section = config.getConfigurationSection(key);
			Shopkeeper shopkeeper = null;
			String type = section.getString("type", "");
			if (type.equals("book")) {
				shopkeeper = new WrittenBookPlayerShopkeeper(section);
			} else if (type.equals("buy")) {
				shopkeeper = new BuyingPlayerShopkeeper(section);
			} else if (type.equals("trade")) {
				shopkeeper = new TradingPlayerShopkeeper(section);
			} else if (type.equals("player") || section.contains("owner")) {
				if (Settings.allowCustomQuantities) {
					shopkeeper = new CustomQuantityPlayerShopkeeper(section);
				} else {
					shopkeeper = new FixedQuantityPlayerShopkeeper(section);
				}
			} else {
				shopkeeper = new AdminShopkeeper(section);
			}
			if (shopkeeper != null) {
				
				// add to shopkeepers by chunk
				List<Shopkeeper> list = allShopkeepersByChunk.get(shopkeeper.getChunk());
				if (list == null) {
					list = new ArrayList<Shopkeeper>();
					allShopkeepersByChunk.put(shopkeeper.getChunk(), list);
				}
				list.add(shopkeeper);
				
				// add to active shopkeepers if spawning not needed
				if (!shopkeeper.needsSpawned()) {
					activeShopkeepers.put(shopkeeper.getId(), shopkeeper);
				}
			}
		}
	}
	
	void save() {
		if (Settings.saveInstantly) {
			saveReal();
		} else {
			dirty = true;
		}
	}
	
	private void saveReal() {
		YamlConfiguration config = new YamlConfiguration();
		int counter = 0;
		for (List<Shopkeeper> shopkeepers : allShopkeepersByChunk.values()) {
			for (Shopkeeper shopkeeper : shopkeepers) {
				ConfigurationSection section = config.createSection(counter + "");
				shopkeeper.save(section);
				counter++;
			}
		}
		
		File file = new File(getDataFolder(), "save.yml");
		if (file.exists()) {
			file.delete();
		}
		try {
			config.save(file);
			debug("Saved shopkeeper data");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public static VolatileCodeHandle getVolatileCode() {
		return volatileCodeHandle;
	}
	
	public static void debug(String message) {
		if (plugin.debug) {
			plugin.getLogger().info(message);
		}
	}
	
	public static void warning(String message) {
		plugin.getLogger().warning(message);
	}

}
