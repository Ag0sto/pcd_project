package pt.projetopcd.iskahoot.server;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.SocketException;

import pt.projetopcd.iskahoot.model.Message;
import pt.projetopcd.iskahoot.model.Player;

/**
 * Thread dedicada a cada jogador ligado ao servidor.
 *
 * Responsabilidades: - Receber registo inicial (username, equipa, código de
 * jogo) - Aguardar início do jogo - Receber respostas e repassá-las ao
 * GameHandler - Enviar mensagens do servidor ao cliente (perguntas, resultados,
 * etc.)
 */
public class DealWithClient extends Thread {

    private final Socket socket;
    private ObjectInputStream in;
    private ObjectOutputStream out;

    // Preenchido após registo bem-sucedido
    private String username;
    private String teamName;
    private String gameId;
    private GameState game;

    // Resposta recebida para a ronda atual (null = ainda não recebida)
    private volatile Integer pendingAnswer = null;
    private final Object answerLock = new Object();

    private volatile boolean running = true;

    public DealWithClient(Socket socket) {
        this.socket = socket;
        setDaemon(true);
    }

    // -------------------------------------------------------
    // Envio de mensagens ao cliente
    // -------------------------------------------------------
    public synchronized void send(Message msg) {
        try {
            out.writeObject(msg);
            out.flush();
            out.reset(); // evita cache de objetos
        } catch (IOException e) {
            System.err.println("[DWC:" + username + "] Erro ao enviar: " + e.getMessage());
            running = false;
        }
    }

    // -------------------------------------------------------
    // Aguardar resposta do jogador para a ronda atual
    // -------------------------------------------------------
    /**
     * Bloqueia até o jogador enviar a resposta (ou a thread ser interrompida).
     */
    public int waitForAnswer() throws InterruptedException {
        synchronized (answerLock) {
            while (pendingAnswer == null && running) {
                answerLock.wait();
            }
            if (!running) {
                throw new InterruptedException("Jogador desligado");
            }
            int ans = pendingAnswer;
            pendingAnswer = null;
            return ans;
        }
    }

    /**
     * Chamado pelo loop de receção quando chega uma resposta.
     */
    private void setAnswer(int answerIndex) {
        synchronized (answerLock) {
            if (pendingAnswer == null) { // aceita apenas a primeira resposta por ronda
                pendingAnswer = answerIndex;
                answerLock.notifyAll();
            }
        }
    }

    /**
     * Limpa a resposta pendente (entre rondas).
     */
    public void clearAnswer() {
        synchronized (answerLock) {
            pendingAnswer = null;
        }
    }

    // -------------------------------------------------------
    // Main loop
    // -------------------------------------------------------
    @Override
    public void run() {
        try {
            // Streams — out antes de in (evita deadlock)
            out = new ObjectOutputStream(socket.getOutputStream());
            in = new ObjectInputStream(socket.getInputStream());

            // 1. Registo
            if (!handleRegister()) {
                return;
            }

            // 2. Loop de receção de mensagens (respostas, etc.)
            receiveLoop();

        } catch (IOException | ClassNotFoundException e) {
            if (running) {
                System.err.println("[DWC:" + username + "] Conexão perdida: " + e.getMessage());
            }
        } finally {
            running = false;
            // notifica waitForAnswer se estiver bloqueado
            synchronized (answerLock) {
                answerLock.notifyAll();
            }
            try {
                socket.close();
            } catch (IOException ignored) {
            }
            System.out.println("[DWC:" + (username != null ? username : "?") + "] Desligado.");
        }
    }

    /**
     * Lê a mensagem de registo e regista o jogador no jogo correto.
     *
     * @return true se bem-sucedido
     */
    private boolean handleRegister() throws IOException, ClassNotFoundException {
        Message msg = (Message) in.readObject();
        if (msg.getType() != Message.Type.REGISTER) {
            send(new Message(Message.Type.ERROR, "Esperava REGISTER"));
            return false;
        }

        // payload: Player com gameId no campo team (reutilizamos os campos)
        // Na prática, o cliente envia: Player(id=0, name=username, teamname=teamName)
        // e o gameId vem num campo separado — usamos Message.RegisterPayload
        RegisterPayload reg = (RegisterPayload) msg.getPayload();
        this.username = reg.username;
        this.teamName = reg.teamName;
        this.gameId = reg.gameId;

        // Procura o jogo no servidor
        GameState gs = Server.getGame(gameId);
        if (gs == null) {
            send(new Message(Message.Type.ERROR, "Jogo '" + gameId + "' não encontrado"));
            return false;
        }

        // Regista no jogo
        boolean ok = gs.registerPlayer(username, teamName, this);
        if (!ok) {
            send(new Message(Message.Type.ERROR, "Registo recusado (nome duplicado ou equipa cheia)"));
            return false;
        }

        this.game = gs;
        System.out.println("[Server] " + username + " (equipa:" + teamName
                + ") registado no jogo " + gameId
                + " [" + gs.getRegisteredCount() + "/" + gs.getExpectedPlayers() + "]");

        // Confirma registo
        Player confirmed = new Player(gs.getRegisteredCount(), username, teamName);
        send(new Message(Message.Type.REGISTERED, confirmed));

        // Notifica o servidor que pode haver jogadores suficientes para iniciar
        Server.notifyPlayerJoined(gameId);

        // Aguarda início do jogo (a thread fica viva recebendo mensagens)
        return true;
    }

    /**
     * Loop contínuo de receção de mensagens do cliente.
     */
    private void receiveLoop() throws IOException, ClassNotFoundException {
        while (running) {
            Message msg;
            try {
                msg = (Message) in.readObject();
            } catch (EOFException | SocketException e) {
                break; // cliente desligou
            }

            switch (msg.getType()) {
                case ANSWER:
                    int idx = (Integer) msg.getPayload();
                    setAnswer(idx);
                    break;
                default:
                    System.err.println("[DWC:" + username + "] Mensagem inesperada: " + msg.getType());
            }
        }
    }

    public void stopClient() {
        running = false;
        synchronized (answerLock) {
            answerLock.notifyAll();
        }
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }

    public String getUsername() {
        return username;
    }

    public String getTeamName() {
        return teamName;
    }

    public boolean isRunning() {
        return running;
    }

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
            this.gameId = gameId;
        }
    }
}
