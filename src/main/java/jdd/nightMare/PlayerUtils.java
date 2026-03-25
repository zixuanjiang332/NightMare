package jdd.nightMare;

import jdd.nightMare.GameConfig.LevelsConfig;
import jdd.nightMare.GameConfig.PlayerConfig;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public class PlayerUtils {

    public static void attemptLevelUp(Player player) {
        int xp = PlayerConfig.getXp(player);
        int level = PlayerConfig.getLevel(player);

        int xpRequired;
        try {
            xpRequired = LevelsConfig.getXp(level + 1);
        } catch (Exception e) {
            return;
        }

        if (xp >= xpRequired) {
            xp -= xpRequired;
            level++;

            player.sendRichMessage("<yellow>You leveled up! Now level <gold>" + level);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        } else {
            return;
        }

        PlayerConfig.setXp(player, xp);
        PlayerConfig.addLevel(player);
        attemptLevelUp(player);
    }

    public static void addXp(Player player, int amount, Message message){
        PlayerConfig.addXp(player, amount);
        player.sendRichMessage(message.setPlaceholders(Placeholder.unparsed("amount", String.valueOf(amount))));
        attemptLevelUp(player);
    }

    public static void displayProgressBar(Player player){
        int currentXp = PlayerConfig.getXp(player);
        int currentLevel = PlayerConfig.getLevel(player);

        int nextLevelXp = LevelsConfig.getXp(currentLevel + 1);
        int barLength = 20;
        int progress = (int) (((double) currentXp / nextLevelXp) * barLength);

        String bar = "<green>█".repeat(progress) + "<gray>█".repeat(barLength-progress);
        player.sendRichMessage("<gold>Progress: <yellow>%s<gray>/<yellow>%s".formatted(currentXp, nextLevelXp));
        player.sendRichMessage("    <dark_gray><bold>[</bold>" + bar + "<bold><dark_gray>]");
    }

    public static void teleport(Player player, Location location){
        player.setVelocity(new Vector(0,0,0));
        player.teleport(location);
    }

    public static void refreshPlayer(Player player){
        player.getInventory().clear();
        player.setHealth(20);
        player.setFoodLevel(20);
        player.setSaturation(6);
        player.setExperienceLevelAndProgress(0);
        player.clearActivePotionEffects();
        player.setGameMode(GameMode.SURVIVAL);
    }
}
