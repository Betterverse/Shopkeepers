package com.nisovin.shopkeepers;

import java.util.List;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;

import com.nisovin.shopkeepers.shopobjects.ShopObject;

public abstract class Shopkeeper {

	protected ShopObject shopObject;
	protected String world;
	protected int x;
	protected int y;
	protected int z;

	public Shopkeeper(ConfigurationSection config) {
		load(config);
	}
	
	/**
	 * Creates a new shopkeeper and spawns it in the world. This should be used when a player is
	 * creating a new shopkeeper.
	 * @param location the location to spawn at
	 * @param prof the id of the profession
	 */
	public Shopkeeper(Location location, ShopObject obj) {
		world = location.getWorld().getName();
		x = location.getBlockX();
		y = location.getBlockY();
		z = location.getBlockZ();
		shopObject = obj;
		shopObject.setShopkeeper(this);
	}
	
	/**
	 * Loads a shopkeeper's saved data from a config section of a config file.
	 * @param config the config section
	 */
	public void load(ConfigurationSection config) {
		world = config.getString("world");
		x = config.getInt("x");
		y = config.getInt("y");
		z = config.getInt("z");
		shopObject = ShopObject.getShopObject(config);
		shopObject.setShopkeeper(this);
		shopObject.load(config);
	}
	
	/**
	 * Saves the shopkeeper's data to the specified configuration section.
	 * @param config the config section
	 */
	public void save(ConfigurationSection config) {
		config.set("world", world);
		config.set("x", x);
		config.set("y", y);
		config.set("z", z);
		shopObject.save(config);
	}
	
	/**
	 * Gets the type of this shopkeeper (admin, normal player, book player, or buying player).
	 * @return the shopkeeper type
	 */
	public abstract ShopkeeperType getType();
	
	public ShopObject getShopObject() {
		return shopObject;
	}
	
	public boolean needsSpawned() {
		return shopObject.needsSpawned();
	}
	
	/**
	 * Spawns the shopkeeper into the world at its spawn location. Also sets the
	 * trade recipes and overwrites the villager AI.
	 */
	public boolean spawn() {
		return shopObject.spawn(world, x, y, z);
	}
	
	/**
	 * Checks if the shopkeeper is active (is alive in the world).
	 * @return whether the shopkeeper is active
	 */
	public boolean isActive() {
		return shopObject.isActive();
	}
	
	/**
	 * Teleports this shopkeeper to its spawn location.
	 * @return whether to update this shopkeeper in the collection
	 */
	public boolean teleport() {
		return shopObject.check(world, x, y, z);
	}
	
	/**
	 * Removes this shopkeeper from the world.
	 */
	public void remove() {
		shopObject.despawn();
	}
	
	protected void delete() {
		shopObject.delete();
	}
	
	/**
	 * Gets a string identifying the chunk this shopkeeper spawns in, 
	 * in the format world,x,z.
	 * @return the chunk as a string
	 */
	public String getChunk() {
		return world + "," + (x >> 4) + "," + (z >> 4);
	}
	
	public String getPositionString() {
		return world + "," + x + "," + y + "," + z;
	}
	
	public Location getActualLocation() {
		return shopObject.getActualLocation();
	}
	
	/**
	 * Gets the name of the world this shopkeeper lives in.
	 * @return the world name
	 */
	public String getWorldName() {
		return world;
	}
	
	public int getX() {
		return x;
	}
	
	public int getY() {
		return y;
	}
	
	public int getZ() {
		return z;
	}
	
	/**
	 * Gets the villager entity for this shopkeeper. Can return null if the shopkeeper
	 * is not spawned in the world.
	 * @return the villager entity
	 */
	//public Villager getVillager() {
	//	return villager;
	//}
	
	/**
	 * Gets the shopkeeper's ID.
	 * @return the id, or 0 if the shopkeeper is not in the world
	 */
	public String getId() {
		return shopObject.getId();
	}

	/**
	 * Gets the shopkeeper's trade recipes. This will be a list of ItemStack[3],
	 * where the first two elemets of the ItemStack[] array are the cost, and the third
	 * element is the trade result (the item sold by the shopkeeper).
	 * @return the trade recipes of this shopkeeper
	 */
	public abstract List<ItemStack[]> getRecipes();

	/**
	 * Called when a player shift-right-clicks on the villager in an attempt to edit
	 * the shopkeeper information. This method should open the editing interface.
	 * @param player the player doing the edit
	 * @return whether the player is now editing (returns false if permission fails)
	 */
	public abstract boolean onEdit(Player player);
	
	/**
	 * Called when a player clicks on any slot in the editor window.
	 * @param event the click event
	 * @return how the main plugin should handle the click
	 */
	public abstract EditorClickResult onEditorClick(InventoryClickEvent event);	
	
	/**
	 * Called when a player closes the editor window.
	 * @param event the close event
	 */
	public abstract void onEditorClose(InventoryCloseEvent event);
	
	/**
	 * Called when a player purchases an item from a shopkeeper.
	 * @param event the click event of the purchase
	 */
	public abstract void onPurchaseClick(InventoryClickEvent event);
	
	protected void closeInventory(HumanEntity player) {
		ShopkeepersPlugin.plugin.closeInventory(player);
	}
	
	/*@SuppressWarnings("unchecked")
	protected void updateRecipes() {
		try {
			EntityVillager ev = ((CraftVillager)villager).getHandle();
			
			Field recipeListField = EntityVillager.class.getDeclaredField(Settings.recipeListVar);
			recipeListField.setAccessible(true);
			MerchantRecipeList recipeList = (MerchantRecipeList)recipeListField.get(ev);
			if (recipeList == null) {
				recipeList = new MerchantRecipeList();
				recipeListField.set(ev, recipeList);
			}
			recipeList.clear();
			for (ItemStack[] recipe : getRecipes()) {
				recipeList.add(ShopRecipe.factory(recipe[0], recipe[1], recipe[2]));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private void removeRecipes() {
		try {
			EntityVillager ev = ((CraftVillager)villager).getHandle();
			
			Field recipeListField = EntityVillager.class.getDeclaredField(Settings.recipeListVar);
			recipeListField.setAccessible(true);
			MerchantRecipeList recipeList = (MerchantRecipeList)recipeListField.get(ev);
			if (recipeList == null) {
				recipeList = new MerchantRecipeList();
				recipeListField.set(ev, recipeList);
			}
			recipeList.clear();
		} catch (Exception e) {
			e.printStackTrace();
		}		
	}
	
	}*/
}
