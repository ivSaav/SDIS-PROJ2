package main.g24.socket;

import main.g24.Peer;
import main.g24.socket.handlers.ISocketManager;
import main.g24.socket.handlers.SocketBackup;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;

import static main.g24.Peer.BLOCK_SIZE;

public class SocketManager {

    public static final String MESSAGE_TERMINATOR = "\r\n\r\n";

    private Peer peer;

    private ISocketManager socketManager = null;
    ByteBuffer buffer = ByteBuffer.allocate(BLOCK_SIZE);

    public SocketManager(Peer peer) {
        this.peer = peer;
    }

    public void onSelect(SelectionKey key) {

        if (socketManager != null) {
            socketManager.onSelect(key);
            return;
        }

        if (key.isReadable()) {
            readPurpose(key);
        }
    }

    private void readPurpose(SelectionKey key) {
        SocketChannel client = (SocketChannel) key.channel();

        try {
            int n;
            // read from tcp channel
            while ((n = client.read(buffer)) > 0) {

                String s = new String(buffer.array(), StandardCharsets.US_ASCII);
                if (s.contains(MESSAGE_TERMINATOR)) {
                    parseRequest(s);
                    if (socketManager == null || !socketManager.validate()) {
                        // Unkown request, closing connection
                        key.channel().close();
                    }
                    return;
                }
            }

            if (n < 0) {
                System.out.println("Client socket closed.");
                key.channel().close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void parseRequest(String request) {
        String[] params = request.split(" ");

        if (params.length < 1)
            return;

        this.socketManager = switch (params[0]) {
            case "BACKUP" -> {
                SocketBackup sb = new SocketBackup();
                sb.init(params[1]);
                yield sb;
            }
            case "RESTORE" -> null;
            case "REPLICATE" -> null;
            default -> null;
        };

        if (socketManager != null)
            socketManager.addPeer(peer);
    }
}
