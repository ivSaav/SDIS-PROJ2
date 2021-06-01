package main.g24.socket.managers.dispatchers;

import main.g24.Peer;
import main.g24.monitors.GeneralMonitor;
import main.g24.socket.managers.ISocketManager;
import main.g24.socket.managers.ReceiveFileSocket;
import main.g24.socket.managers.SocketManager;
import main.g24.socket.messages.AckMessage;
import main.g24.socket.messages.ISocketFileMessage;
import main.g24.socket.messages.ISocketMessage;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

public class DefaultSocketManagerDispatcher implements ISocketManagerDispatcher {

    private final Peer peer;

    public DefaultSocketManagerDispatcher(Peer peer) {
        this.peer = peer;
    }

    @Override
    public ISocketManager dispatch(ISocketMessage message, SelectionKey key) {
        try {
            return switch (message.get_type()) {
                case BACKUP -> {
                    ISocketFileMessage fileMessage = (ISocketFileMessage) message;
                    if (peer.hasCapacity(fileMessage.get_size())) {
                        peer.addFileKey(fileMessage.get_filehash(), fileMessage.get_size(), fileMessage.get_rep_degree(), this.peer.get_id());
                        peer.addStoredFile(fileMessage.get_filehash());
                        yield new ReceiveFileSocket(peer, (ISocketFileMessage) message, true);
                    }

                    System.err.println("NOT YET IMPLEMENTED");
                    yield null;
                }

                case GETFILE -> null;

                case REPLICATE -> {
                    ISocketFileMessage fileMessage = (ISocketFileMessage) message;
                    AckMessage reply = new AckMessage(peer.get_id(), peer.hasCapacity(fileMessage.get_size()));
                    reply.send((SocketChannel) key.channel());
                    yield reply.get_status() ? new ReceiveFileSocket(peer, fileMessage) : null;
                }

                case DELETE -> {
                    ISocketFileMessage deleteMessage = (ISocketFileMessage) message;
                    boolean status = peer.deleteFileCopies(deleteMessage.get_filehash());
                    AckMessage reply = new AckMessage(peer.get_id(), status);
                    reply.send((SocketChannel) key.channel());
                    yield null;
                }

                default -> null;
            };
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

}
