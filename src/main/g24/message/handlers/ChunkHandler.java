package main.g24.message.handlers;

import main.g24.OldFileDetails;
import main.g24.OldPeer;
import main.g24.message.ChunkMonitor;
import main.g24.message.Message;

public class ChunkHandler implements Handler {

    private final OldPeer peer;
    private final Message message;

    public ChunkHandler(OldPeer peer, Message message) {
        this.peer = peer;
        this.message = message;
    }

    @Override
    public void start() {
        OldFileDetails fileDetails = peer.getFileDetails(message.fileId);
        ChunkMonitor cm;
        if (fileDetails == null || (cm = fileDetails.getMonitor(message.chunkNo)) == null)
            return;

        cm.markSolved(message.body, message.version);
    }
}
