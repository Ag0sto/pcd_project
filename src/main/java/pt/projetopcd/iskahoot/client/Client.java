package pt.projetopcd.iskahoot.client;

import pt.projetopcd.iskahoot.model.Player;
import pt.projetopcd.iskahoot.server.Server;

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

    public static void main(String[] args) throws ClassNotFoundException {
        new Client().runClient();
    }

    public void runClient() throws ClassNotFoundException {
        try {
            connectToServer();
            createPlayer();
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

    void connectToServer() throws IOException {
        InetAddress endereco = InetAddress.getByName(null);
        System.out.println("Endereco:" + endereco);
        socket = new Socket(endereco, Server.PORTO);
        System.out.println("Socket:" + socket);
        in = new ObjectInputStream(socket.getInputStream());
        out = new ObjectOutputStream(socket.getOutputStream());

        System.out.println("Ligado ao servidor");
    }

    void createPlayer() throws IOException, ClassNotFoundException {
        Scanner sc = new Scanner(System.in);

        System.out.println("Nome do Player: ");
        String name = sc.nextLine();

        System.out.println("Nome da Equipa: ");
        String team = sc.nextLine();

        System.out.println("Código da Sessão: ");
        String code = sc.nextLine();

        player = new Player(0, name, team); //id = 0, pois o servidor irá atribuir um id único a cada jogador
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) { }
    }

    void sendPlayers() throws IOException, ClassNotFoundException {
        out.writeObject(player);
        out.flush();

        System.out.println("\nPlayer " + player.getName() + " enviado para o servidor.");

        Object response = in.readObject();
        System.out.println("Resposta do servidor: " + response);
    }
}
