package main.g24.socket.messages;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;

public interface ISocketMessage {
    String toString();
    Type get_type();

    void send(SocketChannel socketChannel) throws IOException;

    default void send(SocketChannel socketChannel, String header) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(header.getBytes(StandardCharsets.UTF_8));
        socketChannel.write(buffer);
    }
}
