package jdd.nightMare.Game;

import jdd.nightMare.Command.TeamJoinCommand;
import jdd.nightMare.GameConfig.MapConfig;
import jdd.nightMare.NightMare;
import jdd.nightMare.GameConfig.PlayerConfig;
import jdd.nightMare.GameConfig.PluginConfigManager;
import jdd.nightMare.Message;
import jdd.nightMare.PlayerUtils;
import jdd.nightMare.NightMare;
import jdd.nightMare.tasks.BossManager;
import jdd.nightMare.tasks.EndCountdown;
import jdd.nightMare.tasks.GameStageTask;
import jdd.nightMare.tasks.StartCountdown;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.*;
import org.jetbrains.annotations.Nullable;
import org.bukkit.plugin.java.JavaPlugin;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class Game {
    private final GameConfiguration gameConfiguration;
    private final GameManager gameManager;
    private final GameMap map;
    private GameState gameState;
    private final String id;
    private final HashMap<Player, PlayerSession> gamePlayers;
    private final Set<GameTeam> gameTeams;
    private final StartCountdown startCountdown;
    private final HashSet<Player> alivePlayers;
    private final HashSet<Player> deadPlayers;
    private final HashMap<GameTeam, Location> teamSpawnLocations;
    private final int minPlayers;
    private final int maxPlayers;
    private final List<ResourceSpawner> spawners = new ArrayList<>();
    private BukkitTask resourceTask;
    private BukkitTask passiveIncomeTask;
    private final Set<Player> spectators;
    private final HashMap<GameTeam, Boolean> teamAliveMap;
    private final HashMap<Player, List<Player>> hiddenPlayers;
    private final GameScoreboard gameScoreboard;
    private final HashMap<Player, Integer> kills;
    public static String teamID[] = {"red", "blue", "yellow", "green"};
    private final MiniMessage mm = MiniMessage.miniMessage();
    private final Set<Location> placedBlocks = ConcurrentHashMap.newKeySet();
    private final GameStageTask gameStageTask;
    private final BossManager bossManager;
    private static final org.bukkit.NamespacedKey HEALTH_KEY = new org.bukkit.NamespacedKey("nightmare", "health_boost");
    public Game(GameConfiguration gameConfiguration, GameManager gameManager, GameMap map) {
        this.gameConfiguration = gameConfiguration;
        this.gameStageTask = new GameStageTask(this,gameManager);
        this.gameManager = gameManager;
        this.map = map;
        this.bossManager=new BossManager(this);
        this.gameState = GameState.WAITING;
        this.id = GameManager.generateID();
        this.gamePlayers = new HashMap<>();
        this.startCountdown = new StartCountdown(this);
        this.maxPlayers = gameConfiguration.getMaxTeams() * gameConfiguration.getTeamSize();
        this.minPlayers = gameConfiguration.getMinTeams() * gameConfiguration.getTeamSize();
        this.gameTeams = new HashSet<>();
        this.teamSpawnLocations = new HashMap<>();
        this.alivePlayers = new HashSet<>();
        this.deadPlayers = new HashSet<>();
        this.spectators = new HashSet<>();
        this.teamAliveMap = new HashMap<>();
        this.gameScoreboard = new GameScoreboard(this, gameManager);
        this.hiddenPlayers = new HashMap<>();
        this.kills = new HashMap<>();
        this.spawners.addAll(map.loadSpawnersFromConfig(map.getName()));
        for (int x=0; x<gameConfiguration.getMaxTeams(); x++){
            GameTeam team = new GameTeam(teamID[x],this);
            String teamName = teamID[x];
            Location teamSpawnLocation = map.getTeamSpawnLocations().get(x);
            teamSpawnLocations.put(team, teamSpawnLocation);
            gameTeams.add(team);
            teamAliveMap.put(team, true);
        }
        startCountdown.runTaskTimer(NightMare.getInstance(), 0, 20);
        if (map.getTeamSpawnLocations().size()<gameTeams.size())
            NightMare.getInstance().getLogger().warning("%s没有足够的配置, 地图: %s".formatted(map.getName(), gameConfiguration.getName()));
    }
    public Game getGame(){
        return this;
    }
    public Set<Location> getPlacedBlocks() {
        return placedBlocks;
    }
    public ItemStack getBrandSelectorItem() {
        ItemStack star = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = star.getItemMeta();
        meta.displayName(Component.text("§6§l烙印选择器 §7(右键打开)").decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("§7点击选择你本局比赛的特殊能力").decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("§8最多可同时携带 6 种烙印").decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        star.setItemMeta(meta);
        return star;
    }
    public void clearHealthModifier(Player player) {
        var attr = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        if (attr == null) return;
        List<org.bukkit.attribute.AttributeModifier> toRemove = new ArrayList<>();
        for (org.bukkit.attribute.AttributeModifier m : attr.getModifiers()) {
            if (m.getKey().equals(HEALTH_KEY)) {
                toRemove.add(m);
            }
        }
        if (toRemove.isEmpty()) return;
        toRemove.forEach(attr::removeModifier);
        if (player.getHealth() > attr.getValue()) {
            player.setHealth(attr.getValue());
        }
    }
    public GameTeam getTeamByColor(String color) {
        return gameTeams.stream()
                .filter(t -> t.getTeamName().equals( color))
                .findFirst()
                .orElse(null);
    }
    public List<ResourceSpawner> getSpawners(){
        return spawners;
    }
    public void startTasks() {
        gameStageTask.runTaskTimer(NightMare.getInstance(), 0L, 20L);
        passiveIncomeTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (gameStageTask.getCurrentPhase() == GamePhase.NIGHT_5) {
                    this.cancel();
                }
                ItemStack iron = new ItemStack(Material.IRON_INGOT, 1);
                for (Player player : getGamePlayers().keySet()) {
                    if (player.getGameMode() == GameMode.SURVIVAL) {
                        player.getInventory().addItem(iron);
                    }
                }
            }
        }.runTaskTimer(NightMare.getInstance(), 0L, 40L);
    }
    public GameStageTask getGameStageTask() {
        return gameStageTask;
    }
    public void updateKills(Player player) {
        Scoreboard scoreboard = player.getScoreboard();
        Team killsTeam = scoreboard.getTeam("killsDisplay");

        if (killsTeam != null) {
            int kills = gameManager.getPlayerSession(player).getKills(); // 从你的游戏逻辑类获取最新击杀数
            killsTeam.suffix(Component.text(kills, NamedTextColor.GREEN));
        }
    }
    public void updateScoreboard(Player player) {
        Scoreboard scoreboard = player.getScoreboard();
        String[] teamNames = {"红队", "蓝队", "黄队", "绿队"};
        NamedTextColor[] teamColors = {NamedTextColor.RED, NamedTextColor.BLUE, NamedTextColor.YELLOW, NamedTextColor.GREEN};
        for (int i = 0; i < 4; i++) {
            Team t = scoreboard.getTeam(teamNames[i].toLowerCase() + "_status");
            if (t != null) {
                GameTeam gameTeam = getTeamByColor(teamID[i]);
                if (gameTeam != null) {
                    if (gameTeam.isBedAlive()) {
                        t.suffix(Component.text(" ✔", NamedTextColor.GREEN));
                    } else {
                        int alive = gameTeam.getAlivedPlayerCount();
                        t.suffix(alive > 0 ? Component.text(" " + alive, NamedTextColor.GOLD) : Component.text(" ✘", NamedTextColor.RED));
                    }
                }
            }
        }
    }
    public void updateAllScoreboards() {
        for (Player p : this.getGamePlayers().keySet()) {
            updateScoreboard(p);
        }
    }
    public GameTeam getTeamByTeamName(String teamName){
        return gameTeams.stream()
                .filter(t -> t.getTeamName().equals(teamName))
                .findFirst()
                .orElse(null);
    }
    public void stopTasks() {
        if (resourceTask != null) resourceTask.cancel();
        if (gameStageTask!=null)gameStageTask.cancel();
        if (passiveIncomeTask != null) passiveIncomeTask.cancel();
        for (ResourceSpawner spawner : spawners) {
            spawner.remove();
        }
    }
    public void giveInitialEquipment(Player player) {
        PlayerInventory inv = player.getInventory();
        GameTeam team = gameManager.getPlayerSession(player).getGameTeam();
        // 1. 获取队伍对应的 Bukkit Color
        Color teamColor = getBukkitColor(team.getTeamName());
        // 2. 创建并穿戴皮革套装
        inv.setHelmet(createColoredArmor(Material.LEATHER_HELMET, teamColor));
        inv.setChestplate(createColoredArmor(Material.LEATHER_CHESTPLATE, teamColor));
        inv.setLeggings(createColoredArmor(Material.LEATHER_LEGGINGS, teamColor));
        inv.setBoots(createColoredArmor(Material.LEATHER_BOOTS, teamColor));
        // 3. 给予初始木剑
        ItemStack woodenSword = new ItemStack(Material.WOODEN_SWORD);
        ItemMeta swordMeta = woodenSword.getItemMeta();
        if (swordMeta != null) {
            swordMeta.setUnbreakable(true); // 设置无法破坏
            swordMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
            woodenSword.setItemMeta(swordMeta);
        }
        ItemStack wool = new ItemStack(getPlayerTeamWool(player),8);
        inv.addItem(woodenSword);
        inv.addItem( wool);
    }
    private Material getPlayerTeamWool(Player player) {
        try {
            PlayerSession session = gameManager.getPlayerSession(player);
            String teamColor = session.getGame().getTeam(player).getTeamName().toLowerCase();
            return switch (teamColor) {
                case "red" -> Material.RED_WOOL;
                case "blue" -> Material.BLUE_WOOL;
                case "green" -> Material.LIME_WOOL;
                case "yellow" -> Material.YELLOW_WOOL;
                default -> Material.WHITE_WOOL;
            };
        } catch (Exception e) { return Material.WHITE_WOOL; }
    }
    private ItemStack createColoredArmor(Material material, Color color) {
        ItemStack item = new ItemStack(material);
        LeatherArmorMeta meta = (LeatherArmorMeta) item.getItemMeta();

        if (meta != null) {
            meta.setColor(color);          // 设置颜色
            meta.setUnbreakable(true);      // 设置无法破坏
            // 隐藏杂乱的标签
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_DYE);
            item.setItemMeta(meta);
        }
        return item;
    }

    private Color getBukkitColor(String chatColor) {
        // 将 ChatColor 转换为 LeatherArmor 所需的 RGB Color
        switch (chatColor) {
            case "red": return Color.RED;
            case "blue": return Color.BLUE;
            case "green": return Color.GREEN;
            case "yellow": return Color.YELLOW;
            default: return Color.WHITE;
        }
    }
    public  void updateVisibility(Player player) {
        if (gameManager.hasActiveSession(player)){
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (player.equals(other)) continue;
                if (!gameManager.hasActiveSession( other)){
                    player.hidePlayer(NightMare.getInstance(), other);
                    other.hidePlayer(NightMare.getInstance(), player);
                }
                else{
                    String otherId=gameManager.getPlayerSessions().get(other).getGame().getId();
                    String thisId=gameManager.getPlayerSessions().get(player).getGame().getId();
                    if (!otherId.equals(thisId)){
                        player.hidePlayer(NightMare.getInstance(), other);
                        other.hidePlayer(NightMare.getInstance(), player);
                    }
                    else{
                        player.showPlayer(NightMare.getInstance(), other);
                        other.showPlayer(NightMare.getInstance(), player);
                    }
                }
            }
        }
        else{
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (player.equals(other)) continue;
                if (gameManager.hasActiveSession(other)){
                    player.hidePlayer(NightMare.getInstance(), other);
                    other.hidePlayer(NightMare.getInstance(), player);
                }
                else{
                    player.showPlayer(NightMare.getInstance(), other);
                    other.showPlayer(NightMare.getInstance(), player);
                }
            }
        }
    }
    public void addPlayerToTeam(Player player, String teamName){
        for (GameTeam team : gameTeams){
            if (team.getTeamName().equals(teamName)){
                if (getTeam( player)!=null){
                    getTeam( player).removePlayer(player);
                }
                team.addPlayer(player);
                gameManager.getPlayerSession(player).setGameTeam(team);
                return;
            }
        }
    }
    public void playerJoin(Player player){
        player.showPlayer(NightMare.getInstance(), player);
        if (hasStarted()){
            if (isActive())
                Message.send(player, "<gray>游戏已开始");
            else
                Message.send(player, "<gray>游戏已结束");
            return;
        }
        Message.send(player, "<gray>加入 %s".formatted(id));
        PlayerUtils.refreshPlayer(player);
        PlayerSession session = gameManager.createPlayerSession(player, this);
        gamePlayers.put(player, session);
        alivePlayers.add(player);
        telepotToWaitting( player);
        gameScoreboard.createScoreboard(player);
        broadcastMessage(Message.PLAYER_JOINED_GAME.setPlaceholders(
                Placeholder.unparsed("player", player.getName()),
                Placeholder.unparsed("player-count", String.valueOf(getPlayerCount())),
                Placeholder.unparsed("max-players", String.valueOf(maxPlayers))));

        if (isWaiting()){
            if (getPlayerCount()==minPlayers){
                setGameState(GameState.STARTING);

            }
            if (getPlayerCount()==maxPlayers){
                setGameState(GameState.STARTING);
                startCountdown.setTime(20);
            }
        }
        updateScoreboardPlayerCount();
        updateVisibility(player);
        ItemStack bed = new ItemStack(Material.RED_BED);
        ItemMeta meta = bed.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§b§l选择队伍");
            bed.setItemMeta(meta);
        }
        if (!player.getInventory().contains(Material.RED_BED)) {
            player.getInventory().addItem(bed);
        }
        player.getInventory().addItem(getBrandSelectorItem());
    }
    public void removeSpectator(Player player){
        player.setCollidable(true);
        player.setGameMode(GameMode.SURVIVAL);

        GameManager.getSpectatorTeam().removePlayer(player);

        hiddenPlayers.keySet().forEach(inGamePlayer -> {
            if (inGamePlayer==player)
                return;
            inGamePlayer.showPlayer(NightMare.getInstance(), player);
            List<Player> hidden = hiddenPlayers.get(inGamePlayer);
            hidden.remove(player);
            hiddenPlayers.put(inGamePlayer, hidden);
        });

        spectators.remove(player);
    }
    public void addSpectator(Player player){
        player.setCollidable(false);
        player.setGameMode(GameMode.SPECTATOR);
        GameManager.getSpectatorTeam().addPlayer(player);

        if (!hiddenPlayers.containsKey(player))
            hiddenPlayers.put(player, List.of());

        hiddenPlayers.keySet().forEach(inGamePlayer -> {
            if (inGamePlayer==player)
                return;
            inGamePlayer.hidePlayer(NightMare.getInstance(), player);
            List<Player> hidden = hiddenPlayers.get(inGamePlayer);
            hidden.add(player);
            hiddenPlayers.put(inGamePlayer, hidden);
        });

        spectators.add(player);
    }
    public void playerLeave(Player player){
        showSpectators(player);
        player.showPlayer(NightMare.getInstance(), player);
        if (!hasStarted()){
            if (getTeam( player) != null) {
               getTeam( player).removePlayer( player);
            } else {
                Bukkit.getLogger().info("[NightMare] 玩家 " + player.getName() + " 在未分配队伍时退出了游戏。");
            }
            gamePlayers.remove(player);
            alivePlayers.remove(player);
            broadcastMessage(Message.PLAYER_LEFT_GAME.setPlaceholders(
                    Placeholder.unparsed("player", player.getName()),
                    Placeholder.unparsed("player-count", String.valueOf(getPlayerCount())),
                    Placeholder.unparsed("max-players", String.valueOf(maxPlayers))));
        }
        else if (isActive()) {
            if (deadPlayers.contains(player)) {
                removeSpectator(player);
            } else {
                playerDie( player);
                broadcastMessage(Message.PLAYER_QUIT_GAME.setPlaceholders(
                        Placeholder.unparsed("player", player.getName()),
                        Placeholder.unparsed("player-count", String.valueOf(getPlayerCount())),
                        Placeholder.unparsed("max-players", String.valueOf(maxPlayers))));
                player.setHealth(0);
                removeSpectator(player);
                gamePlayers.remove(player);
            }
        }

        gameManager.deletePlayerSession(player);
        PlayerUtils.refreshPlayer(player);
        PlayerUtils.teleport(player, gameManager.getLobbyLocation());
        player.clearTitle();
        gameScoreboard.removeScoreboard(player);
        if (isStarting()){
            if (getPlayerCount()<minPlayers)
                setGameState(GameState.WAITING);
        }
        updateScoreboardPlayerCount();
        if (getPlayerCount()==0) {
            deleteGame();
        }
        updateVisibility(player);
    }

    public void playerDie(Player player){
        if (spectators.contains(player))
            return;
        PlayerSession session = gameManager.getPlayerSession(player);
        session.markAsDead();
        alivePlayers.remove(player);
        deadPlayers.add(player);
        addSpectator(player);
        if (!isTeamAlive(getTeam(player)))
            teamAliveMap.put(getTeam(player), false);
        if (getAliveTeams().size()==1){
            setGameState(GameState.ENDED);
        }
        new BukkitRunnable(){
            @Override
            public void run() {
                updateAllScoreboards();
            }
         }.runTaskLater(NightMare.getInstance(), 10);
    }

    public void updateScoreboardPlayerCount(){
        getInGamePlayers().forEach(gameScoreboard::updatePlayerCount);
    }
    public void updateScoreboardStartCountdown(){
        getInGamePlayers().forEach(gameScoreboard::updateStartCountdown);
    }
    public boolean isAlive(Player player){
        return alivePlayers.contains(player);
    }

    public @Nullable GameTeam getTeamWon(){
        if (getAliveTeams().size()!=1)
            return null;
        else
            return getAliveTeams().stream().toList().getFirst();
    }

    public Set<Player> getDeadPlayers(){
        return deadPlayers;
    }

    public void broadcastMessage(String message){
        for (Player player : getInGamePlayers())
            player.sendRichMessage(message);
    }

    public void broadcastMessage(Component component){
        for (Player player : getInGamePlayers())
            player.sendMessage(component);
    }

    public void broadcastTitle(Component title, Component subtitle, Duration fadeIn, Duration stay, Duration fadeOut, @Nullable Collection<Player> players){
        List<Player> playerList;
        if (players == null)
            playerList = getInGamePlayers().stream().toList();
        else playerList = players.stream().toList();

        for (Player player : playerList)
            player.showTitle(Title.title(title, subtitle, Title.Times.times(fadeIn, stay, fadeOut)));
    }

    public void broadcastTitle(Component title, Component subtitle, @Nullable Collection<Player> players){
        List<Player> playerList;
        if (players == null)
            playerList = getInGamePlayers().stream().toList();
        else playerList = players.stream().toList();

        for (Player player : playerList)
            player.showTitle(Title.title(title, subtitle));
    }
    public GameConfiguration getGameConfiguration() {
        return gameConfiguration;
    }

    public GameState getGameState() {
        return gameState;
    }

    public void setGameState(GameState newState) {
        GameState before = this.gameState;
        this.gameState = newState;

        if (before == GameState.STARTING && newState == GameState.WAITING ) {
            broadcastMessage("<gray>没有足够的玩家开始游戏");
            startCountdown.setTime(120);
        }

        switch (newState){

            case STARTED -> {
                for (Player player : getInGamePlayers()){
                    player.getInventory().clear();
                    player.getEnderChest().clear();
                    if (getTeam(player)==null){
                        int minCount = gameConfiguration.getTeamSize();
                        GameTeam finalTeam = null;
                        for (GameTeam team : gameTeams){
                            if (team.getPlayerCount() < minCount && team.getPlayerCount() < gameConfiguration.getTeamSize()) {
                                minCount = team.getPlayerCount();
                                finalTeam = team;
                            }
                        }
                        if (finalTeam==null){
                            Bukkit.getLogger().warning("人数有误");
                        }
                        addPlayerToTeam(player,finalTeam.getTeamName());
                        TeamJoinCommand.updatePlayerTeamDisplay(player,finalTeam.getTeamName());
                    }

                }
                gameTeams.forEach(team -> {if (!isTeamAlive(team)) {
                    teamAliveMap.remove(team);
                    Block block = getMap().getTeamBedLocation(team.getTeamName()).getBlock();
                    if (!(block.getBlockData() instanceof org.bukkit.block.data.type.Bed bedData)) {
                        return;
                    }
                    Block otherPart = block.getRelative(bedData.getFacing());
                    block.setType(Material.AIR, false);
                    if (otherPart.getType().name().endsWith("_BED")) {
                        otherPart.setType(Material.AIR, false);
                    }
                    team.setBedAlive(false);
                }});
                getInGamePlayers().forEach(player -> {hiddenPlayers.put(player, new ArrayList<>()); gameScoreboard.registerStartedTeams(player);});
                for (Player player : gamePlayers.keySet()){
                    kills.put(player, 0);
                    teleportToTeamSpawnLocation( player);
                }
                startTasks();
            }
            case DRAW -> {
                stopTasks();
                broadcastTitle(
                        mm.deserialize("<gold>游戏平局!"),
                        mm.deserialize("<gray>没有人获得胜利"),
                        getInGamePlayers()
                );
                gamePlayers.keySet().forEach(player -> {
                    gameScoreboard.removeScoreboard(player);
                });
                new EndCountdown(this).runTaskTimer(NightMare.getInstance(), 0, 20);
            }
            case ENDED -> {
                BukkitTask task = new BukkitRunnable(){
                    public void run(){
                        gameStageTask.stopGame();
                        gamePlayers.keySet().forEach(player -> {
                            gameScoreboard.removeScoreboard(player);
                        });
                        new EndCountdown(getGame()).runTaskTimer(NightMare.getInstance(), 0, 20);
                        Set<Player> lost = new HashSet<>();
                        getGameTeams().forEach(team -> {if (team!=getTeamWon()) lost.addAll(team.getTeamPlayers());});
                        lost.retainAll(spectators);
                        stopTasks();
                        Set<Player> others = new HashSet<>(spectators);
                        others.removeAll(lost);
                        others.removeAll(getTeamWon().getTeamPlayers());
                        broadcastTitle(mm.deserialize(Message.VICTORY_TITLE.text()), mm.deserialize(Message.VICTORY_SUBTITLE.text()), getTeamWon().getTeamPlayers());
                        broadcastTitle(mm.deserialize(Message.GAME_ENDED_TITLE.text()), mm.deserialize(Message.GAME_ENDED_SUBTITLE.text()), others);
                        broadcastTitle(mm.deserialize(Message.LOST_TITLE.text()), mm.deserialize(Message.LOST_SUBTITLE.text()), lost);
                        lost.forEach(PlayerUtils::displayProgressBar);
                        getDeadTeams().forEach(team -> team.getTeamPlayers().forEach(player -> PlayerConfig.addLoss(player, gameConfiguration)));
                    }
                }.runTaskLater(NightMare.getInstance(), 40L);
            }
        }
    }

    public BossManager getBossManager() {
        return bossManager;
    }

    public GameMap getMap() {
        return map;
    }

    public String getId() {
        return id;
    }

    public HashMap<Player, PlayerSession> getGamePlayers() {
        return gamePlayers;
    }

    public int getPlayerCount(){
        return gamePlayers.size();
    }

    public void deleteGame(){
        map.getBukkitWorld().getPlayers().forEach(player -> {gameManager.deletePlayerSession(player); gameScoreboard.removeScoreboard(player); PlayerUtils.refreshPlayer(player); player.setGameMode(GameMode.SURVIVAL);player.teleport(gameManager.getLobbyLocation()); showSpectators(player); if (isSpectator(player)) removeSpectator(player);});
        map.unload();
        gameManager.getGames().remove(id);
    }


    public StartCountdown getStartCountdown() {
        return startCountdown;
    }

    public HashSet<Player> getAlivePlayers() {
        return alivePlayers;
    }

    public Set<GameTeam> getGameTeams() {
        return gameTeams;
    }

    public int getMinPlayers() {
        return minPlayers;
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public HashMap<GameTeam, Location> getTeamSpawnLocations() {
        return teamSpawnLocations;
    }

    public GameTeam getTeam(Player player){
        for (GameTeam team : gameTeams){
            if (team.getTeamPlayers().contains(player))
                return team;
        }
        return null;
    }
    public void telepotToWaitting (Player player){
        Location location =map.getSpectatorLocation();
        player.teleport(location);
    }

    public void teleportToTeamSpawnLocation(Player player){
        GameTeam team = getTeam(player);
        if (team == null || !teamSpawnLocations.containsKey(team)) return;
        Location spawnLoc = teamSpawnLocations.get(team).clone();
        Location centerLoc = new Location(spawnLoc.getWorld(), 0, spawnLoc.getY(), 0);
        double dx = centerLoc.getX() - spawnLoc.getX();
        double dz = centerLoc.getZ() - spawnLoc.getZ();
        float lookAtCenterYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        spawnLoc.setYaw(lookAtCenterYaw);
        spawnLoc.setPitch(0f);
        player.teleport(spawnLoc);
    }

    public Set<Player> getSpectators(){
        return spectators;
    }
    public Set<Player> getInGamePlayers(){
        HashSet<Player> players = new HashSet<>();
        players.addAll(getAlivePlayers());
        players.addAll(getSpectators());
        return players;
    }

    public Set<GameTeam> getAliveTeams(){
        return teamAliveMap.keySet().stream().filter(teamAliveMap::get).collect(Collectors.toSet());
    }

    public Set<GameTeam> getDeadTeams(){
        return teamAliveMap.keySet().stream().filter(team -> !teamAliveMap.get(team)).collect(Collectors.toSet());
    }

    public boolean isTeamAlive(GameTeam team){
        if (team.getPlayerCount()==0)
            return false;
        return !deadPlayers.containsAll(team.getTeamPlayers());
    }
    public boolean isSpectator(Player player){
        return spectators.contains(player);
    }
    public void showSpectators(Player player){
        spectators.forEach(spectator -> player.showPlayer(NightMare.getInstance(), spectator));
    }

    public void hideSpectators(Player player){
        spectators.forEach(spectator -> player.hidePlayer(NightMare.getInstance(), spectator));
    }

    public void addKill(Player player){
        kills.put(player, kills.get(player) + 1);
    }

    public GameScoreboard getGameScoreboard(){
        return gameScoreboard;
    }

    public boolean hasStarted(){
        return gameState == GameState.STARTED || gameState == GameState.ENDED;
    }

    public boolean hasEnded(){
        return gameState == GameState.ENDED;
    }

    public boolean isWaiting(){
        return gameState == GameState.WAITING;
    }

    public boolean isStarting(){
        return gameState == GameState.STARTING;
    }

    public boolean isActive(){
        return gameState == GameState.STARTED;
    }
}