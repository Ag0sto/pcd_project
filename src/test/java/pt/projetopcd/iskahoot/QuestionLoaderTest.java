package pt.projetopcd.iskahoot;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.Test;

import pt.projetopcd.iskahoot.model.Question;
import pt.projetopcd.iskahoot.model.QuestionLoader;

public class QuestionLoaderTest {

    @Test
    public void testLoadQuestions() {

        List<Question> questions = QuestionLoader.loadQuestions();

        assertNotNull(questions, "Lista é null");
        assertFalse(questions.isEmpty(), "Lista está vazia");

        System.out.println("\n===== PERGUNTAS CARREGADAS =====");
        System.out.println("Total: " + questions.size());

        int i = 1;
        for (Question q : questions) {
            System.out.println("\n--- Pergunta " + i + " ---");
            System.out.println("Texto: " + q.getQuestion());
            System.out.println("Pontos: " + q.getPoints());
            System.out.println("Resposta correta index: " + q.getCorrect());
            System.out.println("Opções:");

            List<String> options = q.getOptions();
            assertNotNull(options, "Opções são null");

            for (int j = 0; j < options.size(); j++) {
                System.out.println("  " + j + " -> " + options.get(j));
            }

            i++;
        }

        System.out.println("\n===== FIM =====\n");
    }
}