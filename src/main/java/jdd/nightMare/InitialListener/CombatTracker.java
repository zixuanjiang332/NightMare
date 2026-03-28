package jdd.nightMare.InitialListener;

import jdd.nightMare.Game.Game;
import jdd.nightMare.Game.GameManager;
import jdd.nightMare.Game.PlayerSession;
import jdd.nightMare.NightMare;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Sound;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CombatTracker implements Listener {

    // 存储玩家最后一次受到的有效攻击
    private final Map<UUID, DamageRecord> damageTracker = new HashMap<>();
    private final NightMare plugin;
    private final GameManager gameManager;// 你的主类实例

    public CombatTracker(NightMare plugin,GameManager gameManager) {
        this.plugin = plugin;
        this.gameManager = gameManager;
    }
    public void playKillSound(Player killer) {
        // 使用箭矢命中玩家的声音，但音调极高，非常清脆
        killer.playSound(killer.getLocation(), Sound.ENTITY_ARROW_HIT_PLAYER, 1.0f, 0.5f); // 这是一个闷响
        // 叠加一个高频音
        killer.playSound(killer.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);
    }

    /**
     * 第一部分：记录伤害来源
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;

        Entity damager = event.getDamager();
        Player attacker = null;

        // 1. 近战攻击
        if (damager instanceof Player) {
            attacker = (Player) damager;
        }
        // 2. 远程射击 (弓箭、雪球等)
        else if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player) {
            attacker = (Player) projectile.getShooter();
        }
        // 3. TNT 爆炸 (读取我们在生成 TNT 时打上的标记)
        else if (damager instanceof TNTPrimed tnt) {
            if (tnt.hasMetadata("source_player")) {
                String uuidStr = tnt.getMetadata("source_player").get(0).asString();
                attacker = Bukkit.getPlayer(UUID.fromString(uuidStr));
            }
        }

        // 如果找到了合法的攻击者，且不是自己炸自己/射自己
        if (attacker != null && !attacker.equals(victim)) {
            damageTracker.put(victim.getUniqueId(), new DamageRecord(
                    attacker.getUniqueId(),
                    System.currentTimeMillis(),
                    event.getCause()
            ));
        }
    }

    /**
     * 第二部分：接管死亡判定
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        event.deathMessage(null); // 清除原版死亡消息
        Player trueKiller = victim.getKiller(); // 先尝试获取原版的
        // 如果原版获取不到（虚空、摔死、TNT炸死往往获取不到）
        if (trueKiller == null) {
            DamageRecord record = damageTracker.get(victim.getUniqueId());
            // 检查记录是否存在且在有效时间(15秒)内
            if (record != null && record.isValid()) {
                Player potentialKiller = Bukkit.getPlayer(record.attackerId());
                if (potentialKiller != null && potentialKiller.isOnline()) {
                    trueKiller = potentialKiller;
                }
            }
        }
        // --- 处理最终的击杀逻辑 ---
        if (trueKiller != null) {
            playKillSound(trueKiller);
            gameManager.getPlayerSession(trueKiller).addKill();
            gameManager.getPlayerSession(trueKiller).getGame().updateKills(trueKiller);
            double killerHealth = trueKiller.getHealth();
            Bukkit.broadcastMessage("§7[NightMare] §e" + getColoredName(victim) + " §7被 §c" + getColoredName(trueKiller) + " §7杀死了 §8(§c" + (int)killerHealth + " HP§8)");
        } else {
            // 真正的自然死亡/自杀
            Bukkit.broadcastMessage("§7[NightMare] §e" + getColoredName( victim) + " §7意外死亡。");
        }
        // 玩家死后清除他的受击记录，避免复活后直接掉虚空算在上一个人头上
        damageTracker.remove(victim.getUniqueId());
    }
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        damageTracker.remove(event.getPlayer().getUniqueId());
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
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerHurt(EntityDamageEvent event){
        if (!(event.getEntity() instanceof Player player)) return;
        if (!gameManager.hasActiveSession(player)) {
            event.setCancelled(true);
            return;
        }
        Game game = gameManager.getPlayerSession(player).getGame();
        if (!game.isActive() || game.getSpectators().contains(player)){
            event.setCancelled(true);
            return;
        }
        if (event instanceof EntityDamageByEntityEvent edbeEvent) {
            Player attacker = null;
            if (edbeEvent.getDamager() instanceof Player p) {
                attacker = p;
            } else if (edbeEvent.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player p) {
                attacker = p;
            }
            if (attacker != null) {
                if (game.isSpectator(attacker) || game.getTeam(player) == game.getTeam(attacker)) {
                    event.setCancelled(true);
                } else {
                    if (event.isCancelled()) {
                        event.setCancelled(false);
                    }
                }
            }
        }
    }
}