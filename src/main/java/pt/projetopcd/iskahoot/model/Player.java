package pt.projetopcd.iskahoot.model;

import java.io.Serializable;

public class Player implements Serializable {

    private int id;
    private String name;
    private int score;
    private String teamname;

    public Player(int id, String name, String teamname) {
        this.id       = id;
        this.name     = name;
        this.score    = 0;
        this.teamname = teamname;
    }

    public void setId(int id) { this.id = id; }

    public int    getId()    { return id; }
    public String getName()  { return name; }
    public int    getScore() { return score; }
    public String getTeam()  { return teamname; }

    public void addScore(int points) { this.score += points; }

    @Override
    public String toString() {
        return "Player{" + name + ", team=" + teamname + ", score=" + score + "}";
    }
}
