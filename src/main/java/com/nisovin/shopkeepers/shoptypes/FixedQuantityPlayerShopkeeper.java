package com.nisovin.shopkeepers.shoptypes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import com.nisovin.shopkeepers.EditorClickResult;
import com.nisovin.shopkeepers.Settings;
import com.nisovin.shopkeepers.ShopkeeperType;
import com.nisovin.shopkeepers.shopobjects.ShopObject;

public class FixedQuantityPlayerShopkeeper extends PlayerShopkeeper {

	protected HashMap<ItemTypeAndQuantity, Integer> costs;
	
	public FixedQuantityPlayerShopkeeper(ConfigurationSection config) {
		super(config);
	}

	public FixedQuantityPlayerShopkeeper(Player owner, Block chest, Location location, ShopObject shopObject) {
		super(owner, chest, location, shopObject);
		this.costs = new HashMap<ItemTypeAndQuantity, Integer>();
	}
	
	@Override
	public void load(ConfigurationSection config) {
		super.load(config);
		costs = new HashMap<ItemTypeAndQuantity, Integer>();
		ConfigurationSection costsSection = config.getConfigurationSection("costs");
		if (costsSection != null) {
			for (String key : costsSection.getKeys(false)) {
				ConfigurationSection itemSection = costsSection.getConfigurationSection(key);
				ItemTypeAndQuantity type = new ItemTypeAndQuantity();
				type.id = itemSection.getInt("id");
				type.data = (short)itemSection.getInt("data");
				type.amount = itemSection.getInt("amount");
				if (itemSection.contains("enchants")) {
					type.enchants = itemSection.getString("enchants");
				}
				int cost = itemSection.getInt("cost");
				costs.put(type, cost);
			}
		}
	}
	
	@Override
	public void save(ConfigurationSection config) {
		super.save(config);
		ConfigurationSection costsSection = config.createSection("costs");
		int count = 0;
		for (ItemTypeAndQuantity type : costs.keySet()) {
			ConfigurationSection itemSection = costsSection.createSection(count + "");
			itemSection.set("id", type.id);
			itemSection.set("data", type.data);
			itemSection.set("amount", type.amount);
			if (type.enchants != null) {
				itemSection.set("enchants", type.enchants);
			}
			itemSection.set("cost", costs.get(type));
			count++;
		}
	}
	
	@Override
	public ShopkeeperType getType() {
		return ShopkeeperType.PLAYER_NORMAL;
	}
	
	@Override
	public List<ItemStack[]> getRecipes() {
		List<ItemStack[]> recipes = new ArrayList<ItemStack[]>();
		List<ItemTypeAndQuantity> types = getTypesFromChest();
		for (ItemTypeAndQuantity type : types) {
			if (costs.containsKey(type)) {
				ItemStack[] recipe = new ItemStack[3];
				int cost = costs.get(type);
				setRecipeCost(recipe, cost);
				recipe[2] = type.getItemStack();
				recipes.add(recipe);
			}
		}
		return recipes;
	}

	@Override
	public boolean onPlayerEdit(Player player) {
		Inventory inv = Bukkit.createInventory(player, 27, Settings.editorTitle);
		// show types
		List<ItemTypeAndQuantity> types = getTypesFromChest();
		for (int i = 0; i < types.size() && i < 8; i++) {
			ItemTypeAndQuantity type = types.get(i);
			inv.setItem(i, type.getItemStack());
			int cost = 0;
			if (costs.containsKey(type)) {
				cost = costs.get(type);
			}
			setEditColumnCost(inv, i, cost);
		}
		// add the special buttons
		setActionButtons(inv);
		// show editing inventory
		player.openInventory(inv);
		return true;
	}
	
	@Override
	public EditorClickResult onEditorClick(InventoryClickEvent event) {
		event.setCancelled(true);
		return super.onEditorClick(event);
	}
	
	@Override
	protected void saveEditor(Inventory inv) {
		for (int i = 0; i < 8; i++) {
			ItemStack item = inv.getItem(i);
			if (item != null && item.getType() != Material.AIR) {
				int cost = getCostFromColumn(inv, i);
				if (cost > 0) {
					costs.put(new ItemTypeAndQuantity(item), cost);
				} else {
					costs.remove(new ItemTypeAndQuantity(item));
				}
			}
		}
	}
	
