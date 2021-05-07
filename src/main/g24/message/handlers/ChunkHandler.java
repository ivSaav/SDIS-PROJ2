package main.g24.message.handlers;

import main.g24.FileDetails;
import main.g24.Peer;
import main.g24.message.ChunkMonitor;
import main.g24.message.Message;

public class ChunkHandler implements Handler {

    private final Peer peer;
    private final Message message;

    public ChunkHandler(Peer peer, Message message) {
        this.peer = peer;
        this.message = message;
    }

    @Override
    public void start() {
        FileDetails fileDetails = peer.getFileDetails(message.fileId);
        ChunkMonitor cm;
        if (fileDetails == null || (cm = fileDetails.getMonitor(message.chunkNo)) == null)
            return;

        cm.markSolved(message.body, message.version);
    }
}
