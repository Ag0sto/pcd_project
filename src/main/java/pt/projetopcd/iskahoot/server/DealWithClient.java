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
 *
 * ALTERAÇÃO: a resposta do jogador é agora processada diretamente
 * no receiveLoop() desta thread, eliminando a necessidade de criar
 * threads auxiliares no GameHandler para aguardar cada resposta.
 * O GameHandler expõe notifyAnswer() para ser chamado daqui.
 */
public class DealWithClient extends Thread {

    private final Socket  socket;
    private final Server  server;
    private ObjectInputStream  in;
    private ObjectOutputStream out;

    private String    username;
    private String    teamName;
    private String    gameId;
    private GameState game;

    // Referência ao GameHandler ativo — definida quando o jogo arranca.
    // Volatile porque é escrita pela thread do GameHandler e lida por esta.
    private volatile GameHandler gameHandler = null;

    private volatile boolean running = true;

    public DealWithClient(Socket socket, Server server) {
        this.socket = socket;
        this.server = server;
        setDaemon(true);
    }

    // ─────────────────────────────────────────────────────────
    // Envio
    // ─────────────────────────────────────────────────────────

    public synchronized void send(Message msg) {
        try {
            out.writeObject(msg);
            out.flush();
            out.reset();
        } catch (IOException e) {
            running = false;
        }
    }

    // ─────────────────────────────────────────────────────────
    // Ligação ao GameHandler
    // ─────────────────────────────────────────────────────────

    /**
     * Chamado pelo GameHandler quando o jogo arranca, para que esta
     * thread saiba a quem entregar as respostas recebidas.
     */
    public void setGameHandler(GameHandler gh) {
        this.gameHandler = gh;
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
        System.out.println("[DWC] " + username + " (" + teamName + ") no jogo "
                + gameId + " [" + gs.getRegisteredCount() + "/" + gs.getExpectedPlayers() + "]");

        Player confirmed = new Player(gs.getRegisteredCount(), username, teamName);
        send(new Message(Message.Type.REGISTERED, confirmed));

        // Bloqueia na barreira até todos os jogadores estarem registados;
        // o último a chegar submete o GameHandler ao pool automaticamente.
        server.awaitGameStart(gameId);
        return true;
    }

    /**
     * Loop de receção de mensagens do cliente.
     *
     * Quando chega um ANSWER, em vez de o guardar num campo e obrigar
     * o GameHandler a criar uma thread para esperar por ele, entregamo-lo
     * diretamente ao GameHandler através de notifyAnswer(). É esta thread
     * (DealWithClient) que faz o trabalho — sem threads extra.
     */
    private void receiveLoop() throws IOException, ClassNotFoundException {
        while (running) {
            Message msg;
            try {
                msg = (Message) in.readObject();
            } catch (EOFException | SocketException e) {
                break;
            }

            if (msg.getType() == Message.Type.ANSWER) {
                int answerIdx = (Integer) msg.getPayload();
                GameHandler gh = gameHandler;
                if (gh != null) {
                    // Processamento feito nesta thread — não é necessária nenhuma
                    // thread auxiliar no GameHandler.
                    gh.notifyAnswer(this, answerIdx);
                }
            }
        }
    }

    public void stopClient() {
        running = false;
        try { socket.close(); } catch (IOException ignored) {}
    }

    public String  getUsername() { return username; }
    public String  getTeamName() { return teamName; }
    public boolean isRunning()   { return running; }
}