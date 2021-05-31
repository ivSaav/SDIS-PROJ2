package main.g24.socket;

import main.g24.Peer;
import main.g24.socket.handlers.ISocketManager;
import main.g24.socket.handlers.SocketManager;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Iterator;
import java.util.Set;

import static main.g24.Peer.BLOCK_SIZE;

public class ServerSocketHandler implements Runnable {

    private final Peer peer;
    private Selector selector;
    private String filepath;
    private int operation;

    public Selector getSelector() {
        return selector;
    }

    public ServerSocketHandler(Peer peer) {
        this.peer = peer;
    }

    public void prepareWriteOperation(String filename) {
        this.filepath = filename;
        this.operation = SelectionKey.OP_WRITE;
    }

    public void prepareReadOperation(String filePath) {
        Path path = Paths.get(filePath);
        try {
            // create output file
            Files.createDirectories(path.getParent());
            Files.deleteIfExists(path);
            Files.createFile(path);
        }
        catch (IOException e) {
            e.printStackTrace();
        }

        this.filepath = filePath;
        this.operation = SelectionKey.OP_READ;
    }


    // source: https://www.baeldung.com/java-nio-selector
    @Override
    public void run() {
        try {

            ByteBuffer buffer = ByteBuffer.allocate(BLOCK_SIZE);

            selector = Selector.open();
            ServerSocketChannel serverSocket = ServerSocketChannel.open();
            serverSocket.socket().bind(new InetSocketAddress(peer.get_address(), peer.get_port()));
            serverSocket.configureBlocking(false);

            serverSocket.register(selector, SelectionKey.OP_ACCEPT);

            while (true) {

                System.out.println("[#] Blocking for select");

                selector.select();

                Set<SelectionKey> selectedKeys = selector.selectedKeys();
                Iterator<SelectionKey> iter = selectedKeys.iterator();

                while (iter.hasNext()) {

                    SelectionKey key = iter.next();

                    if (key.isAcceptable()) {
                        register(serverSocket);
                    }

                    if (key.attachment() != null && key.attachment() instanceof ISocketManager) {
                        ((ISocketManager) key.attachment()).onSelect(key);
                    }

                    iter.remove();

                }

            }

        }
        catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    private void register(ServerSocketChannel socketChannel) throws IOException {
        SocketChannel client = socketChannel.accept();
        client.configureBlocking(false);
        ISocketManager iSocketManager = new SocketManager(peer);
        client.register(selector, iSocketManager.interestOps(), iSocketManager);
    }

    public void register(SocketChannel socket, ISocketManager manager) throws IOException {
        socket.configureBlocking(false);
        socket.register(selector, manager.interestOps(), manager);

        selector.wakeup();
    }

}
