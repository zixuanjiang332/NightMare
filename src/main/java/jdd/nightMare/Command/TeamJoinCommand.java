package jdd.nightMare.Command;

import jdd.nightMare.Game.Game;
import jdd.nightMare.Game.GameManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

public class TeamJoinCommand implements CommandExecutor {
    private static GameManager gameManager;

    public TeamJoinCommand(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player player = (Player) sender;
        Game game = gameManager.getPlayerSession( player).getGame();
        if (args.length < 2) return true;
        String teamName = args[1];
        // 1. 执行加入队伍的逻辑 (你自己的游戏逻辑)
       if (game.getTeamByTeamName(teamName).getPlayerCount()==game.getGameConfiguration().getTeamSize()){
           player.sendMessage("§c队伍已满！");
       }
       else {
           game.addPlayerToTeam(player, teamName);
       }
        // 2. 处理 TAB 颜色与 Team 归属
        updatePlayerTeamDisplay(player, teamName);
        player.sendMessage("§a你已加入 " + teamName + " 队伍！");
        return true;
    }

    public static void updatePlayerTeamDisplay(Player player, String teamName) {
        Scoreboard scoreboard = player.getScoreboard();
        Team team = scoreboard.getTeam(teamName);
        team.addEntry(player.getName());
        for (Player onlinePlayer : gameManager.getPlayerSession( player).getGame().getInGamePlayers()){
            Scoreboard onlinePlayerScoreboard = onlinePlayer.getScoreboard();
            Team onlinePlayerTeam = onlinePlayerScoreboard.getTeam(teamName);
            onlinePlayerTeam.addEntry(player.getName());
        }
    }

}