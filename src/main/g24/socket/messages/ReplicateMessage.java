package main.g24.socket.messages;

import main.g24.chord.INode;

import java.rmi.RemoteException;

public class ReplicateMessage implements ISocketFileMessage {
    // <PROTOCOL> <SENDER_ID> <SENDER_IP> <SENDER_PORT> <FILEHASH> <FILE_SIZE> <REP_DEGREE>

    public final int sender_id, origin_id, origin_port, rep_degree;
    public final String origin_ip;
    public final String filehash;
    public final long file_size;

    public ReplicateMessage(int sender_id, int origin_id, String origin_ip, int origin_port, String filehash, long file_size, int rep_degree) {
        this.sender_id = sender_id;
        this.origin_id = origin_id;
        this.origin_ip = origin_ip;
        this.origin_port = origin_port;
        this.filehash = filehash;
        this.file_size = file_size;
        this.rep_degree = rep_degree;
    }

    public static ReplicateMessage from(INode node, String filehash, long file_size, int rep_degree) {
        try {
            return new ReplicateMessage(
                    node.get_id(),
                    node.get_id(),
                    node.get_address().getHostName(),
                    node.get_port(),
                    filehash,
                    file_size,
                    rep_degree
            );
        } catch (RemoteException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static ReplicateMessage from(INode node, ReplicateMessage message, boolean decrease) {
        try {
            return new ReplicateMessage(
                    node.get_id(),
                    message.origin_id,
                    message.origin_ip,
                    message.origin_port,
                    message.filehash,
                    message.file_size,
                    decrease ? message.rep_degree - 1 : message.rep_degree
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
        return String.format("REPLICATE %d %d %s %d %s %d %d", sender_id, origin_id, origin_ip, origin_port, filehash, file_size, rep_degree);
    }

    @Override
    public String toString() {
        return "REPLICATE " + sender_id + " " + origin_id + " " + filehash.substring(0, 6) + " " + file_size + " " + rep_degree;
    }

    public static ISocketMessage from(String[] args) {
        if (args.length < 8)
            return null;

        return new ReplicateMessage(
                Integer.parseInt(args[1]), // sender id
                Integer.parseInt(args[2]), // origin id
                args[3], // origin ip
                Integer.parseInt(args[4]), // origin port
                args[5], // filehash
                Long.parseLong(args[6]), // file_size
                Integer.parseInt(args[7]) // rep_degree
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
