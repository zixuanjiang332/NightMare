package jdd.nightMare.InitialListener;
import jdd.nightMare.Game.BrandType;
import jdd.nightMare.Game.Game;
import jdd.nightMare.Game.GameManager;
import jdd.nightMare.Game.PlayerSession;
import jdd.nightMare.Shop.BrandGUI;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
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
        if (s1 == null || s2 == null || s1.getGame() == null || s2.getGame() == null) return false;
        var team1 = s1.getGame().getTeam(p1);
        var team2 = s2.getGame().getTeam(p2);
        return team1 != null && team1.equals(team2);
    }
    @EventHandler
    public void onCombat(EntityDamageByEntityEvent event) {
        // --- 处理攻击方烙印 ---
        if (event.getDamager() instanceof Player attacker && event.getEntity() instanceof LivingEntity victim) {
            PlayerSession session = gameManager.getPlayerSession(attacker);
            Game game = session.getGame();
            if (session != null) {
                if ((event.getEntity() instanceof Player victimer)){
                    if (isTeammate(attacker, victimer)){
                        return;
                    }
                }
                // 1. 自适应五级
                if (session.hasBrand(BrandType.ADAPTIVE_5.id) ) {
                    double maxHp = victim.getAttribute(Attribute.MAX_HEALTH).getValue();
                    if (victim.getHealth() / maxHp >= 0.8) {
                            event.setDamage(event.getDamage() * 1.2);
                            attacker.sendMessage("§c[NightMare] 触发了 自适应五级！伤害提升！");
                    }
                }
                //2. 狂怒之火 (FURIOUS_FIRE)
                        if (session != null && session.hasBrand(BrandType.FURIOUS_FIRE.id)) {
                            double maxHealth = victim.getAttribute(Attribute.MAX_HEALTH).getValue();
                            double currentHealth = victim.getHealth();
                            if (currentHealth / maxHealth < 0.30) {
                                event.setDamage(event.getDamage() * 1.20);
                                attacker.playSound(attacker.getLocation(), org.bukkit.Sound.ENTITY_BLAZE_HURT, 1.0f, 2.0f);
                                attacker.sendMessage("§c[狂怒之火] §7触发额外伤害！");
                            }
                        }
                // 3. 致盲术 (BLIND_STRIKE)
                if (session.hasBrand(BrandType.BLIND_STRIKE.id) && session.isBrandReady(BrandType.BLIND_STRIKE.id)) {
                    if (random.nextDouble() <= 0.15) { // 15% 概率
                        victim.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 0));
                        session.setBrandCooldown(BrandType.BLIND_STRIKE.id, BrandType.BLIND_STRIKE.maxCooldown);
                        attacker.sendMessage("§8[NightMare] 致盲术生效！敌人已陷入黑暗。");
                        attacker.playSound(attacker.getLocation(), org.bukkit.Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1f, 1f);
                    }
                }
            // 4. 奔雷怒吼 (THUNDER_ROAR)
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
                    if (random.nextDouble() <= 0.2) {
                        org.bukkit.util.Vector direction = victim.getLocation().toVector()
                                .subtract(attacker.getLocation().toVector())
                                .setY(0)
                                .normalize();
                        org.bukkit.util.Vector velocity = direction.multiply(1.2).setY(0.2);

                        victim.setVelocity(velocity);
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
                    if (random.nextDouble()<=0.3){
                        victim.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 200, 1));
                        session.setBrandCooldown(BrandType.BARRIER.id, BrandType.BARRIER.maxCooldown);
                        victim.sendMessage("§6[NightMare] 屏障激活！获得大量额外护盾。");
                        victim.playSound(victim.getLocation(), org.bukkit.Sound.ITEM_ARMOR_EQUIP_DIAMOND, 1f, 1f);
                    }
                }
            }

            // 8. 第二回合 (SECOND_ROUND)
            if (session.hasBrand(BrandType.SECOND_ROUND.id) && session.isBrandReady(BrandType.SECOND_ROUND.id)) {
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
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        PlayerSession victimSession = gameManager.getPlayerSession(victim);
        if (victimSession == null) return;
            // 烙印：超载 (挨打给急迫)
            if (victimSession.hasBrand(BrandType.OVERLOAD.id) && victimSession.isBrandReady(BrandType.OVERLOAD.id)) {
                victimSession.setBrandCooldown(BrandType.OVERLOAD.id, BrandType.OVERLOAD.maxCooldown);
                victim.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 100, 0)); // 5秒 急迫 I
                victim.sendMessage("§e[烙印] 触发超载，获得急迫！");
            }

            // 烙印：神行 (挨打给速度)
            if (victimSession.hasBrand(BrandType.WINDWALKER.id) && victimSession.isBrandReady(BrandType.WINDWALKER.id)) {
                victimSession.setBrandCooldown(BrandType.WINDWALKER.id, BrandType.WINDWALKER.maxCooldown);
                victim.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, 1)); // 3秒 速度 II
                victim.sendMessage("§b[烙印] 触发神行，获得速度！");
            }
        // 1. 引力崩坏 (弓箭命中)
        if (event.getDamager() instanceof org.bukkit.entity.Arrow arrow && arrow.getShooter() instanceof Player shooter) {
            PlayerSession shooterSession = gameManager.getPlayerSession(shooter);
            if (shooterSession != null && shooterSession.hasBrand(BrandType.GRAVITY_COLLAPSE.id)) {
                if (Math.random() <= 0.10) { // 10% 的概率触发
                    victim.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 40, 0)); // 2秒 漂浮 I
                    shooter.sendMessage("§d[烙印] 引力崩坏触发，敌人已升空！");
                }
            }
        }
        // 2. 刺杀 (近战跳劈)
        else if (event.getDamager() instanceof Player attacker) {
            PlayerSession attackerSession = gameManager.getPlayerSession(attacker);
            if (attackerSession != null && attackerSession.hasBrand(BrandType.ASSASSINATION.id)&& attackerSession.isBrandReady(BrandType.ASSASSINATION.id)) {
                boolean isCrit = !attacker.isOnGround() && attacker.getFallDistance() > 0.0f
                        && !attacker.isInWater() && !attacker.hasPotionEffect(PotionEffectType.BLINDNESS);
                if (isCrit && Math.random() <= 0.08) {
                    event.setDamage(event.getDamage() * 2);
                    attackerSession.setBrandCooldown(BrandType.ASSASSINATION.id, BrandType.ASSASSINATION.maxCooldown);
                    attacker.sendMessage("§4[烙印] 致命刺杀！造成双倍暴击伤害！");
                    attacker.getWorld().playSound(attacker.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 0.5f);
                    attacker.getWorld().spawnParticle(Particle.CRIT, victim.getLocation().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.1);
                }
            }
        }
}
    @EventHandler
    public void onPlayerDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        PlayerSession victimSession = gameManager.getPlayerSession(victim);
        if (victimSession == null) return;
    // 9. 强行着陆 (HARD_LANDING)
        if (event.getCause() == org.bukkit.event.entity.EntityDamageEvent.DamageCause.FALL) {
            if (victimSession.hasBrand(BrandType.HARD_LANDING.id) && victimSession.isBrandReady(BrandType.HARD_LANDING.id)) {
            event.setCancelled(true); // 免疫本次摔落伤害
            victimSession.setBrandCooldown(BrandType.HARD_LANDING.id, BrandType.HARD_LANDING.maxCooldown);
            victim.sendMessage("§b[NightMare] 强行着陆生效！本次摔落伤害已免疫。");
            victim.playSound(victim.getLocation(), org.bukkit.Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 1f, 2f);
        }
    }
}
    @EventHandler
    public void onUseSelector(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (item != null && item.getType() == Material.NETHER_STAR) {
            brandGUI.open(event.getPlayer());
        }
    }
    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        PlayerSession session = gameManager.getPlayerSession(player);
        if (session == null) return;
        if (event.getBlockPlaced().getType().name().endsWith("_WOOL")) {
            if (session.hasBrand(BrandType.ARIES.id)) {
                if (random.nextDouble() <= 0.10) {
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
                            nearbyPlayer.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 100, 1));
                            nearbyPlayer.sendMessage("§5[NightMare] 你受到了 " + victim.getName() + " 临死前的腐败诅咒！");
                        }
                    }
                }
                // 4. 设置冷却并给死者反馈（如果他还没点重生）
                session.setBrandCooldown(BrandType.CORRUPTION_5.id, BrandType.CORRUPTION_5.maxCooldown);
                victim.sendMessage("§5[NightMare] 腐败已触发！");
            }
        }
        if (session != null && session.hasBrand(BrandType.MARTYR.id) && session.isBrandReady(BrandType.MARTYR.id)) {
            session.setBrandCooldown(BrandType.MARTYR.id, BrandType.MARTYR.maxCooldown); // 触发 60 秒冷却
            Location loc = victim.getLocation();
            TNTPrimed tnt = loc.getWorld().spawn(loc, TNTPrimed.class);
            tnt.setYield(3.0F);
            tnt.setFuseTicks(40);

            victim.sendMessage("§c[烙印] 你触发了同归于尽！");
            loc.getWorld().playSound(loc, Sound.ENTITY_WITHER_SPAWN, 0.5f, 1.5f);
        }

        Player killer = victim.getKiller();
        if (killer != null) {
            PlayerSession killerSession = gameManager.getPlayerSession(killer);
            if (killerSession != null && killerSession.hasBrand(BrandType.WAR_STOMP.id) && killerSession.isBrandReady(BrandType.WAR_STOMP.id)) {
                killerSession.setBrandCooldown(BrandType.WAR_STOMP.id, BrandType.WAR_STOMP.maxCooldown);
                killer.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 100, 0));
                killer.sendMessage("§4[烙印] 触发战争践踏，获得力量增益！");
                killer.playSound(killer.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 2.0f);
            }
        }
    }
}