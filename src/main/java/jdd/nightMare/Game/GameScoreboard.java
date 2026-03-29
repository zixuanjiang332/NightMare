package jdd.nightMare.Game;

import jdd.nightMare.Game.GameScoreboard;
import jdd.nightMare.NightMare;
import jdd.nightMare.tasks.GameStageTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;

public class GameScoreboard {

    private final Game game;
    private final GameManager gameManager;
    private final HashMap<Player, Boolean> hasRegisteredStartedTeams;
    private final HashMap<Player, Boolean> hasScoreboard;

    public GameScoreboard(Game game, GameManager gameManager) {
        this.game = game;
        this.gameManager = gameManager;
        hasRegisteredStartedTeams = new HashMap<>();
        hasScoreboard = new HashMap<>();
    }

    public void setupPlayerTeams(Player player) {
        Scoreboard board = player.getScoreboard();
        String[] teams = {"red", "blue", "yellow", "green"};

        for (String teamName : teams) {
            Team team = board.getTeam(teamName);
            if (team == null) {
                team = board.registerNewTeam(teamName);
            }
            ChatColor color = switch (teamName.toLowerCase()) {
                case "blue" -> ChatColor.BLUE;
                case "yellow" -> ChatColor.YELLOW;
                case "red" -> ChatColor.RED;
                case "green" -> ChatColor.GREEN;
                default -> ChatColor.WHITE;
            };
            team.setColor(color);
            team.setPrefix(color.toString() + "[" + teamName.toUpperCase() + "] ");
            team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
            team.setAllowFriendlyFire(false);
        }
    }

    public void createScoreboard(Player player){
        hasScoreboard.put(player, true);
        hasRegisteredStartedTeams.put(player, false);

        Scoreboard scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective objective = scoreboard.registerNewObjective(NightMare.getInstance().getName(), "dummy");
        Objective healthObj = scoreboard.registerNewObjective("health_display", Criteria.HEALTH, Component.text("❤", NamedTextColor.RED));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        objective.displayName(Component.text("起床战争", NamedTextColor.GOLD, TextDecoration.BOLD));
        healthObj.setDisplaySlot(DisplaySlot.BELOW_NAME);

        Team countdown = scoreboard.registerNewTeam("countdown");

        countdown.addEntry(ChatColor.DARK_BLUE.toString());
        countdown.prefix(Component.text("开始: ", NamedTextColor.GREEN));
        if (game.isWaiting()) {
            countdown.prefix(Component.text("等待玩家中", NamedTextColor.DARK_GRAY));
            countdown.suffix(Component.empty());
        } else {
            countdown.suffix(Component.text(game.getStartCountdown().getTime() + "s", NamedTextColor.GREEN));
        }

        objective.getScore(ChatColor.DARK_BLUE.toString()).setScore(8);

        Team playerCount = scoreboard.registerNewTeam("playerCount");

        playerCount.addEntry(ChatColor.DARK_AQUA.toString());
        playerCount.prefix(Component.text("玩家: "));
        playerCount.suffix(Component.text(game.getAlivePlayers().size() + "/" + game.getMaxPlayers()));

        objective.getScore(ChatColor.DARK_AQUA.toString()).setScore(5);

        Team config = scoreboard.registerNewTeam("Config");

        config.addEntry(ChatColor.YELLOW.toString());
        config.prefix(Component.text("模式: "));
        config.suffix(Component.text(game.getGameConfiguration().getName()));

        objective.getScore(ChatColor.YELLOW.toString()).setScore(0);

        Team map = scoreboard.registerNewTeam("Map");

        map.addEntry(ChatColor.RED.toString());
        map.prefix(Component.text("地图:落日手搓"));
        map.suffix(Component.text(game.getMap().getName()));

        objective.getScore(ChatColor.RED.toString()).setScore(1);

        LocalDateTime dateTime = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd");

        objective.getScore("§7" + formatter.format(dateTime)).setScore(11);
        objective.getScore(ChatColor.DARK_GREEN + " ").setScore(4);
        objective.getScore(ChatColor.DARK_PURPLE + " ").setScore(9);
        objective.getScore(ChatColor.DARK_GRAY + " ").setScore(7);
        player.setScoreboard(scoreboard);
        setupPlayerTeams(player);
        syncExistingPlayersToBoard(player);
        for (Player onlinePlayer : game.getInGamePlayers()) {
            onlinePlayer.setHealth(onlinePlayer.getHealth());
        }
    }
    private void syncExistingPlayersToBoard(Player newPlayer) {
        for (Player onlinePlayer : game.getInGamePlayers()) {
            GameTeam teamObj = game.getTeam(onlinePlayer);
            if (teamObj == null) {
                continue;
            }
            String teamName = teamObj.getTeamName();
            if (teamName == null) continue;
            Team team = newPlayer.getScoreboard().getTeam(teamName);
            if (team != null) {
                team.addEntry(onlinePlayer.getName());
            }
        }
    }

