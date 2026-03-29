package jdd.nightMare;

import jdd.nightMare.Command.BedWarsCommand;
import jdd.nightMare.Command.TeamJoinCommand;
import jdd.nightMare.Game.Game;
import jdd.nightMare.Game.GameConfiguration;
import jdd.nightMare.Game.GameManager;
import jdd.nightMare.Game.GameMap;
import jdd.nightMare.GameConfig.*;
import jdd.nightMare.InitialListener.*;
import jdd.nightMare.Shop.BrandGUI;
import org.bukkit.Bukkit;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;

public final class NightMare extends JavaPlugin {
    private static NightMare instance;

    public static final HashMap<Player, GameMap> viewingMaps = new HashMap<>();
    private LobbyBoardManager lobbyBoardManager;
    @Override
    public void onLoad() {
        instance = this;
    }

    @Override
    public void onEnable() {
        ConfigurationSerialization.registerClass(GameConfiguration.class);
        loadConfigs();
        GameConfigurationManager.init();
        KillMessagesConfig.init();
        MessagesConfig.init();
        LevelsConfig.init();
        MapConfig.init();
        PlayerConfig.init();
        PluginConfigManager.init();
        GameManager gameManager = new GameManager();
        this.lobbyBoardManager = new LobbyBoardManager(gameManager);
        this.lobbyBoardManager.startUpdateTask();
        // 热重载保护：给当前所有在线的非游戏玩家发计分板
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!gameManager.hasActiveSession(p)) {
                lobbyBoardManager.applyLobbyBoard(p);
            }
        }
        getServer().getPluginManager().registerEvents(new CombatTracker(this, gameManager),this);
        getServer().getPluginManager().registerEvents(new GameListeners(gameManager), this);
        getServer().getPluginManager().registerEvents(new LobbyListeners(), this);
        getServer().getPluginManager().registerEvents(new DragEnchantListener(), this);
        getServer().getPluginManager().registerEvents(new BossDeathListener(gameManager), this);
        BrandGUI  brandGUI = new BrandGUI(gameManager);
        getServer().getPluginManager().registerEvents(new CustomBowListener(this), this);
        getServer().getPluginManager().registerEvents(new MeleeBloodListener(this), this);
        getServer().getPluginManager().registerEvents(new SpecialItemsListener(gameManager), this);
        getServer().getPluginManager().registerEvents(brandGUI, this);
        getServer().getPluginManager().registerEvents(new BrandListener(gameManager,brandGUI), this);
        getCommand("sp").setExecutor(new TeamJoinCommand(gameManager));
        BedWarsCommand bedWarsCommand = new BedWarsCommand(gameManager);
        getCommand("sb").setExecutor(bedWarsCommand);
        getCommand("leave").setExecutor(bedWarsCommand);
        getCommand("start").setExecutor(bedWarsCommand);
        getCommand("end").setExecutor(bedWarsCommand);
        getCommand("wait").setExecutor(bedWarsCommand);
        getCommand("shuaxing").setExecutor(bedWarsCommand);
    }

    public static NightMare getInstance() {
        return instance;
    }

    @Override
    public void onDisable() {
        GameManager.games.values().forEach(Game::deleteGame);
        viewingMaps.values().forEach(GameMap::unload);
    }

    public static void loadConfigs() {
        PluginConfigManager.load();
        GameConfigurationManager.load();
        MapConfig.load();
        PlayerConfig.load();
        KillMessagesConfig.load();
        MessagesConfig.load();
        LevelsConfig.load();
    }
    public LobbyBoardManager getLobbyBoardManager() {
        return lobbyBoardManager;
    }
}

