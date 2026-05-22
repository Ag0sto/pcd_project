package pt.projetopcd.iskahoot.model;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * Protocolo de mensagens cliente ↔ servidor.
 *
 * CLIENT → SERVER : REGISTER, ANSWER
 * SERVER → CLIENT : REGISTERED, ERROR, WAITING, QUESTION, ROUND_END, GAME_END
 */
public class Message implements Serializable {

    public enum Type {
        REGISTER, ANSWER,
        REGISTERED, ERROR, WAITING, QUESTION, ROUND_END, GAME_END
    }

    private final Type   type;
    private final Object payload;

    public Message(Type type, Object payload) {
        this.type    = type;
        this.payload = payload;
    }

    public Type   getType()    { return type; }
    public Object getPayload() { return payload; }

    // ═══════════════════════════════════════════════════════
    // Payload: QuestionMsg
    // ═══════════════════════════════════════════════════════

    public static class QuestionMsg implements Serializable {
        public final int          questionNumber;
        public final int          totalQuestions;
        public final String       questionText;
        public final List<String> options;
        public final int          points;
        public final int          timeLimitSeconds;
        public final boolean      isTeamRound;

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

    // ═══════════════════════════════════════════════════════
    // Payload: PlayerResult — estatísticas individuais de um jogador
    // ═══════════════════════════════════════════════════════

    /**
     * Snapshot das estatísticas de um jogador para um dado momento.
     * Usado dentro de RoundResult.
     */
    public static class PlayerResult implements Serializable {
        public final String  username;
        public final String  teamName;
        public final int     totalPoints;   // pontuação acumulada
        public final int     roundPoints;   // pontos ganhos nesta ronda
        public final int     bonusApplied;  // 1 = normal, 2 = bónus velocidade
        public final boolean answeredCorrectly;
        public final boolean hasAnswered;

        public PlayerResult(String username, String teamName,
                            int totalPoints, int roundPoints,
                            int bonusApplied, boolean answeredCorrectly,
                            boolean hasAnswered) {
            this.username          = username;
            this.teamName          = teamName;
            this.totalPoints       = totalPoints;
            this.roundPoints       = roundPoints;
            this.bonusApplied      = bonusApplied;
            this.answeredCorrectly = answeredCorrectly;
            this.hasAnswered       = hasAnswered;
        }
    }

    // ═══════════════════════════════════════════════════════
    // Payload: TeamResult — agregação da equipa
    // ═══════════════════════════════════════════════════════

    public static class TeamResult implements Serializable {
        public final String teamName;
        public final int    totalPoints;  // soma dos totalPoints dos jogadores
        public final int    roundPoints;  // soma dos roundPoints dos jogadores nesta ronda

        public TeamResult(String teamName, int totalPoints, int roundPoints) {
            this.teamName    = teamName;
            this.totalPoints = totalPoints;
            this.roundPoints = roundPoints;
        }
    }

    // ═══════════════════════════════════════════════════════
    // Payload: RoundResult — enviado em ROUND_END e GAME_END
    // ═══════════════════════════════════════════════════════

    /**
     * Contém:
     *  - correctOption   : índice da resposta certa
     *  - playerResults   : lista com o estado individual de cada jogador
     *  - teamResults     : lista com a pontuação agregada de cada equipa
     *  - winnerTeam      : equipa vencedora (só relevante no GAME_END)
     *  - isGameEnd       : distingue ROUND_END de GAME_END
     */
    public static class RoundResult implements Serializable {
        public final int               correctOption;
        public final List<PlayerResult> playerResults;
        public final List<TeamResult>   teamResults;
        public final String            winnerTeam;
        public final boolean           isGameEnd;

        public RoundResult(int correctOption,
                           List<PlayerResult> playerResults,
                           List<TeamResult>   teamResults,
                           String winnerTeam,
                           boolean isGameEnd) {
            this.correctOption  = correctOption;
            this.playerResults  = playerResults;
            this.teamResults    = teamResults;
            this.winnerTeam     = winnerTeam;
            this.isGameEnd      = isGameEnd;
        }
    }

    // ═══════════════════════════════════════════════════════
    // Payload: RegisterPayload
    // ═══════════════════════════════════════════════════════

    public static class RegisterPayload implements Serializable {
        public final String username;
        public final String teamName;
        public final String gameId;

        public RegisterPayload(String username, String teamName, String gameId) {
            this.username = username;
            this.teamName = teamName;
            this.gameId   = gameId;
        }
    }
}
