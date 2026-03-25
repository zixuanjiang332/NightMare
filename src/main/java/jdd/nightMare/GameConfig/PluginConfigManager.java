package jdd.nightMare.GameConfig;

import jdd.nightMare.NightMare;
import jdd.nightMare.NightMare;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

public class PluginConfigManager {

    private static final File file = new File(NightMare.getInstance().getDataFolder(), "config.yml");
    private static FileConfiguration config = NightMare.getInstance().getConfig();

    public static void init(){
        if (!file.exists())
            NightMare.getInstance().saveResource("config.yml", false);
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

    public static FileConfiguration getConfig() {
        return config;
    }

    public static void setLobbyLocation(Location location){
        config.set("lobby-settings.lobby-location", location);
        save();
    }

    public static Location getLobbyLocation(){
        return (Location) config.get("lobby-settings.lobby-location");
    }

    public static void setCageMaterial(String material){
        config.set("cage-material", material);
        save();
    }

    public static Material getCageMaterial(){
        return Material.valueOf(config.getString("cage-material"));
    }

    public static boolean isInvulnerableInLobby(){
        return config.getBoolean("lobby-settings.invulnerable-in-lobby");
    }

    public static boolean getCanPickUpItemsInLobby(){
        return config.getBoolean("lobby-settings.can-pickup-items");
    }

    public static boolean canDropItemsInLobby(){
        return config.getBoolean("lobby-settings.can-drop-items");
    }

    public static boolean canPlaceBlocksInLobby(){
        return config.getBoolean("lobby-settings.can-place-blocks");
    }

    public static boolean canBreakBlocksInLobby(){
        return config.getBoolean("lobby-settings.can-break-blocks");
    }

    public static boolean canPickupExpOrbsInLobby(){
        return config.getBoolean("lobby-settings.can-pickup-exp");
    }

    public static boolean sendActionFailedMessageInLobby(){
        return config.getBoolean("lobby-settings.send-action-failed-message");
    }

}
