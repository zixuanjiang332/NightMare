package jdd.nightMare.GameConfig;

import jdd.nightMare.NightMare;
import jdd.nightMare.NightMare;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

public class MessagesConfig {

    private static final File file = new File(NightMare.getInstance().getDataFolder(), "messages.yml");
    private static FileConfiguration config;
    private static final MiniMessage mm = MiniMessage.miniMessage();

    public static void init(){
        if (!file.exists())
            NightMare.getInstance().saveResource("messages.yml", false);
        load();

    }

    public static void load(){
        config = YamlConfiguration.loadConfiguration(file);
    }

    public static String getMessage(String path){

        String messageStr = config.getString(path);
        if (messageStr == null)
            messageStr = "";
        return (messageStr);
    }
}
