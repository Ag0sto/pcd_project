package pt.projetopcd.iskahoot.server;

import java.util.HashMap;
import java.util.Map;

public class ScoreManager {

    private final Map<String, Integer> teamScores = new HashMap<>();

    public synchronized void addPlayerScore(String teamId, int score) {
        teamScores.put(teamId, teamScores.getOrDefault(teamId, 0) + score);
    }

    public synchronized Map<String, Integer> getTeamScores() {
        return new HashMap<>(teamScores);
    }
}
