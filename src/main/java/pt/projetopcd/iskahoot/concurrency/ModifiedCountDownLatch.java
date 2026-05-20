package pt.projetopcd.iskahoot.concurrency;

import static jdk.jfr.internal.consumer.EventLog.stop;

public class ModifiedCountDownLatch {

    private static final int MAX_QUESTION_TIME = 10;

    private final TeamBarrier barrier;
    private Thread timerThread;

    public ModifiedCountDownLatch(TeamBarrier barrier) {
        this.barrier = barrier;
    }

    public void start(){
        //Cancelar timer anterior, se existir
        stop();

        timerThread = new Thread(() -> {
            try {
                for (int i = MAX_QUESTION_TIME; i > 0; i--) {
                    System.out.println("  [Timer] " + i + "s");
                    Thread.sleep(1000);
                }
                System.out.println("  [Timer] Tempo esgotado!");
                barrier.releaseAll();
            } catch (InterruptedException e) {
                System.out.println("  [Timer] Cancelado — todos responderam.");
            }
        });

        timerThread.setDaemon(true);
        timerThread.start();
    }

    public void stop() {
        if (timerThread != null && timerThread.isAlive()) {
            timerThread.interrupt();
        }
    }
}
