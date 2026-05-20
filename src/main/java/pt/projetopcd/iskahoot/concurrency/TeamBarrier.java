package pt.projetopcd.iskahoot.concurrency;

public class TeamBarrier {

    private final ModifiedCountDownLatch timer;

    private int pending;      // jogadores que ainda não responderam
    private boolean open;     // true = ronda ativa, jogadores podem responder
    private boolean released; // true = barreira libertada, await() pode retornar

    public TeamBarrier() {
        this.timer = new ModifiedCountDownLatch(this);
        this.pending = 0;
        this.open = false;
        this.released = true;
    }

    public synchronized void startRound(int numPlayers) {
        this.pending = numPlayers;
        this.open = true;
        this.released = false;

        System.out.println("Rounda aberta " + numPlayers);
        timer.start();
        notifyAll();
    }

    // Chamado por cada DealWithClient quando o jogador submete resposta
    // Devolve true se a resposta foi aceite (dentro do tempo), false se não
    // ------------------------------------------------------------------
    public synchronized boolean arrive() throws InterruptedException {
        if (!open) {
            // Ronda já fechada — resposta rejeitada
            return false;
        }

        pending--;
        System.out.println("  [Barrier] Jogador respondeu. Em falta: " + pending);

        if (pending <= 0) {
            releaseAll();
        }

        // Jogador fica bloqueado até a ronda terminar
        while (!released) {
            wait();
        }

        return true;
    }

    public synchronized void await() throws InterruptedException {
        while (!released) {
            wait();
        }
    }

    public synchronized void releaseAll() {
        if(released) return;
        released = true;
        open = false;
        timer.stop();// para o timer, se estiver a correr
        System.out.println("Barreira liberada");
        notifyAll();
    }

    public boolean isOpen() {
        return open;
    }
}
