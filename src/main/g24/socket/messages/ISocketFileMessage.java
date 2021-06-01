package main.g24.socket.messages;

public interface ISocketFileMessage extends ISocketMessage {
    String get_filehash();
    long get_size();
}
