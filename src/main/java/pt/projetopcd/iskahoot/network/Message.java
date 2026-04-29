package pt.projetopcd.iskahoot.network;

import java.io.Serializable;

public class Message implements Serializable {
    public String text;
    public int id;

    public Message(String text, int id) {
        this.text = text;
        this.id = id;
    }

    public String getText() {
        return text;
    }

    public int getId() {
        return id;
    }
}
