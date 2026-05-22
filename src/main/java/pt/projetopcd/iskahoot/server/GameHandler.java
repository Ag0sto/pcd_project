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
import pt.projetopcd.iskahoot.model.Player;
import pt.projetopcd.iskahoot.model.Question;
import pt.projetopcd.iskahoot.model.QuestionLoader;

/**
 * Thread do jogo.
 *
 * Pontuação: - Individual: cada Player.recordAnswer() guarda roundPoints e
 * totalPoints. - Equipa: Team.getTotalPoints() = soma dos Players (calculado
 * on-the-fly). - O ScoreManager constrói snapshots para enviar ao cliente.
 */
public class GameHandler extends Thread {

    private static final int QUESTION_TIME = 30;
    private static final int BONUS_FACTOR = 2;   // x2 para os primeiros a responder
    private static final int BONUS_COUNT = 2;   // quantos jogadores recebem bónus

    private final GameState game;
    private List<Question> questions;

    public GameHandler(GameState game) {
        this.game = game;
        setDaemon(false);
    }

    // ─────────────────────────────────────────────────────────
    // run
    // ─────────────────────────────────────────────────────────
    @Override
    public void run() {
        System.out.println("[GameHandler:" + game.getGameId() + "] Iniciado.");

        List<Question> all = QuestionLoader.loadQuestions();
        Collections.shuffle(all);
        questions = all.subList(0, Math.min(game.getNumQuestions(), all.size()));

        broadcast(new Message(Message.Type.WAITING, "O jogo vai começar!"));
        pause(2000);

        for (int i = 0; i < questions.size(); i++) {
            Question q = questions.get(i);
            boolean teamRound = (i % 2 == 1);

            // Reseta stats de ronda de todos os jogadores + AnswerManager
            game.setCurrentQuestion(q, i, teamRound);

            System.out.println("[GameHandler] Pergunta " + (i + 1) + "/" + questions.size()
                    + (teamRound ? " [EQUIPA]" : " [INDIVIDUAL]"));

            // Envia pergunta
            broadcast(new Message(Message.Type.QUESTION, new Message.QuestionMsg(
                    i + 1, questions.size(),
                    q.getQuestion(), q.getOptions(),
                    q.getPoints(), QUESTION_TIME, teamRound
            )));

            // Processa respostas e atualiza pontuações individuais
            if (teamRound) {
                processTeamRound(q);
            } else {
                processIndividualRound(q);
            }

            // Constrói snapshot com pontuações individuais + totais de equipa
            Message.RoundResult result = buildRoundResult(q.getCorrect(), false);
            broadcast(new Message(Message.Type.ROUND_END, result));

            pause(5000);
        }

        // Fim do jogo
        Message.RoundResult finalResult = buildRoundResult(-1, true);
        broadcast(new Message(Message.Type.GAME_END, finalResult));
        System.out.println("[GameHandler:" + game.getGameId() + "] Fim. Vencedor: "
                + finalResult.winnerTeam);

        for (DealWithClient dwc : game.getAllHandlers()) {
            dwc.stopClient();
        }
        Server.removeGame(game.getGameId());
    }

    // ─────────────────────────────────────────────────────────
    // Ronda individual — ModifiedCountDownLatch
    // ─────────────────────────────────────────────────────────
    private void processIndividualRound(Question q) {
        int total = game.getRegisteredCount();
        ModifiedCountDownLatch latch
                = new ModifiedCountDownLatch(BONUS_FACTOR, BONUS_COUNT, QUESTION_TIME, total);

        List<Thread> workers = new ArrayList<>();
        for (DealWithClient dwc : game.getAllHandlers()) {
            dwc.clearAnswer();
            Thread t = new Thread(() -> {
                try {
                    int answerIdx = dwc.waitForAnswer();
                    int factor = latch.countDown(); // obtém fator de bónus

                    boolean correct = (answerIdx == q.getCorrect());

                    // ► Pontuação registada no Player individual
                    game.recordPlayerAnswer(dwc.getUsername(), correct, q.getPoints(), factor);

                    System.out.printf("  [Ind] %-12s idx=%d %s pts=%d (x%d)%n",
                            dwc.getUsername(), answerIdx,
                            correct ? "CERTO" : "ERRADO",
                            correct ? q.getPoints() * factor : 0, factor);

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            t.setDaemon(true);
            t.start();
            workers.add(t);
        }

        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        workers.forEach(Thread::interrupt);
    }

    // ─────────────────────────────────────────────────────────
    // Ronda de equipa — TeamBarrier
    // ─────────────────────────────────────────────────────────
    private void processTeamRound(Question q) {
        Map<String, pt.projetopcd.iskahoot.model.Team> teams = game.getTeams();
        List<Thread> allWorkers = new ArrayList<>();
        CountDownLatch allTeamsDone = new CountDownLatch(teams.size());

        for (pt.projetopcd.iskahoot.model.Team team : teams.values()) {
            String teamName = team.getTeamName();

            // Respostas dos membros desta equipa nesta ronda
            Map<String, Integer> teamAnswers = new HashMap<>();

            TeamBarrier barrier = new TeamBarrier(
                    team.getPlayers().size(), QUESTION_TIME,
                    allAnswered -> {
                        // BarrierAction: determina pontos individuais de cada membro
                        int correct = q.getCorrect();

                        for (Player p : team.getPlayers()) {
                            Integer ans;
                            synchronized (teamAnswers) {
                                ans = teamAnswers.get(p.getName());
                            }
                            boolean isCorrect = (ans != null && ans == correct);

                            // Bónus de equipa: se todos acertaram, factor = 2;
                            // caso contrário, factor = 1 (sem bónus de velocidade nas rondas de equipa)
                            boolean allCorrect = !teamAnswers.isEmpty() && teamAnswers.values().stream().allMatch(a -> a == correct);
                            int factor = allCorrect ? 2 : 1;

                            game.recordPlayerAnswer(p.getName(), isCorrect, q.getPoints(), factor);

                            System.out.printf("  [Eq] %-12s %s pts=%d (x%d, allCorrect=%b)%n", p.getName(), isCorrect ? "CERTO" : (ans == null ? "SEM RESP." : "ERRADO"), isCorrect ? q.getPoints() * factor : 0, factor, allCorrect);
                        }
                        allTeamsDone.countDown();
                    }
            );

            // Uma worker thread por membro
            for (Player p : team.getPlayers()) {
                DealWithClient dwc = game.getHandler(p.getName());
                if (dwc == null) {
                    continue;
                }
                dwc.clearAnswer();

                Thread t = new Thread(() -> {
                    try {
                        int answerIdx = dwc.waitForAnswer();
                        synchronized (teamAnswers) {
                            teamAnswers.put(p.getName(), answerIdx);
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

        // Aguarda tempo máximo + margem (barreiras têm timers próprios)
        try {
            allTeamsDone.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        allWorkers.forEach(Thread::interrupt);
    }

    // ─────────────────────────────────────────────────────────
    // Construção do RoundResult
    // ─────────────────────────────────────────────────────────
    private Message.RoundResult buildRoundResult(int correctOption, boolean isGameEnd) {
        List<Message.PlayerResult> playerResults = ScoreManager.buildPlayerResults(game);
        List<Message.TeamResult> teamResults = ScoreManager.buildTeamResults(game);
        String winner = isGameEnd ? ScoreManager.getWinnerTeam(game) : null;

        return new Message.RoundResult(correctOption, playerResults, teamResults,
                winner, isGameEnd);
    }

    // ─────────────────────────────────────────────────────────
    // Utilitários
    // ─────────────────────────────────────────────────────────
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
