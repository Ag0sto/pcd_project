package pt.projetopcd.iskahoot.server;

import java.util.HashMap;
import java.util.Map;

import pt.projetopcd.iskahoot.model.Player;
import pt.projetopcd.iskahoot.model.Question;
import pt.projetopcd.iskahoot.model.Team;

public class GameState {

    private final String gameId;

    private Question question;

    private final Map<String, Player> players = new HashMap<>();
    private final Map<String, Team> teams = new HashMap<>();

    private final AnswerManager answerManager;
    private final ScoreManager scoreManager;
    private final TimerManager timerManager;

    public GameState(String gameId) {
        this.gameId = gameId;
        this.answerManager = new AnswerManager();
        this.scoreManager = new ScoreManager();
        this.timerManager = new TimerManager();
    }
/*
    public syncronized void setCurrentQuestion(Question q) {
        this.question = q;
        this.answerManager.reset();
        this.timerManager.startTimer(question.getTimeLimit());
    }
*/
    public Question getCurrentQuestion() {
        return question;
    }

    public AnswerManager getAnswerManager() {
        return answerManager;
    }

    public ScoreManager getScoreManager() {
        return scoreManager;
    }

    public TimerManager getTimerManager() {
        return timerManager;
    }

    public String getGameId() {
        return gameId;
    }

    public Map<String, Player> getPlayers() {
        return players;
    }

    public Map<String, Team> getTeams() {
        return teams;
    }
}
