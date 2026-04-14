package pt.projetopcd.iskahoot.model;

import java.util.List;

public class QuestionFile {

    private String name;
    private List<Question> questions;

    public QuestionFile(){}

    public String getName(){
        return name;
    }

    public List<Question> getQuestions() {
        return questions;
    }

}
