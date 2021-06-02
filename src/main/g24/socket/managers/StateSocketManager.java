package main.g24.socket.managers;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Map;

import main.g24.FileDetails;
import main.g24.Peer;

public class StateSocketManager implements ISocketManager {


    private final Peer peer;
    private ByteBuffer buffer;
    private int interestOp;


    public StateSocketManager(Peer peer, int interestOp) {
        this.peer = peer;
        this.interestOp = interestOp;
    }

    public StateSocketManager(Peer peer) {
        this.peer = peer;
        this.interestOp = SelectionKey.OP_READ;
    }

    @Override
    public void onSelect(SelectionKey key) {
        if (key.isReadable()) {
            receiveState(key);
        }
        else if (key.isWritable()) {
            sendState(key);
        }
    }

    @Override
    public void init() {
        if (this.interestOp == SelectionKey.OP_WRITE) {

            ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
            Map<Integer, Map<String, FileDetails>> fk = peer.getFileKeys();

            ObjectOutputStream objStream;
            try {
                objStream = new ObjectOutputStream(byteStream);
                objStream.writeObject(fk);

            } catch (IOException e) {
                e.printStackTrace();
            }

            byte[] obj = byteStream.toByteArray();
            buffer = ByteBuffer.wrap(obj);
        }
        else {
            buffer = ByteBuffer.allocate(Peer.BLOCK_SIZE);
        }
    }

    @Override
    public int interestOps() {
        return this.interestOp;
    }

    @SuppressWarnings("MagicConstant")
    private void receiveState(SelectionKey key) {
        SocketChannel client = (SocketChannel) key.channel();

        int n;
        try {
            // read from tcp channel
            while ((n = client.read(buffer)) > 0) { }

            if (n < 0) {
                buffer.flip();

                @SuppressWarnings("unchecked")
                Map<Integer, Map<String, FileDetails>> fileKeys = (Map<Integer, Map<String, FileDetails>>) byteToObj(buffer.array());
                peer.setPredecessorKeys(fileKeys);
                client.close();
            }

        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            System.err.println("[X] Couldn't deserialize object.");
        }
    }

    @SuppressWarnings("MagicConstant")
    private void sendState(SelectionKey key) {
        SocketChannel client = (SocketChannel) key.channel();

        int n;
        try {
            // read from tcp channel
            while ((n = client.write(buffer)) > 0) {}

            // wrote everithing (close)
            if (!buffer.hasRemaining()) {
                client.close();
            }

            if (n < 0) {
                System.err.println("[X] Socket closed before sending.");
                client.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static Object byteToObj(byte[] bytes) throws IOException, ClassNotFoundException {
        ByteArrayInputStream byteStream = new ByteArrayInputStream(bytes);
        ObjectInputStream objStream = new ObjectInputStream(byteStream);
    
        return objStream.readObject();
    }


}
