package jdd.nightMare.tasks;
import jdd.nightMare.Game.Game;
import jdd.nightMare.NightMare;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import jdd.nightMare.Game.Game;
import jdd.nightMare.NightMare;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class BossManager {
    private final Game game;
    private final List<LivingEntity> activeBosses = new ArrayList<>();
    private BukkitRunnable particleTask;

    public static final NamespacedKey BOSS_TYPE_KEY = new NamespacedKey(NightMare.getInstance(), "boss_type");
    public static final NamespacedKey BOSS_LEVEL_KEY = new NamespacedKey(NightMare.getInstance(), "boss_level");

    public BossManager(Game game) {
        this.game = game;
        startBossSkillTask();
        startParticleTask();
    }

    public void spawnNightBosses(int level) {
        // 获取地图配置的副岛坐标（应有4个）
        List<Location> spawnerLocations = game.getMap().getSideLocations();
        if (spawnerLocations.isEmpty()) return;

        String[] types = {"WIND", "FIRE", "WOOD", "WATER"};

        cleanup(); // 清理旧Boss

        // 循环生成，确保每个坐标生成一个不同类型的 Boss
        for (int i = 0; i < Math.min(types.length, spawnerLocations.size()); i++) {
            Location loc = spawnerLocations.get(i).clone().add(0, 1, 0);
            loc.getChunk().load();

            // 【修复 1】统一使用 Zombie 确保 setupBossAppearance 能正确处理
            Zombie boss = (Zombie) loc.getWorld().spawnEntity(loc, EntityType.ZOMBIE);
            boss.setBaby(false);
            // 【修复 2】防止僵尸变异成溺尸或在阳光下自燃
            boss.setConversionTime(-1);
            double hp = (level == 1) ? 100.0 : 200.0;
            if (boss.getAttribute(Attribute.MAX_HEALTH) != null) {
                boss.getAttribute(Attribute.MAX_HEALTH).setBaseValue(hp);
                boss.setHealth(hp);
            }

            boss.setCustomNameVisible(true);
            boss.setRemoveWhenFarAway(false);
            boss.setCanPickupItems(false); // 防止捡起玩家掉落物

            // 写入 PDC 数据
            boss.getPersistentDataContainer().set(BOSS_TYPE_KEY, PersistentDataType.STRING, types[i]);
            boss.getPersistentDataContainer().set(BOSS_LEVEL_KEY, PersistentDataType.INTEGER, level);

            // 【修复 3】必须调用外观设置方法！
            setupBossAppearance(boss, types[i], level);

            synchronized (activeBosses) {
                activeBosses.add(boss);
            }

            // 炫酷降临特效：闪电（无伤害）
            loc.getWorld().strikeLightningEffect(loc);
        }
        Bukkit.broadcast(Component.text("§e§l[噩梦守卫] §f四大元素守卫已在副岛降临！"));
    }

    /**
     * 【修复 4】完善外观设置逻辑
     */
    private void setupBossAppearance(Zombie boss, String type, int level) {
        boss.getEquipment().clear();
        String displayName;
        Color armorColor;
        Material tool = Material.AIR;

        switch (type) {
            case "WIND" -> {
                displayName = "§f§l风之守卫";
                armorColor = Color.WHITE;
                tool = Material.FEATHER;
            }
            case "FIRE" -> {
                displayName = "§c§l火之守卫";
                armorColor = Color.RED;
                tool = Material.BLAZE_ROD;
            }
            case "WOOD" -> {
                displayName = "§a§l木之守卫";
                armorColor = Color.GREEN;
                tool = Material.OAK_SAPLING;
            }
            case "WATER" -> {
                displayName = "§b§l水之守卫";
                armorColor = Color.BLUE;
                tool = Material.PRISMARINE_SHARD;
            }
            default -> {
                displayName = "§8守卫";
                armorColor = Color.GRAY;
            }
        }

        // 设置皮革套装颜色
        boss.getEquipment().setHelmet(getColoredArmor(Material.LEATHER_HELMET, armorColor));
        boss.getEquipment().setChestplate(getColoredArmor(Material.LEATHER_CHESTPLATE, armorColor));
        boss.getEquipment().setLeggings(getColoredArmor(Material.LEATHER_LEGGINGS, armorColor));
        boss.getEquipment().setBoots(getColoredArmor(Material.LEATHER_BOOTS, armorColor));

        // 手持物
        if (tool != Material.AIR) {
            boss.getEquipment().setItemInMainHand(new ItemStack(tool));
        }

        // 加上掉落率锁定（0%），防止玩家刷装备
        boss.getEquipment().setHelmetDropChance(0.0f);
        boss.getEquipment().setChestplateDropChance(0.0f);
        boss.getEquipment().setLeggingsDropChance(0.0f);
        boss.getEquipment().setBootsDropChance(0.0f);
        // 设置主手物品掉落率 (1.21.x 正确方法名)
        boss.getEquipment().setItemInMainHandDropChance(0.0f);
        boss.setAI(false);
        if (boss.getAttribute(Attribute.KNOCKBACK_RESISTANCE) != null) {
            boss.getAttribute(Attribute.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
        }
        boss.setCustomName(displayName + " §e[Lv." + level + "]");
    }
    public static final NamespacedKey BOSS_ROLE_KEY = new NamespacedKey(NightMare.getInstance(), "boss_role");

    public void spawnMidBoss() {
        Location midLoc = game.getMap().getSpectatorLocation().clone().add(0, 12.5, 0);
        midLoc.getChunk().load();
        // 生成凋零
        Wither wither = (Wither) midLoc.getWorld().spawnEntity(midLoc, EntityType.WITHER);
        // 基础设置
        wither.setCustomName("§d§l噩梦主宰");
        wither.setCustomNameVisible(true);
        wither.getAttribute(Attribute.MAX_HEALTH).setBaseValue(200.0);
        wither.setHealth(200.0);
        wither.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(0.0);
        wither.getAttribute(Attribute.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
        wither.setGravity(false);
        // 标记为中岛 Boss
        wither.getPersistentDataContainer().set(BOSS_ROLE_KEY, PersistentDataType.STRING, "MID_BOSS");
        synchronized (activeBosses) {
            activeBosses.add(wither);
        }
        Bukkit.broadcast(Component.text("§5§l[NightMare] §f床已自毁，中岛 Boss §d§l凋零 §f已降临！击败它获得全队增益！"));
        midLoc.getWorld().strikeLightningEffect(midLoc);
        new BukkitRunnable() {
            @Override
            public void run() {
                if (wither.isDead() || !wither.isValid()) {
                    this.cancel();
                    return;
                }
                // 强制锁定坐标和朝向（可以根据玩家位置微调朝向，或者固定朝向）
                wither.teleport(midLoc);
            }
        }.runTaskTimer(NightMare.getInstance(), 0L, 1L);
        new BukkitRunnable() {
            int attackCooldown = 0; // 手动计时器
            @Override
            public void run() {
                if (wither == null || wither.isDead()) {
                    this.cancel();
                    return;
                }
                // 2. 逻辑分频：每 20 tick (1秒) 寻找一次最近玩家并尝试攻击
                attackCooldown++;
                if (attackCooldown >= 40) { // 设定 2 秒攻击一次
                    Player target = getNearestEnemyPlayer(wither, 30.0); // 搜索 30 格内
                    if (target != null) {
                        launchWitherSkull(wither, target);
                    }
                    attackCooldown = 0; // 重置计时器
                }
            }
        }.runTaskTimer(NightMare.getInstance(), 0L, 1L);
    }
    private void launchWitherSkull(Wither source, Player target) {
        Vector direction = target.getEyeLocation().toVector()
                .subtract(source.getEyeLocation().toVector())
                .normalize();
        // 如果你想让它射得更准，可以用 launchProjectile
        source.launchProjectile(WitherSkull.class, direction.multiply(1.5));
        source.getWorld().playSound(source.getLocation(), Sound.ENTITY_WITHER_SHOOT, 1f, 1f);
    }
    private ItemStack getColoredArmor(Material material, Color color) {
        ItemStack item = new ItemStack(material);
        LeatherArmorMeta meta = (LeatherArmorMeta) item.getItemMeta();
        meta.setColor(color);
        meta.setUnbreakable(true);
        item.setItemMeta(meta);
        return item;
    }
    private Player getNearestEnemyPlayer(Entity origin, double radius) {
        Player nearest = null;
        double minDistanceSquared = radius * radius; // 使用平方比较，避免开方运算(Math.sqrt)，极大提升性能

        // 只获取半径范围内的实体，比遍历全服玩家快 100 倍
        for (Entity entity : origin.getNearbyEntities(radius, radius, radius)) {
            if (entity instanceof Player player) {
                // 1. 基础状态过滤：排除死人、创造模式、观察者
                if (player.isDead() || !player.isValid()) continue;
                if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) continue;

                // 2. 团队过滤：如果你有队友系统，可以在这里排除“守护者”NPC或同队玩家
                // if (isTeammate(origin, player)) continue;

                // 3. 距离计算
                double distSq = player.getLocation().distanceSquared(origin.getLocation());
                if (distSq < minDistanceSquared) {
                    minDistanceSquared = distSq;
                    nearest = player;
                }
            }
        }
        return nearest;
    }

    // --- 粒子特效与技能逻辑保持不变 (已包含在下方以确保完整) ---

    private void startParticleTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                synchronized (activeBosses) {
                    Iterator<LivingEntity> it = activeBosses.iterator();
                    while (it.hasNext()) {
                        LivingEntity boss = it.next();
                        if (boss.isDead() || !boss.isValid()) {
                            it.remove();
                            continue;
                        }
                        spawnBossAura(boss.getLocation().add(0, 1, 0),
                                boss.getPersistentDataContainer().get(BOSS_TYPE_KEY, PersistentDataType.STRING));
                    }
                }
            }
        }.runTaskTimerAsynchronously(NightMare.getInstance(), 20L, 5L);
    }

    private void spawnBossAura(Location loc, String type) {
        if (type == null) return;
        World w = loc.getWorld();
        switch (type) {
            case "WIND" -> w.spawnParticle(Particle.CLOUD, loc, 5, 0.3, 0.5, 0.3, 0.05);
            case "FIRE" -> w.spawnParticle(Particle.FLAME, loc, 5, 0.3, 0.5, 0.3, 0.02);
            case "WOOD" -> w.spawnParticle(Particle.HAPPY_VILLAGER, loc, 5, 0.3, 0.5, 0.3, 0.01);
            case "WATER" -> w.spawnParticle(Particle.BUBBLE_POP, loc, 5, 0.3, 0.5, 0.3, 0.02);
        }
    }

    private void startBossSkillTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                synchronized (activeBosses) {
                    for (LivingEntity boss : activeBosses) {
                        if (boss.isDead() || !boss.isValid()) continue;
                        String type = boss.getPersistentDataContainer().get(BOSS_TYPE_KEY, PersistentDataType.STRING);
                        int level = boss.getPersistentDataContainer().getOrDefault(BOSS_LEVEL_KEY, PersistentDataType.INTEGER, 1);
                        boss.getNearbyEntities(8, 8, 8).stream()
                                .filter(e -> e instanceof Player p && p.getGameMode() == GameMode.SURVIVAL)
                                .map(e -> (Player) e)
                                .forEach(p -> triggerSkill(boss, p, type, level));
                    }
                }
            }
        }.runTaskTimer(NightMare.getInstance(), 100L, 100L);
    }
    /* boss.getNearbyEntities(8, 8, 8).stream()
                                .filter(e -> e instanceof Player p && p.getGameMode() == GameMode.SURVIVAL)
                                .map(e -> (Player) e)
                                .forEach(p -> triggerSkill(boss, p, type, level));*/

    private void triggerSkill(LivingEntity boss, Player p, String type, int level) {
        double damage = 3.0;
        switch (type) {
            case "WIND" -> {
                p.setVelocity(new org.bukkit.util.Vector(0, 1.2, 0));
                p.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 20, 2));
            }
            case "FIRE" -> {
                p.damage(damage, boss);
                p.setFireTicks(80);
            }
            case "WATER" -> {
                p.damage(damage, boss);
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 2));
            }
            case "WOOD" -> {
                p.damage(damage, boss);
                p.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 60, 0));
            }
        }
    }

    public void cleanup() {
        synchronized (activeBosses) {
            activeBosses.forEach(Entity::remove);
            activeBosses.clear();
        }
    }
}