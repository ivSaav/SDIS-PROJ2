package main.g24.message.handlers;

import main.g24.Chunk;
import main.g24.OldPeer;
import main.g24.SdisUtils;
import main.g24.message.Message;
import main.g24.message.MessageType;

import java.util.Collection;

public class DeleteHandler implements Handler {

    private final OldPeer peer;
    private final Message message;

    public DeleteHandler(OldPeer peer, Message message) {
        this.peer = peer;
        this.message = message;
    }

    @Override
    public void start() {
        String fileHash = message.fileId;
        if (peer.getStoredFiles().containsKey(fileHash)){

            // remove reclaimed chunks of this file as this is a message sent by the initiator peer
            // and the initiator peer doesn't store copies of it's initiated files
            peer.getReclaimedChunks().remove(fileHash);

            Collection<Chunk> chunks = peer.getStoredFiles().get(fileHash).getChunks();

            chunks.removeIf(chunk -> chunk.removeStorage(peer)); // remove chunk from fileHash List
            Chunk.removeFileDir(peer, fileHash);

            if (!(SdisUtils.isInitialVersion(peer.getVersion()) && SdisUtils.isInitialVersion(message.version))) {
                byte[] deletedMessage = Message.createMessage(peer.getVersion(), MessageType.DELETED, peer.getId(), message.fileId, message.chunkNo);
                peer.getControlChannel().multicast(deletedMessage);
            }

            if (chunks.isEmpty()) { // remove file entry from files hashmap
                peer.removeStoredFile(fileHash);
            }

            peer.setChangesFlag();
        }
    }
}
