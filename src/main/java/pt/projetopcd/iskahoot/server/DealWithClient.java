package pt.projetopcd.iskahoot.server;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.SocketException;

import pt.projetopcd.iskahoot.concurrency.ModifiedCountDownLatch;
import pt.projetopcd.iskahoot.concurrency.TeamBarrier;
import pt.projetopcd.iskahoot.model.Message;
import pt.projetopcd.iskahoot.model.Player;

/**
 * Thread dedicada a cada jogador ligado ao servidor.
 *
 * Responsabilidades:
 * - Receber registo inicial (username, equipa, código de jogo)
 * - Aguardar início do jogo (barreira de arranque)
 * - Receber respostas e processá-las diretamente (latch ou barrier)
 * - Enviar mensagens do servidor ao cliente (perguntas, resultados, etc.)
 */
public class DealWithClient extends Thread {

    private final Socket socket;
    private final Server server;
    private ObjectInputStream in;
    private ObjectOutputStream out;

    // Preenchido após registo bem-sucedido
    private String username;
    private String teamName;
    private String gameId;
    private GameState game;

    private volatile boolean running = true;

    // -------------------------------------------------------
    // Contexto da ronda atual — atribuído pelo GameHandler
    // antes de enviar a pergunta, limpo após a ronda.
    // -------------------------------------------------------

    // Ronda individual: latch a decrementar quando o jogador responder
    private volatile ModifiedCountDownLatch currentLatch = null;

    // Ronda de equipa: barrier + mapa de respostas da equipa a preencher
    private volatile TeamBarrier        currentBarrier     = null;
    private volatile java.util.Map<String, Integer> currentTeamAnswers = null;

    public DealWithClient(Socket socket, Server server) {
        this.socket = socket;
        this.server = server;
        setDaemon(true);
    }

    // -------------------------------------------------------
    // Envio de mensagens ao cliente
    // -------------------------------------------------------
    public synchronized void send(Message msg) {
        try {
            out.writeObject(msg);
            out.flush();
            out.reset();
        } catch (IOException e) {
            System.err.println("[DWC:" + username + "] Erro ao enviar: " + e.getMessage());
            running = false;
        }
    }

    // -------------------------------------------------------
    // API chamada pelo GameHandler para configurar a ronda
    // -------------------------------------------------------

    /**
     * Prepara o DealWithClient para uma ronda individual.
     * Deve ser chamado antes de enviar a pergunta ao cliente.
     */
    public void prepareIndividualRound(ModifiedCountDownLatch latch) {
        this.currentLatch       = latch;
        this.currentBarrier     = null;
        this.currentTeamAnswers = null;
    }

    /**
     * Prepara o DealWithClient para uma ronda de equipa.
     * Deve ser chamado antes de enviar a pergunta ao cliente.
     */
    public void prepareTeamRound(TeamBarrier barrier, java.util.Map<String, Integer> teamAnswers) {
        this.currentBarrier     = barrier;
        this.currentTeamAnswers = teamAnswers;
        this.currentLatch       = null;
    }

    /** Limpa o contexto da ronda (entre perguntas). */
    public void clearRound() {
        this.currentLatch       = null;
        this.currentBarrier     = null;
        this.currentTeamAnswers = null;
    }

    // -------------------------------------------------------
    // Main loop
    // -------------------------------------------------------
    @Override
    public void run() {
        try {
            // out antes de in para evitar deadlock
            out = new ObjectOutputStream(socket.getOutputStream());
            in  = new ObjectInputStream(socket.getInputStream());

            if (!handleRegister()) {
                return;
            }

            receiveLoop();

        } catch (IOException | ClassNotFoundException e) {
            if (running) {
                System.err.println("[DWC:" + username + "] Conexão perdida: " + e.getMessage());
            }
        } finally {
            running = false;
            // Se estiver bloqueado numa barreira/latch, liberta
            forceReleaseCurrentRound();
            try {
                socket.close();
            } catch (IOException ignored) {}
            System.out.println("[DWC:" + (username != null ? username : "?") + "] Desligado.");
        }
    }

