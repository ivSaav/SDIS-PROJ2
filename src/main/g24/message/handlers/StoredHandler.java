package main.g24.message.handlers;

import main.g24.Peer;
import main.g24.message.Message;

public class StoredHandler implements Handler {

    private final Peer peer;
    private final Message message;

    public StoredHandler(Peer peer, Message message) {
        this.peer = peer;
        this.message = message;
    }

    @Override
    public void start() {
        peer.addPerceivedReplication(message.senderId, message.fileId, message.chunkNo);
        // marks a chunk as solved if the desired replication degree has been reached
        //only for initiator peer
        peer.resolveInitiatedChunk(message.fileId, message.chunkNo);
        peer.setChangesFlag();
    }
}
