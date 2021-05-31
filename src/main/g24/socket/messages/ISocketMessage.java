package main.g24.socket.messages;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;

public interface ISocketMessage {
    String toString();
    Type get_type();

    String gen_header();

    default void send(SocketChannel socketChannel) throws IOException {
        System.out.println("\t[>] " + this);

        ByteBuffer buffer = ByteBuffer.wrap((this.gen_header() + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        socketChannel.write(buffer);
    }
}
