package jdd.nightMare.GameConfig;

import jdd.nightMare.Game.GameConfiguration;
import jdd.nightMare.NightMare;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;

public class PlayerConfig {

    private static final File file = new File(NightMare.getInstance().getDataFolder(), "player_data.yml");
    private static FileConfiguration config;

    public static void init(){
        if (!file.exists())
            NightMare.getInstance().saveResource("player_data.yml", false);
        load();

    }

    public static void load(){
        config = YamlConfiguration.loadConfiguration(file);
    }

    public static void save(){
        try {
            config.save(file);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static int getKills(Player player, GameConfiguration gameConfig){
        return config.getInt(player.getName() + ".stats.%s.kills".formatted(gameConfig.getName()));
    }

    public static int getWins(Player player, GameConfiguration gameConfig){
        return config.getInt(player.getName() + ".stats.%s.wins".formatted(gameConfig.getName()));
    }

    public static int getLosses(Player player, GameConfiguration gameConfig){
        return config.getInt(player.getName() + ".stats.%s.losses".formatted(gameConfig.getName()));
    }

    public static int getDeaths(Player player, GameConfiguration gameConfig){
        return config.getInt(player.getName() + ".stats.%s.deaths".formatted(gameConfig.getName()));
    }

    public static int getWinStreak(Player player, GameConfiguration gameConfig){
        return config.getInt(player.getName() + ".stats.%s.win-streak".formatted(gameConfig.getName()));
    }

    public static void addKill(Player player, GameConfiguration gameConfig){
        config.set(player.getName() + ".stats.%s.kills".formatted(gameConfig.getName()), getKills(player, gameConfig)+1);
        save();
    }

    public static void addWin(Player player, GameConfiguration gameConfig){
        config.set(player.getName() + ".stats.%s.wins".formatted(gameConfig.getName()), getWins(player, gameConfig)+1);
        addWinStreak(player, gameConfig);
        save();
    }

    public static void addLoss(Player player, GameConfiguration gameConfig){
        config.set(player.getName() + ".stats.%s.losses".formatted(gameConfig.getName()), getLosses(player, gameConfig)+1);
        resetWinStreak(player, gameConfig);
        save();
    }

    public static void addDeath(Player player, GameConfiguration gameConfig){
        config.set(player.getName() + ".stats.%s.deaths".formatted(gameConfig.getName()), getDeaths(player, gameConfig)+1);
        save();
    }

    public static void addWinStreak(Player player, GameConfiguration gameConfig){
        config.set(player.getName() + ".stats.%s.win-streak".formatted(gameConfig.getName()), getWinStreak(player, gameConfig)+1);
        save();
    }

    public static void resetWinStreak(Player player, GameConfiguration gameConfig){
        config.set(player.getName() + ".stats.%s.win-streak".formatted(gameConfig.getName()), 0);
        save();
    }

    public static String getKillMessageKey(Player player){
        return config.getString(player.getName() + ".settings.kill-message-key");
    }

    public static void setKillMessageKey(Player player, String key){
        config.set(player.getName() + ".settings.kill-message-key", key);
        save();
    }

    public static int getXp(Player player){
        return config.getInt(player.getName() + ".progress.xp");
    }

    public static void addXp(Player player, int amount){
        config.set(player.getName() + ".progress.xp", getXp(player) + amount);
        save();
    }

    public static void setXp(Player player, int xp){
        config.set(player.getName() + ".progress.xp", xp);
        save();
    }

    public static int getLevel(Player player){
        return config.getInt(player.getName() + ".progress.level");
    }

    public static void setLevel(Player player, int level){
        config.set(player.getName() + ".progress.level", level);
        save();
    }

    public static void addLevel(Player player){
        setLevel(player, getLevel(player) + 1);
        save();
    }

}
