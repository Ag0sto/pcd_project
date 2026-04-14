package pt.projetopcd.iskahoot.model;

import pt.projetopcd.iskahoot.server.*;

import java.util.*;

public class GameState {

    private final String gameId;

    private Question question;

    private Map<String, Player> players = new HashMap<String, Player>();
    private Map<String, Team> teams = new HashMap<String, Team>();

    private AnswerManager answerManager;
    private ScoreManager scoreManager;
    private TimerManager timerManager;

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
}
