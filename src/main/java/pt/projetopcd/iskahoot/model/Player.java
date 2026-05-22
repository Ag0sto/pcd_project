package pt.projetopcd.iskahoot.model;

import java.io.Serializable;

/**
 * Representa um jogador.
 *
 * A pontuação da equipa é sempre calculada como a soma das
 * pontuações individuais de todos os seus jogadores — nunca
 * é guardada de forma independente na equipa.
 *
 * Campos de ronda (roundPoints, bonusApplied, answeredCorrectly)
 * são transitórios: são resetados no início de cada pergunta
 * e usados pelo servidor/GUI para mostrar o placar da ronda.
 */
public class Player implements Serializable {

    private final int    id;
    private final String name;
    private final String teamName;

    /** Pontuação acumulada ao longo de todo o jogo. */
    private int totalPoints;

    /** Pontos ganhos apenas na ronda atual (resetado a cada pergunta). */
    private int roundPoints;

    /** Fator de bónus aplicado na ronda atual (1 = normal, 2 = dobro). */
    private int bonusApplied;

    /** Indica se o jogador respondeu corretamente na ronda atual. */
    private boolean answeredCorrectly;

    /** Indica se o jogador respondeu (independentemente de certo/errado). */
    private boolean hasAnswered;

    public Player(int id, String name, String teamName) {
        this.id       = id;
        this.name     = name;
        this.teamName = teamName;
        this.totalPoints       = 0;
        this.roundPoints       = 0;
        this.bonusApplied      = 1;
        this.answeredCorrectly = false;
        this.hasAnswered       = false;
    }

    // -------------------------------------------------------
    // Lógica de pontuação
    // -------------------------------------------------------

    /**
     * Regista a resposta do jogador e calcula os seus pontos individuais.
     *
     * @param correct  true se a resposta é correta
     * @param basePoints cotação base da pergunta
     * @param factor   fator de bónus (1 ou 2)
     */
    public synchronized void recordAnswer(boolean correct, int basePoints, int factor) {
        this.hasAnswered       = true;
        this.answeredCorrectly = correct;
        this.bonusApplied      = factor;

        if (correct) {
            this.roundPoints  = basePoints * factor;
            this.totalPoints += this.roundPoints;
        } else {
            this.roundPoints = 0;
        }
    }

    /** Reseta os campos de ronda (chamado no início de cada pergunta). */
    public synchronized void resetRound() {
        this.roundPoints       = 0;
        this.bonusApplied      = 1;
        this.answeredCorrectly = false;
        this.hasAnswered       = false;
    }

    // -------------------------------------------------------
    // Getters
    // -------------------------------------------------------

    public int     getId()               { return id; }
    public String  getName()             { return name; }
    public String  getTeam()             { return teamName; }

    public synchronized int     getTotalPoints()       { return totalPoints; }
    public synchronized int     getRoundPoints()       { return roundPoints; }
    public synchronized int     getBonusApplied()      { return bonusApplied; }
    public synchronized boolean isAnsweredCorrectly()  { return answeredCorrectly; }
    public synchronized boolean isHasAnswered()        { return hasAnswered; }

    @Override
    public String toString() {
        return "Player{" + name + ", team=" + teamName
                + ", total=" + totalPoints + ", round=" + roundPoints + "}";
    }
}
