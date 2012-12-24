package com.nisovin.shopkeepers;

import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.Event.Result;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import com.nisovin.shopkeepers.events.OpenTradeEvent;
import com.nisovin.shopkeepers.shopobjects.VillagerShop;

public class VillagerListener implements Listener {

	final ShopkeepersPlugin plugin;
	
	public VillagerListener(ShopkeepersPlugin plugin) {
		this.plugin = plugin;
	}
	
	@EventHandler
	void onEntityInteract(PlayerInteractEntityEvent event) {
		if (event.getRightClicked() instanceof Villager) {
			Villager villager = (Villager)event.getRightClicked();
			ShopkeepersPlugin.debug("Player " + event.getPlayer().getName() + " is interacting with villager at " + villager.getLocation());
			Shopkeeper shopkeeper = plugin.activeShopkeepers.get("entity" + villager.getEntityId());
			if (event.isCancelled()) {
				ShopkeepersPlugin.debug("  Cancelled by another plugin");
			} else if (shopkeeper != null && event.getPlayer().isSneaking()) {
				// modifying a shopkeeper
				ShopkeepersPlugin.debug("  Opening editor window...");
				event.setCancelled(true);
				boolean isEditing = shopkeeper.onEdit(event.getPlayer());
				if (isEditing) {
					ShopkeepersPlugin.debug("  Editor window opened");
					plugin.editing.put(event.getPlayer().getName(), shopkeeper.getId());
				} else {
					ShopkeepersPlugin.debug("  Editor window NOT opened");
				}
			} else if (shopkeeper != null) {
				// trading with shopkeeper
				ShopkeepersPlugin.debug("  Opening trade window...");
				OpenTradeEvent evt = new OpenTradeEvent(event.getPlayer(), shopkeeper);
				Bukkit.getPluginManager().callEvent(evt);
				if (evt.isCancelled()) {
					ShopkeepersPlugin.debug("  Trade cancelled by another plugin");
					event.setCancelled(true);
					return;
				}
				// open trade window
				event.setCancelled(true);
				plugin.openTradeWindow(shopkeeper, event.getPlayer());
				plugin.purchasing.put(event.getPlayer().getName(), shopkeeper.getId());
				ShopkeepersPlugin.debug("  Trade window opened");
			} else if (Settings.disableOtherVillagers && shopkeeper == null) {
				// don't allow trading with other villagers
				ShopkeepersPlugin.debug("  Non-shopkeeper, trade prevented");
				event.setCancelled(true);
			} else if (shopkeeper == null) {
				ShopkeepersPlugin.debug("  Non-shopkeeper");
			}
		}
	}
	
	@EventHandler(priority=EventPriority.HIGHEST)
	void onPlayerInteract(PlayerInteractEvent event) {
		Player player = event.getPlayer();
		
		// check for player shop spawn
		if (Settings.createPlayerShopWithEgg && player.getGameMode() != GameMode.CREATIVE) {
			String playerName = player.getName();
			ItemStack inHand = player.getItemInHand();
			if (inHand != null && inHand.getType() == Material.MONSTER_EGG && inHand.getDurability() == 120) {
				if (event.getAction() == Action.RIGHT_CLICK_AIR) {
					// cycle shop options
					ShopkeeperType shopType = plugin.selectedShopType.get(playerName);
					shopType = ShopkeeperType.next(player, shopType);
					if (shopType != null) {
						plugin.selectedShopType.put(playerName, shopType);
						if (shopType == ShopkeeperType.PLAYER_NORMAL) {
							plugin.sendMessage(player, Settings.msgSelectedNormalShop);
						} else if (shopType == ShopkeeperType.PLAYER_BOOK) {
							plugin.sendMessage(player, Settings.msgSelectedBookShop);
						} else if (shopType == ShopkeeperType.PLAYER_BUY) {
							plugin.sendMessage(player, Settings.msgSelectedBuyShop);
						} else if (shopType == ShopkeeperType.PLAYER_TRADE) {
							plugin.sendMessage(player, Settings.msgSelectedTradeShop);
						}
					}
				} else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
					Block block = event.getClickedBlock();
										
					if (block.getType() == Material.CHEST && (!plugin.selectedChest.containsKey(playerName) || !plugin.selectedChest.get(playerName).equals(block))) {
						if (event.useInteractedBlock() != Result.DENY) {
							// check if it's recently placed
							List<String> list = plugin.recentlyPlacedChests.get(playerName);
							if (list == null || !list.contains(block.getWorld().getName() + "," + block.getX() + "," + block.getY() + "," + block.getZ())) {
								// chest not recently placed
								plugin.sendMessage(player, Settings.msgChestNotPlaced);
							} else {
								// select chest
								plugin.selectedChest.put(playerName, event.getClickedBlock());
								plugin.sendMessage(player, Settings.msgSelectedChest);
							}
						} else {
							ShopkeepersPlugin.debug("Right-click on chest prevented, player " + player.getName() + " at " + block.getLocation().toString());
						}
					} else {
						Block chest = plugin.selectedChest.get(playerName);
						if (chest == null) {
							plugin.sendMessage(player, Settings.msgMustSelectChest);
						} else if ((int)chest.getLocation().distance(block.getLocation()) > Settings.maxChestDistance) {
							plugin.sendMessage(player, Settings.msgChestTooFar);
						} else {
							// get shop type
							ShopkeeperType shopType = plugin.selectedShopType.get(playerName);
							if (shopType == null) shopType = ShopkeeperType.next(player, null);
							
							if (shopType != null) {
								// create player shopkeeper
								Shopkeeper shopkeeper = plugin.createNewPlayerShopkeeper(player, chest, block.getLocation().add(0, 1.5, 0), shopType, new VillagerShop());
								if (shopkeeper != null) {
									// send message
									plugin.sendCreatedMessage(player, shopType);
									// remove egg
									inHand.setAmount(inHand.getAmount() - 1);
									if (inHand.getAmount() > 0) {
										player.setItemInHand(inHand);
									} else {
										player.setItemInHand(null);
									}
								}
							}
							
							// clear selection vars
							plugin.selectedShopType.remove(playerName);
							plugin.selectedChest.remove(playerName);
						}
					}
				}
				event.setCancelled(true);
			}
		}
	}
	
	@EventHandler
	void onEntityDamage(EntityDamageEvent event) {
		// don't allow damaging shopkeepers!
		if (plugin.activeShopkeepers.containsKey("entity" + event.getEntity().getEntityId())) {
			event.setCancelled(true);
			if (event instanceof EntityDamageByEntityEvent) {
				EntityDamageByEntityEvent evt = (EntityDamageByEntityEvent)event;
				if (evt.getDamager() instanceof Monster) {
					evt.getDamager().remove();
				}
			}
		}
	}
	
	@EventHandler
	void onTarget(EntityTargetEvent event) {
		Entity target = event.getTarget();
		if (target != null && target.getType() == EntityType.VILLAGER && plugin.activeShopkeepers.containsKey("entity" + target.getEntityId())) {
			event.setCancelled(true);
		}
	}
	
}
