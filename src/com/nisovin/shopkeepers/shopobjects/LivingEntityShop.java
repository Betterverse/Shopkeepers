package com.nisovin.shopkeepers.shopobjects;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;

import com.nisovin.shopkeepers.ShopkeepersPlugin;

public abstract class LivingEntityShop extends ShopObject {
	
	protected LivingEntity entity;
	private String uuid;
	private int respawnAttempts = 0;
	
	@Override
	public void load(ConfigurationSection config) {
		if (config.contains("uuid")) {
			uuid = config.getString("uuid");
		}
	}

	@Override
	public void save(ConfigurationSection config) {
		if (entity != null) {
			config.set("uuid", entity.getUniqueId().toString());
		}
	}
	
	protected abstract EntityType getEntityType();
	
	@Override
	public boolean needsSpawned() {
		return true;
	}
	
	@Override
	public boolean spawn(String world, int x, int y, int z) {
		// prepare location
		World w = Bukkit.getWorld(world);
		Location loc = new Location(w, x + .5, y + .5, z + .5);
		// find old villager
		if (uuid != null && !uuid.isEmpty()) {
			Entity[] entities = loc.getChunk().getEntities();
			for (Entity e : entities) {
				if (e.getType() == getEntityType() && e.getUniqueId().toString().equalsIgnoreCase(uuid) && e.isValid()) {
					entity = (LivingEntity)e;
					entity.setHealth(entity.getMaxHealth());
					entity.teleport(loc);
					break;
				}
			}
		}
		// spawn villager
		if (entity == null || !entity.isValid()) {
			entity = (LivingEntity)w.spawnEntity(loc, getEntityType());
			uuid = entity.getUniqueId().toString();
		}
		if (entity != null && entity.isValid()) {
			overwriteAI();
			return true;
		} else {
			if (entity != null) {
				entity.remove();
				entity = null;
			}
			return false;
		}
	}

	@Override
	public boolean isActive() {
		return entity != null && !entity.isDead();
	}

	@Override
	public String getId() {
		if (entity != null) {
			return "entity" + entity.getEntityId();
		}
		return null;
	}
	
	@Override
	public Location getActualLocation() {
		if (entity == null || !entity.isValid()) {
			return null;
		} else {
			return entity.getLocation();
		}
	}
	
	@Override
	public boolean check(String world, int x, int y, int z) {
		if (entity == null || !entity.isValid()) {
			boolean spawned = spawn(world, x, y, z);
			ShopkeepersPlugin.warning("Shopkeeper (" + world + "," + x + "," + y + "," + z + ") missing, respawn " + (spawned?"successful":"failed"));
			if (spawned) {
				respawnAttempts = 0;
				return true;
			} else {
				return (++respawnAttempts > 5);
			}
		} else {
			World w = Bukkit.getWorld(world);
			Location loc = new Location(w, x + .5, y, z + .5, entity.getLocation().getYaw(), entity.getLocation().getPitch());
			if (entity.getLocation().distanceSquared(loc) > .4) {
				entity.teleport(loc);
				overwriteAI();
				ShopkeepersPlugin.debug("Shopkeeper (" + world + "," + x + "," + y + "," + z + ") out of place, teleported back");
			}
			return false;
		}
	}

	@Override
	public void despawn() {
		if (entity != null) {
			entity.remove();
			entity.setHealth(0);
			entity = null;
		}
	}
	
	@Override
	public void delete() {
		despawn();
	}
	
	protected void overwriteAI() {
		ShopkeepersPlugin.getVolatileCode().overwriteLivingEntityAI(entity);
	}
	
}
