package pt.projetopcd.iskahoot.server;

import java.util.HashMap;
import java.util.Map;

/**
 * Regista as respostas dos jogadores para a ronda atual. Thread-safe via
 * synchronized.
 */
public class AnswerManager {

    /**
     * username -> índice da resposta escolhida
     */
    private final Map<String, Integer> answers = new HashMap<>();

    /**
     * username -> fator multiplicativo obtido no countDown
     */
    private final Map<String, Integer> factors = new HashMap<>();

    /**
     * Regista a resposta de um jogador (apenas uma vez).
     */
    public synchronized boolean submitAnswer(String username, int answerIndex, int factor) {
        if (answers.containsKey(username)) {
            return false; // já respondeu

        }
        answers.put(username, answerIndex);
        factors.put(username, factor);
        return true;
    }

    public synchronized Map<String, Integer> getAnswers() {
        return new HashMap<>(answers);
    }

    public synchronized Map<String, Integer> getFactors() {
        return new HashMap<>(factors);
    }

    public synchronized int getFactorFor(String username) {
        return factors.getOrDefault(username, 1);
    }

    public synchronized boolean hasAnswered(String username) {
        return answers.containsKey(username);
    }

    public synchronized int size() {
        return answers.size();
    }

    public synchronized void reset() {
        answers.clear();
        factors.clear();
    }
}
