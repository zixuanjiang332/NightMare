package jdd.nightMare;
import jdd.nightMare.Game.GameManager;
import jdd.nightMare.NightMare;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class LobbyBoardManager {

    private final GameManager gameManager;
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");

    public LobbyBoardManager(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    public void applyLobbyBoard(Player player) {
        Component header = Component.text("\n§d§l✦ §5§l噩梦空间 §d§l✦\n");
        Component footer = Component.text("\n§b✨ §e复刻噩梦空间 §7(§a只供游玩不做任何如盈利等其他意图§7) §b✨\n");
        player.sendPlayerListHeaderAndFooter(header, footer);
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective obj = board.registerNewObjective("lobby_board", "dummy", Component.text("§d§l✦ §5§l噩梦空间 §d§l✦"));
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        obj.getScore("§1").setScore(6); // 空行
        Team timeTeam = board.registerNewTeam("time");
        timeTeam.addEntry("§2");
        timeTeam.prefix(Component.text("§6⌚ 宇宙时间: §b" + LocalDateTime.now().format(formatter)));
        obj.getScore("§2").setScore(5);
        obj.getScore("§3").setScore(4); // 空行
        obj.getScore("§d👤 噩梦空间冠军: §a" + player.getName()).setScore(3);
        obj.getScore("§4").setScore(2); // 空行
        obj.getScore("§b§l复刻噩梦空间 §7(§e只供游玩不做任何如盈利等其他意图§7)").setScore(1);
        player.setScoreboard(board);
    }
    public void startUpdateTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                String timeStr = "§6⌚ 宇宙时间: §b" + LocalDateTime.now().format(formatter);
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (!gameManager.hasActiveSession( player)) {
                        Scoreboard board = player.getScoreboard();
                        Team timeTeam = board.getTeam("time");
                        if (timeTeam != null) {
                            timeTeam.prefix(Component.text(timeStr));
                        }
                    }
                }
            }
        }.runTaskTimer(NightMare.getInstance(), 0L, 20L);
    }
}