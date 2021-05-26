package main.g24.message.handlers;

import main.g24.Chunk;
import main.g24.OldPeer;
import main.g24.SdisUtils;
import main.g24.message.Message;
import main.g24.message.MessageType;

import java.util.Random;
import java.util.concurrent.TimeUnit;

public class PutchunkHandler implements Handler {

    private final OldPeer peer;
    private final Message message;

    public PutchunkHandler(OldPeer peer, Message message) {
        this.peer = peer;
        this.message = message;
    }

    @Override
    public void start() {
        if (peer.isInitiator(message.fileId)) // for PUTCHUNK messages sent because of the REMOVED PROTOCOL (ignore chunk)
            return;

        if (!peer.hasDiskSpace(message.body.length)) { // checking if there is enough disk space for this chunk
            System.out.println("[!] Not enough space for chunk " + message.chunkNo);
            return;
        }

        boolean alreadyStored = peer.hasStoredChunk(message.fileId, message.chunkNo);

        Chunk chunk = null;
        if (alreadyStored) {
            // mark as solved if the chunk has a monitor associated
            // a monitor is added when Receiving a REMOVED message and the desired replication degree isn't fulfilled
            peer.resolveRemovedChunk(message.fileId, message.chunkNo); // prevent PUTCHUNK message
        } else {
            chunk = new Chunk(message.fileId, message.chunkNo, message.body.length);
            peer.addStoredChunk(chunk, message.replicationDegree);
        }

        PutchunkHandler ph = this;
        Chunk finalChunk = chunk;
        peer.getScheduledPool().schedule(() -> {
            if (!SdisUtils.isInitialVersion(peer.getVersion())
                    && finalChunk != null
                    && finalChunk.getPerceivedReplication() >= peer.getFileReplication(finalChunk.getFilehash())) {
                // Enhancement for v2.0
                // Only store if replication degree was not hit yet
                peer.removeStoredChunk(message.fileId, message.chunkNo);
                return;
            }

            byte[] storedMessage = Message.createMessage(message.version, MessageType.STORED, peer.getId(), finalChunk.getFilehash(), finalChunk.getChunkNo());
            peer.getControlChannel().multicast(storedMessage);

            if (!alreadyStored) {
                finalChunk.store(peer, message.body);
                peer.setChangesFlag();

                // Edge case for version 2.0
                // If a drops bellow the desired replication degree a PUTCHUNK message is sent from a non initiator peer
                // If the peer of sends a chunk is a non initiator peer then that peer is also storing that chunk
                // Therefore a new replication needs to be added
                if (peer.isReclaimedChunk(message.fileId, message.chunkNo)) {
                    peer.addPerceivedReplication(message.senderId, message.fileId, message.chunkNo);
                }
            }
        }, new Random().nextInt(400), TimeUnit.MILLISECONDS);
    }

}
