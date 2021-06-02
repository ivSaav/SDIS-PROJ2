package main.g24.socket.managers.dispatchers;

import main.g24.Peer;
import main.g24.SdisUtils;
import main.g24.chord.INode;
import main.g24.chord.Node;
import main.g24.monitors.GeneralMonitor;
import main.g24.socket.managers.ISocketManager;
import main.g24.socket.managers.ReceiveFileSocket;
import main.g24.socket.managers.SocketManager;
import main.g24.socket.messages.*;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.rmi.RemoteException;

public class ReplicateDispatcher implements ISocketManagerDispatcher {
    private final Peer peer;
    private final int key_owner;
    private final String fileHash;
    private final Path path;

    public ReplicateDispatcher(Peer peer, int key_owner, String fileHash, Path path) {
        this.peer = peer;
        this.key_owner = key_owner;
        this.fileHash = fileHash;
        this.path = path;
    }

    private ISocketManager onFileRetrieval() {
        try {
            INode node = peer.find_successor(key_owner);
            ISocketMessage m = new ReplicatedMessage(peer.get_id(), fileHash);

            SocketChannel socket = SocketChannel.open();
            socket.configureBlocking(false);
            socket.connect(node.get_socket_address());

            ISocketManager manager = new SocketManager(() -> {
                try {
                    m.send(socket);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return null;
            });
            peer.getSelector().register(socket, manager);

        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }

    @Override
    public ISocketManager dispatch(ISocketMessage message, SelectionKey key) {
        return switch (message.get_type()) {
            case FILEHERE -> {
                FileHereMessage filehere = (FileHereMessage) message;
                if (filehere.file_at_id == key_owner) {
                    // File will be transfered
                    yield new ReceiveFileSocket(peer, filehere, this::onFileRetrieval);
                } else {
                    // File elsewhere
                    System.err.println("NOT YET IMPLEMENTED");
                    yield null;
                }
            }

            default -> null;
        };
    }

    public static ISocketManager initiateReplicate(Peer peer, String fileHash) {
        try {
            GetFileMessage message = GetFileMessage.from(peer, fileHash);
            if (message == null)
                return null;

            int file_key = Node.chordID(fileHash);
            INode key_owner = peer.find_successor(file_key);

            SocketChannel socket = SocketChannel.open();
            socket.configureBlocking(false);
            socket.connect(key_owner.get_socket_address());

            ISocketManager restoreManager = new SocketManager(() -> {
                try {
                    message.send(socket);
                } catch (IOException e) {
                    e.printStackTrace();
                    return null;
                }

                try {
                    Path filepath = Paths.get(peer.getStoragePath(fileHash));
                    return new SocketManager(new ReplicateDispatcher(peer, key_owner.get_id(), fileHash, filepath));
                } catch (RemoteException e) {
                    e.printStackTrace();
                    return null;
                }
            });

            peer.getSelector().register(socket, restoreManager);
            return null;
        }
        catch (IOException ioe) {
            ioe.printStackTrace();
        }
        return null;
    }
}
