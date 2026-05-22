package pt.projetopcd.iskahoot.server;

import pt.projetopcd.iskahoot.model.Message;
import pt.projetopcd.iskahoot.model.Player;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.SocketException;

/**
 * Thread dedicada a cada jogador ligado ao servidor.
 * Registo, envio de mensagens e receção de respostas.
 */
public class DealWithClient extends Thread {

    private final Socket       socket;
    private ObjectInputStream  in;
    private ObjectOutputStream out;

    private String    username;
    private String    teamName;
    private String    gameId;
    private GameState game;

    private volatile Integer pendingAnswer = null;
    private final    Object  answerLock    = new Object();
    private volatile boolean running       = true;

    public DealWithClient(Socket socket) {
        this.socket = socket;
        setDaemon(true);
    }

    // ─────────────────────────────────────────────────────────
    // Envio
    // ─────────────────────────────────────────────────────────

    public synchronized void send(Message msg) {
        try {
            out.writeObject(msg);
            out.flush();
            out.reset(); // evita cache de referências
        } catch (IOException e) {
            running = false;
        }
    }

    // ─────────────────────────────────────────────────────────
    // Resposta do jogador
    // ─────────────────────────────────────────────────────────

    public int waitForAnswer() throws InterruptedException {
        synchronized (answerLock) {
            while (pendingAnswer == null && running) answerLock.wait();
            if (!running) throw new InterruptedException("Jogador desligado");
            int ans = pendingAnswer;
            pendingAnswer = null;
            return ans;
        }
    }

    private void setAnswer(int idx) {
        synchronized (answerLock) {
            if (pendingAnswer == null) {
                pendingAnswer = idx;
                answerLock.notifyAll();
            }
        }
    }

    public void clearAnswer() {
        synchronized (answerLock) { pendingAnswer = null; }
    }

    // ─────────────────────────────────────────────────────────
    // Main loop
    // ─────────────────────────────────────────────────────────

    @Override
    public void run() {
        try {
            out = new ObjectOutputStream(socket.getOutputStream());
            in  = new ObjectInputStream(socket.getInputStream());

            if (!handleRegister()) return;
            receiveLoop();

        } catch (IOException | ClassNotFoundException e) {
            if (running) System.err.println("[DWC:" + username + "] Erro: " + e.getMessage());
        } finally {
            running = false;
            synchronized (answerLock) { answerLock.notifyAll(); }
            try { socket.close(); } catch (IOException ignored) {}
            System.out.println("[DWC:" + (username != null ? username : "?") + "] Desligado.");
        }
    }

    private boolean handleRegister() throws IOException, ClassNotFoundException {
        Message msg = (Message) in.readObject();
        if (msg.getType() != Message.Type.REGISTER) {
            send(new Message(Message.Type.ERROR, "Esperava REGISTER"));
            return false;
        }

        Message.RegisterPayload reg = (Message.RegisterPayload) msg.getPayload();
        this.username = reg.username;
        this.teamName = reg.teamName;
        this.gameId   = reg.gameId;

        GameState gs = Server.getGame(gameId);
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
        System.out.println("[DWC] " + username + " (" + teamName + ") no jogo "
                + gameId + " [" + gs.getRegisteredCount() + "/" + gs.getExpectedPlayers() + "]");

        Player confirmed = new Player(gs.getRegisteredCount(), username, teamName);
        send(new Message(Message.Type.REGISTERED, confirmed));
        Server.notifyPlayerJoined(gameId);
        return true;
    }

    private void receiveLoop() throws IOException, ClassNotFoundException {
        while (running) {
            Message msg;
            try {
                msg = (Message) in.readObject();
            } catch (EOFException | SocketException e) { break; }

            if (msg.getType() == Message.Type.ANSWER) {
                setAnswer((Integer) msg.getPayload());
            }
        }
    }

    public void stopClient() {
        running = false;
        synchronized (answerLock) { answerLock.notifyAll(); }
        try { socket.close(); } catch (IOException ignored) {}
    }

    public String  getUsername() { return username; }
    public String  getTeamName() { return teamName; }
    public boolean isRunning()   { return running; }
}
