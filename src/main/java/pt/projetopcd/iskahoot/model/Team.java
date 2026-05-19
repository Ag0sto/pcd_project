package pt.projetopcd.iskahoot.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Team implements Serializable {

    private int id;
    private int score;
    private List<Player> players;

    public Team(int id) {
        this.id = id;
        this.score = 0;
        this.players = new ArrayList<Player>();
    }

    public int getId() {
        return id;
    }

    public int getScore() {
        return score;
    }

    public void addPlayer(Player player) {
        players.add(player);
    }

    public void addScore(Player player, int points) {
        if(players.contains(player)) {
            player.addScore(points);
        }
        this.score += points;
    }

}
