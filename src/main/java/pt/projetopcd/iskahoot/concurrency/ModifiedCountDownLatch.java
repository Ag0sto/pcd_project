package pt.projetopcd.iskahoot.concurrency;

/**
 * CountDownLatch modificado para perguntas individuais.
 *
 * - bonusCount : número de jogadores que recebem bónus (ex: 2) - bonusFactor :
 * multiplicador aplicado aos primeiros (ex: 2 = dobro) - waitPeriod : tempo
 * máximo de espera em segundos - count : número total de jogadores (= número de
 * countdown() esperados)
 *
 * countDown() devolve o fator a aplicar à cotação do jogador. await() bloqueia
 * até count==0 ou tempo expirar.
 */
public class ModifiedCountDownLatch {

    private final int bonusFactor;
    private final int bonusCount;
    private final int waitPeriod;

    private int count;          // jogadores que ainda não responderam
    private int answered;       // jogadores que já responderam (para bónus)
    private boolean timedOut;
    private boolean done;

    private Thread timerThread;

    public ModifiedCountDownLatch(int bonusFactor, int bonusCount, int waitPeriod, int count) {
        this.bonusFactor = bonusFactor;
        this.bonusCount = bonusCount;
        this.waitPeriod = waitPeriod;
        this.count = count;
        this.answered = 0;
        this.timedOut = false;
        this.done = false;

        startTimer();
    }

    private void startTimer() {
        timerThread = new Thread(() -> {
            try {
                Thread.sleep(waitPeriod * 1000L);
                synchronized (ModifiedCountDownLatch.this) {
                    if (!done) {
                        timedOut = true;
                        done = true;
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
     * @return fator multiplicativo a aplicar à pontuação: bonusFactor se o
     * jogador está nos primeiros bonusCount, 1 caso contrário.
     */
    public synchronized int countDown() {
        if (done) {
            return 1; // ronda já fechada
        }
        answered++;
        int factor = (answered <= bonusCount) ? bonusFactor : 1;

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

    /**
     * Bloqueia até todos responderem ou o tempo expirar.
     */
    public synchronized void await() throws InterruptedException {
        while (!done) {
            wait();
        }
    }

    public synchronized boolean isTimedOut() {
        return timedOut;
    }

    public synchronized boolean isDone() {
        return done;
    }
}
