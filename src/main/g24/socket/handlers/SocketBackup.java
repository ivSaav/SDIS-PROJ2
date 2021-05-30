package main.g24.socket.handlers;

import main.g24.Peer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

public class SocketBackup implements ISocketManager {

    private Peer peer;
    private boolean valid = true;

    private FileChannel fileChannel;

    public void init(String filehash) {
        try {
            Path filePath = Paths.get("shrug.png");
            fileChannel = FileChannel.open(filePath, StandardOpenOption.READ);
            long size = Files.size(filePath);

            ByteBuffer buffer = ByteBuffer.allocate(Peer.BLOCK_SIZE);
        } catch (IOException e) {
            e.printStackTrace();
            valid = false;
        }
    }

    @Override
    public void onSelect(SelectionKey key) {
        if (key.isWritable()) {
            sendFile(key);
        }
    }

    private void sendFile(SelectionKey key) {
        ByteBuffer buffer = ByteBuffer.allocate(Peer.BLOCK_SIZE);

        try {
            int n;
            while ((n = fileChannel.read(buffer)) > 0) {
                // flip before writing
                buffer.flip();

                // write buffer to channel
                while (buffer.hasRemaining())
                    ((SocketChannel) key.channel()).write(buffer);

                buffer.clear();
            }

            fileChannel.close();
            key.channel().close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void addPeer(Peer peer) {
        this.peer = peer;
    }
}
