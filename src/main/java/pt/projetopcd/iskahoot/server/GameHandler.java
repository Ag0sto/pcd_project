package pt.projetopcd.iskahoot.server;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import pt.projetopcd.iskahoot.concurrency.ModifiedCountDownLatch;
import pt.projetopcd.iskahoot.concurrency.TeamBarrier;
import pt.projetopcd.iskahoot.model.Message;
import pt.projetopcd.iskahoot.model.Question;
import pt.projetopcd.iskahoot.model.QuestionLoader;

/**
 * Thread dedicada à gestão de um jogo.
 *
 * Ciclo: 1. Aguarda todos os jogadores registados. 2. Para cada pergunta: a.
 * Envia pergunta a todos os clientes. b. Aguarda respostas (via
 * ModifiedCountDownLatch ou TeamBarrier). c. Calcula pontuações. d. Envia
 * placar a todos os clientes. 3. Envia resultado final e encerra.
 */
public class GameHandler extends Thread {

    private static final int QUESTION_TIME = 30; // segundos por pergunta
    private static final int BONUS_FACTOR = 2;  // pontuação dobrada para os 2 primeiros
    private static final int BONUS_COUNT = 2;  // número de jogadores com bónus

    private final GameState game;
    private List<Question> questions;

    public GameHandler(GameState game) {
        this.game = game;
        setDaemon(false);
    }

    @Override
    public void run() {
        System.out.println("[GameHandler:" + game.getGameId() + "] Jogo iniciado!");

        // Carrega e seleciona perguntas aleatoriamente
        List<Question> all = QuestionLoader.loadQuestions();
        Collections.shuffle(all);
        int n = Math.min(game.getNumQuestions(), all.size());
        questions = all.subList(0, n);

        // Envia mensagem de início a todos
        broadcast(new Message(Message.Type.WAITING, "O jogo vai começar!"));
        pause(2000);

        // Ciclo de perguntas
        for (int i = 0; i < questions.size(); i++) {
            Question q = questions.get(i);
            boolean teamRound = (i % 2 == 1); // alterna: 0=individual, 1=equipa, ...

            game.setCurrentQuestion(q, i, teamRound);
            game.getScoreManager().resetRound();

            System.out.println("[GameHandler] Pergunta " + (i + 1) + "/" + questions.size()
                    + (teamRound ? " [EQUIPA]" : " [INDIVIDUAL]"));

            // Envia pergunta
            Message.QuestionMsg qMsg = new Message.QuestionMsg(
                    i + 1, questions.size(),
                    q.getQuestion(), q.getOptions(),
                    q.getPoints(), QUESTION_TIME, teamRound
            );
            broadcast(new Message(Message.Type.QUESTION, qMsg));

            // Processa respostas
            if (teamRound) {
                processTeamRound(q, i);
            } else {
                processIndividualRound(q, i);
            }

            // Calcula e envia placar
            Map<String, Integer> roundPts = game.getScoreManager().getRoundPoints();
            Map<String, Integer> totalPts = game.getTotalTeamScores();

            // Determina melhor equipa na ronda
            String bestTeam = roundPts.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey).orElse("-");

            Message.RoundResult result = new Message.RoundResult(
                    q.getCorrect(), new HashMap<>(totalPts),
                    new HashMap<>(roundPts), bestTeam, false
            );
            broadcast(new Message(Message.Type.ROUND_END, result));

            pause(5000); // pausa entre rondas para o cliente mostrar o placar
        }

