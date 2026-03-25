package jdd.nightMare.Game;

import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class GameTeam {

    private final HashSet<Player> teamPlayers;
    private final String teamName;
    private Boolean bedAlive;
    private final Game game;
    public GameTeam(String teamName,Game game) {
        this.teamPlayers = new HashSet<>();
        this.teamName=teamName;
        this.bedAlive=true;
        this.game=game;
    }
    public void setBedAlive(Boolean bedAlive) {
        this.bedAlive = bedAlive;
    }
    public Boolean isBedAlive() {
        return bedAlive;
    }
    public Set<Player> getTeamPlayers() {
        return teamPlayers;
    }
    public String getTeamName() {
        return teamName;
    }
    public int getPlayerCount(){
        return teamPlayers.size();
    }
    public int getAlivedPlayerCount(){
        int count=0;
        for (Player player : teamPlayers){
            if (game.isAlive(player)){
                count++;
            }
        }
        return count;
    }
    public void addPlayer(Player player){
        teamPlayers.add(player);
    }

    public void removePlayer(Player player){
        teamPlayers.remove(player);
    }
}
