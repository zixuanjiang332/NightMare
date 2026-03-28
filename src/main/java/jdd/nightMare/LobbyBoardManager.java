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

    /**
     * 为玩家应用大厅的炫酷 TAB 和计分板
     */
    public void applyLobbyBoard(Player player) {
        // 1. 设置炫酷的 TAB 列表 (星芒符号 + 多重渐变感配色)
        Component header = Component.text("\n§d§l✦ §5§l噩梦空间 §d§l✦\n");
        Component footer = Component.text("\n§b✨ §e复刻噩梦空间 §7(§a只给朋友玩不放服务器§7) §b✨\n");
        player.sendPlayerListHeaderAndFooter(header, footer);

        // 2. 创建独立的大厅计分板 (标题加上了粉色星芒)
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective obj = board.registerNewObjective("lobby_board", "dummy", Component.text("§d§l✦ §5§l噩梦空间 §d§l✦"));
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        // --- 开始排版右侧炫酷计分板 ---
        obj.getScore("§1").setScore(6); // 空行
        // 动态时间 (金色手表符号 + 亮蓝时间文本)
        Team timeTeam = board.registerNewTeam("time");
        timeTeam.addEntry("§2"); // 颜色代码占位符
        timeTeam.prefix(Component.text("§6⌚ 宇宙时间: §b" + LocalDateTime.now().format(formatter)));
        obj.getScore("§2").setScore(5);

        obj.getScore("§3").setScore(4); // 空行
        // 玩家 ID (粉色小人符号 + 荧光绿名字，把玩家称呼改为"猎梦者"提升逼格)
        obj.getScore("§d👤 噩梦空间冠军: §a" + player.getName()).setScore(3);
        obj.getScore("§4").setScore(2); // 空行
        // 专属留言 (亮蓝加粗主体 + 黄色高亮后缀)
        obj.getScore("§b§l复刻噩梦空间 §7(§e只给朋友玩不放服务器§7)").setScore(1);

        // 赋予玩家
        player.setScoreboard(board);
    }
    /**
     * 启动大厅时间刷新任务
     */
    public void startUpdateTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                // 这里的格式必须和上面的 timeTeam.prefix 保持绝对一致！
                String timeStr = "§6⌚ 宇宙时间: §b" + LocalDateTime.now().format(formatter);

                for (Player player : Bukkit.getOnlinePlayers()) {
                    // 只为在大厅里（不在游戏中）的玩家刷新时间
                    if (!gameManager.hasActiveSession( player)) {
                        Scoreboard board = player.getScoreboard();
                        Team timeTeam = board.getTeam("time");
                        if (timeTeam != null) {
                            timeTeam.prefix(Component.text(timeStr));
                        }
                    }
                }
            }
        }.runTaskTimer(NightMare.getInstance(), 0L, 20L); // 每秒 (20 ticks) 刷新一次
    }
}