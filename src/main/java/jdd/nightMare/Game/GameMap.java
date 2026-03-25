package jdd.nightMare.Game;
import jdd.nightMare.GameConfig.MapConfig;
import jdd.nightMare.NightMare;
import net.kyori.adventure.util.TriState;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Villager;
import org.bukkit.persistence.PersistentDataType;
import org.codehaus.plexus.util.FileUtils;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GameMap {

    private final File sourceWorldFolder;
    private final String worldName;
    private File activeWorldFolder;
    private World bukkitWorld;
    private final Map<String, Location> teamBedLocations = new HashMap<>();
    private final List<Location> sideLocations = new ArrayList<>();
    public GameMap(String worldName) {
        this.worldName = worldName;
        this.sourceWorldFolder = new File(new File(NightMare.getInstance().getDataFolder(),"maps"), worldName);
        load();
    }
    public void setupShopsAndBeds() {
        YamlConfiguration config = MapConfig.getMapConfig();
        ConfigurationSection mapSection = config.getConfigurationSection(this.worldName);
        if (mapSection == null) return;

        // --- 1. 读取并放置商店村民 ---
        ConfigurationSection shopSection = mapSection.getConfigurationSection("team-shop-locations");
        if (shopSection != null) {
            for (String team : shopSection.getKeys(false)) {
                List<Double> coords = shopSection.getDoubleList(team);
                if (coords.size() >= 3) {
                    // 生成村民的坐标
                    Location loc = new Location(bukkitWorld, coords.get(0), coords.get(1), coords.get(2));
                    // 在世界中生成村民
                    Villager villager = bukkitWorld.spawn(loc, Villager.class);

                    // 设置村民属性 (无AI, 无敌, 静音, 不会因为玩家走远而消失)
                    villager.setAI(false);
                    villager.setInvulnerable(true);
                    villager.setSilent(true);
                    villager.setRemoveWhenFarAway(false);
                    villager.setCollidable(false); // 防止被玩家推来推去
                    // 设置外观和名字 (可根据需求修改)
                    villager.setProfession(Villager.Profession.ARMORER);
                    villager.customName(net.kyori.adventure.text.Component.text("§e" + team.toUpperCase() + " 商店"));
                    villager.setCustomNameVisible(true);
                }
            }
            Bukkit.getLogger().info("地图 " + this.worldName + " 的商店村民已就位。");
        }

        // --- 2. 读取并记录床的坐标 ---
        ConfigurationSection bedSection = mapSection.getConfigurationSection("team-bed-locations");
        if (bedSection != null) {
            for (String team : bedSection.getKeys(false)) {
                List<Double> coords = bedSection.getDoubleList(team);
                if (coords.size() >= 3) {
                    // 这里的坐标是床的位置
                    Location loc = new Location(bukkitWorld, coords.get(0), coords.get(1), coords.get(2));
                    teamBedLocations.put(team.toLowerCase(), loc);
                }
            }
            Bukkit.getLogger().info("地图 " + this.worldName + " 的床位坐标已记录。");
        }
    }
    public Location getTeamBedLocation(String teamName) {
        if (teamName == null) return null;
        return teamBedLocations.get(teamName.toLowerCase());
    }
    public Map<String, Location> getAllBedLocations() {
        return teamBedLocations;
    }

    public List<Location> getSideLocations() {
        return sideLocations;
    }

    public  List<ResourceSpawner> loadSpawnersFromConfig(String map) {
        List<ResourceSpawner> spawnerList = new ArrayList<>();
        YamlConfiguration config = MapConfig.getMapConfig();
        ConfigurationSection mapSection = config.getConfigurationSection(map);
        if (mapSection == null) {
            Bukkit.getLogger().warning("找不到地图配置: " + map);
            return spawnerList;
        }
        // 读取 'spawners' 列表
        List<Map<?, ?>> rawSpawners = mapSection.getMapList("spawners");
        for (Map<?, ?> entry : rawSpawners) {
            try {
                // 1. 获取并校验类型
                String typeName = (String) entry.get("type");
                ResourceSpawner.Type type = ResourceSpawner.Type.valueOf(typeName.toUpperCase());

                // 2. 获取并解析坐标字符串
                String locStr = (String) entry.get("location");
                Location loc = deserializeLocation(locStr);

                if (loc != null) {
                    if (type.equals(ResourceSpawner.Type.SIDE_IRON)){
                        sideLocations.add(loc);
                    }
                    spawnerList.add(new ResourceSpawner(loc, type));
                }
            } catch (Exception e) {
                Bukkit.getLogger().severe("解析资源点时出错: " + entry.toString() + " -> " + e.getMessage());
            }
        }

        Bukkit.getLogger().info("地图 " + map + " 已成功加载 " + spawnerList.size() + " 个资源刷新点。");
        return spawnerList;
    }

   public  Location deserializeLocation(String s) {
        if (s == null) return null;
        String[] parts = s.split(",");
        if (parts.length < 4) return null;

        World world = this.getBukkitWorld();
        if (world == null) return null;

        try {
            double x = Double.parseDouble(parts[1]);
            double y = Double.parseDouble(parts[2]);
            double z = Double.parseDouble(parts[3]);

            // 关键优化：统一对齐到方块中心，确保掉落物位置统一
            return new Location(world, Math.floor(x) + 0.5, Math.floor(y) + 0.1, Math.floor(z) + 0.5);
        } catch (NumberFormatException e) {
            return null;
        }
    }
    public boolean load(){
        if (isLoaded()) return true;

        System.out.println(activeWorldFolder);
        this.activeWorldFolder = new File(
                Bukkit.getWorldContainer() ,
                sourceWorldFolder.getName() + "_active_" + System.currentTimeMillis()
        );

        try{
            FileUtils.copyDirectoryStructure(sourceWorldFolder, activeWorldFolder);
            this.bukkitWorld = Bukkit.createWorld(new WorldCreator(activeWorldFolder.getName()).keepSpawnLoaded(TriState.FALSE));

            if (bukkitWorld!=null){
                this.bukkitWorld.setAutoSave(false);
                bukkitWorld.setPVP(true);
                bukkitWorld.setVoidDamageAmount(10000);
                bukkitWorld.getPersistentDataContainer().set(new NamespacedKey(NightMare.getInstance(), "bedwars_map"), PersistentDataType.BOOLEAN, true);
                bukkitWorld.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
                bukkitWorld.setGameRule(GameRule.KEEP_INVENTORY, false);
                bukkitWorld.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true);
                bukkitWorld.setGameRule(GameRule.DO_MOB_SPAWNING, false);
                bukkitWorld.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
                bukkitWorld.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
                bukkitWorld.setGameRule(GameRule.NATURAL_REGENERATION, true);
                setupShopsAndBeds();
            }
        } catch (Exception e) {
            Bukkit.getLogger().severe("无法加载世界: " + activeWorldFolder.getName());
            e.printStackTrace();

            return false;
        }

        return isLoaded();
    }

    public void unload(){
        if (bukkitWorld!=null) Bukkit.unloadWorld(bukkitWorld, false);
        if (activeWorldFolder!=null) {
            try {
                FileUtils.deleteDirectory(activeWorldFolder);
                Bukkit.getLogger().info("Deleted active world: " + activeWorldFolder.getName());
            } catch (IOException e) {
                Bukkit.getLogger().severe("Could not delete active world: " + activeWorldFolder.getName());
                e.printStackTrace();
            }
        }

        bukkitWorld = null;
        activeWorldFolder = null;
    }

    private boolean isLoaded() {
        return bukkitWorld!=null;
    }

    public World getBukkitWorld(){
        return bukkitWorld;
    }

    public String getName() {
        return worldName;
    }
    public Location getSpectatorLocation(){
        List<Double> coords = MapConfig.getSpectatorTeleportLocation(getName());
        return new Location(bukkitWorld, coords.getFirst(), coords.get(1), coords.getLast());
    }
    public List<Location> getTeamSpawnLocations(){
        List<Location> locations = new ArrayList<>();
        for (List<Double> coordsList : MapConfig.getTeamSpawnCoordinates(getName())){
            locations.add(new Location(bukkitWorld, coordsList.getFirst(), coordsList.get(1), coordsList.getLast()));
        }
        return locations;
    }
}