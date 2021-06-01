package main.g24.socket.messages;

public class AckMessage implements ISocketMessage {
    // <PROTOCOL> <SENDER_ID> <STATUS>

    public final int sender_id, status;

    public AckMessage(int sender_id, int status) {
        this.sender_id = sender_id;
        this.status = status;
    }

    public AckMessage(int sender_id, boolean status) {
        this(sender_id, status ? 1 : 0);
    }

    public boolean get_status() {
        return status != 0;
    }

    @Override
    public Type get_type() {
        return Type.ACK;
    }

    @Override
    public String gen_header() {
        return String.format("ACK %d %d\r\n\r\n", sender_id, status);
    }

    @Override
    public String toString() {
        return "ACK " + sender_id + " " + (status != 0 ? "SUCC" : "FAIL");
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
