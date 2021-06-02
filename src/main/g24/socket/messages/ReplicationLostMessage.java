package main.g24.socket.messages;

public class ReplicationLostMessage implements ISocketMessage {
    // <PROTOCOL> <SENDER_ID> <ID_LOST> <FILEHASH>

    public final int sender_id, peer_lost;
    public final String filehash;

    public ReplicationLostMessage(int sender_id, int peer_lost, String filehash) {
        this.sender_id = sender_id;
        this.peer_lost = peer_lost;
        this.filehash = filehash;
    }

    @Override
    public Type get_type() {
        return Type.REPLLOST;
    }

    @Override
    public String gen_header() {
        return String.format("REPLLOST %d %d %s", sender_id, peer_lost, filehash);
    }

    @Override
    public String toString() {
        return "REPLLOST " + sender_id + " " + peer_lost + " " + filehash.substring(0, 6);
    }

    public static ISocketMessage from(String[] args) {
        if (args.length < 4)
            return null;

        return new ReplicationLostMessage(
                Integer.parseInt(args[1]), // sender id
                Integer.parseInt(args[2]), // peer that lost
                args[3] // filehash
        );
    }
}
