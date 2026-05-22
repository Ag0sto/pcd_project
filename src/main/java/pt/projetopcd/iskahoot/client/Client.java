package pt.projetopcd.iskahoot.client;

import pt.projetopcd.iskahoot.model.Message;
import pt.projetopcd.iskahoot.model.Player;

import javax.swing.SwingUtilities;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.Socket;

/**
 * Cliente IsKahoot.
 *
 * Uso:
 *   java clienteKahoot {IP PORT Jogo Equipa Username}
 *
 * Ou sem argumentos → GUI de login onde o utilizador preenche os campos.
 */
public class Client {

    private ObjectInputStream  in;
    private ObjectOutputStream out;
    private Socket socket;

    private final ClientGUI gui;

    // Parâmetros de ligação
    private String serverHost = "localhost";
    private int    serverPort = 8080;

    public Client(ClientGUI gui) {
        this.gui = gui;
    }

    public Client() {
        this.gui = null;
    }

    // -------------------------------------------------------
    // Main
    // -------------------------------------------------------

    public static void main(String[] args) {
        if (args.length == 5) {
            // Modo linha de comandos: IP PORT Jogo Equipa Username
            String host  = args[0];
            int    port  = Integer.parseInt(args[1]);
            String gameId  = args[2];
            String team    = args[3];
            String username = args[4];

            Client client = new Client();
            client.serverHost = host;
            client.serverPort = port;

            SwingUtilities.invokeLater(() -> {
                ClientGUI g = new ClientGUI();
                Client c2 = new Client(g);
                c2.serverHost = host;
                c2.serverPort = port;
                g.showLoginScreen(gameId, team, username);
                // Ligação automática
                new Thread(() -> c2.connect(gameId, team, username)).start();
            });
        } else {
            // Modo GUI: o utilizador preenche os campos
            SwingUtilities.invokeLater(() -> new ClientGUI().showLoginScreen());
        }
    }

    // -------------------------------------------------------
    // Ligação e registo
    // -------------------------------------------------------

    void connectToServer() throws IOException {
        InetAddress addr = InetAddress.getByName(serverHost);
        socket = new Socket(addr, serverPort);
        // out ANTES de in para evitar deadlock
        out = new ObjectOutputStream(socket.getOutputStream());
        in  = new ObjectInputStream(socket.getInputStream());
        System.out.println("[Client] Ligado a " + serverHost + ":" + serverPort);
    }

    /** Liga, regista e arranca o loop de receção de mensagens. */
    public void connect(String gameId, String teamName, String username) {
        try {
            connectToServer();
            register(gameId, teamName, username);
        } catch (IOException e) {
            if (gui != null) gui.showError("Erro de ligação: " + e.getMessage());
            else e.printStackTrace();
        }
    }

    private void register(String gameId, String teamName, String username) throws IOException {
        // Envia REGISTER — RegisterPayload está agora em Message
        Message.RegisterPayload payload =
                new Message.RegisterPayload(username, teamName, gameId);
        sendMessage(new Message(Message.Type.REGISTER, payload));

        // Aguarda resposta
        try {
            Message reply = (Message) in.readObject();
            if (reply.getType() == Message.Type.ERROR) {
                String reason = (String) reply.getPayload();
                System.err.println("[Client] Registo recusado: " + reason);
                if (gui != null) gui.showError(reason);
                return;
            }
            if (reply.getType() == Message.Type.REGISTERED) {
                Player me = (Player) reply.getPayload();
                System.out.println("[Client] Registado como " + me.getName()
                        + " (ID " + me.getId() + ") equipa:" + me.getTeam());
                if (gui != null) {
                    SwingUtilities.invokeLater(() -> gui.showGameScreen(me));
                }
            }
            // Loop de receção de mensagens do servidor
            receiveLoop();
        } catch (ClassNotFoundException e) {
            System.err.println("[Client] Protocolo desconhecido: " + e.getMessage());
        }
    }

    /** Loop que recebe mensagens do servidor e repassa à GUI. */
    private void receiveLoop() throws IOException, ClassNotFoundException {
        while (true) {
            Message msg;
            try {
                msg = (Message) in.readObject();
            } catch (Exception e) {
                System.out.println("[Client] Conexão encerrada.");
                break;
            }

            switch (msg.getType()) {
                case WAITING:
                    if (gui != null) gui.showWaiting((String) msg.getPayload());
                    else System.out.println("[Aguardar] " + msg.getPayload());
                    break;

                case QUESTION:
                    Message.QuestionMsg qm = (Message.QuestionMsg) msg.getPayload();
                    if (gui != null) gui.showQuestion(qm);
                    else printQuestion(qm);
                    break;

                case ROUND_END:
                    Message.RoundResult rr = (Message.RoundResult) msg.getPayload();
                    if (gui != null) gui.showRoundResult(rr);
                    else printRoundResult(rr, false);
                    break;

                case GAME_END:
                    Message.RoundResult final_ = (Message.RoundResult) msg.getPayload();
                    if (gui != null) gui.showGameEnd(final_);
                    else printRoundResult(final_, true);
                    return; // fim

                default:
                    System.out.println("[Client] Mensagem desconhecida: " + msg.getType());
            }
        }
    }

    // -------------------------------------------------------
    // Envio de resposta
    // -------------------------------------------------------

    public void sendAnswer(int optionIndex) {
        try {
            sendMessage(new Message(Message.Type.ANSWER, optionIndex));
        } catch (IOException e) {
            System.err.println("[Client] Erro ao enviar resposta: " + e.getMessage());
        }
    }

    private synchronized void sendMessage(Message msg) throws IOException {
        out.writeObject(msg);
        out.flush();
        out.reset();
    }

    // -------------------------------------------------------
    // Terminal (sem GUI)
    // -------------------------------------------------------

    private void printQuestion(Message.QuestionMsg q) {
        System.out.println("\n=== Pergunta " + q.questionNumber + "/" + q.totalQuestions
                + (q.isTeamRound ? " [EQUIPA]" : " [INDIVIDUAL]") + " ===");
        System.out.println(q.questionText + " (" + q.points + " pts, " + q.timeLimitSeconds + "s)");
        for (int i = 0; i < q.options.size(); i++) {
            System.out.println("  " + i + ") " + q.options.get(i));
        }
    }

    private void printRoundResult(Message.RoundResult r, boolean isFinal) {
        System.out.println(isFinal ? "\n=== FIM DO JOGO ===" : "\n--- Fim da Ronda ---");
        if (!isFinal && r.correctOption >= 0)
            System.out.println("Resposta correta: opção " + r.correctOption);

        System.out.println("\n  Jogadores:");
        for (Message.PlayerResult pr : r.playerResults) {
            System.out.printf("    %-12s (%s)  ronda:+%d  total:%d  bonus:x%d  %s%n",
                    pr.username, pr.teamName,
                    pr.roundPoints, pr.totalPoints, pr.bonusApplied,
                    pr.answeredCorrectly ? "✔" : (pr.hasAnswered ? "✘" : "—"));
        }
        System.out.println("\n  Equipas:");
        for (Message.TeamResult tr : r.teamResults) {
            System.out.printf("    %-12s  ronda:+%d  total:%d%n",
                    tr.teamName, tr.roundPoints, tr.totalPoints);
        }
        if (isFinal) System.out.println("\nVencedor: " + r.winnerTeam);
    }
}
