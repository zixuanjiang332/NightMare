package jdd.nightMare.Game;

import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GameConfiguration implements ConfigurationSerializable {

    private final String name;
    private final int minTeams;
    private final int maxTeams;
    private final int teamSize;
    private final int xpPerKill;
    private final int xpPerWin;
    private final String[] allowedMapIDs;

    public GameConfiguration(String name, int minTeams, int maxTeams, int teamSize,  int xpPerKill, int xpPerWin, String... allowedMapIDs) {
        this.maxTeams = maxTeams;
        this.minTeams = minTeams;
        this.name = name;
        this.teamSize = teamSize;
        this.xpPerKill = xpPerKill;
        this.xpPerWin = xpPerWin;
        this.allowedMapIDs = allowedMapIDs;
    }

    public int getMaxTeams() {
        return maxTeams;
    }

    public int getMinTeams() {
        return minTeams;
    }

    public String getName(){
        return name;
    }

    public String[] getAllowedMapIDs() {
        return allowedMapIDs;
    }

    public int getXpPerKill(){
        return xpPerKill;
    }

    public int getXpPerWin(){
        return xpPerWin;
    }

    @Override
    public @NotNull Map<String, Object> serialize() {
        Map<String, Object> map = new HashMap<>();
        map.put("name", name);
        map.put("min-teams", minTeams);
        map.put("max-teams", maxTeams);
        map.put("team-size", teamSize);
        map.put("map-ids", allowedMapIDs);
        map.put("xp-per-kill", xpPerKill);
        map.put("xp-per-win", xpPerWin);


        return map;
    }

    public static GameConfiguration deserialize(Map<String, Object> map){
        String name = (String) map.get("name");
        int minTeams = (int) map.get("min-teams");
        int maxTeams = (int) map.get("max-teams");
        int teamSize = (int) map.get("team-size");
        int xpPerWin = (int) map.get("xp-per-win");
        int xpPerKill = (int) map.get("xp-per-kill");
        List<String> ids = (List<String>) map.get("map-ids");
        String[] allowedMapIDs = ids.toArray(new String[0]);
        return new GameConfiguration(name, minTeams, maxTeams, teamSize, xpPerKill, xpPerWin, allowedMapIDs);
    }

    public int getTeamSize() {
        return teamSize;
    }
}
