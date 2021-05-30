package main.g24.socket;

import main.g24.Peer;

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

                selector.select();

                Set<SelectionKey> selectedKeys = selector.selectedKeys();
                Iterator<SelectionKey> iter = selectedKeys.iterator();

                while (iter.hasNext()) {

                    SelectionKey key = iter.next();

                    if (key.isAcceptable()) {
                        System.out.println("Souto");
                        register(selector, serverSocket);
                    }

                    if (key.isWritable() || key.isReadable()) {
                        ((SocketManager) key.attachment()).onSelect(key);
                    }


                    iter.remove();
                }
            }

        }
        catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    private void register(Selector selector, ServerSocketChannel socketChannel) throws IOException {
        SocketChannel client = socketChannel.accept();
        client.configureBlocking(false);
        client.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE, new SocketManager(peer));
    }

    private void readAndSave(ByteBuffer buffer, SelectionKey key) throws IOException {
        SocketChannel client = (SocketChannel) key.channel();

        // open out file
        Path path = Paths.get(filepath);
        FileChannel fileChannel = FileChannel.open(path, StandardOpenOption.WRITE, StandardOpenOption.APPEND);

        int n;
        // read from tcp channel
        while ((n = client.read(buffer)) > 0) {
            // flip before writing
            buffer.flip();

            // write to output file
            fileChannel.write(buffer);

            buffer.clear();
        }

        if (n < 0) {
            System.out.println("Client socket closed.");
            key.channel().close();
            fileChannel.close();
        }
    }

}
