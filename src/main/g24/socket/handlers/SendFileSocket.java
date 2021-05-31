package main.g24.socket.handlers;

import main.g24.Peer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class SendFileSocket implements ISocketManager {

    private FileChannel fileChannel;
    private ByteBuffer buffer;

    private final Path filepath;

    public SendFileSocket(Path filepath) {
        this.filepath = filepath;
    }

    @Override
    public void init() {
        try {
            fileChannel = FileChannel.open(filepath, StandardOpenOption.READ);
            buffer = ByteBuffer.allocate(Peer.BLOCK_SIZE);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public int interestOps() {
        return SelectionKey.OP_WRITE;
    }

    @Override
    public void onSelect(SelectionKey key) {
        if (key.isWritable()) {
            sendFile(key);
        }
    }

    private void sendFile(SelectionKey key) {
        SocketChannel channel = (SocketChannel) key.channel();

        try {
            int n, wrote_n;
            do {
                if ((n = fileChannel.read(buffer)) < 0 && buffer.position() == 0) {
                    // End connection
//                    System.out.println("Closed");
                    fileChannel.close();
                    key.channel().close();
                    break;
                }
//                System.out.println("Read from file " + n);
                buffer.flip();

                wrote_n = channel.write(buffer);
//                System.out.println("Wrote to socket " + wrote_n);
                buffer.compact();
            } while (wrote_n > 0);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
