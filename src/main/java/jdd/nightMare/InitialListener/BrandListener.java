package jdd.nightMare.InitialListener;
import jdd.nightMare.Game.BrandType;
import jdd.nightMare.Game.GameManager;
import jdd.nightMare.Game.PlayerSession;
import jdd.nightMare.Shop.BrandGUI;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.inventory.ItemStack;

import java.util.Random;

public class BrandListener implements Listener {
    private final GameManager gameManager;
    private final Random random = new Random();
    private final BrandGUI brandGUI;

    public BrandListener(GameManager gameManager, BrandGUI brandGUI) {
        this.gameManager = gameManager;
        this.brandGUI = brandGUI;
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

    // 监听攻击事件 (自适应、腐败、枷锁、钢铁之心)
    @EventHandler
    public void onCombat(EntityDamageByEntityEvent event) {
        // --- 处理攻击方烙印 ---
        if (event.getDamager() instanceof Player attacker && event.getEntity() instanceof LivingEntity victim) {
            PlayerSession session = gameManager.getPlayerSession(attacker);
            if (session != null) {
                if ((event.getEntity() instanceof Player victimer)){
                    if (isTeammate(attacker, victimer)){
                        return;
                    }
                }
                // 1. 自适应五级
                if (session.hasBrand(BrandType.ADAPTIVE_5.id) && session.isBrandReady(BrandType.ADAPTIVE_5.id)) {
                    double maxHp = attacker.getAttribute(Attribute.MAX_HEALTH).getValue();
                    if (attacker.getHealth() / maxHp >= 0.8) {
                        if (random.nextDouble() <= 0.1) {
                            event.setDamage(event.getDamage() * 1.2);
                            session.setBrandCooldown(BrandType.ADAPTIVE_5.id, BrandType.ADAPTIVE_5.maxCooldown);
                            attacker.sendMessage("§c[NightMare] 触发了 自适应五级！伤害提升！");
                        }
                    }
                }
                // 2. 致盲术 (BLIND_STRIKE)
                if (session.hasBrand(BrandType.BLIND_STRIKE.id) && session.isBrandReady(BrandType.BLIND_STRIKE.id)) {
                    if (random.nextDouble() <= 0.15) { // 15% 概率
                        victim.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 100, 0)); // 5秒
                        session.setBrandCooldown(BrandType.BLIND_STRIKE.id, BrandType.BLIND_STRIKE.maxCooldown);
                        attacker.sendMessage("§8[NightMare] 致盲术生效！敌人已陷入黑暗。");
                        attacker.playSound(attacker.getLocation(), org.bukkit.Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1f, 1f);
                    }
                }
            // 3. 奔雷怒吼 (THUNDER_ROAR)
                if (session.hasBrand(BrandType.THUNDER_ROAR.id) && session.isBrandReady(BrandType.THUNDER_ROAR.id)) {
                    if (random.nextDouble() <= 0.10) { // 10% 概率
                        victim.getWorld().strikeLightningEffect(victim.getLocation());
                        // 造成额外 4 点真实伤害 (忽略护甲)
                        victim.damage(3.0, attacker);
                        session.setBrandCooldown(BrandType.THUNDER_ROAR.id, BrandType.THUNDER_ROAR.maxCooldown);
                        attacker.sendMessage("§e[NightMare] 奔雷怒吼！召唤雷霆一击。");
                    }
                }
                // 5. 枷锁制裁
                if (session.hasBrand(BrandType.SHACKLES.id) && session.isBrandReady(BrandType.SHACKLES.id)) {
                    if (random.nextDouble() <= 0.15) { // 15% 概率
                        victim.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 60, 0));
                        session.setBrandCooldown(BrandType.SHACKLES.id, BrandType.SHACKLES.maxCooldown);
                        attacker.sendMessage("§8[NightMare] 触发了 枷锁制裁！");
                    }
                }
                // 6. 震击
                if (session.hasBrand(BrandType.SHOCKWAVE.id) && session.isBrandReady(BrandType.SHOCKWAVE.id)) {
                    if (random.nextDouble() <= 0.2) { // 15% 概率
                        // 计算击退向量：从攻击者指向受害者的水平向量
                        org.bukkit.util.Vector direction = victim.getLocation().toVector()
                                .subtract(attacker.getLocation().toVector())
                                .setY(0) // 先清空Y轴，保证水平推力方向纯正
                                .normalize();
                        org.bukkit.util.Vector velocity = direction.multiply(1.2).setY(0.2);

                        victim.setVelocity(velocity);
                        // 视觉与音效反馈
                        victim.getWorld().spawnParticle(org.bukkit.Particle.EXPLOSION, victim.getLocation().add(0, 1, 0), 1);
                        attacker.playSound(attacker.getLocation(), org.bukkit.Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 1f, 0.5f);
                        attacker.sendMessage("§6[NightMare] 触发了 震撼！敌人被扇飞了！");
                        session.setBrandCooldown(BrandType.SHOCKWAVE.id, BrandType.SHOCKWAVE.maxCooldown);
                    }
                }
            }
        }

        // --- 处理受击方烙印 ---
        if (event.getEntity() instanceof Player victim) {
            PlayerSession session = gameManager.getPlayerSession(victim);
            if (session != null) {
                if ((event.getDamager() instanceof Player attacker)){
                    if (isTeammate(attacker, victim)){
                        return;
                    }
                }
                // 6. 钢铁之心
                if (session.hasBrand(BrandType.HEART_OF_STEEL.id) && session.isBrandReady(BrandType.HEART_OF_STEEL.id)) {
                    // 给予两颗黄心(Amplifier 0 = 4点生命吸收) 持续10秒
                    victim.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 200, 0));
                    session.setBrandCooldown(BrandType.HEART_OF_STEEL.id, BrandType.HEART_OF_STEEL.maxCooldown);
                    victim.sendMessage("§7[NightMare] 钢铁之心已激活！获得临时护盾！");
                }
            }
            // 7. 屏障 (BARRIER)
            if (session.hasBrand(BrandType.BARRIER.id) && session.isBrandReady(BrandType.BARRIER.id)) {
                double maxHp = victim.getAttribute(Attribute.MAX_HEALTH).getValue();
                // 受击后血量低于 50% 触发
                if (victim.getHealth() / maxHp <= 0.5) {
                    victim.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 200, 2));
                    session.setBrandCooldown(BrandType.BARRIER.id, BrandType.BARRIER.maxCooldown);
                    victim.sendMessage("§6[NightMare] 屏障激活！获得大量额外护盾。");
                    victim.playSound(victim.getLocation(), org.bukkit.Sound.ITEM_ARMOR_EQUIP_DIAMOND, 1f, 1f);
                }
            }

            // 8. 第二回合 (SECOND_ROUND)
            if (session.hasBrand(BrandType.SECOND_ROUND.id) && session.isBrandReady(BrandType.SECOND_ROUND.id)) {
                // 濒死状态 (血量低于 4点 / 2颗心)
                if (victim.getHealth() <= 4.0 && random.nextDouble() <= 0.05) { // 5% 概率
                    if (victim.getInventory().getItemInOffHand().getType() == Material.AIR) {
                        victim.getInventory().setItemInOffHand(new ItemStack(Material.TOTEM_OF_UNDYING));
                        session.setBrandCooldown(BrandType.SECOND_ROUND.id, BrandType.SECOND_ROUND.maxCooldown);
                        victim.sendMessage("§d[NightMare] 第二回合！命运之神给予了你图腾。");
                        victim.playSound(victim.getLocation(), org.bukkit.Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.5f);
                    }
                }
            }
        }
    }
    @EventHandler
    public void onDamage(EntityDamageEvent event) {
    if (!(event.getEntity() instanceof Player player)) return;
    PlayerSession session = gameManager.getPlayerSession(player);
    if (session == null) return;
    // 9. 强行着陆 (HARD_LANDING)
    if (event.getCause() == org.bukkit.event.entity.EntityDamageEvent.DamageCause.FALL) {
        if (session.hasBrand(BrandType.HARD_LANDING.id) && session.isBrandReady(BrandType.HARD_LANDING.id)) {
            event.setCancelled(true); // 免疫本次摔落伤害
            session.setBrandCooldown(BrandType.HARD_LANDING.id, BrandType.HARD_LANDING.maxCooldown);
            player.sendMessage("§b[NightMare] 强行着陆生效！本次摔落伤害已免疫。");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 1f, 2f);
        }
    }
}
    @EventHandler
    public void onUseSelector(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (item != null && item.getType() == Material.NETHER_STAR) {
            // 打开 GUI
            brandGUI.open(event.getPlayer());
        }
    }
    // 监听放置方块事件 (白羊)
    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        PlayerSession session = gameManager.getPlayerSession(player);
        if (session == null) return;

        // 检查是不是放的羊毛
        if (event.getBlockPlaced().getType().name().endsWith("_WOOL")) {
            if (session.hasBrand(BrandType.ARIES.id)) {
                if (random.nextDouble() <= 0.10) { // 10%概率返还
                    // 延迟 1 tick 给予，防止与原版物品消耗冲突
                    org.bukkit.Bukkit.getScheduler().runTaskLater(jdd.nightMare.NightMare.getInstance(), () -> {
                        player.getInventory().addItem(new ItemStack(event.getBlockPlaced().getType(), 1));
                        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.5f);
                    }, 1L);
                }
            }
        }
    }
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        PlayerSession session = gameManager.getPlayerSession(victim);

        // 1. 检查死者是否拥有“腐败 V”烙印且冷却已好
        if (session != null && session.hasBrand(BrandType.CORRUPTION_5.id)
                && session.isBrandReady(BrandType.CORRUPTION_5.id)) {
            if (new Random().nextDouble() <= 1) {
                double radius = 4.0; // 爆炸波及半径
                Location deathLoc = victim.getLocation();
                // 2. 获取周围实体
                for (Entity entity : victim.getNearbyEntities(radius, radius, radius)) {
                    if (entity instanceof Player nearbyPlayer) {
                        if (!isTeammate(victim, nearbyPlayer)) {
                            nearbyPlayer.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 60, 1));
                            nearbyPlayer.sendMessage("§5[NightMare] 你受到了 " + victim.getName() + " 临死前的腐败诅咒！");
                        }
                    }
                }
                // 4. 设置冷却并给死者反馈（如果他还没点重生）
                session.setBrandCooldown(BrandType.CORRUPTION_5.id, BrandType.CORRUPTION_5.maxCooldown);
                victim.sendMessage("§5[NightMare] 腐败已触发！");
            }
        }
    }
}