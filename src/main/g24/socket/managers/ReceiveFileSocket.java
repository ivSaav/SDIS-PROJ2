package main.g24.socket.managers;

import main.g24.Peer;
import main.g24.socket.messages.AckMessage;
import main.g24.socket.messages.ISocketFileMessage;

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

    private final ISocketFileMessage message;
    private final Peer peer;
    private FileChannel fileChannel;
    private ByteBuffer buffer;

    private final ISocketManager afterTransferSocketManager;
    private final boolean echoStatus;

    private long file_remaining;

    private ReceiveFileSocket(Peer peer, ISocketFileMessage message, ISocketManager afterTransferSocketManager, boolean echoStatus) {
        this.peer = peer;
        this.message = message;
        this.afterTransferSocketManager = afterTransferSocketManager;
        this.echoStatus = echoStatus;

        this.file_remaining = message.get_size();
    }

    public ReceiveFileSocket(Peer peer, ISocketFileMessage message, ISocketManager afterTransferSocketManager) {
        this(peer, message, afterTransferSocketManager, false);
    }

    public ReceiveFileSocket(Peer peer, ISocketFileMessage message, boolean echoStatus) {
        this(peer, message, null, echoStatus);
    }

    public ReceiveFileSocket(Peer peer, ISocketFileMessage message) {
        this(peer, message, null, false);
    }

    @Override
    public void onSelect(SelectionKey key) {
        if (key.isReadable()) {
            temp(key);
        }
    }

    @Override
    public void init() throws IOException {
        // open out file
        Path path = Paths.get(peer.getStoragePath(message.get_filehash()));
        Files.createDirectories(path.getParent());
        Files.deleteIfExists(path);
        Files.createFile(path);
        fileChannel = FileChannel.open(path, StandardOpenOption.WRITE);

        buffer = ByteBuffer.allocate(Peer.BLOCK_SIZE);
    }

    @Override
    public int interestOps() {
        return SelectionKey.OP_READ;
    }

    @SuppressWarnings("MagicConstant")
    private void temp(SelectionKey key) {
        SocketChannel client = (SocketChannel) key.channel();

        int n;
        try {
            // read from tcp channel
            while ((n = client.read(buffer)) > 0) {

//                System.out.println("Read from socket " + n);
                file_remaining -= n;

                // flip before writing
                buffer.flip();

                // write to output file
                fileChannel.write(buffer);
                buffer.compact();
            }

//            System.out.println("Out of cycle: Read " + n);

            if (file_remaining <= 0) {

                if (echoStatus) {
                    echoStatus(client, true);
                }

                if (afterTransferSocketManager == null) {
                    client.close();
                } else {
                    afterTransferSocketManager.init();
                    key.interestOps(afterTransferSocketManager.interestOps());
                    key.attach(afterTransferSocketManager);
                }

                return;
            }

            if (n < 0) {
                fileChannel.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void echoStatus(SocketChannel client, boolean status) throws IOException {
        AckMessage ack = new AckMessage(peer.get_id(), status);
        ack.send(client);
    }
}
