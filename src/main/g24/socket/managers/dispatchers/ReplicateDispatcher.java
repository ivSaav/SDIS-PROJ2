package main.g24.socket.managers.dispatchers;

import main.g24.Peer;
import main.g24.chord.INode;
import main.g24.chord.Node;
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
    private final int key;
    private int last_hop;
    private final String fileHash;
    private final Path path;

    private long size;

    protected ReplicateDispatcher(Peer peer, int key, int last_hop, String fileHash, Path path) {
        this.peer = peer;
        this.key = key;
        this.last_hop = last_hop;
        this.fileHash = fileHash;
        this.path = path;
    }

    private ISocketManager onFileRetrieval() {
        peer.addStoredFile(fileHash, size);

        // Notify to increase the rep_degree
        try {
            INode node = peer.find_successor(key);
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
                this.size = filehere.size;
                if (filehere.file_at_id == last_hop) {
                    // File will be transfered
                    yield new ReceiveFileSocket(peer, filehere, this::onFileRetrieval);
                } else {
                    // File elsewhere
                    yield this.redirectGetFile(filehere);
                }
            }

            default -> null;
        };
    }

    private ISocketManager redirectGetFile(FileHereMessage fileHere) {
        try {
            GetFileMessage message = GetFileMessage.from(peer, fileHash);
            if (message == null)
                return null;

            INode file_owner = peer.find_successor(fileHere.file_at_id);
            this.last_hop = file_owner.get_id();

            SocketChannel socket = SocketChannel.open();
            socket.configureBlocking(false);
            socket.connect(file_owner.get_socket_address());

            ISocketManager replicateManager = new SocketManager(() -> {
                try {
                    message.send(socket);
                } catch (IOException e) {
                    e.printStackTrace();
                    return null;
                }

                return new SocketManager(this);
            });

            peer.getSelector().register(socket, replicateManager);
            return null;
        }
        catch (IOException ioe) {
            ioe.printStackTrace();
        }
        return null;
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

            ISocketManager replicateManager = new SocketManager(() -> {
                try {
                    message.send(socket);
                } catch (IOException e) {
                    e.printStackTrace();
                    return null;
                }

                try {
                    Path filepath = Paths.get(peer.getStoragePath(fileHash));
                    return new SocketManager(new ReplicateDispatcher(peer, file_key, key_owner.get_id(), fileHash, filepath));
                } catch (RemoteException e) {
                    e.printStackTrace();
                    return null;
                }
            });

            peer.getSelector().register(socket, replicateManager);
            return null;
        }
        catch (IOException ioe) {
            ioe.printStackTrace();
        }
        return null;
    }
}
