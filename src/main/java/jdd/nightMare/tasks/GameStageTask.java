package jdd.nightMare.tasks;

import jdd.nightMare.Game.*;
import jdd.nightMare.NightMare;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Team;

public class GameStageTask extends BukkitRunnable {
    private final Game game;
    private GamePhase currentPhase = GamePhase.DAY_1;
    private int secondsRemaining = 180; // 每个阶段 180 秒
    private int gameuptimeSeconds = 0;
    private final GameManager gameManager;
    public GameStageTask(Game game, GameManager gameManager) {
        this.game = game;
        this.gameManager = gameManager;
    }

    @Override
    public void run() {
        gameuptimeSeconds++;
        for (Player p : game.getGamePlayers().keySet()) {
            PlayerSession session = gameManager.getPlayerSession(p);
            if (session == null) continue;
            session.tickBrandCooldowns();
            session.tickSpeicalItemCooldowns();
            if (gameuptimeSeconds == 90) {
                if (session.hasBrand(BrandType.TNT_SUPPLY.id)) {
                    p.getInventory().addItem(new ItemStack(Material.TNT, 1));
                    p.sendMessage("§4[烙印] TNT 补给已送达！");
                }
                if (session.hasBrand(BrandType.DEFENDER.id)) {
                    p.getInventory().addItem(new ItemStack(Material.OAK_PLANKS, 8));
                    p.sendMessage("§e[烙印] 保卫者物资已送达！");
                }
            }

        }
        secondsRemaining--;

        // --- 新增：倒计时 30 秒预警 ---
        if (secondsRemaining == 30&& !currentPhase.equals(GamePhase.NIGHT_5)) {
            sendPhaseWarning();
        }
        game.getSpawners().forEach(spawner -> spawner.tick(currentPhase));

        if (secondsRemaining <= 0) {
            if (currentPhase == GamePhase.NIGHT_5) {
                if (game.getAliveTeams().size() > 1) {
                    game.setGameState(GameState.DRAW);
                } else {
                    game.setGameState(GameState.ENDED);
                }
                this.cancel();
                return;
            }

            transitionToNextPhase();
        }

        for (Player p : game.getGamePlayers().keySet()) {
            updateTimeOnly(p);
        }
    }

    private void transitionToNextPhase() {
        this.currentPhase = currentPhase.next();
        this.secondsRemaining = 180;
        Bukkit.broadcast(Component.text("§6阶段切换: §e" + currentPhase.name()));
        long targetTime = currentPhase.isNight() ? 18000L : 1000L;
        startSmoothTimeShift(game.getMap().getBukkitWorld(), targetTime);
        int bossLevel = (currentPhase.ordinal() >= GamePhase.NIGHT_3.ordinal()) ? 2 : 1;
        if (currentPhase.isNight()&&!currentPhase.equals(GamePhase.NIGHT_3)) {
            game.getBossManager().spawnNightBosses(bossLevel);
        }
        else{
            game.getBossManager().cleanup();
        }
        switch (currentPhase) {
            case NIGHT_1:
            case NIGHT_2:
                break;
            case NIGHT_3:
                destroyAllBeds();
                game.getBossManager().spawnMidBoss();
                break;
            case NIGHT_4:
                break;
            case DAY_5:
                break;
            case NIGHT_5:
                startWorldBorderShrink();
                break;
        }
    }
    private void startSmoothTimeShift(World world, long targetTime) {
        new BukkitRunnable() {
            private int ticksRun = 0;
            private final int totalTicks = 100;

            @Override
            public void run() {
                if (ticksRun >= totalTicks) {
                    world.setTime(targetTime);
                    this.cancel();
                    return;
                }
                long currentTime = world.getTime();
                long diff = (targetTime - currentTime + 24000) % 24000;
                long increment = diff / (totalTicks - ticksRun);
                world.setTime(currentTime + increment);
                ticksRun++;
            }
        }.runTaskTimer(NightMare.getInstance(), 0L, 1L);
    }

    private void sendPhaseWarning() {
        String nextStatus = currentPhase.isNight() ? "§e§l白天" : "§c§l黑夜";
        String title = "§6§l阶段切换预警";
        String subtitle = "§f距离 " + nextStatus + " §f降临还有 §e30 §f秒";
        for (Player p : game.getGamePlayers().keySet()) {
            p.sendTitle(title, subtitle, 10, 40, 10);
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
            p.sendMessage("§6[NightMare] §f准备好，" + nextStatus + " §f即将在 30 秒后开始。");
        }
    }

    private void destroyAllBeds() {
        Bukkit.broadcast(Component.text("§c[警告] 所有的床已自毁！玩家现在无法重生！"));
        for (Player online : game.getGamePlayers().keySet()){
            online.playSound(online.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.8f);
        }
        for (Player online : game.getGamePlayers().keySet()){
            online.sendTitle("§c§l床被拆毁了！", "§f你失去了复活机会", 10, 40, 10);
        }
        game.getGameTeams().forEach(team -> team.setBedAlive(false));
        game.getGameTeams().forEach(team -> {
            Block block = game.getMap().getTeamBedLocation(team.getTeamName()).getBlock();
            if (!(block.getBlockData() instanceof org.bukkit.block.data.type.Bed bedData)) {
                return;
            }
            Block otherPart = block.getRelative(bedData.getFacing());
            block.setType(Material.AIR, false);
            if (otherPart.getType().name().endsWith("_BED")) {
                otherPart.setType(Material.AIR, false);
            }
        });
        game.updateAllScoreboards();
    }

    private void startWorldBorderShrink() {
        World world = game.getMap().getBukkitWorld();
        WorldBorder border = world.getWorldBorder();
        border.setCenter(0.5, 0.5); // 设置中心点
        border.setSize(250);                // 初始大小
        border.setDamageAmount(1); // 越界每秒扣血量
        border.setWarningDistance(2);
        border.setSize(1, 180);
    }
    public void stopGame() {
        game.getBossManager().cleanup();
    }
    private void updateTimeOnly(Player p) {
        Team t = p.getScoreboard().getTeam("phaseDisplay");
        if (t != null) {
            t.suffix(Component.text(currentPhase.getDisplayName() + " " + formatTime(secondsRemaining), NamedTextColor.YELLOW));
        }
    }
    private String formatTime(int seconds) {
        return String.format("%02d:%02d", seconds / 60, seconds % 60);
    }
    public GamePhase getCurrentPhase() { return currentPhase; }
    public int getSecondsRemaining() { return secondsRemaining; }
}