package jdd.nightMare.GameConfig;

import jdd.nightMare.NightMare;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

public class LevelsConfig {

    private static final File file = new File(NightMare.getInstance().getDataFolder(), "levels.yml");
    private static FileConfiguration config;

    public static void init(){
        if (!file.exists())
            NightMare.getInstance().saveResource("levels.yml", false);
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

    public static HashMap<Integer, Integer> getLevelsAndXp(){
        return (HashMap<Integer, Integer>) config.get("levels");
    }

    public static int getXp(int level){
        return config.getInt("levels." + level);
    }

}
