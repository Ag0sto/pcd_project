package pt.projetopcd.iskahoot.server;

import java.io.FileReader;
import java.io.Reader;
import java.util.List;

import com.google.gson.Gson;

import pt.projetopcd.iskahoot.model.Question;
import pt.projetopcd.iskahoot.model.QuestionFile;

public class QuestionLoader {

    public static List<Question> loadQuestions() {
        Gson gson = new Gson();
        String file = "src\\main\\resources\\quizzes.json";

        try (Reader reader = new FileReader(file)) {

            QuestionFile q = gson.fromJson(reader, QuestionFile.class);
            return q.getQuestions();

        } catch (Exception e) {
            throw new RuntimeException("Failed to load questions", e);
        }
    }
}