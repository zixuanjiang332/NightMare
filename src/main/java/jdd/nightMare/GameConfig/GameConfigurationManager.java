package jdd.nightMare.GameConfig;

import jdd.nightMare.Game.GameConfiguration;
import jdd.nightMare.NightMare;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class GameConfigurationManager {

    private final static File file = new File(NightMare.getInstance().getDataFolder(), "game_configs.yml");
    private static FileConfiguration config;

    public static void init(){
        if (!file.exists())
            NightMare.getInstance().saveResource("game_configs.yml", false);
        load();

    }


    public static void load(){
        config = YamlConfiguration.loadConfiguration(file);
    }

    public static void saveConfig(){
        try {
            config.save(file);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        load();
    }

    public static void saveGameConfiguration(GameConfiguration config){
        GameConfigurationManager.config.set("game-config."+config.getName(), config);
        saveConfig();
    }

    public static void deleteGameConfiguration(String name){
        config.set("game-config."+name, null);
        saveConfig();
    }

    public static @Nullable GameConfiguration getGameConfiguration(String name){
        return config.getObject("game-config."+name, GameConfiguration.class);
    }


    public static boolean doesExist(String name){
        return getGameConfiguration(name)!=null;
    }

    public static Set<GameConfiguration> getSavedGameConfigurations(){
        Set<GameConfiguration> configSet = new HashSet<>();
        ConfigurationSection section = config.getConfigurationSection("game-config");

        if (section == null)
            return configSet;

        for (String name : section.getKeys(false)){
            configSet.add(getGameConfiguration(name));
        }

        return configSet;
    }

}
