package pt.projetopcd.iskahoot.model;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * Protocolo de mensagens entre cliente e servidor.
 *
 * CLIENT -> SERVER:
 *   REGISTER  : payload = Player
 *   ANSWER    : payload = Integer (índice da opção escolhida)
 *
 * SERVER -> CLIENT:
 *   REGISTERED    : payload = Player (com id atribuído)
 *   ERROR         : payload = String (motivo)
 *   QUESTION      : payload = QuestionMsg
 *   ROUND_END     : payload = RoundResult
 *   GAME_END      : payload = RoundResult (pontuações finais)
 *   WAITING       : payload = String (mensagem de espera)
 */
public class Message implements Serializable {

    public enum Type {
        // cliente -> servidor
        REGISTER, ANSWER,
        // servidor -> cliente
        REGISTERED, ERROR, QUESTION, ROUND_END, GAME_END, WAITING
    }

    private final Type type;
    private final Object payload;

    public Message(Type type, Object payload) {
        this.type = type;
        this.payload = payload;
    }

    public Type getType()    { return type; }
    public Object getPayload() { return payload; }

    // -------------------------------------------------------
    // Subclasses de payload (todas Serializable)
    // -------------------------------------------------------

    /** Enviada pelo servidor quando envia uma pergunta. */
    public static class QuestionMsg implements Serializable {
        public final int questionNumber;   // 1-based
        public final int totalQuestions;
        public final String questionText;
        public final List<String> options;
        public final int points;
        public final int timeLimitSeconds;
        public final boolean isTeamRound;  // true = ronda de equipa

        public QuestionMsg(int questionNumber, int totalQuestions,
                           String questionText, List<String> options,
                           int points, int timeLimitSeconds, boolean isTeamRound) {
            this.questionNumber   = questionNumber;
            this.totalQuestions   = totalQuestions;
            this.questionText     = questionText;
            this.options          = options;
            this.points           = points;
            this.timeLimitSeconds = timeLimitSeconds;
            this.isTeamRound      = isTeamRound;
        }
    }

    /** Enviada pelo servidor no fim de cada ronda e no fim do jogo. */
    public static class RoundResult implements Serializable {
        public final int correctOption;          // índice da resposta certa
        public final Map<String, Integer> teamScores;   // equipa -> pontuação total
        public final Map<String, Integer> roundPoints;  // equipa -> pontos nesta ronda
        public final String winnerTeam;          // equipa com mais pontos (fim do jogo)
        public final boolean isGameEnd;

        public RoundResult(int correctOption,
                           Map<String, Integer> teamScores,
                           Map<String, Integer> roundPoints,
                           String winnerTeam,
                           boolean isGameEnd) {
            this.correctOption = correctOption;
            this.teamScores    = teamScores;
            this.roundPoints   = roundPoints;
            this.winnerTeam    = winnerTeam;
            this.isGameEnd     = isGameEnd;
        }
    }
}
