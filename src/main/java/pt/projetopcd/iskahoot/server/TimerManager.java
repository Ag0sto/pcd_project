package pt.projetopcd.iskahoot.server;

/**
 * Utilitário simples de cronómetro (mantido para compatibilidade). A lógica de
 * tempo das rondas é gerida pelos latch/barrier em GameHandler.
 */
public class TimerManager {

    private long endTime;

    public void startTimer(int seconds) {
        endTime = System.currentTimeMillis() + seconds * 1000L;
    }

    public boolean isTimeUp() {
        return System.currentTimeMillis() >= endTime;
    }

    public long getTimeRemaining() {
        return Math.max(endTime - System.currentTimeMillis(), 0);
    }
}
