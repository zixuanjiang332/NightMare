package jdd.nightMare.Game;

import jdd.nightMare.InitialListener.SpecialItemsListener;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

public class PlayerSession {

    private final Player player;
    private GameTeam team=null;
    private final Game game;
    private int kills;
    private boolean isDead;
    private final Set<PotionEffect> bossBuffs = new HashSet<>();
    private final List<String> activeBrands = new ArrayList<>();
    private final Map<String, Integer> brandCooldowns = new HashMap<>();
    private final Map<String,Integer>specialItemCooldowns= new HashMap<>();
    private final String [] specialItems=new String[]{"回城卷轴","行走平台","自救平台","蹦床","降落伞"};
    public PlayerSession(Player player, Game game) {
        this.player = player;
        this.game = game;
        this.kills = 0;
        this.isDead = false;
        for (String specialItem : specialItems) {
            specialItemCooldowns.put(specialItem, 0);
        }
    }
    public Map<String, Integer> getSpecialItemCooldowns() {
        return specialItemCooldowns;
    }


    public static class PersistentEffect {
        public final PotionEffectType type;
        public final int amplifier;
        public final long expiryTimestamp; // 计算出来的到期时间戳

        public PersistentEffect(PotionEffectType type, int amplifier, int durationTicks) {
            this.type = type;
            this.amplifier = amplifier;
            // 1 tick = 50ms，计算该 Buff 应该在什么时候消失
            this.expiryTimestamp = System.currentTimeMillis() + (durationTicks * 50L);
        }
    }

    // 存储列表
    private final List<PersistentEffect> persistentEffects = new ArrayList<>();

    public void trackBossBuff(PotionEffectType type, int amplifier, int durationTicks) {
        this.persistentEffects.add(new PersistentEffect(type, amplifier, durationTicks));
    }

    public List<PersistentEffect> getPersistentEffects() {
        return persistentEffects;
    }

    public void clearPersistentEffects() {
        this.persistentEffects.clear();
    }
    public boolean toggleBrand(String brandId) {
        if (activeBrands.contains(brandId)) {
            activeBrands.remove(brandId); // 再次点击取消
            brandCooldowns.remove(brandId);
            return true;
        } else {
            if (activeBrands.size() >= 6) return false; // 超过6个
            activeBrands.add(brandId);
            brandCooldowns.put(brandId, 0); // 初始0冷却
            return true;
        }
    }

    public boolean hasBrand(String brandId) {
        return activeBrands.contains(brandId);
    }
    /** 检查冷却是否完毕 */
    public boolean isBrandReady(String brandId) {
        return brandCooldowns.getOrDefault(brandId, 0) <= 0;
    }
    /** 触发烙印后，手动设置冷却 */
    public void setBrandCooldown(String brandId, int seconds) {
        brandCooldowns.put(brandId, seconds);
    }
    /** 每秒执行一次，降低所有技能冷却 */
    public void tickBrandCooldowns() {
        for (String key : new ArrayList<>(brandCooldowns.keySet())) {
            int cd = brandCooldowns.get(key);
            if (cd > 0) {
                brandCooldowns.put(key, cd - 1);
            }
        }
    }
    public void tickSpeicalItemCooldowns(){
        for (String key : new ArrayList<>(specialItemCooldowns.keySet())) {
            int cd = specialItemCooldowns.get(key);
            if (cd > 0) {
                specialItemCooldowns.put(key, cd - 1);
            }
        }
    }

    public List<String> getActiveBrands() {
        return activeBrands;
    }
    public void setGameTeam (GameTeam team){
        this.team = team;
    }
    public GameTeam getGameTeam(){
        return team;
    }
    public int getKills() {
        return kills;
    }

    public void addKill() {
        this.kills++;
    }

    public boolean isDead() {
        return isDead;
    }

    public void markAsDead() {
        this.isDead = true;
    }

    public void markAsAlive(){
        this.isDead = false;
    }

    public Game getGame() {
        return game;
    }

    public Player getPlayer() {
        return player;
    }
}
