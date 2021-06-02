package main.g24.socket.managers.dispatchers;

import main.g24.Peer;
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
import java.rmi.RemoteException;

public class RestoreDispatcher implements ISocketManagerDispatcher {

    private final Peer peer;
    private final int key;
    private int last_hop;
    private final String fileHash;
    private final Path path;
    private final GeneralMonitor monitor;

    public RestoreDispatcher(Peer peer, int key, int last_hop, String fileHash, Path path, GeneralMonitor monitor) {
        this.peer = peer;
        this.key = key;
        this.last_hop = last_hop;
        this.fileHash = fileHash;
        this.path = path;
        this.monitor = monitor;
    }

    private ISocketManager onFileRetrieval() {
       monitor.resolve("success");
        return null;
    }

    @Override
    public ISocketManager dispatch(ISocketMessage message, SelectionKey key) {
        return switch (message.get_type()) {
            case FILEHERE -> {
                FileHereMessage here = (FileHereMessage) message;
                if (here.file_at_id == last_hop) {
                    // File will be transfered
                    yield new ReceiveFileSocket(peer, here, path, this::onFileRetrieval);
                } else {
                    // File elsewhere
                    yield redirectGetFile(here);
                }
            }

            case ACK -> {
                AckMessage ack = (AckMessage) message;
                if (!ack.get_status()) {
                    // NACK
                    if (monitor != null)
                        monitor.resolve("failure");
                }
                yield null;
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

            ISocketManager restoreManager = new SocketManager(() -> {
                try {
                    message.send(socket);
                } catch (IOException e) {
                    e.printStackTrace();
                    return null;
                }

                return new SocketManager(this);
            });

            peer.getSelector().register(socket, restoreManager);
            return null;
        }
        catch (IOException ioe) {
            ioe.printStackTrace();
        }
        return null;
    }

    public static boolean initiateRestore(Peer peer, String fileHash, Path path, GeneralMonitor monitor) {
        try {
            GetFileMessage message = GetFileMessage.from(peer, fileHash);
            if (message == null)
                return false;

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
                    return new SocketManager(new RestoreDispatcher(peer, file_key, key_owner.get_id(), fileHash, path, monitor));
                } catch (RemoteException e) {
                    e.printStackTrace();
                    return null;
                }
            });

            peer.getSelector().register(socket, restoreManager);
            return true;
        } catch (IOException e) {
            e.printStackTrace();
        }

        return false;
    }
}
