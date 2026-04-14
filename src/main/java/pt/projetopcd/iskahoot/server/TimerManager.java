package pt.projetopcd.iskahoot.server;

public class TimerManager {

    private long endTime;

    public void startTimer(int seconds) {
        endTime = System.currentTimeMillis() + seconds * 1000;
    }

    public boolean isTimeUp() {
        return System.currentTimeMillis() >= endTime;
    }

    public long getTimeRemaining() {
        return Math.max(endTime - System.currentTimeMillis(), 0);
    }
}