        // Fim do jogo
        Map<String, Integer> finalScores = game.getTotalTeamScores();
        String winner = finalScores.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey).orElse("-");

        System.out.println("[GameHandler:" + game.getGameId() + "] Fim do jogo! Vencedor: " + winner);

        Message.RoundResult finalResult = new Message.RoundResult(
                -1, new HashMap<>(finalScores),
                Collections.emptyMap(), winner, true
        );
        broadcast(new Message(Message.Type.GAME_END, finalResult));

        // Encerra todos os handlers de clientes
        for (DealWithClient dwc : game.getAllHandlers()) {
            dwc.stopClient();
        }

        Server.removeGame(game.getGameId());
    }

    // -------------------------------------------------------
    // Ronda individual: CountDownLatch modificado
    // -------------------------------------------------------
    private void processIndividualRound(Question q, int questionIndex) {
        int totalPlayers = game.getRegisteredCount();

        ModifiedCountDownLatch latch = new ModifiedCountDownLatch(
                BONUS_FACTOR, BONUS_COUNT, QUESTION_TIME, totalPlayers
        );

        // Para cada handler, lança uma thread de receção de resposta
        List<Thread> workers = new ArrayList<>();
        for (DealWithClient dwc : game.getAllHandlers()) {
            dwc.clearAnswer();
            Thread t = new Thread(() -> {
                try {
                    int answerIdx = dwc.waitForAnswer();
                    int factor = latch.countDown(); // regista e obtém fator

                    boolean correct = (answerIdx == q.getCorrect());
                    int pts = correct ? q.getPoints() * factor : 0;

                    String team = game.getTeamOf(dwc.getUsername());
                    if (pts > 0 && team != null) {
                        game.getScoreManager().addRoundPoints(team, pts);
                        game.addTeamScore(team, pts);
                    }

                    System.out.printf("  [Individual] %s respondeu %d (%s) -> %d pts (x%d)%n",
                            dwc.getUsername(), answerIdx,
                            correct ? "CERTO" : "ERRADO", pts, factor);

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            t.setDaemon(true);
            t.start();
            workers.add(t);
        }

        // Aguarda fim da ronda (todos responderam ou tempo esgotado)
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Interrompe workers que ainda estejam bloqueados
        workers.forEach(Thread::interrupt);

        // Aguarda que todos os workers terminem antes de continuar
        for (Thread t : workers) {
            try {
                t.join(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    // -------------------------------------------------------
    // Ronda de equipa: TeamBarrier
    // -------------------------------------------------------
    private void processTeamRound(Question q, int questionIndex) {
        Map<String, List<String>> teams = game.getTeams();
        List<Thread> allWorkers = new ArrayList<>();

        CountDownLatch allTeamsDone = new CountDownLatch(teams.size());

        // Uma barreira por equipa
        for (Map.Entry<String, List<String>> entry : teams.entrySet()) {
            String teamName = entry.getKey();
            List<String> members = entry.getValue();

            // Respostas da equipa nesta ronda
            Map<String, Integer> teamAnswers = new HashMap<>();

            TeamBarrier barrier = new TeamBarrier(
                    members.size(), QUESTION_TIME,
                    allAnswered -> {
                        // barrierAction: calcula pontuação da equipa
                        int correct = q.getCorrect();
                        boolean allCorrect = !teamAnswers.isEmpty()
                        && teamAnswers.values().stream().allMatch(a -> a == correct);

                        int pts = 0;
                        if (allCorrect) {
                            pts = q.getPoints() * 2; // bónus equipa completa
                        } else {
                            // conta só os que acertaram individualmente
                            long nCorrect = teamAnswers.values().stream().filter(a -> a == correct).count();
                            pts = (int) (nCorrect * q.getPoints());
                        }

                        if (pts > 0) {
                            game.getScoreManager().addRoundPoints(teamName, pts);
                            game.addTeamScore(teamName, pts);
                        }
                        System.out.printf("  [Equipa] %s: allCorrect=%b pts=%d%n",
                                teamName, allCorrect, pts);

                        // Notifica o GameHandler que esta equipa terminou
                        allTeamsDone.countDown();
                    }
            );

            // Thread por jogador da equipa
            for (String username : members) {
                DealWithClient dwc = findHandler(username);
                if (dwc == null) {
                    allTeamsDone.countDown();
                    continue;
                }
                dwc.clearAnswer();

                Thread t = new Thread(() -> {
                    try {
                        int answerIdx = dwc.waitForAnswer();
                        synchronized (teamAnswers) {
                            teamAnswers.put(username, answerIdx);
                        }
                        barrier.arrive();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        try {
                            barrier.arrive();
                        } catch (InterruptedException ignored) {
                        }
                    }
                });
                t.setDaemon(true);
                t.start();
                allWorkers.add(t);
            }
        }

        // Aguarda todas as barreiras libertadas (o tempo máximo é gerido pelas barreiras)
        // Aguardamos o QUESTION_TIME + margem
        try {
            allTeamsDone.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        allWorkers.forEach(Thread::interrupt);
    }

    // -------------------------------------------------------
    // Utilitários
    // -------------------------------------------------------
    private DealWithClient findHandler(String username) {
        for (DealWithClient dwc : game.getAllHandlers()) {
            if (dwc.getUsername().equals(username)) {
                return dwc;
            }
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
