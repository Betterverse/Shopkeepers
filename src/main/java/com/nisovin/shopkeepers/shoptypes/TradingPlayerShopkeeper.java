package com.nisovin.shopkeepers.shoptypes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import com.nisovin.shopkeepers.EditorClickResult;
import com.nisovin.shopkeepers.Settings;
import com.nisovin.shopkeepers.ShopkeeperType;
import com.nisovin.shopkeepers.shopobjects.ShopObject;
import com.nisovin.shopkeepers.util.ItemType;

public class TradingPlayerShopkeeper extends PlayerShopkeeper {

	private Map<ItemType, Cost> costs;
	
	public TradingPlayerShopkeeper(ConfigurationSection config) {
		super(config);
	}

	public TradingPlayerShopkeeper(Player owner, Block chest, Location location, ShopObject shopObject) {
		super(owner, chest, location, shopObject);
		this.costs = new HashMap<ItemType, Cost>();
	}
	
	@Override
	public void load(ConfigurationSection config) {
		super.load(config);
		costs = new HashMap<ItemType, Cost>();
		ConfigurationSection costsSection = config.getConfigurationSection("costs");
		if (costsSection != null) {
			for (String key : costsSection.getKeys(false)) {
				ConfigurationSection itemSection = costsSection.getConfigurationSection(key);
				ItemType type = new ItemType();
				Cost cost = new Cost();
				type.id = itemSection.getInt("id");
				type.data = (short)itemSection.getInt("data");
				if (itemSection.contains("enchants")) {
					type.enchants = itemSection.getString("enchants");
				}
				cost.amount = itemSection.getInt("amount");
				cost.item1Type = itemSection.getInt("item1type");
				cost.item1Data = (short)itemSection.getInt("item1data");
				cost.item1Amount = itemSection.getInt("item1amount");
				cost.item2Type = itemSection.getInt("item2type");
				cost.item2Data = (short)itemSection.getInt("item2data");
				cost.item2Amount = itemSection.getInt("item2amount");
				costs.put(type, cost);
			}
		}
	}
	
	@Override
	public void save(ConfigurationSection config) {
		super.save(config);
		config.set("type", "trade");
		ConfigurationSection costsSection = config.createSection("costs");
		int count = 0;
		for (ItemType type : costs.keySet()) {
			Cost cost = costs.get(type);
			ConfigurationSection itemSection = costsSection.createSection(count + "");
			itemSection.set("id", type.id);
			itemSection.set("data", type.data);
			if (type.enchants != null) {
				itemSection.set("enchants", type.enchants);
			}
			itemSection.set("amount", cost.amount);
			itemSection.set("item1type", cost.item1Type);
			itemSection.set("item1data", cost.item1Data);
			itemSection.set("item1amount", cost.item1Amount);
			itemSection.set("item2type", cost.item2Type);
			itemSection.set("item2data", cost.item2Data);
			itemSection.set("item2amount", cost.item2Amount);
			count++;
		}
	}

	@Override
	public ShopkeeperType getType() {
		return ShopkeeperType.PLAYER_TRADE;
	}

	@Override
	public List<ItemStack[]> getRecipes() {
		List<ItemStack[]> recipes = new ArrayList<ItemStack[]>();
		Map<ItemType, Integer> chestItems = getItemsFromChest();
		for (ItemType type : costs.keySet()) {
			if (chestItems.containsKey(type)) {
				Cost cost = costs.get(type);
				int chestAmt = chestItems.get(type);
				if (chestAmt >= cost.amount) {
					ItemStack[] recipe = new ItemStack[3];
					if (cost.item1Type > 0 && cost.item1Amount > 0) {
						recipe[0] = new ItemStack(cost.item1Type, cost.item1Amount, cost.item1Data);
					}
					if (cost.item2Type > 0 && cost.item2Amount > 0) {
						recipe[1] = new ItemStack(cost.item2Type, cost.item2Amount, cost.item2Data);
					}
					recipe[2] = type.getItemStack(cost.amount);
					recipes.add(recipe);
				}
			}
		}
		return recipes;
	}

	@Override
	protected boolean onPlayerEdit(Player player) {
		Inventory inv = Bukkit.createInventory(player, 27, Settings.editorTitle);
		
		// add the sale types
		Map<ItemType, Integer> typesFromChest = getItemsFromChest();
		int i = 0;
		for (ItemType type : typesFromChest.keySet()) {
			Cost cost = costs.get(type);
			if (cost != null) {
				inv.setItem(i, type.getItemStack(cost.amount));
				if (cost.item1Type > 0 && cost.item1Amount > 0) {
					inv.setItem(i + 9, new ItemStack(cost.item1Type, cost.item1Amount, cost.item1Data));
				}
				if (cost.item2Type > 0 && cost.item2Amount > 0) {
					inv.setItem(i + 18, new ItemStack(cost.item2Type, cost.item2Amount, cost.item2Data));
				}
			} else {
				inv.setItem(i, type.getItemStack(1));
			}
			i++;
			if (i > 8) break;
		}
		
		// add the special buttons
		setActionButtons(inv);
		
		player.openInventory(inv);
		
		return true;
	}

