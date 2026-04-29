package pt.projetopcd.iskahoot.client;

import pt.projetopcd.iskahoot.network.Message;
import pt.projetopcd.iskahoot.server.Server;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.Socket;

public class Client {
    private ObjectInputStream in;
    private ObjectOutputStream out;
    private Socket socket;
    private Message msg;
    public static void main(String[] args) throws ClassNotFoundException {
        new Client().runClient();
    }

    public void runClient() throws ClassNotFoundException {
        try {
            connectToServer();
            sendMessages();
        } catch (IOException e) {// ERRO...
        } finally {//a fechar...
            try {
                socket.close();
            } catch (IOException e) {//...
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
    }

    void sendMessages() throws IOException, ClassNotFoundException {
        for (int i = 0; i < 10; i++) {
            msg = new Message("Mensagem " + i, i);
            out.writeObject(msg);
            Message msg = (Message) in.readObject();
            System.out.println("Recebi:" + msg.text);
            out.flush();
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {//...
            }
        }
        msg = new Message("FIM", -1);
        out.writeObject(msg);
    }

}
