package main.g24.socket.managers.dispatchers;

import main.g24.Peer;
import main.g24.socket.managers.ISocketManager;
import main.g24.socket.managers.ReceiveFileSocket;
import main.g24.socket.managers.SendFileSocket;
import main.g24.socket.managers.SocketManager;
import main.g24.socket.messages.*;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.rmi.RemoteException;
import java.util.Collection;


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
                    BackupMessage fileMessage = (BackupMessage) message;
                    if (peer.hasCapacity(fileMessage.get_size())) {
                        peer.addFileToKey(fileMessage.get_filehash(), fileMessage.get_size(), fileMessage.get_rep_degree(), this.peer.get_id());
                        peer.addStoredFile(fileMessage.get_filehash());
                        yield new ReceiveFileSocket(peer, (ISocketFileMessage) message, () -> {
                            AckMessage ack = new AckMessage(peer.get_id(), true);
                            try {
                                ack.send((SocketChannel) key.channel());
                            } catch (IOException e) {
                                e.printStackTrace();
                            }

                            // Replication go bbbbrrrrr
                            ReplicateMessage repl = ReplicateMessage.from(peer, fileMessage.filehash, fileMessage.file_size, fileMessage.rep_degree);
                            try {
                                SocketChannel socket = SocketChannel.open();
                                socket.configureBlocking(false);
                                socket.connect(peer.get_successor().get_socket_address());

                                ISocketManager repl_manager = new SocketManager(() -> {
                                    try {
                                        repl.send(socket);
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                    return null;
                                });

                                peer.getSelector().register(socket, repl_manager);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            return null;
                        });
                    }

                    System.err.println("NOT YET IMPLEMENTED");
                    yield null;
                }

                case REPLICATE -> {
                    ReplicateMessage fileMessage = (ReplicateMessage) message;
                    AckMessage reply = new AckMessage(peer.get_id(), peer.hasCapacity(fileMessage.get_size()));
                    reply.send((SocketChannel) key.channel());
                    yield reply.get_status() ? new ReceiveFileSocket(peer, fileMessage) : null;
                }

                case DELKEY -> {
                    ISocketFileMessage deleteMessage = (ISocketFileMessage) message;
                    boolean status = peer.deleteFileCopies(deleteMessage.get_filehash());
                    AckMessage reply = new AckMessage(peer.get_id(), status);
                    reply.send((SocketChannel) key.channel());
                    yield null;
                }

                case DELCOPY -> { 
                    ISocketFileMessage deleteMessage = (ISocketFileMessage) message;
                    peer.deleteFile(deleteMessage.get_filehash());
                    yield null; // delete copy messages don't require Acknowledgements
                }

                case REMOVED -> {
                    RemovedMessage removedMessage = (RemovedMessage) message;

                    // remove tracked copy from reclaimed peer
                    peer.removeTrackedCopy(removedMessage.get_filehash(), removedMessage.sender_id);
                    // send acknowledgement to reclaimed peer
                    new AckMessage(peer.get_id(), true).send((SocketChannel) key.channel());
                    // TODO: Replicate if needed
                    yield null;
                }

                case GETFILE -> {
                    GetFileMessage fileMessage = (GetFileMessage) message;
                    ISocketMessage reply;
                    ISocketManager futureManager = null;
                    Collection<Integer> peers_storing;
                    if (peer.storesFile(fileMessage.filehash)) {
                        Path path = Paths.get(peer.getStoragePath(fileMessage.filehash));
                        reply = FileHereMessage.from(peer, peer.get_id(), fileMessage.filehash, Files.size(path));
                        futureManager = new SendFileSocket(path);
                    } else if ((peers_storing = peer.findWhoStores(fileMessage.filehash)) == null || peers_storing.isEmpty()) {
                        reply = new AckMessage(peer.get_id(), false);
                    } else {
                        int chosen_peer = peers_storing.stream().findAny().get();
                        reply = FileHereMessage.from(peer, chosen_peer, fileMessage.filehash, -1);
                    }
                    reply.send((SocketChannel) key.channel());
                    yield futureManager;
                }

                default -> null;
            };
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

}
