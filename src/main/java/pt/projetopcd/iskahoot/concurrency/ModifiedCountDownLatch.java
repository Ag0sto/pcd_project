package pt.projetopcd.iskahoot.concurrency;

import java.util.function.IntConsumer;

/**
 * CountDownLatch modificado para perguntas individuais.
 *
 * countDown(IntConsumer action) executa a action dentro do lock,
 * passando-lhe o factor de bónus calculado, e só depois decrementa
 * o contador — garantindo que quando await() desbloquear, toda a
 * lógica de pontuação (recordPlayerAnswer) já terminou.
 *
 * Devolve:
 *   bonusFactor ou 1  — se a ronda estava aberta (action foi executada)
 *  -1                 — se a ronda já estava fechada (action NÃO é executada)
 */
public class ModifiedCountDownLatch {

    private final int bonusFactor;
    private final int bonusCount;
    private final int waitPeriod;

    private int     count;
    private int     answered;
    private boolean timedOut;
    private boolean done;

    private Thread timerThread;

    public ModifiedCountDownLatch(int bonusFactor, int bonusCount, int waitPeriod, int count) {
        this.bonusFactor = bonusFactor;
        this.bonusCount  = bonusCount;
        this.waitPeriod  = waitPeriod;
        this.count       = count;
        this.answered    = 0;
        this.timedOut    = false;
        this.done        = false;

        startTimer();
    }

    private void startTimer() {
        timerThread = new Thread(() -> {
            try {
                Thread.sleep(waitPeriod * 1000L);
                synchronized (ModifiedCountDownLatch.this) {
                    if (!done) {
                        timedOut = true;
                        done     = true;
                        System.out.println("  [Latch] Tempo esgotado!");
                        ModifiedCountDownLatch.this.notifyAll();
                    }
                }
            } catch (InterruptedException e) {
                // latch terminou antes do tempo — normal
            }
        });
        timerThread.setDaemon(true);
        timerThread.start();
    }

    /**
     * Regista a resposta de um jogador.
     *
     * A {@code action} recebe o factor de bónus e é executada dentro do
     * lock do latch, antes de decrementar o contador interno. Assim, quando
     * o GameHandler acorda do await(), toda a pontuação já está registada.
     *
     * @param action  chamada com o factor calculado (ex: recordPlayerAnswer)
     * @return factor de bónus aplicado (>= 1), ou -1 se a ronda já fechou
     */
    public synchronized int countDown(IntConsumer action) {
        if (done) {
            return -1; // ronda já fechada — resposta tardia, ignorar
        }

        answered++;
        int factor = (answered <= bonusCount) ? bonusFactor : 1;

        // Executa o registo de pontuação dentro do lock, com o factor correto.
        // O GameHandler só é acordado (notifyAll) depois disto terminar.
        if (action != null) action.accept(factor);

        count--;
        System.out.println("  [Latch] countDown: responderam=" + answered
                + " faltam=" + count + " fator=" + factor);

        if (count <= 0) {
            done = true;
            timerThread.interrupt();
            notifyAll();
        }
        return factor;
    }

    public synchronized void await() throws InterruptedException {
        while (!done) {
            wait();
        }
    }

    public synchronized boolean isTimedOut() { return timedOut; }
    public synchronized boolean isDone()     { return done; }
}