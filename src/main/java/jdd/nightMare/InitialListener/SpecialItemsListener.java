package jdd.nightMare.InitialListener;
import jdd.nightMare.Game.Game;
import jdd.nightMare.Game.GameManager;
import jdd.nightMare.Game.PlayerSession;
import jdd.nightMare.NightMare;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Egg;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

public class SpecialItemsListener implements Listener {

    private final NightMare plugin = NightMare.getInstance();
    private final GameManager gameManager;
    // 存储当前活跃的行走平台方块
    private final java.util.Map<UUID, Set<Location>> activePlatformBlocks = new java.util.HashMap<>();
    // 存储所有蹦床的方块，用于免疫掉落蹦床时的瞬间伤害
    private final Set<Location> globalTrampolineBlocks = new HashSet<>();
    private final Map<Location, UUID> activeTraps = new HashMap<>();
    public SpecialItemsListener(GameManager gameManager) {
        this.gameManager = gameManager;
    }
    @EventHandler
    public void onFireballUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.FIRE_CHARGE) return;
        // 检查名字是否为火焰弹 (根据你商店设置的名字调整)
        if (item.hasItemMeta() && item.getItemMeta().getDisplayName().contains("火焰弹")) {
            event.setCancelled(true);
            Player player = event.getPlayer();
            item.setAmount(item.getAmount() - 1);
            org.bukkit.entity.Fireball fireball = player.launchProjectile(org.bukkit.entity.Fireball.class);
            // 设置火球属性
            fireball.setVelocity(player.getLocation().getDirection().multiply(1.5)); // 飞行速度
            fireball.setYield(2.5F); // 爆炸威力 (TNT是4.0，2.5刚好能炸掉一层方块并提供不错的击退)
            fireball.setIsIncendiary(false); // 关闭产生火焰方块 (防止服务器到处都是火)

            // 打上专属标记，方便后面拦截爆炸伤害
            fireball.setMetadata("custom_fireball", new org.bukkit.metadata.FixedMetadataValue(NightMare.getInstance(), player.getUniqueId().toString()));

            player.playSound(player.getLocation(), Sound.ENTITY_GHAST_SHOOT, 1.0f, 1.0f);
        }
    }
    @EventHandler
    public void onFireballExplode(EntityExplodeEvent event) {
        // 拦截自定义火球的爆炸
        if (event.getEntity() instanceof org.bukkit.entity.Fireball fireball && fireball.hasMetadata("custom_fireball")) {
            Game game = gameManager.getGameFromWorld(event.getEntity().getWorld());
            event.blockList().removeIf(block ->
                    !game.getPlacedBlocks().contains(block.getLocation())
            );
        }
    }

    @EventHandler
    public void onFireballDamage(EntityDamageByEntityEvent event) {
        // 处理“火球跳”对自己造成的伤害
        if (event.getDamager() instanceof org.bukkit.entity.Fireball fireball && fireball.hasMetadata("custom_fireball")) {
            if (event.getEntity() instanceof Player victim) {
                String shooterUUID = fireball.getMetadata("custom_fireball").get(0).asString();
                // 如果被炸的是发射者本人 (他在尝试火球跳)
                if (victim.getUniqueId().toString().equals(shooterUUID)) {
                    // 将伤害大幅降低 (比如只扣 1 颗心)，但保留原版的爆炸击退力
                    event.setDamage(2.0);
                }
            }
        }
    }
    @EventHandler
    public void onUseItem(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (gameManager.getPlayerSession( player)==null)return;
        Map<String,Integer>coolDowns = gameManager.getPlayerSession( player).getSpecialItemCooldowns();
        if (item == null) return;
        Action action = event.getAction();
        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            // --- 指南针：追踪最近的其他队伍玩家 ---
            if (item.getType() == Material.COMPASS) {
                trackNearestEnemy(player);
                return; // 指南针通常不消耗
            }

            if (!item.hasItemMeta()) return;
            String displayName = PlainTextComponentSerializer.plainText().serialize(item.getItemMeta().displayName());

            // --- 1. 降落伞 ---
            if (displayName.contains("降落伞")) {
                if (coolDowns.get("降落伞")>0){
                    player.sendMessage("§c[NightMare] 降落伞冷却中！冷却剩余:§f"+coolDowns.get("降落伞")+"s");
                    event.setCancelled(true);
                    return;
                }
                event.setCancelled(true);
                consumeItem(player);
                player.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 30, 40));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 1200, 0));
                player.setMetadata("PARACHUTE_ACTIVE", new FixedMetadataValue(plugin, true));
                player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_ELYTRA, 1f, 1f);
                player.sendMessage("§b[NightMare] §f降落伞已开启！(按 Shift 可主动收伞)");
                coolDowns.put("降落伞", 45);
            }

            // --- 2. 行走平台 (5秒时长, #字形) ---
            else if (displayName.contains("行走平台")) {
                if (coolDowns.get("行走平台")>0){
                    player.sendMessage("§c[NightMare] 行走平台冷却中！冷却剩余:§f"+coolDowns.get("行走平台")+"s");
                    event.setCancelled(true);
                    return;
                }
                event.setCancelled(true);
                consumeItem(player);
                // 时间缩减到 5 秒
                player.setMetadata("WALKING_PLATFORM", new FixedMetadataValue(plugin, System.currentTimeMillis() + 5000));
                updateWalkingPlatform(player, player.getLocation().subtract(0, 1, 0));
                player.sendMessage("§a[NightMare] §f行走平台激活！持续5秒 (按 Shift 可提前取消)");
                coolDowns.put("行走平台", 30);
            }

            // --- 3. 蹦床 (7x7大范围) ---
            else if (displayName.contains("蹦床")) {
                if (coolDowns.get("蹦床")>0){
                    player.sendMessage("§c[NightMare] 蹦床冷却中！冷却剩余:§f"+coolDowns.get("蹦床")+"s");
                    event.setCancelled(true);
                    return;
                }
                Location deployLoc = player.getLocation().subtract(0, 1, 0);
                World world = deployLoc.getWorld();
                int centerX = deployLoc.getBlockX();
                int centerY = deployLoc.getBlockY();
                int centerZ = deployLoc.getBlockZ();
                // 2. 障碍物检测 (7x7 范围，即中心向外扩展 3 格)
                boolean hasObstacle = false;
                for (int x = -3; x <= 3; x++) {
                    for (int z = -3; z <= 3; z++) {
                        Block block = world.getBlockAt(centerX + x, centerY, centerZ + z);
                        if (block.getType() != Material.AIR) {
                            hasObstacle = true;
                            break; // 发现障碍物，跳出内层循环
                        }
                    }
                    if (hasObstacle) break; // 跳出外层循环
                }
                // 3. 拦截逻辑
                if (hasObstacle) {
                    player.sendMessage("§c[NightMare] 7x7 范围内存在障碍物，蹦床无法展开！");
                    event.setCancelled(true);
                    return; // 直接返回，不扣除物品，不进入冷却
                }
                event.setCancelled(true);
                consumeItem(player);
                createLargeTrampoline(player.getLocation().subtract(0, 1, 0));
                player.sendMessage("§d[NightMare] §f7x7 巨型蹦床已部署！");
                coolDowns.put("蹦床", 30);
            }

            // --- 4. 回城卷轴 (火药) ---
            else if (displayName.contains("回城卷轴") && item.getType() == Material.GUNPOWDER) {
                if (coolDowns.get("回城卷轴")>0){
                    player.sendMessage("§c[NightMare] 回城卷轴冷却中！冷却剩余:§f"+coolDowns.get("回城卷轴")+"s");
                    event.setCancelled(true);
                    return;
                }
                event.setCancelled(true);
                // 防止重复使用刷屏或卡 Bug
                if (player.hasMetadata("RECALLING")) {
                    player.sendMessage("§c[NightMare] 你已经在使用回城卷轴了！");
                    return;
                }
                ItemStack scrollToRefund = item.clone();
                scrollToRefund.setAmount(1);
                consumeItem(player);
                startRecallTask(player, scrollToRefund,coolDowns);
            }
        }
    }
    @EventHandler
    public void onTrapPlace(org.bukkit.event.block.BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (item.getType() == Material.STRING && item.hasItemMeta() && item.getItemMeta().getDisplayName().contains("陷阱")) {
            Player player = event.getPlayer();
            if (!gameManager.hasActiveSession(player)) return;
            Location trapLoc = event.getBlockPlaced().getLocation();
            activeTraps.put(trapLoc, player.getUniqueId());
            player.sendMessage("§a[NightMare] 陷阱线已布置！敌人踩到时将触发警报。");
            player.playSound(trapLoc, Sound.BLOCK_STONE_PLACE, 1f, 1f);
        }
    }
    @EventHandler
    public void onTrapBreak(org.bukkit.event.block.BlockBreakEvent event) {
        // 如果陷阱线被任何人（包含敌人）挖掉了，从记录中安全移除，防止内存泄漏
        if (event.getBlock().getType() == Material.TRIPWIRE) {
            activeTraps.remove(event.getBlock().getLocation());
        }
    }
    private boolean isTeammate(Player p1, Player p2) {
        PlayerSession s1 = gameManager.getPlayerSession(p1);
        PlayerSession s2 = gameManager.getPlayerSession(p2);
        // 如果任何一方不在游戏会话中，视为非队友（或者为了安全视为队友，取决于你的逻辑）
        if (s1 == null || s2 == null || s1.getGame() == null || s2.getGame() == null) return false;
        // 获取各自的队伍
        var team1 = s1.getGame().getTeam(p1);
        var team2 = s2.getGame().getTeam(p2);
        // 判定队伍是否相同
        return team1 != null && team1.equals(team2);
    }
    private void startRecallTask(Player player, ItemStack refundItem,Map<String,Integer>coolDowns) {
        player.setMetadata("RECALLING", new FixedMetadataValue(plugin, true));
        final Location startLoc = player.getLocation().clone();
        new BukkitRunnable() {
            int timeLeft = 5;
            @Override
            public void run() {
                // 1. 玩家离线或死亡，直接取消任务并清理标记
                if (!player.isOnline() || player.isDead()) {
                    player.removeMetadata("RECALLING", plugin);
                    this.cancel();
                    return;
                }

                // 2. 检测标记是否被清除（由受击事件触发）
                if (!player.hasMetadata("RECALLING")) {
                    interruptRecall(player, refundItem, "§c[NightMare] 你受到了伤害，回城失效！");
                    this.cancel();
                    coolDowns.put("回城卷轴", 0);
                    return;
                }
                // 3. 检测玩家是否移动
                // 使用 distanceSquared 比较，允许原地转头(Yaw/Pitch 变化)，但不能走路
                Location currentLoc = player.getLocation();
                if (startLoc.getWorld() != currentLoc.getWorld() || startLoc.distanceSquared(currentLoc) > 0.1) {
                    player.removeMetadata("RECALLING", plugin);
                    interruptRecall(player, refundItem, "§c[NightMare] 你移动了位置，回城失效！");
                    this.cancel();
                    coolDowns.put("回城卷轴", 0);
                    return;
                }

                // 4. 倒计时结束，执行传送
                if (timeLeft <= 0) {
                    player.removeMetadata("RECALLING", plugin);
                    teleportToTeamSpawn(player); // 调用你原有的传送方法
                    this.cancel();
                    coolDowns.put("回城卷轴", 40);
                    return;
                }
                player.sendTitle("§b回城施法中", "§e" + timeLeft + " §7秒后传送 (请勿移动)", 0, 25, 0);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 1f, 1f);

                timeLeft--;
            }
        }.runTaskTimer(plugin, 0L, 20L); // 0延迟，每20 tick(1秒) 执行一次
    }
    private void interruptRecall(Player player, ItemStack refundItem, String reason) {
        player.sendMessage(reason);
        player.playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK, 1f, 1f);
        player.resetTitle(); // 清除屏幕上的倒计时

        // 尝试退还卷轴到背包，如果背包满了则掉落在玩家脚下
        if (!player.getInventory().addItem(refundItem).isEmpty()) {
            player.getWorld().dropItem(player.getLocation(), refundItem);
        }
    }

    // ==============================================
    //               摔落伤害与安全机制修复
    // ==============================================
    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        // 【新增】回城打断机制：只要造成了实质性伤害，就移除施法标记
        if (player.hasMetadata("RECALLING") && event.getFinalDamage() > 0) {
            // 移除标记后，下一秒的 Runnable 就会捕捉到标记消失，并执行退还逻辑
            player.removeMetadata("RECALLING", plugin);
        }

        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            // 修复降落伞无缓降摔死：只要有降落伞标记，强行免疫摔伤
            if (player.hasMetadata("PARACHUTE_ACTIVE")) {
                event.setCancelled(true);
                return;
            }

            // 修复蹦床跳跃落地摔死：如果是从蹦床起跳的，免疫本次落地伤害并移除标记
            if (player.hasMetadata("BOUNCED")) {
                event.setCancelled(true);
                player.removeMetadata("BOUNCED", plugin);
                return;
            }

            // 修复高空掉落时，瞬间放置蹦床砸在羊毛上摔死
            Block underBlock = player.getLocation().subtract(0, 0.1, 0).getBlock();
            if (globalTrampolineBlocks.contains(underBlock.getLocation())) {
                event.setCancelled(true);
            }
        }
    }

    // ==============================================
    //               Shift 潜行取消道具逻辑
    // ==============================================
    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        if (event.isSneaking()) {
            // 取消降落伞
            if (player.hasMetadata("PARACHUTE_ACTIVE")) {
                player.removeMetadata("PARACHUTE_ACTIVE", plugin);
                player.removePotionEffect(PotionEffectType.SLOW_FALLING);
                player.sendMessage("§7[NightMare] 已手动收起降落伞。");
            }
            // 取消行走平台
            if (player.hasMetadata("WALKING_PLATFORM")) {
                clearWalkingPlatform(player);
                player.removeMetadata("WALKING_PLATFORM", plugin);
                player.sendMessage("§7[NightMare] 已手动收起行走平台。");
            }
        }
    }

    // ==============================================
    //               末影珍珠：骑乘与禁传送
    // ==============================================
    @EventHandler
    public void onPearlLaunch(ProjectileLaunchEvent event) {
        if (event.getEntity() instanceof org.bukkit.entity.EnderPearl pearl) {
            if (pearl.getShooter() instanceof Player player) {
                pearl.addPassenger(player); // 玩家骑上珍珠
                player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 1f, 1.5f);
            }
        }
        // 搭桥蛋逻辑保持不变... (省略部分同原代码，避免过长)
        else if (event.getEntity() instanceof Egg egg && egg.getShooter() instanceof Player player) {
            // 【副手检测】玩家很可能把搭桥蛋放在副手扔出
            ItemStack mainHand = player.getInventory().getItemInMainHand();
            ItemStack offHand = player.getInventory().getItemInOffHand();
            boolean isBridgeEgg = false;
            if (mainHand.hasItemMeta() && PlainTextComponentSerializer.plainText().serialize(mainHand.getItemMeta().displayName()).contains("搭桥蛋")) {
                isBridgeEgg = true;
            } else if (offHand.hasItemMeta() && PlainTextComponentSerializer.plainText().serialize(offHand.getItemMeta().displayName()).contains("搭桥蛋")) {
                isBridgeEgg = true;
            }

            if (!isBridgeEgg) return;
            Material teamWool = getPlayerTeamWool(player);
            Location startLoc = egg.getLocation().clone();
            Game game = gameManager.getGameFromWorld(startLoc.getWorld());
            // 【优化 1：加快初始速度】让鸡蛋飞得更快，桥铺得更迅速平缓
            egg.setVelocity(egg.getVelocity().multiply(1.2));
            // 启动动态轨迹追踪
            new BukkitRunnable() {
                int ticks = 0;
                @Override
                public void run() {
                    // 终止条件 A：落地或撞墙
                    if (egg.isDead() || !egg.isValid() || egg.isOnGround()) {
                        this.cancel();
                        return;
                    }
                    Location currentLoc = egg.getLocation();
                    // 【优化 2：大幅增加距离与时间锁】
                    // 限制最大飞行 15，或最多飞 60 tick (3秒)
                    if (currentLoc.distanceSquared(startLoc) > 275 || ticks > 60) {
                        egg.remove(); // 强制在半空中没收鸡蛋，掐断轨迹
                        this.cancel();
                        return;
                    }
                    // --- 切片生成逻辑 ---
                    Vector velocity = egg.getVelocity().setY(0).normalize();
                    if (velocity.lengthSquared() > 0) {
                        Vector right = new Vector(-velocity.getZ(), 0, velocity.getX()).normalize();
                        // 【优化 3：增厚桥面，增加羊毛数量】
                        // 宽度固定 3 格 (-1 到 1)，去除了随机缺损概率
                        for (int w = -1; w <= 1; w++) {
                            Location blockLoc = currentLoc.clone().subtract(0, 2, 0).add(right.clone().multiply(w));
                            Block block = blockLoc.getBlock();

                            if (block.getType() == Material.AIR || block.getType().name().endsWith("_WATER")) {
                                block.setType(teamWool);
                                game.getPlacedBlocks().add(block.getLocation());
                            }
                            // 【新增】在桥的最中间 (w=0) 的下方额外垫一层羊毛，增加厚度防踩空
                            if (w == 0) {
                                Block bottomBlock = blockLoc.clone().subtract(0, 1, 0).getBlock();
                                if (bottomBlock.getType() == Material.AIR || bottomBlock.getType().name().endsWith("_WATER")) {
                                    bottomBlock.setType(teamWool);
                                    game.getPlacedBlocks().add(bottomBlock.getLocation());
                                }
                            }
                        }
                        // 伴随飞行的音效
                        currentLoc.getWorld().playSound(currentLoc, Sound.BLOCK_WOOL_PLACE, 0.5f, 1.2f);
                    }

                    ticks++;
                }
            }.runTaskTimer(NightMare.getInstance(), 1L, 1L);
        }
    }
    @EventHandler
    public void onPearlTeleport(PlayerTeleportEvent event) {
        // 珍珠落地时，取消原版传送，玩家会自动在珍珠坠毁点下车
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.ENDER_PEARL) {
            event.setCancelled(true);
        }
    }

    // ==============================================
    //               移动检测：降落伞与行走平台
    // ==============================================
    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        // 落地自动收伞
        if (player.hasMetadata("PARACHUTE_ACTIVE") && player.isOnGround()) {
            player.removePotionEffect(PotionEffectType.SLOW_FALLING);
            player.removeMetadata("PARACHUTE_ACTIVE", plugin);
        }

        // 行走平台动态生成
        if (player.hasMetadata("WALKING_PLATFORM")) {
            long expiry = player.getMetadata("WALKING_PLATFORM").get(0).asLong();
            if (System.currentTimeMillis() > expiry) {
                clearWalkingPlatform(player);
                player.removeMetadata("WALKING_PLATFORM", plugin);
                return;
            }
            if (event.getFrom().getBlockX() != event.getTo().getBlockX() ||
                    event.getFrom().getBlockZ() != event.getTo().getBlockZ() ||
                    event.getFrom().getBlockY() != event.getTo().getBlockY()) {
                updateWalkingPlatform(player, player.getLocation().subtract(0, 1, 0));
            }
        }
        Block blockAtFeet = player.getLocation().getBlock();
        // 检查脚下的方块是不是绊线
        if (blockAtFeet.getType() == Material.TRIPWIRE) {
            Location trapLoc = blockAtFeet.getLocation();
            // 检查这个绊线是不是玩家布置的陷阱
            if (activeTraps.containsKey(trapLoc)) {
                UUID ownerUUID = activeTraps.get(trapLoc);
                Player owner = Bukkit.getPlayer(ownerUUID);
                // 判定是否为敌人 (如果主人离线了，判定为必须触发)
                boolean isEnemy = true;
                if (owner != null) {
                    isEnemy = !isTeammate(owner, player);
                }
                // 只有生存模式的敌人踩上去才会触发
                if (isEnemy && player.getGameMode() == GameMode.SURVIVAL) {
                    // 1. 给敌人施加负面效果
                    player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1));
                    trapLoc.getWorld().playSound(trapLoc, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1f, 1f);
                    // 2. 通知主人
                    if (owner != null && owner.isOnline()) {
                        owner.sendTitle("§c§l陷阱被触发！", "§f" + player.getName() + " §7踩中了你的陷阱线！", 10, 60, 10);
                        owner.playSound(owner.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 2f);
                    }
                    // 3. 销毁陷阱（阅后即焚）
                    activeTraps.remove(trapLoc);
                    blockAtFeet.setType(Material.AIR);
                }
            }
        }
    }

    // ==============================================
    //               具体道具的实现方法
    // ==============================================

    /**
     * 行走平台：生成 # 字形 (5x5去掉四角)
     */
    private void updateWalkingPlatform(Player player, Location center) {
        UUID uuid = player.getUniqueId();
        Set<Location> newBlocks = new HashSet<>();
        Set<Location> oldBlocks = activePlatformBlocks.getOrDefault(uuid, new HashSet<>());

        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                // 如果 X 或 Z 的绝对值 <= 1，则属于 3x3 中心及上下左右延伸，构成 # 字形
                if (Math.abs(x) <= 1 || Math.abs(z) <= 1) {
                    Location loc = center.clone().add(x, 0, z).getBlock().getLocation();
                    newBlocks.add(loc);
                }
            }
        }

        for (Location oldLoc : oldBlocks) {
            if (!newBlocks.contains(oldLoc)) {
                Block b = oldLoc.getBlock();
                if (b.getType() == getPlayerTeamWool(player)) b.setType(Material.AIR);
            }
        }
        for (Location newLoc : newBlocks) {
            Block b = newLoc.getBlock();
            if (b.getType() == Material.AIR) b.setType(getPlayerTeamWool(player));
        }
        activePlatformBlocks.put(uuid, newBlocks);
        if (oldBlocks.isEmpty()) player.playSound(center, Sound.BLOCK_WOOL_PLACE, 0.5f, 1.2f);
    }

    private void clearWalkingPlatform(Player player) {
        Set<Location> blocks = activePlatformBlocks.remove(player.getUniqueId());
        if (blocks != null) {
            for (Location loc : blocks) {
                if (loc.getBlock().getType() == getPlayerTeamWool(player)) loc.getBlock().setType(Material.AIR);
            }
        }
    }

    /**
     * 蹦床：7x7范围，去除抗性，依赖元数据防摔
     */
    private void createLargeTrampoline(Location center) {
        final Material mat = Material.LIME_WOOL;
        final Set<Location> tBlocks = new HashSet<>();

        // 生成 7x7
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                Block b = center.clone().add(x, 0, z).getBlock();
                if (b.getType() == Material.AIR || b.getType() == Material.TALL_GRASS) {
                    b.setType(mat);
                    tBlocks.add(b.getLocation());
                    globalTrampolineBlocks.add(b.getLocation()); // 加入全局保护池
                }
            }
        }

        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks > 100) { // 5秒后消失
                    for (Location loc : tBlocks) {
                        if (loc.getBlock().getType() == mat) loc.getBlock().setType(Material.AIR);
                        globalTrampolineBlocks.remove(loc); // 移出保护池
                    }
                    this.cancel();
                    return;
                }

                for (Player p : Bukkit.getOnlinePlayers()) {
                    Location underLoc = p.getLocation().subtract(0, 0.1, 0).getBlock().getLocation();
                    if (tBlocks.contains(underLoc)) {
                        p.setVelocity(new Vector(0, 2.5, 0)); // 7x7蹦床力度稍加
                        p.setFallDistance(0); // 清空已有的下落距离
                        p.setMetadata("BOUNCED", new FixedMetadataValue(plugin, true)); // 标记用于免疫下次落地伤害
                        p.playSound(p.getLocation(), Sound.ENTITY_SLIME_JUMP, 1f, 1.2f);
                        // 移除了 PotionEffectType.RESISTANCE，玩家不再无敌
                    }
                }
                ticks += 2;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    /**
     * 指南针：追踪最近的其他队伍玩家
     */
    private void trackNearestEnemy(Player player) {
        Player nearest = null;
        double minDist = Double.MAX_VALUE;
        PlayerSession session = gameManager.getPlayerSession(player);

        if (session != null && session.getGame() != null) {
            for (Player target : session.getGame().getInGamePlayers()) {
                if (target.equals(player) || !target.isOnline() || target.isDead()) continue;
                if (session.getGame().getTeam(target) == session.getGame().getTeam(player)) continue;
                if (target.getWorld() != player.getWorld()) continue;
                double dist = target.getLocation().distanceSquared(player.getLocation());
                if (dist < minDist) {
                    minDist = dist;
                    nearest = target;
                }
            }
        }

        if (nearest != null) {
            player.setCompassTarget(nearest.getLocation());
            player.sendMessage("§a[NightMare] 已锁定最近的敌人: §c" + nearest.getName()+"距离你"+Math.sqrt(minDist)+"米");
        } else {
            player.sendMessage("§7[NightMare] 当前世界未发现其他队伍的敌人。");
        }
    }

    /**
     * 回城卷轴：传送回队伍出生点
     */
    private void teleportToTeamSpawn(Player player) {
        PlayerSession session = gameManager.getPlayerSession(player);
        if (session != null && session.getGame() != null) {
            // 请根据你的 GameTeam 类的方法修改获取队伍 SpawnLocation 的方法
            Location teamSpawn = session.getGame().getTeamSpawnLocations().get(session.getGame().getTeam(player));
            if (teamSpawn != null) {
                // 播放一个传送音效和粒子
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
                player.teleport(teamSpawn);
                player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
                player.sendMessage("§e[NightMare] §f已使用回城卷轴返回基地。");
            }
        }
    }

    private Material getPlayerTeamWool(Player player) {
        try {
            PlayerSession session = gameManager.getPlayerSession(player);
            String teamColor = session.getGame().getTeam(player).getTeamName().toLowerCase();
            return switch (teamColor) {
                case "red" -> Material.RED_WOOL;
                case "blue" -> Material.BLUE_WOOL;
                case "green" -> Material.LIME_WOOL;
                case "yellow" -> Material.YELLOW_WOOL;
                default -> Material.WHITE_WOOL;
            };
        } catch (Exception e) { return Material.WHITE_WOOL; }
    }

    private void consumeItem(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(null);
        }
    }
}