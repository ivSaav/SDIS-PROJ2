package main.g24.socket.managers;

import main.g24.Peer;
import main.g24.monitors.GeneralMonitor;
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
import java.util.function.Supplier;

public class ReceiveFileSocket implements ISocketManager {

    private final ISocketFileMessage message;
    private final Peer peer;
    private final Path destination;
    private FileChannel fileChannel;
    private ByteBuffer buffer;

    private final GeneralMonitor resolve;
    private final Supplier<ISocketManager> onTransferEnd;

    private long file_remaining;

    private ReceiveFileSocket(Peer peer, ISocketFileMessage message, Path destination, Supplier<ISocketManager> onTransferEnd, GeneralMonitor resolve) {
        this.peer = peer;
        this.message = message;
        this.destination = destination;
        this.onTransferEnd = onTransferEnd;
        this.resolve = resolve;

        this.file_remaining = message.get_size();
    }

    public ReceiveFileSocket(Peer peer, ISocketFileMessage message, Supplier<ISocketManager> onTransferEnd) {
        this(peer, message, null, onTransferEnd, null);
    }

    public ReceiveFileSocket(Peer peer, ISocketFileMessage message, Path destination, GeneralMonitor resolve) {
        this(peer, message, destination, null, resolve);
    }

    public ReceiveFileSocket(Peer peer, ISocketFileMessage message) {
        this(peer, message, null, null, null);
    }

    @Override
    public void onSelect(SelectionKey key) {
        if (key.isReadable()) {
            readFile(key);
        }
    }

    @Override
    public void init() throws IOException {
        // open out file
        Path path = destination != null ? destination : Paths.get(peer.getStoragePath(message.get_filehash()));
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
    private void readFile(SelectionKey key) {
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

                fileChannel.close();

                if (resolve != null)
                    resolve.resolve("success");

                ISocketManager futureManager;
                if (onTransferEnd == null || (futureManager = onTransferEnd.get()) == null)
                    client.close();
                else
                    ISocketManager.transitionTo(key, futureManager);

                return;
            }

            if (n < 0) {
                fileChannel.close();
                if (resolve != null)
                    resolve.resolve("failure");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
