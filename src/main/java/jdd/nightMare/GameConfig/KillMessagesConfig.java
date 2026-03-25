package jdd.nightMare.GameConfig;
import jdd.nightMare.NightMare;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Set;

public class KillMessagesConfig {

    private static final File file = new File(NightMare.getInstance().getDataFolder(), "kill_messages.yml");
    private static FileConfiguration config;

    public static void init(){
        if (!file.exists())
            NightMare.getInstance().saveResource("kill_messages.yml", false);
        load();

    }

    public static void load() {
        config = YamlConfiguration.loadConfiguration(file);
    }

    public static @NotNull Set<String> getMessageKeys(){
        return config.getKeys(false);
    }

    public static String getMessage(String key){
        return config.getString(key + ".message");
    }

    public static String getDisplayName(String key){
        return config.getString(key + ".display-name");
    }

    public static String getMaterial(String key){
        return config.getString(key + ".material");
    }

    public static String getMessage(String key, String killer, String victim){
        return getMessage(key).replaceAll("<killer>", killer).replaceAll("<victim>", victim);
    }

}