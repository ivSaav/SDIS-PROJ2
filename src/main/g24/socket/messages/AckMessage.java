package main.g24.socket.messages;

import main.g24.chord.INode;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.rmi.RemoteException;

public class AckMessage implements ISocketMessage {
    // <PROTOCOL> <SENDER_ID> <STATUS>

    public final int sender_id, status;

    public AckMessage(int sender_id, int status) {
        this.sender_id = sender_id;
        this.status = status;
    }

    public static AckMessage from(INode node, int status) {
        try {
            return new AckMessage(
                    node.get_id(),
                    status
            );
        } catch (RemoteException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static AckMessage from(INode node, boolean status) {
        try {
            return new AckMessage(
                    node.get_id(),
                    status ? 1 : 0
            );
        } catch (RemoteException e) {
            e.printStackTrace();
            return null;
        }
    }

    public boolean get_status() {
        return status != 0;
    }

    @Override
    public Type get_type() {
        return Type.ACK;
    }

    public void send(SocketChannel socketChannel) throws IOException {
        String header = String.format("ACK %d %d\r\n\r\n", sender_id, status);
        send(socketChannel, header);
    }

    @Override
    public String toString() {
        return "ACK" + sender_id + " " + (status != 0 ? "SUCC" : "FAIL");
    }

    public static ISocketMessage from(String[] args) {
        if (args.length < 3)
            return null;

        return new AckMessage(
                Integer.parseInt(args[1]), // sender id
                Integer.parseInt(args[2]) // status
        );
    }
}