    public void updateStartCountdown(Player player){
        Scoreboard scoreboard = player.getScoreboard();
        Team countdown = scoreboard.getTeam("countdown");
        if (countdown != null) {
            if (game.isWaiting()) {
                countdown.prefix(Component.text("等待玩家中", NamedTextColor.DARK_GRAY));
                countdown.suffix(Component.empty());
            } else {
                countdown.prefix(Component.text("开始于: ", NamedTextColor.GREEN));
                countdown.suffix(Component.text(game.getStartCountdown().getTime() + "s", NamedTextColor.GREEN));
            }
        }
    }

    public void updatePlayerCount(Player player){
        Scoreboard scoreboard = player.getScoreboard();
        Team playerCount = scoreboard.getTeam("playerCount");
        if (playerCount != null)
            playerCount.suffix(Component.text(game.getAlivePlayers().size() + "/" + game.getMaxPlayers()));

    }

    public void registerStartedTeams(Player player){
        Scoreboard scoreboard = player.getScoreboard();

        if (!hasRegisteredStartedTeams.getOrDefault(player, false)
                && game.isActive()) {
            registerGameStartedTeams(scoreboard, scoreboard.getObjective(NightMare.getInstance().getName()), player);
        }
    }


    public void registerGameStartedTeams(Scoreboard scoreboard, Objective objective, Player player){
        hasRegisteredStartedTeams.put(player, true);
        scoreboard.getTeam("countdown").unregister();
        scoreboard.getTeam("playerCount").unregister();
        String[] teamNames = {"红队", "蓝队", "黄队", "绿队"};
        NamedTextColor[] teamColors = {NamedTextColor.RED, NamedTextColor.BLUE, NamedTextColor.YELLOW, NamedTextColor.GREEN};
        ChatColor[] teamEntries = {ChatColor.RED, ChatColor.BLUE, ChatColor.YELLOW, ChatColor.GREEN};
        String teamID[] = {"red", "blue", "yellow", "green"};
        GameStageTask stageTask = game.getGameStageTask(); 
        Team killsTeam = getOrCreateTeam(scoreboard, "killsDisplay", ChatColor.RESET.toString() + ChatColor.WHITE.toString());
        killsTeam.prefix(Component.text("击杀数: ", NamedTextColor.GRAY));
        int kills = gameManager.getPlayerSession( player).getKills();
        killsTeam.suffix(Component.text(kills, NamedTextColor.GREEN));
        objective.getScore(ChatColor.RESET.toString() + ChatColor.WHITE.toString()).setScore(6);
        Team phaseTeam = getOrCreateTeam(scoreboard, "phaseDisplay", ChatColor.LIGHT_PURPLE.toString());
        String timeStr = formatTime(stageTask.getSecondsRemaining());
        phaseTeam.prefix(Component.text("阶段: ", NamedTextColor.GRAY));
        phaseTeam.suffix(Component.text(stageTask.getCurrentPhase().getDisplayName() + " " + timeStr, NamedTextColor.YELLOW));
        objective.getScore(ChatColor.LIGHT_PURPLE.toString()).setScore(15);
        for (int i = 0; i < 4; i++) {
            String teamKey = teamNames[i].toLowerCase() + "_status";
            Team t = scoreboard.getTeam(teamKey);
            if (t == null) t = scoreboard.registerNewTeam(teamKey);
            t.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
            String entry = teamEntries[i].toString();
            t.addEntry(entry);
            t.prefix(Component.text(teamNames[i] + ": ", teamColors[i]));
            GameTeam gameTeam = game.getTeamByColor(teamID[i]);
            if (gameTeam != null) {
                if (gameTeam.isBedAlive()) {
                    t.suffix(Component.text(" ✔", NamedTextColor.GREEN));
                } else {
                    int aliveCount = gameTeam.getAlivedPlayerCount();
                    if (aliveCount > 0) {
                        t.suffix(Component.text(" " + aliveCount, NamedTextColor.GOLD));
                    } else {
                        t.suffix(Component.text(" ✘", NamedTextColor.RED));
                    }
                }
            } else {
                t.suffix(Component.text(" -", NamedTextColor.GRAY)); // 队伍不存在的情况
            }
            objective.getScore(entry).setScore(11 - i);
        }
    }

    public void removeScoreboard(Player player){
        Scoreboard board = player.getScoreboard();
        game.clearHealthModifier( player);
        board.clearSlot(DisplaySlot.SIDEBAR);
        for (Team team : board.getTeams()) {
            team.unregister();
        }
        NightMare.getInstance().getLobbyBoardManager().applyLobbyBoard(player);
    }
    private String formatTime(int seconds) {
        return String.format("%02d:%02d", seconds / 60, seconds % 60);
    }

    private Team getOrCreateTeam(Scoreboard s, String name, String entry) {
        Team t = s.getTeam(name);
        if (t == null) t = s.registerNewTeam(name);
        if (!t.hasEntry(entry)) t.addEntry(entry);
        return t;
    }

}
