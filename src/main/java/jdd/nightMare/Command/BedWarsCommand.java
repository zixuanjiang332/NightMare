package jdd.nightMare.Command;

import jdd.nightMare.Game.Game;
import jdd.nightMare.Game.GameJoinHandler;
import jdd.nightMare.Game.GameManager;
import jdd.nightMare.Game.GameState;
import jdd.nightMare.GameConfig.GameConfigurationManager;
import jdd.nightMare.NightMare;
import jdd.nightMare.tasks.StartCountdown;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class BedWarsCommand implements CommandExecutor {
    private final GameManager gameManager;
    public BedWarsCommand(GameManager gameManager){
        this.gameManager = gameManager;
    }
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("只有玩家可使用此指令。");
            return true;
        }
        if (command.getName().equalsIgnoreCase("sb")) {
            if (args.length < 2 || !args[0].equalsIgnoreCase("join")) {
                sender.sendMessage("用法: /sb join <MODEL>");
                return true;
            }
            GameJoinHandler.joinGame(player, GameConfigurationManager.getGameConfiguration(args[1]), gameManager);
            return true;
        }
        if (command.getName().equalsIgnoreCase("leave")) {
            Bukkit.getLogger().info("玩家 " + player.getName() + " 请求返回大厅");
            if (gameManager.hasActiveSession(player)) {
                var session = gameManager.getPlayerSession(player);
                Game game = session.getGame();
                game.playerLeave(player);
                player.sendMessage("§a已退出当前对局，正在返回大厅...");
            } else {
                player.sendMessage("§c你当前不在对局中！");
            }
            return true;
        }
        if (command.getName().equalsIgnoreCase("start")) {
           Game game = gameManager.getGameFromWorld(player.getWorld());
           if (!game.hasStarted()){
               StartCountdown startCountdown = game.getStartCountdown();
               startCountdown.setTime(0);
               game.setGameState(GameState.STARTING);
           }
        }
        if (command.getName().equalsIgnoreCase("end")) {
            Game game = gameManager.getGameFromWorld(player.getWorld());
            if (game.hasStarted()){
                game.setGameState(GameState.DRAW);
            }
        }
        if (command.getName().equalsIgnoreCase("wait")) {
            Game game = gameManager.getGameFromWorld(player.getWorld());
            if (!game.hasStarted()){
                StartCountdown startCountdown = game.getStartCountdown();
                startCountdown.setTime(999);
            }
        }

        return false;
    }
}
