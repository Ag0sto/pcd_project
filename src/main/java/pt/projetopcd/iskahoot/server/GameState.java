package pt.projetopcd.iskahoot.server;

import pt.projetopcd.iskahoot.model.Player;
import pt.projetopcd.iskahoot.model.Question;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Estado completo de um jogo.
 *
 * Mantém: pergunta atual, jogadores, equipas, respostas, pontuações.
 * Componentes separados para evitar bloqueios desnecessários:
 *   - AnswerManager  : registo das respostas da ronda atual
 *   - ScoreManager   : pontuações acumuladas por equipa
 *   - TimerManager   : cronómetro da ronda
 */
public class GameState {

    private final String gameId;
    private final int numTeams;
    private final int playersPerTeam;
    private final int numQuestions;

    // Jogadores registados: username -> DealWithClient (handler)
    private final Map<String, DealWithClient> handlers = new ConcurrentHashMap<>();

    // Equipas: teamName -> lista de usernames
    private final Map<String, List<String>> teams = new LinkedHashMap<>();

    // Pergunta corrente
    private volatile Question currentQuestion;
    private volatile int currentQuestionIndex = -1;
    private volatile boolean isTeamRound = false;

    // Sub-gestores
    private final AnswerManager  answerManager  = new AnswerManager();
    private final ScoreManager   scoreManager   = new ScoreManager();
    private final TimerManager   timerManager   = new TimerManager();

    // Pontuações acumuladas por equipa
    private final Map<String, Integer> totalTeamScores = new ConcurrentHashMap<>();

    public GameState(String gameId, int numTeams, int playersPerTeam, int numQuestions) {
        this.gameId          = gameId;
        this.numTeams        = numTeams;
        this.playersPerTeam  = playersPerTeam;
        this.numQuestions    = numQuestions;
    }

    // -------------------------------------------------------
    // Registo de jogadores
    // -------------------------------------------------------

    public synchronized boolean registerPlayer(String username, String teamName,
                                               DealWithClient handler) {
        if (handlers.containsKey(username)) return false; // nome duplicado

        List<String> teamMembers = teams.computeIfAbsent(teamName, k -> new ArrayList<>());
        if (teamMembers.size() >= playersPerTeam) return false; // equipa cheia

        if (!teams.containsKey(teamName) && teams.size() >= numTeams) return false; // equipas a mais

        handlers.put(username, handler);
        teamMembers.add(username);
        totalTeamScores.putIfAbsent(teamName, 0);
        return true;
    }

    public synchronized boolean isFull() {
        int total = 0;
        for (List<String> m : teams.values()) total += m.size();
        return total >= numTeams * playersPerTeam;
    }

    public synchronized int getExpectedPlayers() {
        return numTeams * playersPerTeam;
    }

    public synchronized int getRegisteredCount() {
        return handlers.size();
    }

    // -------------------------------------------------------
    // Pergunta corrente
    // -------------------------------------------------------

    public synchronized void setCurrentQuestion(Question q, int index, boolean teamRound) {
        this.currentQuestion      = q;
        this.currentQuestionIndex = index;
        this.isTeamRound          = teamRound;
        this.answerManager.reset();
    }

    public Question getCurrentQuestion()    { return currentQuestion; }
    public int      getCurrentQuestionIndex() { return currentQuestionIndex; }
    public boolean  isTeamRound()           { return isTeamRound; }

    // -------------------------------------------------------
    // Acesso a handlers / equipas
    // -------------------------------------------------------

    public Collection<DealWithClient> getAllHandlers() { return handlers.values(); }

    public synchronized List<String> getTeamMembers(String teamName) {
        return Collections.unmodifiableList(
                teams.getOrDefault(teamName, Collections.emptyList()));
    }

    public synchronized Map<String, List<String>> getTeams() {
        return Collections.unmodifiableMap(teams);
    }

    /** Devolve o nome da equipa de um jogador. */
    public synchronized String getTeamOf(String username) {
        for (Map.Entry<String, List<String>> e : teams.entrySet()) {
            if (e.getValue().contains(username)) return e.getKey();
        }
        return null;
    }

    // -------------------------------------------------------
    // Pontuações
    // -------------------------------------------------------

    public void addTeamScore(String team, int points) {
        totalTeamScores.merge(team, points, Integer::sum);
    }

    public Map<String, Integer> getTotalTeamScores() {
        return Collections.unmodifiableMap(totalTeamScores);
    }

    // -------------------------------------------------------
    // Sub-gestores
    // -------------------------------------------------------

    public AnswerManager  getAnswerManager()  { return answerManager; }
    public ScoreManager   getScoreManager()   { return scoreManager; }
    public TimerManager   getTimerManager()   { return timerManager; }

    // -------------------------------------------------------
    // Getters básicos
    // -------------------------------------------------------

    public String getGameId()       { return gameId; }
    public int    getNumTeams()     { return numTeams; }
    public int    getPlayersPerTeam() { return playersPerTeam; }
    public int    getNumQuestions() { return numQuestions; }
}
