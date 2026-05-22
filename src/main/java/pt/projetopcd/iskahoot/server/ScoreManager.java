package pt.projetopcd.iskahoot.server;

import pt.projetopcd.iskahoot.model.Message;
import pt.projetopcd.iskahoot.model.Player;
import pt.projetopcd.iskahoot.model.Team;

import java.util.*;

/**
 * Constrói os snapshots de pontuação (PlayerResult / TeamResult)
 * a partir do estado atual do GameState.
 *
 * Não guarda pontos — apenas lê os valores já registados nos Players.
 */
public class ScoreManager {

    /**
     * Constrói a lista de PlayerResult a partir de todos os jogadores do jogo.
     * Ordenada por totalPoints decrescente.
     */
    public static List<Message.PlayerResult> buildPlayerResults(GameState game) {
        List<Player> all = game.getAllPlayers();
        all.sort(Comparator.comparingInt(Player::getTotalPoints).reversed());

        List<Message.PlayerResult> out = new ArrayList<>();
        for (Player p : all) {
            out.add(new Message.PlayerResult(
                    p.getName(),
                    p.getTeam(),
                    p.getTotalPoints(),
                    p.getRoundPoints(),
                    p.getBonusApplied(),
                    p.isAnsweredCorrectly(),
                    p.isHasAnswered()
            ));
        }
        return out;
    }

    /**
     * Constrói a lista de TeamResult.
     * totalPoints = soma dos totalPoints dos jogadores da equipa.
     * roundPoints = soma dos roundPoints dos jogadores da equipa nesta ronda.
     * Ordenada por totalPoints decrescente.
     */
    public static List<Message.TeamResult> buildTeamResults(GameState game) {
        Map<String, Team> teams = game.getTeams();
        List<Message.TeamResult> out = new ArrayList<>();

        for (Team t : teams.values()) {
            out.add(new Message.TeamResult(
                    t.getTeamName(),
                    t.getTotalPoints(),
                    t.getRoundPoints()
            ));
        }
        out.sort(Comparator.comparingInt((Message.TeamResult tr) -> tr.totalPoints).reversed());
        return out;
    }

    /**
     * Devolve o nome da equipa com mais pontos totais.
     */
    public static String getWinnerTeam(GameState game) {
        return game.getTeams().values().stream()
                .max(Comparator.comparingInt(Team::getTotalPoints))
                .map(Team::getTeamName)
                .orElse("-");
    }
}
