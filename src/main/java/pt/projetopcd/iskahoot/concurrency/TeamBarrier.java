package pt.projetopcd.iskahoot.concurrency;

/**
 * Barreira para perguntas de equipa.
 *
 * Uma barreira por equipa por ronda.
 * Cada DealWithClient chama arrive() quando o seu jogador responde.
 * O GameHandler chama releaseAll() quando o tempo expirar.
 *
 * A barrierAction (cálculo de pontuação) é executada pelo último
 * jogador a chegar (ou pelo timer), antes de libertar os restantes.
 *
 * Implementada com variáveis condicionais (wait/notifyAll).
 */
public class TeamBarrier {

    public interface BarrierAction {
        /** Executada com o lock adquirido quando todos chegaram ou tempo expirou. */
        void run(boolean allAnswered);
    }

    private final int parties;          // jogadores na equipa
    private int arrived;                // quantos já chamaram arrive()
    private boolean released;           // true quando a barreira foi libertada
    private boolean allAnswered;        // true se todos responderam (vs timeout)

    private final BarrierAction action;
    private Thread timerThread;

    public TeamBarrier(int parties, int waitPeriod, BarrierAction action) {
        this.parties     = parties;
        this.arrived     = 0;
        this.released    = false;
        this.allAnswered = false;
        this.action      = action;

        startTimer(waitPeriod);
    }

    private void startTimer(int seconds) {
        timerThread = new Thread(() -> {
            try {
                Thread.sleep(seconds * 1000L);
                releaseAll(false); // tempo esgotado
            } catch (InterruptedException e) {
                // todos responderam antes — normal
            }
        });
        timerThread.setDaemon(true);
        timerThread.start();
    }

    /**
     * Chamado por cada DealWithClient quando o jogador submete resposta.
     * Bloqueia até a barreira ser libertada.
     * @return true se a resposta chegou a tempo (barreira aberta)
     */
    public synchronized boolean arrive() throws InterruptedException {
        if (released) return false; // já fechada

        arrived++;
        System.out.println("  [Barrier] Chegou " + arrived + "/" + parties);

        if (arrived >= parties) {
            releaseAll(true);
        }

        while (!released) {
            wait();
        }
        return true;
    }

    /**
     * Liberta a barreira. Se ainda não libertada, executa a barrierAction.
     */
    public synchronized void releaseAll(boolean allAnswered) {
        if (released) return;
        this.allAnswered = allAnswered;
        this.released    = true;
        timerThread.interrupt();

        System.out.println("  [Barrier] Libertada. Todos responderam: " + allAnswered);

        if (action != null) {
            action.run(allAnswered);
        }
        notifyAll();
    }

    public synchronized boolean isAllAnswered() { return allAnswered; }
    public synchronized boolean isReleased()    { return released; }
}
