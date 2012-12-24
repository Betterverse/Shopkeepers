package com.nisovin.shopkeepers.volatilecode;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;

import com.nisovin.shopkeepers.Shopkeeper;

public interface VolatileCodeHandle {

	public boolean openTradeWindow(Shopkeeper shopkeeper, Player player);
	
	public void overwriteLivingEntityAI(LivingEntity entity);
	
	public void overwriteVillagerAI(LivingEntity villager);
	
	public void setVillagerProfession(Villager villager, int profession);
	
}
