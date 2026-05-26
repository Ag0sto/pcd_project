package pt.projetopcd.iskahoot.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;

/**
 * Servidor IsKahoot.
 *
 * Lançamento: sem argumentos — o servidor fica à escuta e aguarda comandos na
 * TUI para criar jogos.
 *
 * TUI: new <numEquipas> <jogadoresPorEquipa> <numPerguntas>
 * → cria um novo jogo e imprime o código gerado list → lista jogos ativos com
 * estado e pontuações quit → encerra o servidor
 */
public class Server {

    public static final int PORTO = 8080;

    // Jogos ativos: gameId -> GameState
    private final Map<String, GameState> games = new ConcurrentHashMap<>();

    // Barreiras de arranque: gameId -> CyclicBarrier
    // Cada DealWithClient faz barrier.await() após registo.
    // Quando o último jogador chega, a barrierAction arranca o GameHandler.
    private final Map<String, CyclicBarrier> gameBarriers = new ConcurrentHashMap<>();

    // -------------------------------------------------------
    // API de instância usada por DealWithClient e GameHandler
    // -------------------------------------------------------

    public GameState getGame(String gameId) {
        return games.get(gameId);
    }

    public void removeGame(String gameId) {
        games.remove(gameId);
        gameBarriers.remove(gameId);
        System.out.println("[Server] Jogo " + gameId + " removido.");
    }

    /**
     * Chamado por DealWithClient após o registo bem-sucedido do jogador.
     * Bloqueia na barreira até todos os jogadores estarem presentes;
     * o último a chegar arranca automaticamente o GameHandler.
     */
    public void awaitGameStart(String gameId) {
        CyclicBarrier barrier = gameBarriers.get(gameId);
        if (barrier == null) return;
        try {
            barrier.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (BrokenBarrierException e) {
            // jogo cancelado antes de encher — não faz nada
        }
    }

    // -------------------------------------------------------
    // Criação de jogos
    // -------------------------------------------------------

    private String createGame(int numTeams, int playersPerTeam, int numQuestions) {
        String gameId = generateGameId();
        GameState gs = new GameState(gameId, numTeams, playersPerTeam, numQuestions);
        games.put(gameId, gs);

        int totalPlayers = numTeams * playersPerTeam;

        // barrierAction: executada pela última thread a chegar.
        // Arranca o GameHandler diretamente como nova thread.
        CyclicBarrier barrier = new CyclicBarrier(totalPlayers, () -> {
            System.out.println("[Server] Jogo " + gameId + " completo! A iniciar GameHandler...");
            new GameHandler(gs, Server.this).start();
        });
        gameBarriers.put(gameId, barrier);

        System.out.println("[Server] Jogo criado: " + gameId
                + " (" + numTeams + " equipas x " + playersPerTeam
                + " jogadores, " + numQuestions + " perguntas)");

        return gameId;
    }

    private int gameCounter = 1000;

    private synchronized String generateGameId() {
        return "GAME-" + (gameCounter++);
    }

    // -------------------------------------------------------
    // Aceitar conexões (loop principal de rede)
    // -------------------------------------------------------

    private void acceptConnections() {
        try (ServerSocket ss = new ServerSocket(PORTO)) {
            System.out.println("[Server] A escutar no porto " + PORTO);
            ss.setSoTimeout(0);
            while (true) {
                Socket socket = ss.accept();
                DealWithClient dwc = new DealWithClient(socket, this);
                dwc.start();
            }
        } catch (IOException e) {
            System.err.println("[Server] Erro: " + e.getMessage());
        }
    }

    // -------------------------------------------------------
    // TUI
    // -------------------------------------------------------

    private void runTUI() {
        Scanner sc = new Scanner(System.in);
        printHelp();

        while (sc.hasNextLine()) {
            String line = sc.nextLine().trim();
            if (line.isEmpty()) continue;

            String[] parts = line.split("\\s+");
            String cmd = parts[0].toLowerCase();

            switch (cmd) {
                case "new": {
                    if (parts.length < 4) {
                        System.out.println("Uso: new <numEquipas> <jogadoresPorEquipa> <numPerguntas>");
                        break;
                    }
                    try {
                        int nt = Integer.parseInt(parts[1]);
                        int pp = Integer.parseInt(parts[2]);
                        int nq = Integer.parseInt(parts[3]);
                        String id = createGame(nt, pp, nq);
                        System.out.println(">>> Código do jogo: " + id
                                + "  (aguardando " + (nt * pp) + " jogadores)");
                    } catch (NumberFormatException e) {
                        System.out.println("Argumentos inválidos.");
                    }
                    break;
                }
                case "list": {
                    if (games.isEmpty()) {
                        System.out.println("Nenhum jogo ativo.");
                    } else {
                        for (GameState gs : games.values()) {
                            System.out.printf("  Jogo %-10s | %d/%d jogadores | Pontuações: %s%n",
                                    gs.getGameId(),
                                    gs.getRegisteredCount(),
                                    gs.getExpectedPlayers(),
                                    ScoreManager.buildTeamResults(gs).toString());
                        }
                    }
                    break;
                }
                case "quit":
                case "exit": {
                    System.out.println("A encerrar servidor...");
                    System.exit(0);
                    break;
                }
                case "help":
                    printHelp();
                    break;
                default:
                    System.out.println("Comando desconhecido. Tente 'help'.");
            }
        }
    }

    private void printHelp() {
        System.out.println("=== IsKahoot Server ===");
        System.out.println("  new <numEquipas> <jogadoresPorEquipa> <numPerguntas>  - cria novo jogo");
        System.out.println("  list   - lista jogos ativos");
        System.out.println("  quit   - encerra o servidor");
        System.out.println("  help   - mostra esta ajuda");
    }

    // -------------------------------------------------------
    // Main
    // -------------------------------------------------------

    public static void main(String[] args) {
        Server server = new Server();

        Thread netThread = new Thread(server::acceptConnections);
        netThread.setDaemon(true);
        netThread.start();

        server.runTUI();
    }
}