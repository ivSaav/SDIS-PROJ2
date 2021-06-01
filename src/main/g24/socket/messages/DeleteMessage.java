package main.g24.socket.messages;

public abstract class DeleteMessage implements ISocketFileMessage {
    // <PROTOCOL> <SENDER_ID> <SENDER_IP> <SENDER_PORT> <FILEHASH>

    public final int sender_id, sender_port;
    public final String sender_ip;
    public final String filehash;

    public DeleteMessage(int sender_id, String sender_ip, int sender_port, String filehash) {
        this.sender_id = sender_id;
        this.sender_port = sender_port;
        this.sender_ip = sender_ip;
        this.filehash = filehash;
    }

    @Override
    public Type get_type() {
        return null;
    }

    @Override
    public String gen_header() {
        return "INVALID DELETE MESSAGE";
    }

    @Override
    public String toString() {
        return "INVALID DELETE MESSAGE";
    }

    @Override
    public String get_filehash() {
        return filehash;
    }

    @Override
    public long get_size() {
        return -1;
    }

    @Override
    public int get_rep_degree() {
        return -1;
    }


}
