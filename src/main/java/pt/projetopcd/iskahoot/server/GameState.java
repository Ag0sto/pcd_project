package pt.projetopcd.iskahoot.server;

import pt.projetopcd.iskahoot.model.Player;
import pt.projetopcd.iskahoot.model.Question;
import pt.projetopcd.iskahoot.model.Team;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Estado completo de um jogo.
 *
 * Arquitetura de pontuação:
 *   - Cada Player guarda a sua pontuação individual (totalPoints, roundPoints).
 *   - Cada Team não guarda pontos próprios: getTotalPoints() soma os jogadores.
 *   - O GameState centraliza o acesso a Players e Teams.
 */
public class GameState {

    private final String gameId;
    private final int    numTeams;
    private final int    playersPerTeam;
    private final int    numQuestions;

    // username → Player (modelo com pontuação individual)
    private final Map<String, Player> players = new LinkedHashMap<>();

    // teamName → Team (agrega jogadores)
    private final Map<String, Team> teams = new LinkedHashMap<>();

    // username → DealWithClient (canal de comunicação)
    private final Map<String, DealWithClient> handlers = new ConcurrentHashMap<>();

    // Pergunta corrente
    private volatile Question currentQuestion;
    private volatile int      currentQuestionIndex = -1;
    private volatile boolean  isTeamRound          = false;

    private final AnswerManager answerManager = new AnswerManager();
    private final TimerManager  timerManager  = new TimerManager();

    public GameState(String gameId, int numTeams, int playersPerTeam, int numQuestions) {
        this.gameId         = gameId;
        this.numTeams       = numTeams;
        this.playersPerTeam = playersPerTeam;
        this.numQuestions   = numQuestions;
    }

    // ─────────────────────────────────────────────────────────
    // Registo de jogadores
    // ─────────────────────────────────────────────────────────

    public synchronized boolean registerPlayer(String username, String teamName,
                                               DealWithClient handler) {
        if (players.containsKey(username)) return false; // nome duplicado

        // Valida espaço na equipa
        Team team = teams.get(teamName);
        if (team == null) {
            if (teams.size() >= numTeams) return false; // equipas a mais
            team = new Team(teamName);
            teams.put(teamName, team);
        } else if (team.getPlayers().size() >= playersPerTeam) {
            return false; // equipa cheia
        }

        Player p = new Player(players.size() + 1, username, teamName);
        players.put(username, p);
        team.addPlayer(p);
        handlers.put(username, handler);
        return true;
    }

    public synchronized boolean isFull() {
        return players.size() >= numTeams * playersPerTeam;
    }

    public synchronized int getExpectedPlayers() { return numTeams * playersPerTeam; }
    public synchronized int getRegisteredCount()  { return players.size(); }

    // ─────────────────────────────────────────────────────────
    // Pontuação individual
    // ─────────────────────────────────────────────────────────

    /**
     * Regista a resposta de um jogador e calcula os seus pontos individuais.
     * A pontuação da equipa atualiza-se automaticamente porque Team.getTotalPoints()
     * soma os Player.getTotalPoints() em tempo real.
     */
    public void recordPlayerAnswer(String username, boolean correct,
                                   int basePoints, int factor) {
        Player p = players.get(username);
        if (p != null) p.recordAnswer(correct, basePoints, factor);
    }

    /** Reseta os campos de ronda de todos os jogadores (chamado antes de cada pergunta). */
    public synchronized void resetAllRoundStats() {
        for (Player p : players.values()) p.resetRound();
        answerManager.reset();
    }

    // ─────────────────────────────────────────────────────────
    // Acesso a dados
    // ─────────────────────────────────────────────────────────

    public synchronized Player getPlayer(String username) {
        return players.get(username);
    }

    /** Cópia da lista de jogadores (para iteração segura). */
    public synchronized List<Player> getAllPlayers() {
        return new ArrayList<>(players.values());
    }

    /** Cópia do mapa de equipas (para iteração segura). */
    public synchronized Map<String, Team> getTeams() {
        return new LinkedHashMap<>(teams);
    }

    public synchronized Team getTeam(String teamName) {
        return teams.get(teamName);
    }

    public synchronized String getTeamOf(String username) {
        Player p = players.get(username);
        return p != null ? p.getTeam() : null;
    }

    public Collection<DealWithClient> getAllHandlers()        { return handlers.values(); }
    public DealWithClient             getHandler(String u)   { return handlers.get(u); }

    // ─────────────────────────────────────────────────────────
    // Pergunta corrente
    // ─────────────────────────────────────────────────────────

    public synchronized void setCurrentQuestion(Question q, int index, boolean teamRound) {
        this.currentQuestion      = q;
        this.currentQuestionIndex = index;
        this.isTeamRound          = teamRound;
        resetAllRoundStats();
    }

    public Question getCurrentQuestion()      { return currentQuestion; }
    public int      getCurrentQuestionIndex() { return currentQuestionIndex; }
    public boolean  isTeamRound()             { return isTeamRound; }

    // ─────────────────────────────────────────────────────────
    // Sub-gestores
    // ─────────────────────────────────────────────────────────

    public AnswerManager getAnswerManager() { return answerManager; }
    public TimerManager  getTimerManager()  { return timerManager; }

    // ─────────────────────────────────────────────────────────
    // Getters básicos
    // ─────────────────────────────────────────────────────────

    public String getGameId()         { return gameId; }
    public int    getNumTeams()       { return numTeams; }
    public int    getPlayersPerTeam() { return playersPerTeam; }
    public int    getNumQuestions()   { return numQuestions; }
}
