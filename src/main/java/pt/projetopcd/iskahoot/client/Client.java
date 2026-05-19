package pt.projetopcd.iskahoot.client;

import pt.projetopcd.iskahoot.model.Player;
import pt.projetopcd.iskahoot.server.Server;

import javax.swing.*;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Scanner;

public class Client {
    private ObjectInputStream in;
    private ObjectOutputStream out;
    private Socket socket;
    private Player player;
    private final ClientGUI gui;

    public Client(ClientGUI gui) {
        this.gui = gui;
    }

    public Client() {
        this.gui = null;
    }

    public static void main(String[] args) throws ClassNotFoundException {
        SwingUtilities.invokeLater(() -> new ClientGUI().showLoginScreen());
    }

    /** Fluxo completo em modo terminal (mantém compatibilidade anterior). */
    public void runClientTerminal() throws ClassNotFoundException {
        try {
            connectToServer();
            createPlayerFromConsole();
            sendPlayers();
        } catch (IOException e) {
            e.printStackTrace();// ERRO...
        } finally {//a fechar...
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();//...
            }
        }
    }

    // Ligação ao servidor
    void connectToServer() throws IOException {
        InetAddress endereco = InetAddress.getByName(null);
        System.out.println("Endereco:" + endereco);
        socket = new Socket(endereco, Server.PORTO);
        System.out.println("Socket:" + socket);
        in = new ObjectInputStream(socket.getInputStream());
        out = new ObjectOutputStream(socket.getOutputStream());
        System.out.println("Ligado ao servidor");
    }

    // Criação do Player — via GUI (nome e equipa já conhecidos)
    public void createPlayer(String name, String team) {
        // id = 0 pois o servidor atribui o id definitivo
        player = new Player(0, name, team);
    }

    // Criação do Player — via consola (modo terminal)
    void createPlayerFromConsole() throws IOException, ClassNotFoundException {
        Scanner sc = new Scanner(System.in);

        System.out.println("Nome do Player: ");
        String name = sc.nextLine();

        System.out.println("Nome da Equipa: ");
        String team = sc.nextLine();

    //    System.out.println("Código da Sessão: ");
    //    String code = sc.nextLine();

        player = new Player(0, name, team); //id = 0, pois o servidor irá atribuir um id único a cada jogador
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) { }
    }

    // Envio do Player e receção da resposta do servidor
    void sendPlayers() throws IOException, ClassNotFoundException {
        out.writeObject(player);
        out.flush();

        System.out.println("\nPlayer " + player.getName() + " enviado para o servidor.");

        Player confirmed = (Player) in.readObject();
        System.out.println("Servidor confirmou: " + confirmed.getName() + " com ID " + confirmed.getId());

        if (gui != null) {
            gui.showGameScreen(confirmed);
        }
    }
}
