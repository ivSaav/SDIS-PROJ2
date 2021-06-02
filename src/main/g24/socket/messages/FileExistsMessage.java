package main.g24.socket.messages;

import main.g24.chord.INode;

import java.rmi.RemoteException;

public class FileExistsMessage implements ISocketMessage, ISocketFileMessage {

    // <PROTOCOL> <SENDER_ID> <FILEHASH>

    public final int sender_id;
    public final String filehash;

    public FileExistsMessage(int sender_id, String filehash) {
        this.sender_id = sender_id;
        this.filehash = filehash;
    }

    @Override
    public Type get_type() {
        return Type.FILEEXISTS;
    }

    @Override
    public String gen_header() {
        return String.format("FILEEXISTS %d %s", sender_id, filehash);
    }

    @Override
    public String toString() {
        return "FILEEXISTS " + sender_id + " " + filehash.substring(0, 6);
    }

    public static ISocketMessage from(String[] args) {
        if (args.length < 3)
            return null;

        return new FileExistsMessage(
                Integer.parseInt(args[1]), // sender id
                args[2] // filehash
        );
    }

    @Override
    public String get_filehash() {
        return filehash;
    }

    @Override
    public long get_size() {
        return -1;
    }
}
