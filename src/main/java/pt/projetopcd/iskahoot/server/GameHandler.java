package pt.projetopcd.iskahoot.server;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import pt.projetopcd.iskahoot.concurrency.ModifiedCountDownLatch;
import pt.projetopcd.iskahoot.concurrency.TeamBarrier;
import pt.projetopcd.iskahoot.model.Message;
import pt.projetopcd.iskahoot.model.Question;
import pt.projetopcd.iskahoot.model.QuestionLoader;

/**
 * Thread dedicada à gestão de um jogo.
 *
 * Ciclo: 1. Aguarda todos os jogadores registados. 2. Para cada pergunta: a.
 * Configura o latch/barrier em cada DealWithClient. b. Envia a pergunta. c.
 * Aguarda o latch/barrier (as respostas são processadas pelas threads dos
 * próprios DealWithClient, sem workers extra). d. Envia placar. 3. Envia
 * resultado final e encerra.
 */
public class GameHandler extends Thread {

    private static final int QUESTION_TIME = 30; // segundos por pergunta
    private static final int BONUS_FACTOR  = 2;  // pontuação dobrada para os 2 primeiros
    private static final int BONUS_COUNT   = 2;  // número de jogadores com bónus

    private final GameState game;
    private final Server server;
    private List<Question> questions;

    public GameHandler(GameState game, Server server) {
        this.game   = game;
        this.server = server;
        setDaemon(false);
    }

    @Override
    public void run() {
        System.out.println("[GameHandler:" + game.getGameId() + "] Jogo iniciado!");

        List<Question> all = QuestionLoader.loadQuestions();
        Collections.shuffle(all);
        int n = Math.min(game.getNumQuestions(), all.size());
        questions = all.subList(0, n);

        broadcast(new Message(Message.Type.WAITING, "O jogo vai começar!"));
        pause(2000);

        for (int i = 0; i < questions.size(); i++) {
            Question q = questions.get(i);
            boolean teamRound = (i % 2 == 1);

            game.setCurrentQuestion(q, i, teamRound);
            game.getScoreManager().resetRound();

            System.out.println("[GameHandler] Pergunta " + (i + 1) + "/" + questions.size()
                    + (teamRound ? " [EQUIPA]" : " [INDIVIDUAL]"));

            // Configura o latch/barrier em cada DealWithClient ANTES de enviar a pergunta
            if (teamRound) {
                prepareTeamRound(q);
            } else {
                prepareIndividualRound(q);
            }

            // Envia a pergunta — a partir daqui os DealWithClients processam as respostas
            Message.QuestionMsg qMsg = new Message.QuestionMsg(
                    i + 1, questions.size(),
                    q.getQuestion(), q.getOptions(),
                    q.getPoints(), QUESTION_TIME, teamRound
            );
            broadcast(new Message(Message.Type.QUESTION, qMsg));

            // Aguarda fim da ronda (todos responderam ou tempo esgotado)
            if (teamRound) {
                awaitTeamRound();
            } else {
                awaitIndividualRound();
            }

            // Limpa o contexto de ronda em todos os handlers
            for (DealWithClient dwc : game.getAllHandlers()) {
                dwc.clearRound();
            }

            // Envia placar
            Map<String, Integer> roundPts = game.getScoreManager().getRoundPoints();
            Map<String, Integer> totalPts = game.getTotalTeamScores();

            String bestTeam = roundPts.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey).orElse("-");

            broadcast(new Message(Message.Type.ROUND_END, new Message.RoundResult(
                    q.getCorrect(), new HashMap<>(totalPts),
                    new HashMap<>(roundPts), bestTeam, false
            )));

            if (i < questions.size() - 1) {
                pause(5000);
            }
        }

        // Fim do jogo
        Map<String, Integer> finalScores = game.getTotalTeamScores();
        String winner = finalScores.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey).orElse("-");

        System.out.println("[GameHandler:" + game.getGameId() + "] Fim do jogo! Vencedor: " + winner);

        broadcast(new Message(Message.Type.GAME_END, new Message.RoundResult(
                -1, new HashMap<>(finalScores),
                Collections.emptyMap(), winner, true
        )));

        for (DealWithClient dwc : game.getAllHandlers()) {
            dwc.stopClient();
        }

        server.removeGame(game.getGameId());
    }

    // -------------------------------------------------------
    // Ronda individual
    // -------------------------------------------------------

    // Guardado como campo para awaitIndividualRound() ter acesso
    private ModifiedCountDownLatch currentLatch;

    private void prepareIndividualRound(Question q) {
        currentLatch = new ModifiedCountDownLatch(
                BONUS_FACTOR, BONUS_COUNT, QUESTION_TIME, game.getRegisteredCount()
        );
        for (DealWithClient dwc : game.getAllHandlers()) {
            dwc.prepareIndividualRound(currentLatch);
        }
    }

    private void awaitIndividualRound() {
        try {
            currentLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // -------------------------------------------------------
    // Ronda de equipa
    // -------------------------------------------------------

    // Guardado como campo para awaitTeamRound() ter acesso
    private final Object teamsLock = new Object();
    private int teamsRemaining;

    private void prepareTeamRound(Question q) {
        Map<String, List<String>> teams = game.getTeams();
        teamsRemaining = teams.size();

        for (Map.Entry<String, List<String>> entry : teams.entrySet()) {
            String teamName = entry.getKey();
            List<String> members = entry.getValue();

            Map<String, Integer> teamAnswers = new HashMap<>();

            TeamBarrier barrier = new TeamBarrier(
                    members.size(), QUESTION_TIME,
                    allAnswered -> {
                        // barrierAction: calcula pontuação da equipa
                        int correct = q.getCorrect();
                        boolean allCorrect = !teamAnswers.isEmpty()
                                && teamAnswers.values().stream().allMatch(a -> a == correct);

                        int pts;
                        if (allCorrect) {
                            pts = q.getPoints() * 2;
                        } else {
                            long nCorrect = teamAnswers.values().stream()
                                    .filter(a -> a == correct).count();
                            pts = (int) (nCorrect * q.getPoints());
                        }

                        if (pts > 0) {
                            game.getScoreManager().addRoundPoints(teamName, pts);
                            game.addTeamScore(teamName, pts);
                        }
                        System.out.printf("  [Equipa] %s: allCorrect=%b pts=%d%n",
                                teamName, allCorrect, pts);

                        // Notifica o GameHandler que esta equipa terminou
                        synchronized (teamsLock) {
                            teamsRemaining--;
                            if (teamsRemaining <= 0) {
                                teamsLock.notifyAll();
                            }
                        }
                    }
            );

            // Configura cada DealWithClient da equipa com a barreira partilhada
            for (String username : members) {
                DealWithClient dwc = findHandler(username);
                if (dwc != null) {
                    dwc.prepareTeamRound(barrier, teamAnswers);
                }
            }
        }
    }

    private void awaitTeamRound() {
        synchronized (teamsLock) {
            long deadline = System.currentTimeMillis() + (QUESTION_TIME + 2) * 1000L;
            while (teamsRemaining > 0) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) break;
                try {
                    teamsLock.wait(remaining);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    // -------------------------------------------------------
    // Utilitários
    // -------------------------------------------------------
    private DealWithClient findHandler(String username) {
        for (DealWithClient dwc : game.getAllHandlers()) {
            if (dwc.getUsername().equals(username)) return dwc;
        }
        return null;
    }

    private void broadcast(Message msg) {
        for (DealWithClient dwc : game.getAllHandlers()) {
            dwc.send(msg);
        }
    }

    private void pause(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}