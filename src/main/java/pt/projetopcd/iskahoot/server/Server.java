package pt.projetopcd.iskahoot.server;

import pt.projetopcd.iskahoot.model.Player;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class Server {

    public class DealWithCLient extends Thread{
        public DealWithCLient(Socket socket) throws IOException {
            doConnections(socket);
        }

        private ObjectInputStream in;

        private ObjectOutputStream out;

        void doConnections(Socket socket) throws IOException {

            out = new ObjectOutputStream(socket.getOutputStream());
            in = new ObjectInputStream(socket.getInputStream());
        }

        private void handlePlayer() throws IOException, ClassNotFoundException {

                Player player = (Player) in.readObject();

                player.setId(newId.getAndIncrement());

                System.out.println("Player " + player.getName() + " Id: " + player.getId() + " entrou com a equipa " + player.getTeam());

                out.writeObject(player);
        }

        @Override
        public void run() {
            try {
                handlePlayer();
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    private final AtomicInteger newId = new AtomicInteger(1);
    public static final int PORTO = 8080;
    private ArrayList<ObjectOutputStream> clients = new ArrayList<>();

    public synchronized void addClient(ObjectOutputStream out){
        clients.add(out);
    }

    public synchronized void removeClient(ObjectOutputStream out){
        clients.remove(out);
    }
    public synchronized void sendToAll(Player player) throws IOException{
        for (ObjectOutputStream out : clients) {
            out.writeObject(player);
        }
    }
    public static void main(String[] args) {
        try {
            new Server().startServing();
        } catch (IOException e) {
            // ...
        }
    }

    public void startServing() throws IOException {
        ServerSocket ss = new ServerSocket(PORTO);
        try {
            while(true){
                System.out.println("Esperando ligações ...");
                Socket socket = ss.accept();
                try {//Conexao aceite
                    new DealWithCLient(socket).start();
                } finally {//a fechar
                    //socket.close();
                }
            }
        } finally {
            ss.close();
        }
    }
}
