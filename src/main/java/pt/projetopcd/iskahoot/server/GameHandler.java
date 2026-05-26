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
import pt.projetopcd.iskahoot.model.Team;

/**
 * Thread do jogo.
 *
 * Pontuação:
 * - Individual: cada Player.recordAnswer() guarda roundPoints e totalPoints.
 * - Equipa: Team.getTotalPoints() = soma dos Players (calculado on-the-fly).
 * - O ScoreManager constrói snapshots para enviar ao cliente.
 *
 * ALTERAÇÃO: eliminadas as threads auxiliares em processIndividualRound() e
 * processTeamRound(). As respostas chegam através de notifyAnswer(), chamado
 * diretamente pela thread do DealWithClient de cada jogador, que já existe
 * — não há necessidade de criar novas threads só para aguardar respostas.
 */
public class GameHandler extends Thread {

    private static final int QUESTION_TIME = 30;
    private static final int BONUS_FACTOR  = 2;
    private static final int BONUS_COUNT   = 2;

    private final GameState game;
    private final Server    server;
    private List<Question>  questions;

    // Estado da ronda atual — acedido pela thread do GameHandler (leitura)
    // e pelas threads DealWithClient (escrita via notifyAnswer).
    // Marcados volatile ou protegidos para garantir visibilidade.

    // Ronda individual
    private volatile ModifiedCountDownLatch currentLatch = null;

    // Ronda de equipa: teamName → barreira e mapa de respostas
    private volatile Map<String, TeamBarrier>           currentBarriers    = null;
    private volatile Map<String, Map<String, Integer>>  currentTeamAnswers = null;

    public GameHandler(GameState game, Server server) {
        this.game   = game;
        this.server = server;
        setDaemon(false);
    }

    // ─────────────────────────────────────────────────────────
    // run
    // ─────────────────────────────────────────────────────────

    @Override
    public void run() {
        System.out.println("[GameHandler:" + game.getGameId() + "] Iniciado.");

        // Informar cada DealWithClient de qual é o GameHandler ativo,
        // para que possam chamar notifyAnswer() diretamente.
        for (DealWithClient dwc : game.getAllHandlers()) {
            dwc.setGameHandler(this);
        }

        List<Question> all = QuestionLoader.loadQuestions();
        Collections.shuffle(all);
        questions = all.subList(0, Math.min(game.getNumQuestions(), all.size()));

        broadcast(new Message(Message.Type.WAITING, "O jogo vai começar!"));
        pause(2000);

        for (int i = 0; i < questions.size(); i++) {
            Question q = questions.get(i);
            boolean teamRound = (i % 2 == 1);

            game.setCurrentQuestion(q, i, teamRound);

            System.out.println("[GameHandler] Pergunta " + (i + 1) + "/" + questions.size()
                    + (teamRound ? " [EQUIPA]" : " [INDIVIDUAL]"));

            broadcast(new Message(Message.Type.QUESTION, new Message.QuestionMsg(
                    i + 1, questions.size(),
                    q.getQuestion(), q.getOptions(),
                    q.getPoints(), QUESTION_TIME, teamRound
            )));

            if (teamRound) {
                processTeamRound(q);
            } else {
                processIndividualRound(q);
            }

            // Limpa o estado de ronda para a próxima pergunta
            currentLatch       = null;
            currentBarriers    = null;
            currentTeamAnswers = null;

            broadcast(new Message(Message.Type.ROUND_END, buildRoundResult(q.getCorrect(), false)));

            if (i < questions.size() - 1) {
                pause(5000);
            }
        }

        // Fim do jogo
        Message.RoundResult finalResult = buildRoundResult(-1, true);
        broadcast(new Message(Message.Type.GAME_END, finalResult));
        System.out.println("[GameHandler:" + game.getGameId() + "] Fim. Vencedor: "
                + finalResult.winnerTeam);

        for (DealWithClient dwc : game.getAllHandlers()) {
            dwc.stopClient();
        }
        server.removeGame(game.getGameId());
    }

    // ─────────────────────────────────────────────────────────
    // Ponto de entrada das respostas — chamado pela thread do DealWithClient
    // ─────────────────────────────────────────────────────────

    /**
     * Chamado pela thread do DealWithClient quando o jogador envia uma resposta.
     *
     * Consoante o tipo de ronda atual, delega para o processamento individual
     * ou de equipa. Não cria nenhuma thread nova — o trabalho é feito pela
     * própria thread do DealWithClient que já existe.
     */
    public void notifyAnswer(DealWithClient dwc, int answerIdx) {
        // Captura local das referências voláteis para evitar corridas
        ModifiedCountDownLatch latch       = currentLatch;
        Map<String, TeamBarrier> barriers  = currentBarriers;

        if (latch != null) {
            // Ronda individual
            handleIndividualAnswer(dwc, answerIdx, latch);
        } else if (barriers != null) {
            // Ronda de equipa
            handleTeamAnswer(dwc, answerIdx, barriers);
        }
        // Se ainda não há ronda ativa (chegou cedo demais), ignora.
    }

    // ─────────────────────────────────────────────────────────
    // Ronda individual — ModifiedCountDownLatch
    // ─────────────────────────────────────────────────────────

