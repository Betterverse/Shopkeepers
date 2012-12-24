package com.nisovin.shopkeepers;

import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Event.Result;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.material.Attachable;

import com.nisovin.shopkeepers.events.OpenTradeEvent;
import com.nisovin.shopkeepers.shopobjects.BlockShop;
import com.nisovin.shopkeepers.shoptypes.PlayerShopkeeper;

public class BlockListener implements Listener {

	final ShopkeepersPlugin plugin;
	
	public BlockListener(ShopkeepersPlugin plugin) {
		this.plugin = plugin;
	}
	
	@EventHandler(priority=EventPriority.HIGHEST)
	void onPlayerInteract(PlayerInteractEvent event) {
		Player player = event.getPlayer();
		Block block = event.getClickedBlock();
		
		// check for sign shop
		if (event.getAction() == Action.RIGHT_CLICK_BLOCK && (block.getType() == Material.SIGN_POST || block.getType() == Material.WALL_SIGN)) {
			Shopkeeper shopkeeper = plugin.activeShopkeepers.get("block" + block.getWorld().getName() + "," + block.getX() + "," + block.getY() + "," + block.getZ());
			if (shopkeeper != null) {
				ShopkeepersPlugin.debug("Player " + player.getName() + " is interacting with sign shopkeeper at " + block.getWorld().getName() + "," + block.getX() + "," + block.getY() + "," + block.getZ());
				if (event.useInteractedBlock() == Result.DENY) {
					ShopkeepersPlugin.debug("  Cancelled by another plugin");
				} else if (event.getPlayer().isSneaking()) {
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
				} else {
					ShopkeepersPlugin.debug("  Opening trade window...");
					OpenTradeEvent evt = new OpenTradeEvent(event.getPlayer(), shopkeeper);
					Bukkit.getPluginManager().callEvent(evt);
					if (evt.isCancelled()) {
						ShopkeepersPlugin.debug("  Trade cancelled by another plugin");
						event.setCancelled(true);
						return;
					}
					plugin.openTradeWindow(shopkeeper, event.getPlayer());
					plugin.purchasing.put(event.getPlayer().getName(), shopkeeper.getId());
					ShopkeepersPlugin.debug("  Trade window opened");
					return;
				}
			}
		}
		
		// check for player shop spawn
		if (Settings.signShopCreationItem > 0 && player.getGameMode() != GameMode.CREATIVE) {
			String playerName = player.getName();
			ItemStack inHand = player.getItemInHand();
			if (inHand != null && inHand.getTypeId() == Settings.signShopCreationItem) {
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
						event.setCancelled(true);
					} else if (plugin.selectedChest.containsKey(playerName) && validSignFace(event.getBlockFace())) {
						Block chest = plugin.selectedChest.get(playerName);
						if ((int)chest.getLocation().distance(block.getLocation()) > Settings.maxChestDistance) {
							plugin.sendMessage(player, Settings.msgChestTooFar);
						} else {
							// get shop type
							ShopkeeperType shopType = plugin.selectedShopType.get(playerName);
							if (shopType == null) shopType = ShopkeeperType.next(player, null);
							
							if (shopType != null) {
								// create player shopkeeper
								Block sign = event.getClickedBlock().getRelative(event.getBlockFace());
								if (sign.getType() == Material.AIR) {
									Shopkeeper shopkeeper = plugin.createNewPlayerShopkeeper(player, chest, sign.getLocation(), shopType, new BlockShop());
									if (shopkeeper != null) {										
										// set sign
										sign.setType(Material.WALL_SIGN);
										Sign signState = (Sign)sign.getState();
										((Attachable)signState.getData()).setFacingDirection(event.getBlockFace());
										signState.setLine(0, Settings.signShopFirstLine);
										signState.setLine(2, playerName);
										signState.update();
										event.setCancelled(true);
										
										// send message
										plugin.sendCreatedMessage(player, shopType);
									}
									
									// clear selection vars
									plugin.selectedShopType.remove(playerName);
									plugin.selectedChest.remove(playerName);
								}
							}
						}
					}
				}
			}
		}
	}
	
	@EventHandler
	void onBlockBreak(BlockBreakEvent event) {
		Block block = event.getBlock();
		if (block.getType() == Material.WALL_SIGN || block.getType() == Material.SIGN_POST) {
			if (plugin.activeShopkeepers.containsKey("block" + block.getWorld().getName() + "," + block.getX() + "," + block.getY() + "," + block.getZ())) {
				event.setCancelled(true);
			}			
		}
	}
	
	@EventHandler
	void onSignPlace(SignChangeEvent event) {
		Block block = event.getBlock();
		Shopkeeper shopkeeper = plugin.activeShopkeepers.get("block" + block.getWorld().getName() + "," + block.getX() + "," + block.getY() + "," + block.getZ());
		if (shopkeeper != null && shopkeeper instanceof PlayerShopkeeper) {
			event.setLine(0, Settings.signShopFirstLine);
			event.setLine(1, "");
			event.setLine(2, ((PlayerShopkeeper)shopkeeper).getOwner());
			event.setLine(3, "");
		}
	}
	
	private boolean validSignFace(BlockFace face) {
		return face == BlockFace.NORTH || face == BlockFace.SOUTH || face == BlockFace.EAST || face == BlockFace.WEST;
	}
}
