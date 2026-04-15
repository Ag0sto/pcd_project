
package pt.projetopcd.iskahoot.model;

public class Player {

    private String id;
    private String name;
    private int score;
    private Team team;

    public Player(String id, String name, Team team) {
        this.id = id;
        this.name = name;
        this.score = 0;
        this.team = team;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getScore() {
        return score;
    }

    public void addScore(int points) {
        this.score += points;
    }

    public Team getTeam() {
        return team;
    }
}