    /**
     * Configura o latch e aguarda que todas as respostas cheguem (ou timeout).
     * As respostas são entregues pelas threads DealWithClient via notifyAnswer().
     */
    private void processIndividualRound(Question q) {
        int total = game.getRegisteredCount();

        // Publica o latch antes de aguardar — as threads DealWithClient
        // já podem estar a tentar entregar respostas.
        currentLatch = new ModifiedCountDownLatch(BONUS_FACTOR, BONUS_COUNT, QUESTION_TIME, total);

        try {
            currentLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Processa a resposta de um jogador numa ronda individual.
     * Executado na thread do DealWithClient correspondente.
     */
    private void handleIndividualAnswer(DealWithClient dwc, int answerIdx,
                                        ModifiedCountDownLatch latch) {
        Question q = game.getCurrentQuestion();
        boolean correct = (answerIdx == q.getCorrect());

        // A action recebe o factor e corre dentro do lock do latch, antes do
        // notifyAll() — quando o GameHandler acorda do await(), a pontuação
        // deste jogador já está registada em Player.totalPoints.
        int factor = latch.countDown(f -> {
            game.recordPlayerAnswer(dwc.getUsername(), correct, q.getPoints(), f);
            System.out.printf("  [Ind] %-12s idx=%d %s pts=%d (x%d)%n",
                    dwc.getUsername(), answerIdx,
                    correct ? "CERTO" : "ERRADO",
                    correct ? q.getPoints() * f : 0, f);
        });

        if (factor < 0) {
            System.out.printf("  [Ind] %-12s resposta tardia ignorada%n", dwc.getUsername());
        }
    }

    // ─────────────────────────────────────────────────────────
    // Ronda de equipa — TeamBarrier
    // ─────────────────────────────────────────────────────────

    /**
     * Configura as barreiras de equipa e aguarda que todas as equipas terminem.
     * As respostas são entregues pelas threads DealWithClient via notifyAnswer().
     */
    private void processTeamRound(Question q) {
        Map<String, Team> teams = game.getTeams();

        Map<String, TeamBarrier>          barriers    = new HashMap<>();
        Map<String, Map<String, Integer>> teamAnswers = new HashMap<>();
        CountDownLatch allTeamsDone = new CountDownLatch(teams.size());

        for (Team team : teams.values()) {
            String teamName = team.getTeamName();
            Map<String, Integer> answers = new HashMap<>();
            teamAnswers.put(teamName, answers);

            TeamBarrier barrier = new TeamBarrier(
                    team.getPlayers().size(), QUESTION_TIME,
                    allAnswered -> {
                        // Tira snapshot sob o mesmo lock usado no put —
                        // garante que a action vê todas as respostas registadas.
                        Map<String, Integer> snapshot;
                        synchronized (answers) {
                            snapshot = new HashMap<>(answers);
                        }

                        int correct = q.getCorrect();
                        boolean allCorrect = !snapshot.isEmpty()
                                && snapshot.values().stream().allMatch(a -> a == correct);
                        int factor = allCorrect ? 2 : 1;

                        for (Player p : team.getPlayers()) {
                            Integer ans = snapshot.get(p.getName());
                            boolean isCorrect = (ans != null && ans == correct);
                            game.recordPlayerAnswer(p.getName(), isCorrect,
                                    q.getPoints(), factor);

                            System.out.printf("  [Eq] %-12s %s pts=%d (x%d, allCorrect=%b)%n",
                                    p.getName(),
                                    isCorrect ? "CERTO" : (ans == null ? "SEM RESP." : "ERRADO"),
                                    isCorrect ? q.getPoints() * factor : 0, factor, allCorrect);
                        }
                        allTeamsDone.countDown();
                    }
            );
            barriers.put(teamName, barrier);
        }

        // Publica os mapas — a partir daqui as threads DealWithClient
        // podem começar a entregar respostas via notifyAnswer().
        currentTeamAnswers = teamAnswers;
        currentBarriers    = barriers;

        try {
            allTeamsDone.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Processa a resposta de um jogador numa ronda de equipa.
     * Executado na thread do DealWithClient correspondente.
     */
    private void handleTeamAnswer(DealWithClient dwc, int answerIdx,
                                  Map<String, TeamBarrier> barriers) {
        String teamName = dwc.getTeamName();
        TeamBarrier barrier = barriers.get(teamName);
        if (barrier == null || barrier.isReleased()) return;

        Map<String, Map<String, Integer>> teamAnswers = currentTeamAnswers;
        if (teamAnswers == null) return;
        Map<String, Integer> answers = teamAnswers.get(teamName);
        if (answers == null) return;

        // Regista a resposta primeiro, fora do lock da barrier.
        // A BarrierAction (que lê o answers) só corre quando arrive()
        // for chamado pelo último jogador — nessa altura este put já está visível
        // porque é o próprio jogador a chamar arrive() a seguir.
        synchronized (answers) {
            answers.put(dwc.getUsername(), answerIdx);
        }

        // arrive() é chamado fora do synchronized(answers) para evitar deadlock
        // com a BarrierAction que também faz synchronized(answers) para o snapshot.
        // A visibilidade é garantida: o put aconteceu antes do arrive() desta thread.
        try {
            barrier.arrive();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ─────────────────────────────────────────────────────────
    // Construção do RoundResult
    // ─────────────────────────────────────────────────────────

    private Message.RoundResult buildRoundResult(int correctOption, boolean isGameEnd) {
        List<Message.PlayerResult> playerResults = ScoreManager.buildPlayerResults(game);
        List<Message.TeamResult>   teamResults   = ScoreManager.buildTeamResults(game);
        String winner = isGameEnd ? ScoreManager.getWinnerTeam(game) : null;
        return new Message.RoundResult(correctOption, playerResults, teamResults, winner, isGameEnd);
    }

    // ─────────────────────────────────────────────────────────
    // Utilitários
    // ─────────────────────────────────────────────────────────

    private void broadcast(Message msg) {
        for (DealWithClient dwc : game.getAllHandlers()) dwc.send(msg);
    }

    private void pause(long ms) {
        try { Thread.sleep(ms); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}