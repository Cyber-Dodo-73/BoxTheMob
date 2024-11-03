package fr.cyberdodo.boxTheMob;

import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class BoxTheMob extends JavaPlugin implements Listener {

    private Connection connection;
    private Location storageLocation;

    private static final NamespacedKey USES_KEY = new NamespacedKey("boxthemob", "uses_remaining");
    private final NamespacedKey HAD_CUSTOM_NAME_KEY = new NamespacedKey(this, "had_custom_name");
    private final NamespacedKey CUSTOM_NAME_VISIBLE_KEY = new NamespacedKey(this, "custom_name_visible");

    private static final int MAX_USES = 6;
    private static final String TEMP_NAME = "CTM";

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);

        // Get the world's spawn location
        World world = Bukkit.getWorld("world");
        if (world == null) {
            // or use the default world
            world = Bukkit.getWorlds().get(0);
            if (world == null) {
                getLogger().warning("Default world not found. Plugin will not work.");
                getServer().getPluginManager().disablePlugin(this);
                return;
            }
        }

        // Use the world spawn location and adjust Y-coordinate
        storageLocation = world.getSpawnLocation().clone();
        storageLocation.setY(300); // Adjust Y-coordinate as needed

        // Force the storage chunk to stay loaded (optional)
        Chunk chunk = storageLocation.getChunk();
        chunk.load();
        chunk.setForceLoaded(true);

        try {
            connectToDatabase();
            createTables();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        createCustomBoxRecipe();
        createCaptureGunRecipe();
    }

    @Override
    public void onDisable() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void connectToDatabase() throws SQLException {
        connection = DriverManager.getConnection("jdbc:sqlite:boxthemob.db");
    }

    private void createTables() throws SQLException {
        String createMobsTableSQL = "CREATE TABLE IF NOT EXISTS mobs (" +
                "uuid TEXT PRIMARY KEY, " +
                "mob_type TEXT NOT NULL);";
        PreparedStatement stmt = connection.prepareStatement(createMobsTableSQL);
        stmt.executeUpdate();
        stmt.close();
    }

    private void saveMobToDatabase(UUID uuid, String mobType) throws SQLException {
        String insertSQL = "INSERT INTO mobs (uuid, mob_type) VALUES (?, ?)";
        PreparedStatement stmt = connection.prepareStatement(insertSQL);
        stmt.setString(1, uuid.toString());
        stmt.setString(2, mobType);
        stmt.executeUpdate();
        stmt.close();
    }

    private ResultSet getMobFromDatabase(UUID uuid) throws SQLException {
        String selectSQL = "SELECT * FROM mobs WHERE uuid = ?";
        PreparedStatement stmt = connection.prepareStatement(selectSQL);
        stmt.setString(1, uuid.toString());
        return stmt.executeQuery();
    }

    private void deleteMobFromDatabase(UUID uuid) throws SQLException {
        String deleteSQL = "DELETE FROM mobs WHERE uuid = ?";
        PreparedStatement stmt = connection.prepareStatement(deleteSQL);
        stmt.setString(1, uuid.toString());
        stmt.executeUpdate();
        stmt.close();
    }

    private void createCustomBoxRecipe() {
        ItemStack customBox = new ItemStack(Material.BEEHIVE, 1);
        ItemMeta meta = customBox.getItemMeta();
        meta.setDisplayName("§eMob Box");
        customBox.setItemMeta(meta);

        NamespacedKey key = new NamespacedKey(this, "mob_box");
        ShapedRecipe recipe = new ShapedRecipe(key, customBox);
        recipe.shape("ACA", "CEC", "ACA");
        recipe.setIngredient('A', Material.GOLDEN_APPLE);
        recipe.setIngredient('C', Material.CHEST);
        recipe.setIngredient('E', Material.EGG);

        Bukkit.addRecipe(recipe);
    }

    private void createCaptureGunRecipe() {
        ItemStack captureGun = new ItemStack(Material.WOODEN_HOE, 1);
        ItemMeta meta = captureGun.getItemMeta();
        meta.setDisplayName("§6Capture Gun");

        meta.getPersistentDataContainer().set(USES_KEY, PersistentDataType.INTEGER, MAX_USES);
        meta.setUnbreakable(false);
        meta.addEnchant(Enchantment.LUCK_OF_THE_SEA, 1, false);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        captureGun.setItemMeta(meta);



        NamespacedKey key = new NamespacedKey(this, "capture_gun");
        ShapedRecipe recipe = new ShapedRecipe(key, captureGun);
        recipe.shape("ESE", "DWD", "NSN");
        recipe.setIngredient('E', Material.ENDER_PEARL);
        recipe.setIngredient('S', Material.SHULKER_SHELL);
        recipe.setIngredient('D', Material.DIAMOND);
        recipe.setIngredient('N', Material.NETHERITE_SCRAP);
        recipe.setIngredient('W', Material.SCULK_SHRIEKER);

        Bukkit.addRecipe(recipe);
    }

    private void updateDurability(ItemStack item, int usesRemaining) {
        int maxDurability = Material.WOODEN_HOE.getMaxDurability();
        int durabilityValue = maxDurability - (maxDurability * usesRemaining / MAX_USES);

        item.setDurability((short) durabilityValue); // Définit la durabilité visuelle
    }

    @EventHandler
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        ItemStack result = event.getResult();
        if (result != null && result.getItemMeta() != null && "§6Capture Gun".equals(result.getItemMeta().getDisplayName())) {
            event.setResult(null); // Empêche la réparation dans l'enclume
        }
    }

    @EventHandler
    public void onPrepareItemCraft(PrepareItemCraftEvent event) {
        ItemStack result = event.getInventory().getResult();
        if (result != null && result.getItemMeta() != null && "§6Capture Gun".equals(result.getItemMeta().getDisplayName())) {
            boolean isRepairAttempt = false;
            for (ItemStack item : event.getInventory().getMatrix()) {
                if (item != null && item.getItemMeta() != null && "§6Capture Gun".equals(item.getItemMeta().getDisplayName()) && item.getDurability() > 0) {
                    isRepairAttempt = true;
                    break;
                }
            }
            if (isRepairAttempt) {
                event.getInventory().setResult(null);
            }
        }
    }

    @EventHandler
    public void onEnchantItem(EnchantItemEvent event) {
        ItemStack item = event.getItem();
        if (item.getItemMeta() != null && "§6Capture Gun".equals(item.getItemMeta().getDisplayName())) {
            event.setCancelled(true); // Annule l'enchantement
        }
    }

    @EventHandler
    public void onPlayerExpChange(PlayerExpChangeEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (item != null && item.getItemMeta() != null && "§6Capture Gun".equals(item.getItemMeta().getDisplayName())) {
            if (item.getItemMeta().hasEnchant(Enchantment.MENDING)) {
                event.setAmount(0); // Annule l’effet de mending en retirant l’XP
            }
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        // Check if the action is right-clicking a block
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            Player player = event.getPlayer();
            ItemStack item = player.getInventory().getItemInMainHand();

            if (item != null && item.getType() == Material.WOODEN_HOE && "§6Capture Gun".equals(item.getItemMeta().getDisplayName())) {
                // Prevent the default hoe action
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onPlayerUseGunOnMob(PlayerInteractEntityEvent event) throws SQLException {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (item != null && item.getType() == Material.WOODEN_HOE && "§6Capture Gun".equals(item.getItemMeta().getDisplayName())) {
            ItemMeta meta = item.getItemMeta();
            PersistentDataContainer data = meta.getPersistentDataContainer();

            // First, check if the player has a Mob Box
            if (player.getGameMode() != GameMode.CREATIVE) {
                boolean hasBox = false;
                for (ItemStack inventoryItem : player.getInventory().getContents()) {
                    if (inventoryItem != null && inventoryItem.getType() == Material.BEEHIVE && "§eMob Box".equals(inventoryItem.getItemMeta().getDisplayName())) {
                        inventoryItem.setAmount(inventoryItem.getAmount() - 1);
                        hasBox = true;
                        break;
                    }
                }

                if (!hasBox) {
                    player.sendMessage("§cVous avez besoin d'une Mob Box pour capturer ce mob !");
                    return;
                }
            }

            // Then, decrement the uses of the capture gun
            if (data.has(USES_KEY, PersistentDataType.INTEGER)) {
                int usesRemaining = data.get(USES_KEY, PersistentDataType.INTEGER);
                usesRemaining--;

                if (usesRemaining <= 0) {
                    player.getInventory().remove(item);
                    player.sendMessage("§cVotre Capture Gun est cassé !");
                    player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1, 1);
                    return; // Add return here to prevent further execution
                } else {
                    data.set(USES_KEY, PersistentDataType.INTEGER, usesRemaining);
                    item.setItemMeta(meta);
                    updateDurability(item, usesRemaining);
                }
            }

            // Proceed with capturing the mob
            Entity mob = event.getRightClicked();

            if (mob.getType() == EntityType.WARDEN || mob.getType() == EntityType.ENDER_DRAGON || mob.getType() == EntityType.WITHER) {
                player.sendMessage("§cCe mob ne peut pas être capturé !");
                return;
            }

            if (!mob.getPassengers().isEmpty()) {
                player.sendMessage("§cVous ne pouvez pas capturer un mob avec des passagers.");
                return;
            }

            // Rest of your existing code to handle the mob capture...
            UUID mobUUID = mob.getUniqueId();

            if (mob.getCustomName() == null || mob.getCustomName().equals("")) {
                mob.setCustomName(TEMP_NAME);
                mob.setCustomNameVisible(false);
            } else {
                // Store that the mob had a custom name and its visibility
                if (mob instanceof LivingEntity) {
                    PersistentDataContainer data2 = ((LivingEntity) mob).getPersistentDataContainer();
                    data2.set(HAD_CUSTOM_NAME_KEY, PersistentDataType.BYTE, (byte) 1);
                    data2.set(CUSTOM_NAME_VISIBLE_KEY, PersistentDataType.BYTE, mob.isCustomNameVisible() ? (byte) 1 : (byte) 0);
                }
            }

            mob.teleport(storageLocation);
            mob.setInvulnerable(true);
            mob.setSilent(true);
            mob.setGravity(false);
            mob.addScoreboardTag("captured_mob");

            // Ensure the storage chunk is loaded
            Chunk chunk = storageLocation.getChunk();
            chunk.load();
            chunk.setForceLoaded(true);

            if (mob instanceof LivingEntity) {
                LivingEntity livingEntity = (LivingEntity) mob;
                livingEntity.setAI(false);
                livingEntity.setRemoveWhenFarAway(false); // Prevents mob from being removed when far away
                livingEntity.setPersistent(true); // Prevents mob from naturally despawning
            }

            saveMobToDatabase(mobUUID, mob.getType().name());
            player.sendMessage("§aMob capturé avec succès dans la boîte !");

            // Create the Mob Box item
            ItemStack mobBox = new ItemStack(Material.BEEHIVE);
            ItemMeta meta2 = mobBox.getItemMeta();
            meta2.setDisplayName("§eMob Box contenant " + mob.getType().name());

            meta2.addEnchant(Enchantment.LUCK_OF_THE_SEA, 1, true);
            meta2.addItemFlags(ItemFlag.HIDE_ENCHANTS);

            List<String> lore = new ArrayList<>();
            if (mob.getCustomName() != null && !mob.getCustomName().equals(TEMP_NAME)) {
                lore.add("§6Nom : §f" + mob.getCustomName());
            }
            if (mob instanceof Villager) {
                Villager.Profession profession = ((Villager) mob).getProfession();
                lore.add("§bProfession : §f" + profession.name());
            }
            lore.add("§aCapturé par : §f" + player.getName());
            lore.add("§7§oUUID:" + mobUUID);

            meta2.setLore(lore);
            mobBox.setItemMeta(meta2);
            player.getInventory().addItem(mobBox);
        }
    }


    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) throws SQLException {
        Player player = event.getPlayer();
        ItemStack item = event.getItemInHand();

        if (item.getType() == Material.BEEHIVE && item.getItemMeta().getDisplayName().startsWith("§eMob Box")) {
            ItemMeta meta = item.getItemMeta();

            String uuidString = null;
            List<String> lore = meta.getLore();
            if (lore != null) {
                for (String line : lore) {
                    if (line.contains("UUID:")) {
                        uuidString = line.substring(line.indexOf("UUID:") + "UUID:".length()).trim();
                        break;
                    }
                }
            }

            if (uuidString != null) {
                UUID mobUUID = UUID.fromString(uuidString);

                ResultSet rsMob = getMobFromDatabase(mobUUID);
                if (rsMob.next()) {
                    String mobType = rsMob.getString("mob_type");

                    Entity mob = Bukkit.getEntity(mobUUID);
                    if (mob != null && mobType.equals(mob.getType().name()) && mob.getScoreboardTags().contains("captured_mob")) {
                        mob.teleport(event.getBlock().getLocation().add(0.5, 0, 0.5));
                        mob.setInvulnerable(false);
                        mob.setSilent(false);
                        mob.setGravity(true);

                        if (TEMP_NAME.equals(mob.getCustomName())) {
                            mob.setCustomName(null);
                            mob.setCustomNameVisible(false);
                        } else {
                            if (mob instanceof LivingEntity) {
                                PersistentDataContainer data = ((LivingEntity) mob).getPersistentDataContainer();
                                Byte hadCustomName = data.get(HAD_CUSTOM_NAME_KEY, PersistentDataType.BYTE);
                                Byte customNameVisible = data.get(CUSTOM_NAME_VISIBLE_KEY, PersistentDataType.BYTE);
                                if (hadCustomName != null && hadCustomName == 1) {
                                    mob.setCustomNameVisible(customNameVisible != null && customNameVisible == 1);
                                } else {
                                    mob.setCustomNameVisible(false);
                                }
                                data.remove(HAD_CUSTOM_NAME_KEY);
                                data.remove(CUSTOM_NAME_VISIBLE_KEY);
                            }
                        }

                        mob.removeScoreboardTag("captured_mob");

                        if (mob instanceof LivingEntity) {
                            LivingEntity livingMob = (LivingEntity) mob;
                            livingMob.setAI(true);
                            livingMob.removePotionEffect(PotionEffectType.INVISIBILITY);
                        }

                        player.sendMessage("§aMob relâché avec succès !");
                        player.playSound(event.getBlock().getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1, 1);

                        deleteMobFromDatabase(mobUUID);

                        event.getBlock().setType(Material.AIR);
                        player.getInventory().remove(item);
                    } else {
                        player.sendMessage("§cErreur: Impossible de retrouver le mob capturé. Vérifiez s'il a été déchargé.");
                    }
                }
                rsMob.close();
            } else {
                player.sendMessage("§cErreur: Aucun mob capturé trouvé dans cette boîte.");
                event.setCancelled(true);
            }
        }
    }
}
