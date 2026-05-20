package pt.projetopcd.iskahoot.model;

import com.google.gson.Gson;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.List;

public class QuestionLoader {

    /**
     * Carrega as perguntas do ficheiro quizzes.json.
     * Tenta primeiro pelo classpath (para jar/maven), depois pelo caminho direto.
     */
    public static List<Question> loadQuestions() {
        Gson gson = new Gson();

        // 1. Tenta pelo classpath (funciona tanto em testes como em jar)
        InputStream is = QuestionLoader.class.getClassLoader()
                .getResourceAsStream("quizzes.json");

        if (is != null) {
            try (Reader reader = new InputStreamReader(is)) {
                QuestionFile q = gson.fromJson(reader, QuestionFile.class);
                return q.getQuestions();
            } catch (Exception e) {
                throw new RuntimeException("Erro ao ler quizzes.json do classpath", e);
            }
        }

        // 2. Fallback: caminho direto (compatibilidade com testes existentes)
        try (Reader reader = new java.io.FileReader("src/main/resources/quizzes.json")) {
            QuestionFile q = gson.fromJson(reader, QuestionFile.class);
            return q.getQuestions();
        } catch (Exception e) {
            throw new RuntimeException("Não foi possível carregar as perguntas.", e);
        }
    }
}
