package pt.projetopcd.iskahoot.server;

import java.util.HashMap;
import java.util.Map;

/**
 * Gere as pontuações acumuladas e os pontos da ronda atual.
 */
public class ScoreManager {

    private final Map<String, Integer> roundPoints = new HashMap<>();

    public synchronized void addRoundPoints(String team, int pts) {
        roundPoints.merge(team, pts, Integer::sum);
    }

    public synchronized Map<String, Integer> getRoundPoints() {
        return new HashMap<>(roundPoints);
    }

    public synchronized void resetRound() {
        roundPoints.clear();
    }
}
