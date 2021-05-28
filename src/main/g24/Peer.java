package main.g24;

import main.g24.chord.INode;
import main.g24.chord.Node;

import java.io.File;
import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.rmi.AlreadyBoundException;
import java.rmi.ConnectException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Peer extends Node implements ClientPeerProtocol {

    public static final int BLOCK_SIZE = 1024 * 128;

    private final int id;
    private final SocketHandler selector;

    private final Map<String, String> filenameHashes; // filename --> fileHash



    // PEER
    public Peer(int id, InetAddress addr, int port) {
        super(id, addr, port);
        this.id = id;
        this.selector = new SocketHandler(this);
        this.filenameHashes = new HashMap<>();
    }

    private void init(String service_ap) throws RemoteException {

        // TODO delete any storage from this peer at startup

        Registry registry = LocateRegistry.getRegistry();

        try {
            String[] active = registry.list();
            ClientPeerProtocol stub = (ClientPeerProtocol) UnicastRemoteObject.exportObject(this,0);

            //Bind the remote object's stub in the registry
            registry.bind(service_ap, stub); //register peer object with the name in args[0]

            // look for other peers in the ring
            ClientPeerProtocol root;
            if (active.length > 0) {
                root = (ClientPeerProtocol) registry.lookup(active[0]);
                // join existing ring
                this.join((INode) root);
            }
            else {
                System.out.println("Creating ring");
                this.create();
            }

        } catch (AlreadyBoundException e) {
            System.out.println("[R] Object already bound! Rebinding...");
            registry.rebind(service_ap, this);
        } catch (ConnectException e) {
            System.out.println("[R] Could not connect to RMI!");
            e.printStackTrace();
        } catch (NotBoundException e) {
            e.printStackTrace();
        }

        // chord stabilization protocol
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            try {
                this.stabilize();
                this.fix_fingers();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, 1, 2, TimeUnit.SECONDS);

        ExecutorService tcpService = Executors.newSingleThreadExecutor();
        tcpService.execute(selector);

        System.out.println("[#] Peer " + this.id + " ready");
    }

    // ---------------------------------
    //    PEER INTERFACE OPERATIONS
    // ---------------------------------
    @Override
    public String backup(String path, int repDegree) throws RemoteException {

        try {
            Path filePath = Paths.get(path);
            FileChannel fileChannel = FileChannel.open(filePath, StandardOpenOption.READ);
            long size = Files.size(filePath);

            ByteBuffer buffer = ByteBuffer.allocate(BLOCK_SIZE);

            String fileHash = SdisUtils.createFileHash(path, id);
            this.filenameHashes.put(path, fileHash);
            if (fileHash == null)
                return "failure";

            List<SocketChannel> successors = new ArrayList<>();
            INode nextNode = this;
            for (int i = 0; i < repDegree && i < CHORD_BITS; i++) {
                 nextNode = this.find_successor(nextNode.get_id() + 1);

                 // TODO verify if nextNode is live

                // notify successor node
                nextNode.storeFile(this.id, nextNode.getStoragePath(fileHash) , size);

                SocketChannel socket = SocketChannel.open();
                socket.connect(new InetSocketAddress(nextNode.get_address(), nextNode.get_port()));
                successors.add(socket);
            }

            int n;
            while ((n = fileChannel.read(buffer)) > 0) {
                // flip before writing
                buffer.flip();

                // TODO review (possible file corruption)
                for (SocketChannel socketChannel : successors) {
                    // write buffer to channel
                    while (buffer.hasRemaining())
                        socketChannel.write(buffer);

                    buffer.rewind();
                }

                buffer.clear();
            }

            for (SocketChannel socketChannel : successors)
                socketChannel.close();

            fileChannel.close();
            return "success";
        }
        catch (IOException ioe) {
            ioe.printStackTrace();
        }

        return "failure";
    }

    @Override
    public void storeFile(int initId, String fileHash, long fileSize) {
        this.selector.prepareReadOperation(fileHash);
    }

    @Override
    public String delete(String file) throws RemoteException {
        return null;
    }

    @Override
    public String reclaim(int new_capacity) throws RemoteException {
        return null;
    }

    @Override
    public String restore(String file) throws RemoteException {
        return null;
    }

    @Override
    public String state() throws RemoteException {
        return null;
    }


    public static void main(String[] args) throws IOException {

        if (args.length < 3) {
            System.out.println("usage: Peer <id> <remote_object_name> <addr:port>");
            throw new IllegalArgumentException("Invalid usage");
        }

        int id = Integer.parseInt(args[0]);
        String service_ap = args[1];

        String[] host = args[2].split(":");
        InetAddress addr = InetAddress.getLocalHost(); //InetAddress.getByName(host[0]);
        int port = Integer.parseInt(host[1]);

        Peer peer = new Peer(id, addr, port);
        peer.init(service_ap);

    }
}
