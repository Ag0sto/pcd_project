package pt.projetopcd.iskahoot.model;

import java.util.List;

public class Question {

    private String question;
    private int correct;
    private int points;
    private List<String> options;

    public Question() {
    }

    public String getQuestion() {
        return question;
    }

    public int getPoints() {
        return points;
    }

    public int getCorrect() {
        return correct;
    }

    public List<String> getOptions() {
        return options;
    }

    @Override
    public String toString() {
        return "Question: " + question
                + "\nPoints: " + points
                + "\nCorrect: " + correct
                + "\nOptions: " + options;
    }
}
