package com.nisovin.shopkeepers.volatilecode;

import java.lang.reflect.Field;
import java.util.List;

import net.minecraft.server.v1_4_6.*;

import org.bukkit.craftbukkit.v1_4_6.entity.CraftLivingEntity;
import org.bukkit.craftbukkit.v1_4_6.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_4_6.entity.CraftVillager;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;

import com.nisovin.shopkeepers.Settings;
import com.nisovin.shopkeepers.Shopkeeper;

public class VolatileCode_1_4_6 implements VolatileCodeHandle {

	@SuppressWarnings("unchecked")
	@Override
	public boolean openTradeWindow(Shopkeeper shopkeeper, Player player) {

		try {
			EntityVillager villager = new EntityVillager(((CraftPlayer)player).getHandle().world, 0);
			
			Field recipeListField = EntityVillager.class.getDeclaredField(Settings.recipeListVar);
			recipeListField.setAccessible(true);
			MerchantRecipeList recipeList = (MerchantRecipeList)recipeListField.get(villager);
			if (recipeList == null) {
				recipeList = new MerchantRecipeList();
				recipeListField.set(villager, recipeList);
			}
			recipeList.clear();
			for (ItemStack[] recipe : shopkeeper.getRecipes()) {
				recipeList.add(createMerchantRecipe(recipe[0], recipe[1], recipe[2]));
			}
			
			villager.a(((CraftPlayer)player).getHandle());
			
			return true;
		} catch (Exception e) {
			return false;
		}
	}
	
	@SuppressWarnings("rawtypes")
	@Override
	public void overwriteLivingEntityAI(LivingEntity entity) {
		try {
			EntityLiving ev = ((CraftLivingEntity)entity).getHandle();
			
			Field goalsField = EntityLiving.class.getDeclaredField("goalSelector");
			goalsField.setAccessible(true);
			PathfinderGoalSelector goals = (PathfinderGoalSelector) goalsField.get(ev);
			
			Field listField = PathfinderGoalSelector.class.getDeclaredField("a");
			listField.setAccessible(true);
			List list = (List)listField.get(goals);
			list.clear();
			listField = PathfinderGoalSelector.class.getDeclaredField("b");
			listField.setAccessible(true);
			list = (List)listField.get(goals);
			list.clear();

			goals.a(0, new PathfinderGoalFloat(ev));
			goals.a(1, new PathfinderGoalLookAtPlayer(ev, EntityHuman.class, 12.0F, 1.0F));
		} catch (Exception e) {
			e.printStackTrace();
		}		
	}
	
	@SuppressWarnings("rawtypes")
	@Override
	public void overwriteVillagerAI(LivingEntity villager) {
		try {
			EntityVillager ev = ((CraftVillager)villager).getHandle();
			
			Field goalsField = EntityLiving.class.getDeclaredField("goalSelector");
			goalsField.setAccessible(true);
			PathfinderGoalSelector goals = (PathfinderGoalSelector) goalsField.get(ev);
			
			Field listField = PathfinderGoalSelector.class.getDeclaredField("a");
			listField.setAccessible(true);
			List list = (List)listField.get(goals);
			list.clear();
			listField = PathfinderGoalSelector.class.getDeclaredField("b");
			listField.setAccessible(true);
			list = (List)listField.get(goals);
			list.clear();

			goals.a(0, new PathfinderGoalFloat(ev));
			goals.a(1, new PathfinderGoalTradeWithPlayer(ev));
			goals.a(1, new PathfinderGoalLookAtTradingPlayer(ev));
			goals.a(2, new PathfinderGoalLookAtPlayer(ev, EntityHuman.class, 12.0F, 1.0F));
		} catch (Exception e) {
			e.printStackTrace();
		}		
	}

	@Override
	public void setVillagerProfession(Villager villager, int profession) {
		((CraftVillager)villager).getHandle().setProfession(profession);
	}
	
	private MerchantRecipe createMerchantRecipe(ItemStack item1, ItemStack item2, ItemStack item3) {
		MerchantRecipe recipe = new MerchantRecipe(convertItemStack(item1), convertItemStack(item2), convertItemStack(item3));
		try {
			Field maxUsesField = MerchantRecipe.class.getDeclaredField("maxUses");
			maxUsesField.setAccessible(true);
			maxUsesField.set(recipe, 10000);
		} catch (Exception e) {}
		return recipe;
	}
	
	private net.minecraft.server.v1_4_6.ItemStack convertItemStack(org.bukkit.inventory.ItemStack item) {
		if (item == null) return null;
		return org.bukkit.craftbukkit.v1_4_6.inventory.CraftItemStack.asNMSCopy(item);
	}

	
}
