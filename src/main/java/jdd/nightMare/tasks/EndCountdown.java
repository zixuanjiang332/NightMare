package jdd.nightMare.tasks;

import jdd.nightMare.Game.Game;
import org.bukkit.scheduler.BukkitRunnable;

public class EndCountdown extends BukkitRunnable {

    private final Game game;

    int timeleft = 10;
    public EndCountdown(Game game) {
        this.game = game;
    }

    @Override
    public void run() {
        if (timeleft<=0){
            game.deleteGame();
            cancel();
            return;
        }
        String message = "§c游戏结束倒计时: §e" + timeleft + " 秒";
        for (org.bukkit.entity.Player player : game.getInGamePlayers()) {
            player.sendActionBar(net.kyori.adventure.text.Component.text(message));
        }
        timeleft--;
    }

    public Game getGame() {
        return game;
    }
}
