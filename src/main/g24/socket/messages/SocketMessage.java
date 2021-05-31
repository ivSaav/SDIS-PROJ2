package main.g24.socket.messages;

import main.g24.chord.INode;
import main.g24.chord.Node;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.rmi.RemoteException;

public class SocketMessage {

    // <PROTOCOL> <SENDER_ID> <SENDER_IP> <SENDER_PORT> <FILEHASH> <AN_INTEGER>

    public final Type type;
    public final int sender_id, sender_port;
    public final String sender_ip;
    public final String filehash;

    private final int extra;

    public SocketMessage(Type type, int sender_id, String sender_ip, int sender_port, String filehash, int extra) {
        this.type = type;
        this.sender_id = sender_id;
        this.sender_port = sender_port;
        this.sender_ip = sender_ip;
        this.filehash = filehash;
        this.extra = extra;
    }

    public static SocketMessage from(INode node, Type type, String filehash, int extra) {
        try {
            return new SocketMessage(
                    type,
                    node.get_id(),
                    node.get_address().getHostName(),
                    node.get_port(),
                    filehash,
                    extra
            );
        } catch (RemoteException e) {
            e.printStackTrace();
            return null;
        }
    }

    public void send(SocketChannel socketChannel) throws IOException {
        String header = String.format("%s %d %s %d %s %d\r\n\r\n", type.name(), sender_id, sender_ip, sender_port, filehash, extra);
        ByteBuffer buffer = ByteBuffer.wrap(header.getBytes(StandardCharsets.UTF_8));
        socketChannel.write(buffer);
    }

    public static SocketMessage from(String message) {
        String[] args = message.split(" ");

        if (args.length < 6)
            return null;

        return new SocketMessage(
                Type.valueOf(args[0]), // type
                Integer.parseInt(args[1]), // sender id
                args[2], // sender ip
                Integer.parseInt(args[3]), // sender port
                args[4], // filehash
                Integer.parseInt(args[5]) // extra
        );
    }

    @Override
    public String toString() {
        return type + " " + sender_id + " " + filehash.substring(0, 6) + " " + extra;
    }
}
