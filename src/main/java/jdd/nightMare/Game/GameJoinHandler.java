package jdd.nightMare.Game;

import jdd.nightMare.Message;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;

public class GameJoinHandler {


    public static void joinGame(Player player, GameConfiguration gameSettings, GameManager gameManager){

        if (gameManager.hasActiveSession(player))
            return;

        HashMap<String, Game> games = gameManager.getGames();
        if (!games.isEmpty()) {
            boolean isFree = false;
            for (Game game : games.values()) {
                if (gameSettings == game.getGameConfiguration() && game.getPlayerCount() < game.getMaxPlayers() && !game.hasStarted()) {
                    isFree = true;
                    game.playerJoin(player);
                    return;
                }
            }
                if (!isFree) {
                    Game newgame = gameManager.createGame(gameSettings);
                    List<String> maps = GameManager.getValidMaps(gameSettings.getAllowedMapIDs());
                    if (maps.isEmpty()) {
                        Message.send(player, "<red>没有有效的地图");
                        return;
                    }
                    if (newgame != null) {
                        newgame.playerJoin(player);
                        return;
                    }

                }

        }
        else {
            Game game = gameManager.createGame(gameSettings);
            List<String> maps = GameManager.getValidMaps(gameSettings.getAllowedMapIDs());
            if (maps.isEmpty()){
                Message.send(player, "<red>没有有效的地图");
                return;
            }

            if (game!=null){
                game.playerJoin(player);
                return;
            }
        }

        Message.send(player, "<red>无法找到一个游戏去加入");
    }

    public static void joinGame(Player player, String id, GameManager gameManager){
        if (!gameManager.getGames().containsKey(id)){
            Message.send(player, "<red>这个游戏不存在");
            return;
        }
        Game game = gameManager.getGames().get(id);

        game.playerJoin(player);
    }

}
