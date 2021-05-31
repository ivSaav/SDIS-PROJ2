package main.g24.socket.handlers;

import main.g24.Peer;
import main.g24.socket.messages.SocketMessage;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;

import static main.g24.Peer.BLOCK_SIZE;

public class SocketManager implements ISocketManager {

    public static final String MESSAGE_TERMINATOR = "\r\n\r\n";

    private final Peer peer;
    private final ByteBuffer buffer = ByteBuffer.allocate(BLOCK_SIZE);

    public SocketManager(Peer peer) {
        this.peer = peer;
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
            SocketMessage message = SocketMessage.from(params[0]);
            if (message == null)
                return false;

            System.out.println("[-] " + message);

            ISocketManager iSocketManager = switch (message.type) {
                case BACKUP -> new ReceiveFileSocket(peer, message);
                case RESTORE -> null;
                case REPLICATE -> null;
                case DELETE -> null;
                default -> null;
            };

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
