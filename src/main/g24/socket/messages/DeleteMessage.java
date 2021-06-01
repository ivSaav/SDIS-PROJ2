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
    public String get_filehash() {
        return filehash;
    }

    @Override
    public long get_size()  { return -1; }
}
