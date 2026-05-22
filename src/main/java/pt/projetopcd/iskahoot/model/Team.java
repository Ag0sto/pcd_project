package pt.projetopcd.iskahoot.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Representa uma equipa.
 *
 * A pontuação total da equipa é sempre derivada da soma das
 * pontuações individuais dos seus jogadores (getTotalPoints()).
 * Não existe nenhum contador de pontos independente na equipa.
 */
public class Team implements Serializable {

    private final String      teamName;
    private final List<Player> players;

    public Team(String teamName) {
        this.teamName = teamName;
        this.players  = new ArrayList<>();
    }

    public void addPlayer(Player p) {
        players.add(p);
    }

    /** Pontuação total = soma das pontuações individuais de todos os jogadores. */
    public synchronized int getTotalPoints() {
        int sum = 0;
        for (Player p : players) sum += p.getTotalPoints();
        return sum;
    }

    /** Pontos ganhos pela equipa na ronda atual = soma dos roundPoints individuais. */
    public synchronized int getRoundPoints() {
        int sum = 0;
        for (Player p : players) sum += p.getRoundPoints();
        return sum;
    }

    public String      getTeamName() { return teamName; }
    public List<Player> getPlayers() { return Collections.unmodifiableList(players); }

    @Override
    public String toString() {
        return "Team{" + teamName + ", total=" + getTotalPoints() + "}";
    }
}
