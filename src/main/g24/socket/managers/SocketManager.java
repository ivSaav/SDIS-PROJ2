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
import java.util.function.Supplier;


import static main.g24.Peer.BLOCK_SIZE;

public class SocketManager implements ISocketManager {

    public static final String MESSAGE_TERMINATOR = "\r\n\r\n";

    private final ByteBuffer buffer = ByteBuffer.allocate(BLOCK_SIZE);

    private ISocketManagerDispatcher dispatcher = null;
    private final Supplier<ISocketManager> connect;

    public SocketManager(Peer peer) {
        this.dispatcher = new DefaultSocketManagerDispatcher(peer);
        this.connect = null;
    }

    public SocketManager(ISocketManagerDispatcher dispatcher) {
        this.dispatcher = dispatcher;
        this.connect = null;
    }

    public SocketManager(Supplier<ISocketManager> connect) {
        this.dispatcher = null;
        this.connect = connect;
    }

    public void onSelect(SelectionKey key) {
        if (this.connect == null) {
            if (key.isReadable()) {
                readPurpose(key);
            }
        } else {
            if (key.isConnectable()) {
                try {
                    if (((SocketChannel) key.channel()).finishConnect()) {
                        // Connected, move on to the next step
                        ISocketManager nextManager = connect.get();
                        if (nextManager == null) {
                            key.channel().close();
                        } else {
                            ISocketManager.transitionTo(key, nextManager);
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    try {
                        key.channel().close();
                    } catch (IOException ioException) {
                        ioException.printStackTrace();
                    }
                }
            }
        }
    }

    @Override
    public void init() {}

    @Override
    public int interestOps() {
        return connect == null ? SelectionKey.OP_READ : SelectionKey.OP_CONNECT;
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

            ISocketManager.transitionTo(key, iSocketManager);

            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }
}
