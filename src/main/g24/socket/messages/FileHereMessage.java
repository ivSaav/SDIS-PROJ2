package main.g24.socket.messages;

import main.g24.chord.INode;

import java.rmi.RemoteException;

public class FileHereMessage implements ISocketMessage, ISocketFileMessage {

    // <PROTOCOL> <SENDER_ID> <ID> <FILEHASH> <SIZE>

    public final int sender_id, file_at_id;
    public final String filehash;
    public final long size;

    public FileHereMessage(int sender_id, int file_at_id, String filehash, long size) {
        this.sender_id = sender_id;
        this.file_at_id = file_at_id;
        this.filehash = filehash;
        this.size = size;
    }

    public static FileHereMessage from(INode node, int file_at_id, String filehash, long size) {
        try {
            return new FileHereMessage(
                    node.get_id(),
                    file_at_id,
                    filehash,
                    size
            );
        } catch (RemoteException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public Type get_type() {
        return Type.FILEHERE;
    }

    @Override
    public String gen_header() {
        return String.format("FILEHERE %d %d %s %d", sender_id, file_at_id, filehash, size);
    }

    @Override
    public String toString() {
        return "FILEHERE " + sender_id + " " + filehash.substring(0, 6);
    }

    public static ISocketMessage from(String[] args) {
        if (args.length < 5)
            return null;

        return new FileHereMessage(
                Integer.parseInt(args[1]), // sender id
                Integer.parseInt(args[2]), // peer with file
                args[3], // filehash
                Long.parseLong(args[4]) // size
        );
    }

    @Override
    public String get_filehash() {
        return filehash;
    }

    @Override
    public long get_size() { return size; }

}
