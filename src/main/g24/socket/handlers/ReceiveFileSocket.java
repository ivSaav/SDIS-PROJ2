package main.g24.socket.handlers;

import main.g24.Peer;
import main.g24.socket.handlers.ISocketManager;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

public class ReceiveFileSocket implements ISocketManager {

    private Peer peer;
    private String fileHash;
    private FileChannel fileChannel;
    private ByteBuffer buffer;

    public ReceiveFileSocket(Peer peer, String fileHash) {
        this.peer = peer;
        this.fileHash = fileHash;
    }

    public void initStreams() throws IOException {
        // open out file
        Path path = Paths.get(peer.getStoragePath(fileHash));
        Files.createDirectories(path.getParent());
        Files.deleteIfExists(path);
        Files.createFile(path);
        fileChannel = FileChannel.open(path, StandardOpenOption.WRITE);

        buffer = ByteBuffer.allocate(Peer.BLOCK_SIZE);
    }

    @Override
    public void onSelect(SelectionKey key) {
        if (key.isReadable()) {
            temp(key);
        }
    }

    @Override
    public void init() {}

    @Override
    public int interestOps() {
        return SelectionKey.OP_READ;
    }

    private void temp(SelectionKey key) {
        SocketChannel client = (SocketChannel) key.channel();

        int n;
        try {
            // read from tcp channel
            while ((n = client.read(buffer)) > 0) {

//                System.out.println("Read from socket " + n);

                // flip before writing
                buffer.flip();

                // write to output file
                fileChannel.write(buffer);
                buffer.compact();
            }

//            System.out.println("Out of cycle: Read " + n);

            if (n < 0) {
//                System.out.println("Closed");
                client.close();
                fileChannel.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
