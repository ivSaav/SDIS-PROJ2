package main.g24.socket;

import main.g24.Peer;
import main.g24.socket.managers.ISocketManager;
import main.g24.socket.managers.SocketManager;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.*;
import java.util.Iterator;
import java.util.Set;


public class ServerSocketHandler implements Runnable {

    private final Peer peer;
    private Selector selector;

    public Selector getSelector() {
        return selector;
    }

    public ServerSocketHandler(Peer peer) {
        this.peer = peer;
    }


    // source: https://www.baeldung.com/java-nio-selector
    @Override
    public void run() {
        try {

            selector = Selector.open();
            ServerSocketChannel serverSocket = ServerSocketChannel.open();
            serverSocket.socket().bind(new InetSocketAddress(peer.get_address(), peer.get_port()));
            serverSocket.configureBlocking(false);

            serverSocket.register(selector, SelectionKey.OP_ACCEPT);

            while (true) {

//                System.out.println("[#] Blocking for select");

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

    @SuppressWarnings("MagicConstant")
    private void register(ServerSocketChannel socketChannel) throws IOException {
        SocketChannel client = socketChannel.accept();
        client.configureBlocking(false);
        ISocketManager iSocketManager = new SocketManager(peer);
        client.register(selector, iSocketManager.interestOps(), iSocketManager);
    }

    @SuppressWarnings("MagicConstant")
    public void register(SocketChannel socket, ISocketManager manager) throws IOException {
        socket.configureBlocking(false);
        socket.register(selector, manager.interestOps(), manager);

        selector.wakeup();
    }

}