    /**
     * Lê a mensagem de registo e regista o jogador no jogo correto.
     */
    private boolean handleRegister() throws IOException, ClassNotFoundException {
        Message msg = (Message) in.readObject();
        if (msg.getType() != Message.Type.REGISTER) {
            send(new Message(Message.Type.ERROR, "Esperava REGISTER"));
            return false;
        }

        RegisterPayload reg = (RegisterPayload) msg.getPayload();
        this.username = reg.username;
        this.teamName = reg.teamName;
        this.gameId   = reg.gameId;

        GameState gs = server.getGame(gameId);
        if (gs == null) {
            send(new Message(Message.Type.ERROR, "Jogo '" + gameId + "' não encontrado"));
            return false;
        }

        boolean ok = gs.registerPlayer(username, teamName, this);
        if (!ok) {
            send(new Message(Message.Type.ERROR, "Registo recusado (nome duplicado ou equipa cheia)"));
            return false;
        }

        this.game = gs;
        System.out.println("[Server] " + username + " (equipa:" + teamName
                + ") registado no jogo " + gameId
                + " [" + gs.getRegisteredCount() + "/" + gs.getExpectedPlayers() + "]");

        Player confirmed = new Player(gs.getRegisteredCount(), username, teamName);
        send(new Message(Message.Type.REGISTERED, confirmed));

        // Bloqueia na barreira até todos os jogadores estarem registados;
        // o último a chegar dispara o GameHandler automaticamente.
        server.awaitGameStart(gameId);

        return true;
    }

    /**
     * Loop contínuo de receção de mensagens do cliente.
     * Quando recebe uma ANSWER, processa-a diretamente no contexto
     * da ronda atual (latch ou barrier) — sem criar threads extra.
     */
    private void receiveLoop() throws IOException, ClassNotFoundException {
        while (running) {
            Message msg;
            try {
                msg = (Message) in.readObject();
            } catch (EOFException | SocketException e) {
                break;
            }

            switch (msg.getType()) {
                case ANSWER:
                    handleAnswer((Integer) msg.getPayload());
                    break;
                default:
                    System.err.println("[DWC:" + username + "] Mensagem inesperada: " + msg.getType());
            }
        }
    }

    /**
     * Processa uma resposta recebida do cliente.
     * Executa na própria thread do DealWithClient — sem workers extra.
     */
    private void handleAnswer(int answerIdx) {
        // --- Ronda individual ---
        ModifiedCountDownLatch latch = currentLatch;
        if (latch != null && !latch.isDone()) {
            int factor  = latch.countDown();
            boolean correct = (answerIdx == game.getCurrentQuestion().getCorrect());
            int pts = correct ? game.getCurrentQuestion().getPoints() * factor : 0;

            String team = game.getTeamOf(username);
            if (pts > 0 && team != null) {
                game.getScoreManager().addRoundPoints(team, pts);
                game.addTeamScore(team, pts);
            }

            System.out.printf("  [Individual] %s respondeu %d (%s) -> %d pts (x%d)%n",
                    username, answerIdx, correct ? "CERTO" : "ERRADO", pts, factor);
            return;
        }

        // --- Ronda de equipa ---
        TeamBarrier barrier = currentBarrier;
        java.util.Map<String, Integer> teamAnswers = currentTeamAnswers;
        if (barrier != null && !barrier.isReleased()) {
            synchronized (teamAnswers) {
                teamAnswers.put(username, answerIdx);
            }
            try {
                barrier.arrive();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                try { barrier.arrive(); } catch (InterruptedException ignored) {}
            }
        }
    }

    /**
     * Se o jogador desligar a meio de uma ronda, avança o latch/barrier
     * para não bloquear o GameHandler indefinidamente.
     */
    private void forceReleaseCurrentRound() {
        ModifiedCountDownLatch latch = currentLatch;
        if (latch != null && !latch.isDone()) {
            latch.countDown();
        }

        TeamBarrier barrier = currentBarrier;
        if (barrier != null && !barrier.isReleased()) {
            try { barrier.arrive(); } catch (InterruptedException ignored) {}
        }
    }

    public void stopClient() {
        running = false;
        forceReleaseCurrentRound();
        try {
            socket.close();
        } catch (IOException ignored) {}
    }

    public String getUsername() { return username; }
    public String getTeamName() { return teamName; }
    public boolean isRunning()  { return running; }

    // -------------------------------------------------------
    // Payload de registo
    // -------------------------------------------------------
    public static class RegisterPayload implements java.io.Serializable {

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