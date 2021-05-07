package main.g24.message.handlers;

import main.g24.Chunk;
import main.g24.Peer;
import main.g24.message.ChunkMonitor;
import main.g24.message.Message;
import main.g24.message.MessageType;

public class RemovedHandler implements Handler {

    private final Peer peer;
    private final Message message;

    public RemovedHandler(Peer peer, Message message) {
        this.peer = peer;
        this.message = message;
    }

    @Override
    public void start() {
       this.updateLocalChunkReplication(message.senderId, message.fileId, message.chunkNo);
        // track reclaimed chunk
        // solves perceived replication problem after a PUTCHUNK message is received from a non initiator peer
       this.peer.addReclaimedChunk(message.fileId, message.chunkNo);
       peer.setChangesFlag();
    }

    public void updateLocalChunkReplication(int senderId, String fileHash, int chunkNo) { // for REMOVED messages

        Chunk chunk = this.peer.getFileChunk(fileHash, chunkNo);
        int desiredReplication = this.peer.getFileReplication(fileHash);
        if (chunk != null) { // decremented the requested chunk's replication degree
            chunk.removeReplication(senderId); // remove perceived chunk replication

            if (chunk.getPerceivedReplication() < desiredReplication && !peer.isInitiator(fileHash)) { //replication bellow desired level
                System.out.println("[!] Unfulfilled replication for chunkNo " + chunkNo);
                ChunkMonitor monitor = peer.getFileDetails(fileHash).addMonitor(chunkNo);

                // if a PUTCHUNK message is receive within 0 to 400 random milliseconds the chunk is resolved
                // and execution is stopped
                if (monitor.await_send())
                    return;

                // If no PUTCHUNK messages for this chunk have been received send one to the other peers
                byte[] body = chunk.retrieve(peer);
                byte[] message = Message.createMessage(this.peer.getVersion(), MessageType.PUTCHUNK,
                                                        this.peer.getId(), chunk.getFilehash(), chunk.getChunkNo(), desiredReplication, body);
                peer.getBackupChannel().multicast(message); // sending putchunk to other peers
            }
        }
    }
}