	@Override
	public EditorClickResult onEditorClick(InventoryClickEvent event) {
		int slot = event.getRawSlot();
		if (slot >= 0 && slot <= 7) {
			event.setCancelled(true);
			// handle changing sell stack size
			ItemStack item = event.getCurrentItem();
			if (item != null && item.getTypeId() != 0) {
				int amt = item.getAmount();
				if (event.isLeftClick()) {
					if (event.isShiftClick()) {
						amt += 10;
					} else {
						amt += 1;
					}
				} else if (event.isRightClick()) {
					if (event.isShiftClick()) {
						amt -= 10;
					} else {
						amt -= 1;
					}
				}
				if (amt <= 0) amt = 1;
				if (amt > item.getMaxStackSize()) amt = item.getMaxStackSize();
				item.setAmount(amt);
			}
			return EditorClickResult.NOTHING;
		} else if ((slot >= 9 && slot <= 16) || (slot >= 18 && slot <= 25)) {
			event.setCancelled(true);
			ItemStack cursor = event.getCursor();
			if (event.getCursor() != null && event.getCursor().getType() != Material.AIR) {
				// placing item
				event.getInventory().setItem(slot, new ItemStack(cursor.getTypeId(), cursor.getAmount(), cursor.getDurability()));
				event.setCursor(cursor);
			} else {
				// changing stack size
				ItemStack item = event.getCurrentItem();
				if (item != null && item.getTypeId() != 0) {
					int amt = item.getAmount();
					if (event.isLeftClick()) {
						if (event.isShiftClick()) {
							amt += 10;
						} else {
							amt += 1;
						}
					} else if (event.isRightClick()) {
						if (event.isShiftClick()) {
							amt -= 10;
						} else {
							amt -= 1;
						}
					}
					if (amt <= 0) {
						event.getInventory().setItem(slot, null);
					} else {
						if (amt > item.getMaxStackSize()) amt = item.getMaxStackSize();
						item.setAmount(amt);
					}
				}
			}
			return EditorClickResult.NOTHING;
		} else {
			return super.onEditorClick(event);
		}
	}

	@Override
	protected void saveEditor(Inventory inv) {
		for (int i = 0; i < 8; i++) {
			ItemStack item = inv.getItem(i);
			if (item != null && item.getType() != Material.AIR) {
				ItemStack cost1 = null, cost2 = null;
				ItemStack item1 = inv.getItem(i + 9);
				ItemStack item2 = inv.getItem(i + 18);
				if (item1 != null && item1.getType() != Material.AIR) {
					cost1 = item1;
					if (item2 != null && item2.getType() != Material.AIR) {
						cost2 = item2;
					}
				} else if (item2 != null && item2.getType() != Material.AIR) {
					cost1 = item2;
				}
				if (cost1 != null) {
					Cost cost = new Cost();
					cost.amount = item.getAmount();
					cost.item1Type = cost1.getTypeId();
					cost.item1Data = cost1.getDurability();
					cost.item1Amount = cost1.getAmount();
					if (cost2 != null) {
						cost.item2Type = cost2.getTypeId();
						cost.item2Data = cost2.getDurability();
						cost.item2Amount = cost2.getAmount();
					}
					costs.put(new ItemType(item), cost);
				} else {
					costs.remove(new ItemType(item));
				}
			}
		}
	}

	@Override
	public void onPlayerPurchaseClick(InventoryClickEvent event) {
		// prevent shift clicks
		if (event.isShiftClick() || event.isRightClick()) {
			event.setCancelled(true);
			return;
		}
		
		// get type and cost
		ItemStack item = event.getCurrentItem();
		ItemType type = new ItemType(item);
		if (!costs.containsKey(type)) {
			event.setCancelled(true);
			return;
		}
		Cost cost = costs.get(type);
		if (cost.amount != item.getAmount()) {
			event.setCancelled(true);
			return;
		}
		
		// get chest
		Block chest = Bukkit.getWorld(world).getBlockAt(chestx, chesty, chestz);
		if (chest.getType() != Material.CHEST) {
			event.setCancelled(true);
			return;
		}
		
		// remove item from chest
		Inventory inv = ((Chest)chest.getState()).getInventory();
		ItemStack[] contents = inv.getContents();
		boolean removed = removeFromInventory(item, contents);
		if (!removed) {
			event.setCancelled(true);
			return;
		}
		
		// add traded items to chest
		ItemStack cost1 = cost.getItem1();
		ItemStack cost2 = cost.getItem2();
		if (cost1 == null) {
			event.setCancelled(true);
			return;
		} else {
			boolean added = addToInventory(cost1, contents);
			if (!added) {
				event.setCancelled(true);
				return;
			}
		}
		if (cost2 != null) {
			boolean added = addToInventory(cost2, contents);
			if (!added) {
				event.setCancelled(true);
				return;
			}
		}

		// save chest contents
		inv.setContents(contents);
	}
	
	private Map<ItemType, Integer> getItemsFromChest() {
		Map<ItemType, Integer> map = new LinkedHashMap<ItemType, Integer>();
		Block chest = Bukkit.getWorld(world).getBlockAt(chestx, chesty, chestz);
		if (chest.getType() == Material.CHEST) {
			Inventory inv = ((Chest)chest.getState()).getInventory();
			ItemStack[] contents = inv.getContents();
			for (ItemStack item : contents) {
				if (item != null && item.getType() != Material.AIR && item.getTypeId() != Settings.currencyItem && item.getTypeId() != Settings.highCurrencyItem && item.getType() != Material.WRITTEN_BOOK) {
					ItemType si = new ItemType(item);
					if (map.containsKey(si)) {
						map.put(si, map.get(si) + item.getAmount());
					} else {
						map.put(si, item.getAmount());
					}
				}
			}
		}
		return map;
	}
	
	private class Cost {
		
		int amount;
		
		int item1Type;
		short item1Data;
		int item1Amount;
		
		int item2Type;
		short item2Data;
		int item2Amount;
		
		ItemStack getItem1() {
			if (item1Type > 0 && item1Amount > 0) {
				return new ItemStack(item1Type, item1Amount, item1Data);
			} else {
				return null;
			}
		}
		
		ItemStack getItem2() {
			if (item2Type > 0 && item2Amount > 0) {
				return new ItemStack(item2Type, item2Amount, item2Data);
			} else {
				return null;
			}
		}
		
	}

}
