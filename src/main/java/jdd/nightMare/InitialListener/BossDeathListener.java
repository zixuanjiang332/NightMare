package jdd.nightMare.InitialListener;
import jdd.nightMare.Game.Game;
import jdd.nightMare.Game.GameManager;
import jdd.nightMare.Game.GameTeam;
import jdd.nightMare.Game.PlayerSession;
import jdd.nightMare.NightMare;
import jdd.nightMare.tasks.BossManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wither;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class BossDeathListener implements Listener {
    private final GameManager gameManager;
    private final Random random = new Random();

    public BossDeathListener(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    @EventHandler
    public void onBossDeath(EntityDeathEvent event) {
        LivingEntity boss = event.getEntity();

        // 1. 检查 PDC 标记
        if (!boss.getPersistentDataContainer().has(BossManager.BOSS_TYPE_KEY, PersistentDataType.STRING)) return;

        String type = boss.getPersistentDataContainer().get(BossManager.BOSS_TYPE_KEY, PersistentDataType.STRING);
        int level = boss.getPersistentDataContainer().getOrDefault(BossManager.BOSS_LEVEL_KEY, PersistentDataType.INTEGER, 1);
        Player killer = boss.getKiller();
        // 2. 清除僵尸原本的掉落物（如腐肉）
        event.getDrops().clear();
        // 3. 【完善】 生成符合要求的随机附魔书
        for (int i=0;i<random.nextInt(1)+2;i++){
            event.getDrops().add(generateSpecificRandomBook(level));
        }
        // 提示 Boss 死亡
        String bossName = boss.getCustomName() != null ? boss.getCustomName() : "Boss";
        Bukkit.broadcast(Component.text("§6[NightMare] " + bossName + " §f被击杀了！击杀者: §e" + (killer != null ? killer.getName() : "未知")));

        // 4. 【完善】 给队伍全员发放 Buff
        if (killer != null && gameManager.hasActiveSession(killer)) {
            Game game = gameManager.getPlayerSession(killer).getGame();
            GameTeam team = gameManager.getPlayerSession(killer).getGameTeam();
            if (team != null) {
                applyTeamBuffsToAllMembers(game, team, type, level);
            }
        }
    }
    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        PlayerSession session = gameManager.getPlayerSession(player);
        if (session != null && !session.getPersistentEffects().isEmpty()) {
            // 延迟 1 tick 赋予，确保重生流程完成
            org.bukkit.Bukkit.getScheduler().runTaskLater(jdd.nightMare.NightMare.getInstance(), () -> {
                long now = System.currentTimeMillis();

                // 使用迭代器方便在遍历时移除已过期的 Buff
                java.util.Iterator<PlayerSession.PersistentEffect> iterator = session.getPersistentEffects().iterator();

                while (iterator.hasNext()) {
                    PlayerSession.PersistentEffect pEffect = iterator.next();

                    // 计算剩余时长 (毫秒)
                    long remainingMs = pEffect.expiryTimestamp - now;

                    if (remainingMs > 0) {
                        // 转换为 tick (1 tick = 50ms)
                        int remainingTicks = (int) (remainingMs / 50);

                        // 重新施加剩余时长的 Buff
                        player.addPotionEffect(new PotionEffect(pEffect.type, remainingTicks, pEffect.amplifier));
                    } else {
                        // 如果时间已经到了，从追踪列表中移除
                        iterator.remove();
                    }
                }
            }, 1L);
        }
    }
    public void applyBossBuff(Player player, PotionEffectType type, int amplifier, int durationTicks) {
        PlayerSession session = gameManager.getPlayerSession(player);
        if (session != null) {
            // 1. 正常给予原版药水效果
            player.addPotionEffect(new PotionEffect(type, durationTicks, amplifier));
            // 2. 在 Session 中记录过期时间戳，用于死亡重生后的比对
            session.trackBossBuff(type, amplifier, durationTicks);
        }
    }
    @EventHandler
    public void onMidBossDeath(EntityDeathEvent event) {
        // 检查是否是凋零
        if (!(event.getEntity() instanceof Wither wither)) return;

        // 检查 PDC 标记
        if (!wither.getPersistentDataContainer().has(BossManager.BOSS_ROLE_KEY, PersistentDataType.STRING)) return;
        String role = wither.getPersistentDataContainer().get(BossManager.BOSS_ROLE_KEY, PersistentDataType.STRING);
        if ("MID_BOSS".equals(role)) {
            Player killer = wither.getKiller();
            if (killer == null) {
                int bookCount = random.nextInt(2) + 2; // 2 或 3
                for (int i = 0; i < bookCount; i++) {
                    ItemStack book = createRandomEnchantedBook();
                    wither.getLocation().getWorld().dropItemNaturally(wither.getLocation(), book);
                }

                event.getDrops().clear();
                return;
            }
            // 1. 发放全队 Buff (力量 I, 4分钟 = 4800 Ticks)
            GameTeam team = gameManager.getPlayerSession(killer).getGame().getTeam(killer);
            if (team != null) {
                for (Player member : team.getTeamPlayers()) {
                    applyBossBuff(member,PotionEffectType.STRENGTH, 0, 4800);
                    member.sendMessage("§d§l[战利品] §f你的队伍击败了中岛 Boss，获得 4 分钟力量 I 增益！");
                }
            }

            // 2. 生成掉落物 (2-3 本强力附魔书)
            int bookCount = random.nextInt(2) + 2; // 2 或 3
            for (int i = 0; i < bookCount; i++) {
                ItemStack book = createRandomEnchantedBook();
                wither.getLocation().getWorld().dropItemNaturally(wither.getLocation(), book);
            }
            // 清除原版掉落（下界之星）
            event.getDrops().clear();
        }
    }

    private ItemStack createRandomEnchantedBook() {
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        EnchantmentStorageMeta meta = (EnchantmentStorageMeta) book.getItemMeta();

        // 定义随机池
        List<Enchantment> pool = new ArrayList<>(List.of(
                Enchantment.SHARPNESS,      // 锋利
                Enchantment.POWER,          // 力量 (弓)
                Enchantment.PROTECTION,     // 保护
                Enchantment.FIRE_ASPECT     // 火焰附加
        ));

        // 随机选一个
        Collections.shuffle(pool);
        Enchantment selected = pool.get(0);

        // 设置等级：锋利/保护/力量给 4-5 级，火焰附加给 2 级
        int level = 1;
        if (selected == Enchantment.FIRE_ASPECT) {
            level = 2;
        }
        else if (selected==Enchantment.POWER){
            level = 2;
        }
            else {
            level = random.nextInt(2) + 3; // 4 或 5
        }

        meta.addStoredEnchant(selected, level, true);
        book.setItemMeta(meta);
        return book;
    }

    private ItemStack generateSpecificRandomBook(int bossLevel) {
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        EnchantmentStorageMeta meta = (EnchantmentStorageMeta) book.getItemMeta();

        // 1. 定义附魔池
        List<Enchantment> pool = new ArrayList<>(List.of(
                Enchantment.SHARPNESS,       // 锋利
                Enchantment.EFFICIENCY,      // 效率
                Enchantment.PROTECTION,      // 保护
                Enchantment.PROJECTILE_PROTECTION,
                Enchantment.POWER,           // 力量
                Enchantment.FIRE_ASPECT,     // 火焰附加
                Enchantment.FLAME           //火矢
        ));

        // 2. 随机选择一个附魔
        Enchantment selected = pool.get(random.nextInt(pool.size()));
        // 3. 【完善】 设定附魔等级：
        // 1级Boss掉落 I-II 级书； 2级Boss掉落 III-IV 级书。 (5级太强，平衡一下)
        int minLvl = (bossLevel == 1) ? 1 : 3;
        int maxLvl = (bossLevel == 1) ? 2 : 4;
        // 针对最高只有2级的附魔(如火焰附加)做特殊处理
        int enchantLevel;
        if (selected.getMaxLevel() < maxLvl) {
            enchantLevel = selected.getMaxLevel();
        } else {
            // 生成 minLvl 到 maxLvl 之间的随机整数
            enchantLevel = random.nextInt((maxLvl - minLvl) + 1) + minLvl;
        }
        if(selected == Enchantment.POWER){
            if (random.nextInt(5)<2)enchantLevel=2;
            else enchantLevel=1;
        }
        if(selected == Enchantment.FLAME){
            if (random.nextInt(5)<2)enchantLevel=2;
            else enchantLevel=1;
        }
        meta.addStoredEnchant(selected, enchantLevel, true);
        book.setItemMeta(meta);
        return book;
    }
    /**
     * 【完善】 给队伍所有成员施加祝福
     */
    private void applyTeamBuffsToAllMembers(Game game, GameTeam team, String bossType, int bossLevel) {
        PotionEffectType effectType = switch (bossType) {
            case "WIND" -> PotionEffectType.SPEED;           // 速度
            case "FIRE" -> PotionEffectType.HASTE;           // 急迫
            case "WOOD" -> PotionEffectType.RESISTANCE;      // 抵抗
            case "WATER" -> PotionEffectType.REGENERATION; // 生命恢复
            default -> PotionEffectType.GLOWING;
        };

        int amplifier = bossLevel - 1; // 1级Boss给I级果(0)，2级Boss给II级效果(1)
        int durationTicks = 20 * 180; // 持续3分钟 (180秒)

        String typeDesc = switch (bossType) {
            case "WIND" -> "苍穹(速度)";
            case "FIRE" -> "炼狱(急迫)";
            case "WOOD" -> "大地(抵抗)";
            case "WATER" -> "潮汐(恢复)";
            default -> "未知";
        };

        // 遍历该队伍在游戏中的所有成员
        for (Player member : team.getTeamPlayers()) {
            // 确保玩家在线且存活 (没有被清理出 game.getGamePlayers())
            if (member.isOnline() && game.getGamePlayers().containsKey(member)) {
                // 强制应用 Buff
                applyBossBuff(member, effectType,amplifier , durationTicks);
                // 视觉和声音反馈
                member.sendMessage("§a[守卫祝福] §f由于击杀了 §e" + typeDesc + " §fBoss，全队获得了 Blessing " + (bossLevel == 1 ? "I" : "II") + "！");
                member.playSound(member.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.2f);
            }
        }
    }
}