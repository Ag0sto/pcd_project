package pt.projetopcd.iskahoot.server;

import pt.projetopcd.iskahoot.model.Message;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;

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

        private void handleMessages() throws IOException, ClassNotFoundException {
            while (true) {
                Message msg = (Message) in.readObject();
                if (msg.getText().equals("FIM"))
                    break;
                System.out.println("Eco:" + msg.text);
                out.writeObject(msg);
            }
        }

        @Override
        public void run() {
            try {
                handleMessages();
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    public static final int PORTO = 8080;
    private ArrayList<ObjectOutputStream> clients = new ArrayList<>();

    public synchronized void addClient(ObjectOutputStream out){
        clients.add(out);
    }

    public synchronized void removeClient(ObjectOutputStream out){
        clients.remove(out);
    }
    public synchronized void sendToAll(Message msg) throws IOException{
        for (ObjectOutputStream out : clients) {
            out.writeObject(msg);
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
