package main.g24.message.handlers;

import main.g24.MulticastChannel;
import main.g24.OldPeer;
import main.g24.SdisUtils;
import main.g24.message.Message;
import main.g24.message.MessageType;

import java.util.Iterator;

public class InitHandler implements Handler {
    private final OldPeer peer;
    private final Message message;

    public InitHandler(OldPeer peer, Message message) {
        this.peer = peer;
        this.message = message;
    }

    @Override
    public void start() {
        if (SdisUtils.isInitialVersion(peer.getVersion()))
            return;

        Iterator<String> undeletedFiles = peer.getPeerUndeletedFiles(message.senderId);
        MulticastChannel channel = peer.getControlChannel();
        for (Iterator<String> it = undeletedFiles; it.hasNext(); ) {
            String s = it.next();

            byte[] delMessage = Message.createMessage(peer.getVersion(), MessageType.DELETE, message.senderId, message.fileId);
            channel.multicast(delMessage);
        }
    }
}
