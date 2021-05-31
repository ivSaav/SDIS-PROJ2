package main.g24.socket.messages;

import main.g24.chord.INode;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.rmi.RemoteException;

public class BackupMessage implements ISocketMessage, ISocketFileMessage {
    // <PROTOCOL> <SENDER_ID> <SENDER_IP> <SENDER_PORT> <FILEHASH> <REP_DEGREE> <FILE_SIZE>

    public final int sender_id, sender_port;
    public final String sender_ip;
    public final String filehash;
    public final int rep_degree;
    public final long file_size;

    public BackupMessage(int sender_id, String sender_ip, int sender_port, String filehash, int rep_degree, long file_size) {
        this.sender_id = sender_id;
        this.sender_port = sender_port;
        this.sender_ip = sender_ip;
        this.filehash = filehash;
        this.rep_degree = rep_degree;
        this.file_size = file_size;
    }

    public static BackupMessage from(INode node, String filehash, int rep_degree, long file_size) {
        try {
            return new BackupMessage(
                    node.get_id(),
                    node.get_address().getHostName(),
                    node.get_port(),
                    filehash,
                    rep_degree,
                    file_size
            );
        } catch (RemoteException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public Type get_type() {
        return Type.BACKUP;
    }

    @Override
    public String gen_header() {
        return String.format("BACKUP %d %s %d %s %d %d\r\n\r\n", sender_id, sender_ip, sender_port, filehash, rep_degree, file_size);
    }

    @Override
    public String toString() {
        return "BACKUP " + sender_id + " " + filehash.substring(0, 6) + " " + rep_degree + " " + file_size;
    }

    public static ISocketMessage from(String[] args) {
        if (args.length < 6)
            return null;

        return new BackupMessage(
                Integer.parseInt(args[1]), // sender id
                args[2], // sender ip
                Integer.parseInt(args[3]), // sender port
                args[4], // filehash
                Integer.parseInt(args[5]), // rep degree
                Integer.parseInt(args[6]) // file_size
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
