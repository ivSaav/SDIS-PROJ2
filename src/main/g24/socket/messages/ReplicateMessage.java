package main.g24.socket.messages;

import main.g24.chord.INode;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.rmi.RemoteException;

public class ReplicateMessage implements ISocketMessage, ISocketFileMessage {
    // <PROTOCOL> <SENDER_ID> <SENDER_IP> <SENDER_PORT> <FILEHASH> <FILE_SIZE>

    public final int sender_id, sender_port;
    public final String sender_ip;
    public final String filehash;
    public final long file_size;

    public ReplicateMessage(int sender_id, String sender_ip, int sender_port, String filehash, long file_size) {
        this.sender_id = sender_id;
        this.sender_port = sender_port;
        this.sender_ip = sender_ip;
        this.filehash = filehash;
        this.file_size = file_size;
    }

    public static ReplicateMessage from(INode node, String filehash, long file_size) {
        try {
            return new ReplicateMessage(
                    node.get_id(),
                    node.get_address().getHostName(),
                    node.get_port(),
                    filehash,
                    file_size
            );
        } catch (RemoteException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public Type get_type() {
        return Type.REPLICATE;
    }

    @Override
    public String gen_header() {
        return String.format("REPLICATE %d %s %d %s %d", sender_id, sender_ip, sender_port, filehash, file_size);
    }

    @Override
    public String toString() {
        return "REPLICATE " + sender_id + " " + filehash.substring(0, 6) + " " + file_size;
    }

    public static ISocketMessage from(String[] args) {
        if (args.length < 5)
            return null;

        return new ReplicateMessage(
                Integer.parseInt(args[1]), // sender id
                args[2], // sender ip
                Integer.parseInt(args[3]), // sender port
                args[4], // filehash
                Long.parseLong(args[5]) // file_size
        );
    }

    @Override
    public String get_filehash() {
        return filehash;
    }

    @Override
    public long get_size() {
        return file_size;
    }

}
