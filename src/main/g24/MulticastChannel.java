package main.g24;

import main.g24.message.MessageThread;

import java.io.IOException;
import java.net.*;
import java.util.concurrent.ThreadPoolExecutor;

public class MulticastChannel extends Thread {

    private static final int BUFFER_MAX_SIZE = 64500;

    private final Peer peer;
    private final String group;
    private final int port;
    private final ThreadPoolExecutor poolExecutor;

    public MulticastChannel(Peer peer, String address, int port, ThreadPoolExecutor poolExecutor) {
        this.peer = peer;
        this.group = address;
        this.port = port;
        this.poolExecutor = poolExecutor;
    }

    @Override
    public void run() {

        try {
            MulticastSocket socket = new MulticastSocket(this.port);
            InetAddress address = InetAddress.getByName(this.group);
            socket.joinGroup(address);

            while (true) {
                byte[] buffer = new byte[BUFFER_MAX_SIZE];

                if (this.isInterrupted()) {
                    poolExecutor.shutdown();
                    return;
                }

                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                MessageThread mt = new MessageThread(peer, packet);
                this.poolExecutor.submit(mt);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void multicast(byte[] message) {
        try {
            DatagramSocket socket = new DatagramSocket();
            InetAddress address = InetAddress.getByName(this.group);

            DatagramPacket packet = new DatagramPacket(message, message.length, address, this.port);
            socket.send(packet);
            socket.close();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

}
