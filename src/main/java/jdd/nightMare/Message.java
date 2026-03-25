package jdd.nightMare;

import jdd.nightMare.GameConfig.MessagesConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;

public enum Message {
    KILL_MESSAGE("<red><player> <gray>击败了 <red><target>"),
    PLAYER_ONLY_COMMAND("<red>只有玩家可以执行此命令"),
    NO_COMMAND_PERMISSION("<red>你没有权限执行此命令"),
    INVALID_USAGE("<red>未知命令"),
    COMMAND_NOT_FOUND("<red>未发现命令 "),
    COMMAND_NOT_ALLOWED_IN_GAME("<red>你无法在游戏进行时使用此命令"),
    COMMAND_ONLY_IN_GAME("<red>你必须在游戏内使用此命令"),
    PLAYER_JOINED_GAME(MessagesConfig.getMessage("player-joined-game")),
    PLAYER_LEFT_GAME(MessagesConfig.getMessage("player-left-game")),
    PLAYER_QUIT_GAME(MessagesConfig.getMessage("player-quit-game")),
    GAME_STARTING(MessagesConfig.getMessage("game-starting")),
    GAME_STARTING_TITLE(MessagesConfig.getMessage("game-starting-title")),
    GAME_STARTING_SUBTITLE(MessagesConfig.getMessage("game-starting-subtitle")),
    GAME_STARTED_TITLE(MessagesConfig.getMessage("game-started-title")),
    GAME_STARTED_SUBTITLE(MessagesConfig.getMessage("game-started-subtitle")),
    GAME_STARTED(MessagesConfig.getMessage("game-started")),
    CHEST_REFILLED(MessagesConfig.getMessage("chest-refilled")),
    VICTORY_TITLE(MessagesConfig.getMessage("victory-title")),
    VICTORY_SUBTITLE(MessagesConfig.getMessage("victory-subtitle")),
    LOST_TITLE(MessagesConfig.getMessage("lost-title")),
    LOST_SUBTITLE(MessagesConfig.getMessage("lost-subtitle")),
    GAME_ENDED_TITLE(MessagesConfig.getMessage("game-ended-title")),
    GAME_ENDED_SUBTITLE(MessagesConfig.getMessage("game-ended-subtitle")),
    GAME_ENDED(MessagesConfig.getMessage("game-ended")),
    KILL_XP_GAINED(MessagesConfig.getMessage("xp-gained-kill")),
    WIN_XP_GAINED(MessagesConfig.getMessage("xp-gained-win"));

    private final String message;
    private static final String prefix = MessagesConfig.getMessage("prefix");

    Message(String message) {
        this.message = message;
    }

    public void send(CommandSender to) {
        String msg = prefix + message;
        to.sendRichMessage(msg);
    }

    public void send(CommandSender to, TagResolver... placeholders) {
        String text = setPlaceholders(placeholders);
        String msg = prefix + text;
        to.sendRichMessage(msg);
    }

    public String setPlaceholders(TagResolver... placeholders){
        Component text = MiniMessage.miniMessage().deserialize(message, TagResolver.resolver(placeholders));
        return MiniMessage.miniMessage().serialize(text);
    }

    public static void send(CommandSender to,String message){
        to.sendRichMessage(prefix + message);
    }

    public static String getPrefix(){
        return prefix;
    }

    public String text(){
        return message;
    }
}
