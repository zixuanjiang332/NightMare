package jdd.nightMare.InitialListener;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import com.destroystokyo.paper.event.entity.ThrownEggHatchEvent;
import com.destroystokyo.paper.event.player.PlayerPickupExperienceEvent;
import com.destroystokyo.paper.event.player.PlayerSetSpawnEvent;
import io.papermc.paper.event.player.AsyncChatEvent;
import io.papermc.paper.event.player.PlayerPickItemEvent;
import jdd.nightMare.Game.*;
import jdd.nightMare.GameConfig.KillMessagesConfig;
import jdd.nightMare.GameConfig.PlayerConfig;
import jdd.nightMare.GameConfig.PluginConfigManager;
import jdd.nightMare.Message;
import jdd.nightMare.PlayerUtils;
import jdd.nightMare.NightMare;
import jdd.nightMare.Shop.ShopCategory;
import jdd.nightMare.Shop.ShopGUI;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.*;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.damage.DamageType;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.server.BroadcastMessageEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.Vector;
import java.util.*;
import static org.bukkit.Bukkit.getServer;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;

import javax.naming.Name;

public class GameListeners implements Listener {
    private final GameManager gameManager;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private final Map<UUID, List<ItemStack>> savedItems = new HashMap<>();
    public GameListeners(GameManager gameManager) {
        this.gameManager = gameManager;
    }
    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        if (gameManager.hasActiveSession(event.getPlayer()) && event.getTo().getWorld() != gameManager.getPlayerSession(event.getPlayer()).getGame().getMap().getBukkitWorld()) {
            event.setCancelled(true);
            Message.send(event.getPlayer(), "<red>不能传送到游戏之外的世界");
        }
        Player player = event.getPlayer();
        String targetWorld = event.getTo().getWorld().getName();
        if (targetWorld.equals("world_nether"))event.setCancelled(true);
        if (targetWorld.equals("world")) {
            if (player.getInventory().contains(Material.RED_BED)) {
                player.getInventory().remove(Material.RED_BED);
            }
            ItemStack compass = new ItemStack(Material.BLACK_BED);
            ItemMeta meta = compass.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§b§l起床战争");
                compass.setItemMeta(meta);
            }
            if (!player.getInventory().contains(Material.BLACK_BED)) {
                player.getInventory().addItem(compass);
            }
        }
    }

    @EventHandler
    public void onPlayerLeave(PlayerQuitEvent event) {
        event.setQuitMessage(null);
        Player player = event.getPlayer();
        if(!gameManager.hasActiveSession(player))return;
        Game game = gameManager.getPlayerSession(player).getGame();
        if (gameManager.hasActiveSession(player) && gameManager.getPlayerSession(player).getGame().isAlive(player)) {
            gameManager.getPlayerSession(player).getGame().playerLeave(player);
            game.updateAllScoreboards();
        }
    }

    @EventHandler
    public void onEggCrack(ThrownEggHatchEvent event) {
        if (event.getEgg().getWorld().getPersistentDataContainer().has(new NamespacedKey(NightMare.getInstance(), "skywars_map")))
            event.setHatching(false);
    }

    @EventHandler
    public void onDrinkPotion(PlayerItemConsumeEvent event) {
        if (gameManager.hasActiveSession(event.getPlayer())) {
            if (event.getItem().getType() == Material.POTION) {
                event.setReplacement(new ItemStack(Material.AIR));
            }
        }
    }
    @EventHandler
    public void onVillagerClick(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();
        if (entity.getType() == EntityType.VILLAGER) {
            event.setCancelled(true);
            Player player = event.getPlayer();
            Game game = gameManager.getPlayerSession(player).getGame();
            gameManager.getShopGUI().open(player, ShopCategory.WEAPONS);
        }
    }
    @EventHandler
    public void onShopClick(InventoryClickEvent event) {
        String title = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(event.getView().title());
        if (!title.contains("商店")) return;
        Inventory clickedInventory = event.getClickedInventory();
        if (clickedInventory == null) return;
        ItemStack clicked = event.getCurrentItem();
        Player player = (Player) event.getWhoClicked();
        if (clickedInventory.equals(event.getView().getTopInventory())) {
            event.setCancelled(true);
            if (clicked == null || clicked.getType() == Material.AIR) return;
            if (event.getRawSlot() >= 0 && event.getRawSlot() <= 8) {
                for (ShopCategory cat : ShopCategory.values()) {
                    if (clicked.getType() == cat.getIcon()) {
                        gameManager.getShopGUI().open(player, cat);
                        return;
                    }
                }
            }
            gameManager.getShopGUI().handlePurchaseLogic(player, clicked, event.getClick().isShiftClick());

        } else {
            if (event.getClick().isShiftClick()) {
                event.setCancelled(true);
            }
        }
    }
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (!gameManager.hasActiveSession(player)) return;
        Game game = gameManager.getPlayerSession( player).getGame();
        GameTeam team = gameManager.getPlayerSession(player).getGameTeam();
        boolean bedAlive = team.isBedAlive();
        List<ItemStack> drops = event.getDrops();
        List<ItemStack> toSave = new ArrayList<>();
        Iterator<ItemStack> iterator = drops.iterator();
        while (iterator.hasNext()) {
            ItemStack item = iterator.next();
            if (item == null) continue;
            Material type = item.getType();
            boolean isResource = (type == Material.GOLD_INGOT) || (!bedAlive && type == Material.IRON_INGOT);
            if (!isResource) {
                toSave.add(item.clone());
                iterator.remove();
            }
        }
        if (!toSave.isEmpty()) {
            savedItems.put(player.getUniqueId(), toSave);
        }
        event.deathMessage(Component.empty());
        Bukkit.getScheduler().runTaskLater(NightMare.getInstance(), () -> {
            if (player.isOnline()) player.spigot().respawn();
        }, 1L);
    }
    @EventHandler
    public void onSelfRescue(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.BLAZE_ROD) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        PlayerSession session = gameManager.getPlayerSession(player);
        int coolDown = session.getSpecialItemCooldowns().get("自救平台");
        if (coolDown > 0){
            player.sendMessage("§c[NightMare] 自救平台冷却中！冷却剩余:§f"+coolDown+"s");
            return;
        }
        item.setAmount(item.getAmount() - 1);
        Location center = player.getLocation().clone().subtract(0, 1, 0);
        List<Block> placedBlocks = new ArrayList<>();
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                Block target = center.clone().add(x, 0, z).getBlock();
                if (Math.abs(x) <= 1 || Math.abs(z) <= 1) {
                    if (target.getType() == Material.AIR || target.getType() == Material.CAVE_AIR || target.isLiquid()) {
                        target.setType(Material.SLIME_BLOCK); 
                        placedBlocks.add(target);
                    }
                }
            }
        }
        // 播放音效和粒子效果，增强反馈感
        player.playSound(player.getLocation(), Sound.BLOCK_GLASS_PLACE, 1.0f, 1.2f);
        player.spawnParticle(Particle.CLOUD, player.getLocation(), 15, 0.5, 0.5, 0.5, 0.05);
        player.sendMessage("§b[自救] §f平台已生成！它将在 5 秒后消失。");
        session.getSpecialItemCooldowns().put("自救平台", 30);
        Bukkit.getScheduler().runTaskLater(NightMare.getInstance(), () -> {
            for (Block block : placedBlocks) {
                if (block.getType() == Material.SLIME_BLOCK) {
                    block.setType(Material.AIR);
                }
            }
        }, 20 * 5L);
    }
    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event){
        Player player = event.getPlayer();
        if (gameManager.hasActiveSession(player)){
            GameTeam team = gameManager.getPlayerSession(player).getGameTeam();
            PlayerSession session = gameManager.getPlayerSession(player);
            if (session == null || session.getGame() == null) return;
            if (team == null) return;
            Location baseLoc = gameManager.getPlayerSession(player).getGame().getTeamSpawnLocations().get(team);
            Location centerLoc = new Location(baseLoc.getWorld(), 0, baseLoc.getY(), 0);
            float lookAtCenterYaw = calculateLookAtYaw(baseLoc, centerLoc);
            Location finalRespawnLoc = baseLoc.clone();
            finalRespawnLoc.setYaw(lookAtCenterYaw);
            finalRespawnLoc.setPitch(0f);
            event.setRespawnLocation(finalRespawnLoc);
            if (team.isBedAlive()){
                startRebirthSequence(player,finalRespawnLoc);
            }
            else{
                Game game = gameManager.getPlayerSession(player).getGame();
                game.playerDie( player);
            }
        }
    }
    public void startRebirthSequence(Player player,Location spawnLocation) {
        player.setGameMode(GameMode.SPECTATOR);
        new BukkitRunnable() {
            int timeLeft = 5;
            @Override
            public void run() {
                if (timeLeft > 0) {
                    player.sendTitle("§c你已阵亡", "§e将在 " + timeLeft + " 秒后重生", 0, 21, 0);
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                    timeLeft--;
                } else {
                    rebirth(player,spawnLocation);
                    this.cancel();
                }
            }
        }.runTaskTimer(NightMare.getInstance(), 0L, 20L);
    }

    private void rebirth(Player player,Location spawnLocation) {
        player.teleport(spawnLocation);
        player.setGameMode(GameMode.SURVIVAL);
        player.setHealth(player.getAttribute(Attribute.MAX_HEALTH).getBaseValue());
        player.setFoodLevel(20);
        UUID uuid = player.getUniqueId();
        if (savedItems.containsKey(uuid)) {
            List<ItemStack> items = savedItems.get(uuid);
            for (ItemStack item : items) {
                String name = item.getType().name();
                if (name.contains("CHESTPLATE")) player.getInventory().setChestplate(item);
                else if (name.contains("LEGGINGS")) player.getInventory().setLeggings(item);
                else if (name.contains("BOOTS")) player.getInventory().setBoots(item);
                else if (name.contains("HELMET")) {
                    player.getInventory().setHelmet(item);
                } else {
                    player.getInventory().addItem(item);
                }
            }
            savedItems.remove(uuid);
        }
    }
    private float calculateLookAtYaw(Location from, Location to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double angle = Math.atan2(-dx, dz);
        return (float) Math.toDegrees(angle);
    }
    @EventHandler
    public void WeatherChange(WeatherChangeEvent event) {
        event.setCancelled(true);
    }
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        event.setJoinMessage(null);
        Player player = event.getPlayer();
        NightMare.getInstance().getLobbyBoardManager().applyLobbyBoard(player);

        player.getInventory().clear();
        player.sendTitle("§c§l☄ §e§l零月牙老冯爆炸加升天 §c§l☄", "§b✨ 欢迎来到复刻噩梦空间 ✨", 10, 60, 20);
        new BukkitRunnable() {
            @Override
            public void run() {
                player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.8f);
                player.spawnParticle(Particle.EXPLOSION, player.getLocation(), 2, 0.5, 0.5, 0.5, 0);
            }
        }.runTaskLater(NightMare.getInstance(), 20 * 4L);
        if (gameManager.hasActiveSession(event.getPlayer())){
            gameManager.getPlayerSession( player).getGame().playerLeave( player);
        }
        player.teleport(PluginConfigManager.getLobbyLocation());
        ItemStack redBed = new ItemStack(Material.BLACK_BED);
        ItemMeta meta = redBed.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§b§l起床战争");
            redBed.setItemMeta(meta);
        }
        if (!player.getInventory().contains(Material.BLACK_BED)) {
            player.getInventory().addItem(redBed);
        }
        if (player.getInventory().contains(Material.RED_BED)) {
            player.getInventory().remove(Material.RED_BED);
        }
        Player joiner = event.getPlayer();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.equals(joiner)) continue;
            if (gameManager.getPlayerSessions().containsKey( online)) {
                joiner.hidePlayer(NightMare.getInstance(), online);
                online.hidePlayer(NightMare.getInstance(), joiner);
            }
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event){
        Player player = event.getPlayer();
        if (!gameManager.hasActiveSession(player)){
            event.setCancelled( true);
            return;
        }
        Game game = gameManager.getPlayerSession(player).getGame();
        if (gameManager.hasActiveSession(player)){
            if (!gameManager.getPlayerSession(player).getGame().isActive())
                event.setCancelled(true);
        }
        Block targetBlock = event.getBlock();
        Location blockLoc = targetBlock.getLocation();
        if (targetBlock.getType().name().endsWith("_BED")) {
           switch (targetBlock.getType().name()){
               case "RED_BED"->game.getGameTeams().forEach(team -> {
                   if (team.getTeamName().equals("red")){
                       if(game.getTeam( player).getTeamName().equals("red")){
                           player.sendMessage("§c无法破坏自己队伍的床！");
                           event.setCancelled( true);
                           return;
                       }
                       Bukkit.broadcastMessage("§7[!] §e红队的床 §7被 §c" + getColoredName( player) + " §7拆毁了" );
                       for (Player online : game.getGamePlayers().keySet()){
                           online.playSound(online.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.8f);
                       }
                       for (Player online : team.getTeamPlayers()){
                           online.sendTitle("§c§l床被拆毁了！", "§f你失去了复活机会", 10, 40, 10);
                       }
                       team.setBedAlive(false);
                   }
               });
               case "YELLOW_BED"->game.getGameTeams().forEach(team -> {
                   if (team.getTeamName().equals("yellow")){
                       if(game.getTeam( player).getTeamName().equals("yellow")) {
                           player.sendMessage("§c无法破坏自己队伍的床！");
                           event.setCancelled( true);
                           return;
                       }
                       Bukkit.broadcastMessage("§7[!] §e黄队的床 §7被 §c" + getColoredName( player) + " §7拆毁了" );
                       for (Player online : game.getGamePlayers().keySet()){
                           online.playSound(online.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.8f);
                       }
                       for (Player online : team.getTeamPlayers()){
                           online.sendTitle("§c§l床被拆毁了！", "§f你失去了复活机会", 10, 40, 10);
                       }

                       team.setBedAlive(false);
                   }
               });
               case "BLUE_BED"->game.getGameTeams().forEach(team -> {
                   if (team.getTeamName().equals("blue")){
                       if(game.getTeam( player).getTeamName().equals("blue")) {
                           player.sendMessage("§c无法破坏自己队伍的床！");
                           event.setCancelled( true);
                           return;
                       }
                       Bukkit.broadcastMessage("§7[!] §e蓝队的床 §7被 §c" +getColoredName( player) + " §7拆毁了" );
                       for (Player online : game.getGamePlayers().keySet()){
                           online.playSound(online.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.8f);
                       }
                       for (Player online : team.getTeamPlayers()){
                           online.sendTitle("§c§l床被拆毁了！", "§f你失去了复活机会", 10, 40, 10);
                       }

                       team.setBedAlive(false);
                   }
               });
               case "GREEN_BED"->game.getGameTeams().forEach(team -> {
                   if (team.getTeamName().equals("green")){
                       if(game.getTeam( player).getTeamName().equals("green")) {
                           player.sendMessage("§c无法破坏自己队伍的床！");
                           event.setCancelled( true);
                           return;
                       }
                       Bukkit.broadcastMessage("§7[!] §e绿队的床 §7被 §c" + getColoredName( player)+ " §7拆毁了" );
                       for (Player online : game.getGamePlayers().keySet()){
                           online.playSound(online.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.8f);
                       }
                       for (Player online : team.getTeamPlayers()){
                           online.sendTitle("§c§l床被拆毁了！", "§f你失去了复活机会", 10, 40, 10);
                       }

                       team.setBedAlive(false);
                   }
               });
           }
           game.updateAllScoreboards();
            return;
        }
        if (game.getPlacedBlocks().contains(blockLoc)) {
            game.getPlacedBlocks().remove(blockLoc);
        } else {
            event.setCancelled(true);
            player.sendMessage("§c无法破坏该方块！");
        }
    }
    private String getColoredName(Player player) {
        PlayerSession session = gameManager.getPlayerSession(player);
        // 默认灰色
        if (session == null || session.getGame() == null) return "§7" + player.getName();

        var team = session.getGame().getTeam(player);
        if (team == null) return "§7" + player.getName();

        // 获取文字颜色代码
        String colorCode = getChatColorCode(team.getTeamName());

        return colorCode + player.getName();
    }
    private String getChatColorCode(String teamName) {
        // 使用 switch 处理，注意这里匹配的是你 Team 对象里定义的 teamName
        switch (teamName.toLowerCase()) {
            case "red":    return "§c"; // 浅红
            case "blue":   return "§9"; // 浅蓝
            case "green":  return "§a"; // 绿
            case "yellow": return "§e"; // 黄
            case "orange": return "§6"; // 金
            case "purple": return "§5"; // 紫
            default:       return "§7"; // 灰色
        }
    }
    @EventHandler
    public void onWorkbenchInteract(PlayerInteractEvent event) {
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
           Block clickedBlock = event.getClickedBlock();
            if (clickedBlock != null && clickedBlock.getType() == Material.CRAFTING_TABLE) {
                Player player = event.getPlayer();
                if (gameManager.hasActiveSession(player)) {
                    event.setCancelled(true);
                }
            }
        }
    }
    @EventHandler
    public void onPlayerCraft(CraftItemEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            if (gameManager.hasActiveSession(player)) {
                event.setCancelled(true);
                player.sendMessage("§c[NightMare] 比赛中禁止使用合成功能！");
            }
        }
    }
    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event){
        Player player = event.getPlayer();
        if (event.getBlockPlaced().getLocation().getBlockY()>=200){
            event.setCancelled(true);
            player.sendMessage("§c高度过高(200格)无法放置方块！");
        }
        if (gameManager.hasActiveSession(player)){
            if (!gameManager.getPlayerSession(player).getGame().isActive())
                event.setCancelled(true);
        }
        else{
            event.setCancelled(true);
            return ;
        }
        Game game = gameManager.getPlayerSession(player).getGame();
        if (game.getTeamSpawnLocations().containsValue(event.getBlockPlaced().getLocation()))return;
        if (game.getPlacedBlocks().contains(event.getBlockPlaced().getLocation()))return;
        game.getPlacedBlocks().add(event.getBlockPlaced().getLocation());
    }
    @EventHandler
    public void onCreeperSpawn(CreatureSpawnEvent event) {
        if (event.getEntity() instanceof Creeper creeper) {
                creeper.ignite();
                creeper.setExplosionRadius(5);
                creeper.setMaxFuseTicks(25);
        }
    }
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event){
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null) {
            return;
        }
        if (!gameManager.hasActiveSession(player)&&event.getItem().getType()!=Material.BLACK_BED) return;
        Action action = event.getAction();
        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            if (item != null && item.getType() == Material.BLACK_BED) {
                ItemMeta meta = item.getItemMeta();
                if (meta != null && meta.hasDisplayName() && meta.getDisplayName().equals("§b§l起床战争")) {
                    getServer().dispatchCommand(getServer().getConsoleSender(), "dm open bedwars " + player.getName());
                    event.setCancelled(true);
                    return ;
                }
            }
            else if(item.getType()==Material.RED_BED){
                ItemMeta meta = item.getItemMeta();
                if (meta != null && meta.hasDisplayName() && meta.getDisplayName().equals("§b§l选择队伍")) {
                    getServer().dispatchCommand(getServer().getConsoleSender(), "dm open teamchoose " + player.getName());
                    event.setCancelled(true);
                    return ;
                }
            }
        }
        if (!gameManager.hasActiveSession(player))
            return;
        Game game = gameManager.getPlayerSession(player).getGame();
        if (!game.isActive() || game.isSpectator(player))
            event.setCancelled(true);

    }
    @EventHandler
    public void onWitherChangeBlock(EntityChangeBlockEvent event) {
        if (event.getEntityType() == EntityType.WITHER) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onWitherExplode(EntityExplodeEvent event) {
        if (event.getEntityType() == EntityType.WITHER || event.getEntityType() == EntityType.WITHER_SKULL) {
            event.blockList().clear();
        }
    }
    @EventHandler
    public void onTNTThrow(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.TNT) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        item.setAmount(item.getAmount() - 1);
        TNTPrimed tnt = player.getWorld().spawn(player.getEyeLocation(), TNTPrimed.class);
        tnt.setYield(3.5F);
        Vector throwVelocity = player.getLocation().getDirection().multiply(2.8);
        tnt.setVelocity(throwVelocity);
        tnt.setFuseTicks(15);
        tnt.setMetadata("source_player", new FixedMetadataValue(NightMare.getInstance(), player.getUniqueId().toString()));
        player.playSound(player.getLocation(), Sound.ENTITY_WITCH_THROW, 1.0f, 0.5f);
    }
    @EventHandler
    public void onTNTExplode(EntityExplodeEvent event) {
        if (!(event.getEntity() instanceof TNTPrimed)) return;
        Location explodeLoc = event.getLocation();
        double radius = 8.0;

        for (Entity entity : event.getEntity().getNearbyEntities(radius, radius, radius)) {
            if (entity instanceof Player victim) {
                Vector drag = victim.getLocation().toVector().subtract(explodeLoc.toVector());
                double distance = drag.length();
                if (distance < 0.1) continue;
                drag.normalize();
                double power = 6.0 * (1 - (distance / radius));
                drag.setY(drag.getY() + 0.5);
                victim.setVelocity(drag.multiply(power));
                victim.setMetadata("TNT_LAUNCHED", new FixedMetadataValue(NightMare.getInstance(), true));
                new BukkitRunnable() {
                    int timeout = 0;
                    @Override
                    public void run() {
                        timeout += 5;
                        if (!victim.isOnline() || victim.isDead() || !victim.hasMetadata("TNT_LAUNCHED") || timeout > 200) {
                            if (victim.isOnline()) {
                                victim.removeMetadata("TNT_LAUNCHED", NightMare.getInstance());
                            }
                            this.cancel();
                            return;
                        }

                        if (victim.getLocation().getBlock().isLiquid()) {
                            victim.removeMetadata("TNT_LAUNCHED", NightMare.getInstance());
                            this.cancel();
                        }
                    }
                }.runTaskTimer(NightMare.getInstance(), 10L, 5L); // 延迟 0.5 秒后开始，每 5 ticks(0.25秒) 检查一次
            }
        }
    }
    @EventHandler
    public void onPlayerFallDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            if (player.hasMetadata("TNT_LAUNCHED")) {
                event.setCancelled(true);
                player.removeMetadata("TNT_LAUNCHED", NightMare.getInstance());
            }
        }
    }
    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        Game game = gameManager.getGameFromWorld(event.getEntity().getWorld());
        event.blockList().removeIf(block -> {
            // 如果被炸的方块是床，或者不在玩家放置的列表里，就保护它（不让它被炸毁）
            boolean isBed = block.getType().name().endsWith("_BED");
            boolean isGlass = block.getType().name().contains("GLASS");
            boolean isPlayerPlaced = game.getPlacedBlocks().contains(block.getLocation());
            return isBed || !isPlayerPlaced||isGlass;
        });
    }
    @EventHandler
    public void onPlayerHurt(EntityDamageEvent event){
        if (!(event.getEntity() instanceof Player player))
            return;
        if (!gameManager.hasActiveSession(player)) {
            event.setCancelled(true);
            return;
        }
        if (!gameManager.getPlayerSession( player).getGame().isActive()){
            event.setCancelled(true);
        }
        if (!gameManager.hasActiveSession(player))
            return;
        Game game = gameManager.getPlayerSession(player).getGame();
        if (event.getDamageSource().getCausingEntity() instanceof Player causedPlayer){
            if ( game.isSpectator(causedPlayer) || game.getTeam(player) == game.getTeam(causedPlayer))
                event.setCancelled(true);
            return;
        }

        if (!game.isActive())
            event.setCancelled(true);

        if (game.getSpectators().contains(player))
            event.setCancelled(true);


    }
    @EventHandler
    public void onUnifiedChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        PlayerSession session = gameManager.getPlayerSession(player);
        if (session == null || session.getGame() == null) {
            event.viewers().removeIf(viewer -> {
                if (viewer instanceof Player p) {
                    return gameManager.hasActiveSession(p);
                }
                return false;
            });
            event.renderer((source, sourceDisplayName, message, viewer) ->
                    Component.text(player.getName(), NamedTextColor.GRAY)
                            .append(Component.text(": ", NamedTextColor.WHITE))
                            .append(message.color(NamedTextColor.WHITE))
            );
            return;
        }
        Game game = session.getGame();
        GameTeam team = session.getGameTeam();

        String messageStr = PlainTextComponentSerializer.plainText().serialize(event.message());
        boolean isShout = messageStr.startsWith("!") || messageStr.startsWith("！");

        boolean isSpectator = game.isSpectator(player);
        boolean isWaitingLobby = game.isWaiting() || game.isStarting();
        boolean isGameEnded = game.hasEnded();

        NamedTextColor teamColor = getNamedTextColor(team != null ? team.getTeamName() : "gray");
        event.viewers().removeIf(viewer -> viewer instanceof Player);

        if (isWaitingLobby || isGameEnded) {
            event.viewers().addAll(game.getInGamePlayers());
            String prefix = isWaitingLobby ? "[等待区] " : "[结算区] ";
            NamedTextColor prefixColor = isWaitingLobby ? NamedTextColor.AQUA : NamedTextColor.LIGHT_PURPLE;

            event.renderer((source, sourceDisplayName, message, viewer) ->
                    Component.text(prefix, prefixColor)
                            .append(Component.text(player.getName(), teamColor)) // 在等待/结算区也显示队伍颜色
                            .append(Component.text(": ", NamedTextColor.WHITE))
                            .append(message.color(NamedTextColor.WHITE))
            );

        } else if (isSpectator) {
            event.viewers().addAll(game.getSpectators());
            event.renderer((source, sourceDisplayName, message, viewer) ->
                    Component.text("[旁观] ", NamedTextColor.GRAY)
                            .append(Component.text(player.getName(), NamedTextColor.GRAY))
                            .append(Component.text(": ", NamedTextColor.WHITE))
                            .append(message.color(NamedTextColor.GRAY))
            );

        } else if (isShout) {
            event.viewers().addAll(game.getInGamePlayers());
            Component realMessage = event.message().replaceText(builder ->
                    builder.match("^[!！]\\s*").replacement("").once()
            );
            event.renderer((source, sourceDisplayName, message, viewer) ->
                    Component.text("[全局] ", NamedTextColor.GOLD)
                            .append(Component.text(player.getName(), teamColor))
                            .append(Component.text(": ", NamedTextColor.WHITE))
                            .append(realMessage.color(NamedTextColor.WHITE)) // 渲染保留了富文本的真消息
            );

        } else {
            if (team != null) {
                event.viewers().addAll(team.getTeamPlayers());
                event.renderer((source, sourceDisplayName, message, viewer) ->
                        Component.text("[队伍] ", NamedTextColor.GREEN)
                                .append(Component.text(player.getName(), teamColor))
                                .append(Component.text(": ", NamedTextColor.WHITE))
                                .append(message.color(NamedTextColor.GREEN))
                );
            }
        }
    }
    private NamedTextColor getNamedTextColor(String teamName) {
        if (teamName == null) return NamedTextColor.GRAY;
        return switch (teamName.toLowerCase()) {
            case "red" -> NamedTextColor.RED;
            case "blue" -> NamedTextColor.BLUE;
            case "green" -> NamedTextColor.GREEN;
            case "yellow" -> NamedTextColor.YELLOW;
            case "orange" -> NamedTextColor.GOLD;
            case "purple" -> NamedTextColor.DARK_PURPLE;
            default -> NamedTextColor.GRAY;
        };
    }
    @EventHandler(ignoreCancelled = true)
    public void onBossDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof WitherSkull skull && skull.getShooter() instanceof Wither wither) {
            if (wither.hasMetadata("NightMareBoss")) {
                event.setDamage(event.getDamage() * 0.8);
            }
        }
        else if (event.getDamager() instanceof Wither wither && wither.hasMetadata("NightMareBoss")) {
            event.setDamage(event.getDamage() * 0.7);
        }
    }

    @EventHandler
    public void onMessageBroadcast(BroadcastMessageEvent event){
        event.getRecipients().removeIf(sender -> {
            if (sender instanceof Player player){
                gameManager.hasActiveSession(player);
            }
            return false;
        });
    }
    @EventHandler
    public void onBedBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType().name().endsWith("_BED")) {
            event.setDropItems(false);
        }
    }
    @EventHandler
    public void onItemPickUp(PlayerPickItemEvent event){
        Player player = event.getPlayer();
        if (!gameManager.hasActiveSession(player))
            return;
        PlayerSession session = gameManager.getPlayerSession(player);
        if (session.getGame().isSpectator(player) || !session.getGame().isActive())
            event.setCancelled(true);
    }
    @EventHandler
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player) {
            event.setCancelled(true);
            Player player = ((Player) event.getEntity()).getPlayer();
            player.setFoodLevel(20);
        }
    }
    @EventHandler
    public void onItemDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (!gameManager.hasActiveSession(player)) return;
        PlayerSession session = gameManager.getPlayerSession(player);
        if (session.getGame().isSpectator(player)) {
            event.setCancelled(true);
            return;
        }
        ItemStack droppedItem = event.getItemDrop().getItemStack();
        Material type = droppedItem.getType();
        String name = type.name();
        boolean isForbidden = name.endsWith("_SWORD")
                || name.endsWith("_PICKAXE")
                || name.endsWith("_AXE")
                || name.endsWith("_HELMET")
                || name.endsWith("_CHESTPLATE")
                || name.endsWith("_LEGGINGS")
                || name.endsWith("_BOOTS")
                || type == Material.SHEARS;

        if (isForbidden) {
            event.setCancelled(true);
             player.sendMessage("§c禁止丢弃！");
        }
    }
    @EventHandler
    public void onExpPickup(PlayerPickupExperienceEvent event){
        Player player = event.getPlayer();
        if (!gameManager.hasActiveSession(player))
            return;
        PlayerSession session = gameManager.getPlayerSession(player);
        if (session.getGame().isSpectator(player) || !session.getGame().hasStarted())
            event.setCancelled(true);
    }

    @EventHandler
    public void onRespawnPointSet(PlayerSetSpawnEvent event){
        if (gameManager.hasActiveSession(event.getPlayer()))
            event.setCancelled(true);
    }
}
