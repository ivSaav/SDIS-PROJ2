package main.g24.message;

import main.g24.Peer;
import main.g24.SdisUtils;
import main.g24.message.handlers.Handler;
import main.g24.message.handlers.HandlerBuilder;

import java.net.DatagramPacket;

public class MessageThread extends Thread {

    protected final DatagramPacket packet;
    protected final Peer peer;
    protected Message message;
    protected Handler handler;

    public MessageThread(Peer peer, DatagramPacket packet) {
        this.peer = peer;
        this.packet = packet;
    }

    /**
     * Parses the packet it's responsible for
     * @return True if this packet does not belongs to the same peer
     */
    private boolean parsePacket() {
        this.message = Message.parse(packet.getData(), packet.getLength());
        return message.senderId != this.peer.getId();
    }

    @Override
    public void run() {
        if (!parsePacket())
            return;

        System.out.println("[" + peer.getId() + "] " + message);

        if (SdisUtils.isVersionOlder(peer.getVersion(), message.version))
            return;

        try {
            // Choose and build message
            handler = HandlerBuilder.build(peer, message);
            if (handler == null)
                return;
            handler.start();
        }
        catch (Exception e) {
            System.out.println("[!] Encountered an error in MessageThread of type " + message.type);
            e.printStackTrace();
        }
    }
}
