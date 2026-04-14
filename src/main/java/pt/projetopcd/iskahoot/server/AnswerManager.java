package pt.projetopcd.iskahoot.server;

import java.util.*;

public class AnswerManager {

    private final Map<String, Integer> answers = new HashMap<>();

    public synchronized void submitAnswer(String playerId, int answerIndex) {
        if (!answers.containsKey(playerId)) {
            answers.put(playerId, answerIndex);
        }
    }

    public synchronized Map<String, Integer> getAnswers() {
        return new HashMap<>(answers);
    }

    public synchronized void reset() {
        answers.clear();
    }
}
