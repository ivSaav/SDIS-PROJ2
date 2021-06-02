package main.g24.socket.messages;

public class ReplicatedMessage implements ISocketMessage {

    // <PROTOCOL> <SENDER_ID> <FILEHASH>

    public final int sender_id;
    public final String filehash;

    public ReplicatedMessage(int sender_id, String filehash) {
        this.sender_id = sender_id;
        this.filehash = filehash;
    }

    @Override
    public Type get_type() {
        return Type.REPLICATED;
    }

    @Override
    public String gen_header() {
        return String.format("REPLICATED %d %s", sender_id, filehash);
    }

    @Override
    public String toString() {
        return "REPLICATED " + sender_id + " " + filehash.substring(0, 6);
    }

    public static ISocketMessage from(String[] args) {
        if (args.length < 3)
            return null;

        return new ReplicatedMessage(
                Integer.parseInt(args[1]), // sender id
                args[2] // filehash
        );
    }

}
