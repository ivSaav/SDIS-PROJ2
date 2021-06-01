package main.g24.socket.managers.dispatchers;

import main.g24.Peer;
import main.g24.monitors.GeneralMonitor;
import main.g24.socket.managers.ISocketManager;
import main.g24.socket.managers.ReceiveFileSocket;
import main.g24.socket.messages.AckMessage;
import main.g24.socket.messages.FileHereMessage;
import main.g24.socket.messages.ISocketMessage;

import java.nio.channels.SelectionKey;
import java.nio.file.Path;

public class RestoreDispatcher implements ISocketManagerDispatcher {

    private final Peer peer;
    private final int key_owner;
    private final String fileHash;
    private final Path path;
    private final GeneralMonitor monitor;

    public RestoreDispatcher(Peer peer, int key_owner, String fileHash, Path path, GeneralMonitor monitor) {
        this.peer = peer;
        this.key_owner = key_owner;
        this.fileHash = fileHash;
        this.path = path;
        this.monitor = monitor;
    }

    @Override
    public ISocketManager dispatch(ISocketMessage message, SelectionKey key) {
        return switch (message.get_type()) {
            case FILEHERE -> {
                FileHereMessage here = (FileHereMessage) message;
                if (here.file_at_id == key_owner) {
                    // File will be transfered
                    yield new ReceiveFileSocket(peer, here, path, monitor);
                } else {
                    // File elsewhere
                    System.err.println("NOT YET IMPLEMENTED");
                    yield null;
                }
            }

            case ACK -> {
                AckMessage ack = (AckMessage) message;
                if (!ack.get_status()) {
                    // NACK
                    GeneralMonitor monitor = peer.getMonitor(fileHash);
                    if (monitor != null)
                        monitor.resolve("failure");
                }
                yield null;
            }

            default -> null;
        };
    }
}