	@Override
	public void onPlayerPurchaseClick(final InventoryClickEvent event) {		
		// prevent shift clicks
		if (event.isShiftClick() || event.isRightClick()) {
			event.setCancelled(true);
			return;
		}
		
		// get type and cost
		ItemStack theItem = event.getCurrentItem();
		ItemTypeAndQuantity type = new ItemTypeAndQuantity(theItem);
		if (!costs.containsKey(type)) {
			event.setCancelled(true);
			return;
		}
		int cost = costs.get(type);
		
		// get chest
		Block chest = Bukkit.getWorld(world).getBlockAt(chestx, chesty, chestz);
		if (chest.getType() != Material.CHEST) {
			event.setCancelled(true);
			return;
		}
		
		// find item in chest
		Inventory inv = ((Chest)chest.getState()).getInventory();
		ItemStack[] contents = inv.getContents();
		for (int i = 0; i < contents.length; i++) {
			ItemStack item = contents[i];
			if (item != null && item.getTypeId() == type.id && item.getDurability() == type.data && item.getAmount() == type.amount && equalEnchantments(item, theItem)) {
				contents[i] = null;
				if (Settings.highCurrencyItem <= 0 || cost <= Settings.highCurrencyMinCost) {
					boolean added = addToInventory(new ItemStack(Settings.currencyItem, cost, Settings.currencyItemData), contents);
					if (!added) {
						event.setCancelled(true);
						return;
					}
				} else {
					int highCost = cost / Settings.highCurrencyValue;
					int lowCost = cost % Settings.highCurrencyValue;
					boolean added = false;
					if (highCost > 0) {
						added = addToInventory(new ItemStack(Settings.highCurrencyItem, highCost, Settings.highCurrencyItemData), contents);
						if (!added) {
							event.setCancelled(true);
							return;
						}
					}
					if (lowCost > 0) {
						added = addToInventory(new ItemStack(Settings.currencyItem, lowCost, Settings.currencyItemData), contents);
						if (!added) {
							event.setCancelled(true);
							return;
						}
					}
				}
				inv.setContents(contents);
				return;
			}
		}

		// item not found
		event.setCancelled(true);
		closeInventory(event.getWhoClicked());
	}
	
	private List<ItemTypeAndQuantity> getTypesFromChest() {
		List<ItemTypeAndQuantity> types = new ArrayList<ItemTypeAndQuantity>();
		Block chest = Bukkit.getWorld(world).getBlockAt(chestx, chesty, chestz);
		if (chest.getType() == Material.CHEST) {
			Inventory inv = ((Chest)chest.getState()).getInventory();
			ItemStack[] contents = inv.getContents();
			for (ItemStack item : contents) {
				if (item != null && item.getType() != Material.AIR && item.getTypeId() != Settings.currencyItem && item.getTypeId() != Settings.highCurrencyItem && item.getType() != Material.WRITTEN_BOOK) {
					ItemTypeAndQuantity type = new ItemTypeAndQuantity(item);
					if (!types.contains(type)) {
						types.add(type);
					}
				}
			}
		}
		return types;
	}
	
	private class ItemTypeAndQuantity {
		int id;
		short data;
		int amount;
		String enchants;
		
		ItemTypeAndQuantity() {
			
		}
		
		ItemTypeAndQuantity(ItemStack item) {
			id = item.getTypeId();
			data = item.getDurability();
			amount = item.getAmount();
			Map<Enchantment, Integer> enchantments = item.getEnchantments();
			if (enchantments != null && enchantments.size() > 0) {
				enchants = "";
				for (Enchantment e : enchantments.keySet()) {
					enchants += e.getId() + ":" + enchantments.get(e) + " ";
				}
				enchants = enchants.trim();
			}
		}
		
		ItemStack getItemStack() {
			ItemStack item = new ItemStack(id, amount, data);
			if (enchants != null) {
				String[] dataList = enchants.split(" ");
				for (String s : dataList) {
					String[] data = s.split(":");
					item.addUnsafeEnchantment(Enchantment.getById(Integer.parseInt(data[0])), Integer.parseInt(data[1]));
				}
			}
			return item;
		}
		
		@Override
		public int hashCode() {
			return (id + " " + data + " " + amount + (enchants != null ? " " + enchants : "")).hashCode();
		}
		
		@Override
		public boolean equals(Object o) {
			if (o instanceof ItemTypeAndQuantity) {
				ItemTypeAndQuantity i = (ItemTypeAndQuantity)o;
				boolean test = (i.id == this.id && i.data == this.data && i.amount == this.amount);
				if (!test) return false;
				if (i.enchants == null && this.enchants == null) return true;
				if (i.enchants == null || this.enchants == null) return false;
				return i.enchants.equals(this.enchants);
			}
			return false;
		}
	}
	
}
