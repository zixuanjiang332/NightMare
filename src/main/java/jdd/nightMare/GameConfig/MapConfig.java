package jdd.nightMare.GameConfig;

import jdd.nightMare.Game.ResourceSpawner;
import jdd.nightMare.NightMare;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MapConfig {

    private final static File file = new File(NightMare.getInstance().getDataFolder(), "map_data.yml");
    private static YamlConfiguration config;

    public static void init(){
        if (!file.exists())
            NightMare.getInstance().saveResource("map_data.yml", false);
        load();

    }
    public static void load(){
        config = YamlConfiguration.loadConfiguration(file);
    }
    public static YamlConfiguration getMapConfig(){
        return config;
    }

    public static void save(){
        try {
            config.save(file);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        load();
    }

    public static List<String> getMaps(){
        List<String> maps = new ArrayList<>();
        for (String map : config.getKeys(false)){
            File mapFolder = new File(new File(NightMare.getInstance().getDataFolder(),"maps"), map);
            if (mapFolder.exists())
                maps.add(map);
        }
        return maps;
    }


    public static List<Double> getSpectatorTeleportLocation(String map){
        return config.getDoubleList(map + ".spectator-teleport-location");
    }

    public static List<List<Double>> getTeamSpawnCoordinates(String map){
        ConfigurationSection teamsSection = config.getConfigurationSection(map + ".team-spawn-locations");
        List<List<Double>>locations = new ArrayList<>();
        for (String keys: teamsSection.getKeys(false)){
            List< Double> loca = teamsSection.getDoubleList(keys);
            locations.add(loca);
        }
        return locations;
    }

    public static String getID(String map){
        return config.getString(map + ".id");
    }

}
