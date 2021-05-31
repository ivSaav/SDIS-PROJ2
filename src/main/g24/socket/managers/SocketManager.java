package main.g24.socket.managers;

import main.g24.Peer;
import main.g24.socket.managers.dispatchers.DefaultSocketManagerDispatcher;
import main.g24.socket.managers.dispatchers.ISocketManagerDispatcher;
import main.g24.socket.messages.ISocketMessage;
import main.g24.socket.messages.SocketMessageFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;

import static main.g24.Peer.BLOCK_SIZE;

public class SocketManager implements ISocketManager {

    public static final String MESSAGE_TERMINATOR = "\r\n\r\n";

    private final ByteBuffer buffer = ByteBuffer.allocate(BLOCK_SIZE);

    private final ISocketManagerDispatcher dispatcher;

    public SocketManager(Peer peer) {
        this.dispatcher = new DefaultSocketManagerDispatcher(peer);
    }

    public SocketManager(ISocketManagerDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    public void onSelect(SelectionKey key) {
        if (key.isReadable()) {
            readPurpose(key);
        }
    }

    @Override
    public void init() {}

    @Override
    public int interestOps() {
        return SelectionKey.OP_READ;
    }

    private void readPurpose(SelectionKey key) {
        SocketChannel client = (SocketChannel) key.channel();

        try {
            int n;
            // read from tcp channel
            while ((n = client.read(buffer)) > 0) {

                String s = new String(buffer.array(), StandardCharsets.US_ASCII);

                if (s.contains(MESSAGE_TERMINATOR)) {
                    if (!parseRequest(key, s)) {
                        // Unkown request, closing connection
                        key.channel().close();
                    }
                    return;
                }
            }

            if (n < 0) {
                key.channel().close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @SuppressWarnings("MagicConstant")
    private boolean parseRequest(SelectionKey key, String request) {

        String[] params = request.split(MESSAGE_TERMINATOR);

        try {
            ISocketMessage message = SocketMessageFactory.from(params[0]);
            if (message == null)
                return false;

            System.out.println("[<] " + message);

            ISocketManager iSocketManager = this.dispatcher.dispatch(message, key);
            if (iSocketManager == null)
                return false;

            iSocketManager.init();
            key.interestOps(iSocketManager.interestOps());
            key.attach(iSocketManager);

            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }
}
