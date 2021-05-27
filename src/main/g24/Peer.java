package main.g24;

import main.g24.chord.INode;
import main.g24.chord.Node;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.ServerSocketChannel;
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
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Peer extends Node implements ClientPeerProtocol {

    private final int id;
    private static final int BLOCK_SIZE = 1024;


    // PEER
    public Peer(int id, InetAddress addr, int port) {
        super(id, addr, port);
        this.id = id;
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

            // connect to successor peer
            INode succ = this.get_successor();
            SocketChannel socket = SocketChannel.open();
            if (succ.alive()) {
                System.out.println("ADDR " + succ.get_address().toString() + " PORT " + succ.get_port());
                socket.connect(new InetSocketAddress(succ.get_address(), succ.get_port()));
                succ.storeFile(this.addr, this.port, id, path, size);

            }
            else {
                // TODO find another successor
            }

            int n;
            while ((n = fileChannel.read(buffer)) > 0) {

                System.out.println("READ " + n);
                // flip before writing
                buffer.flip();
                // write buffer to channel
                while (buffer.hasRemaining())
                    socket.write(buffer);
                buffer.clear();
            }

            socket.close();
            fileChannel.close();
        }
        catch (IOException ioe) {
            ioe.printStackTrace();
        }

        INode succ = this.get_successor();
        INode pred = this.get_predecessor();
        System.out.println("SUCC " + (succ != null ? succ.get_id() : null) + "\n PRED " + (pred != null ? pred.get_id() : null));
        return "failure";
    }

    @Override
    public void storeFile(InetAddress initAddr, int initPort, int initId, String fileHash, long fileSize) {

        try {
            Path filePath = Paths.get("./out/" + fileHash);
            Files.createDirectories(filePath.getParent());
            Files.createFile(filePath);

            FileChannel fileChannel = FileChannel.open(filePath, StandardOpenOption.WRITE, StandardOpenOption.APPEND);

            ByteBuffer buffer = ByteBuffer.allocate(BLOCK_SIZE);

            ServerSocketChannel serverSocket = ServerSocketChannel.open();
            serverSocket.socket().bind(new InetSocketAddress(this.port));
            SocketChannel client = serverSocket.accept();

//            SocketChannel socket = SocketChannel.open();
//            socket.connect(new InetSocketAddress(this.addr, this.port));

            int n;
            while (fileSize > 0 && (n = client.read(buffer)) > 0) {
                fileSize -= n;

                System.out.println("Read " + n);

                // flip before writing
                buffer.flip();
                fileChannel.write(buffer);

                buffer.clear();
            }
            serverSocket.close();
            client.close();
//            socket.close();
            fileChannel.close();
        }
        catch (IOException ioe) {
            ioe.printStackTrace();
        }
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


    public String getPeerPath() {
        return "peers" + File.separator + "p" + this.id + File.separator;
    }

    public String getStoragePath(String fileHash) {
        return getPeerPath() + "storage" + File.separator + fileHash + File.separator;
    }

    public String getRestorePath(String fileName) {
        File f = new File(fileName);
        return getPeerPath() + "restored" + File.separator + f.getName();
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

        System.out.println("REG");
        Registry registry = LocateRegistry.getRegistry();
        try {
            String[] active = registry.list();
            ClientPeerProtocol stub = (ClientPeerProtocol) UnicastRemoteObject.exportObject(peer,0);

            //Bind the remote object's stub in the registry
            registry.bind(service_ap, stub); //register peer object with the name in args[0]

            // look for other peers in the ring
            ClientPeerProtocol root = peer;
            if (active.length > 0) {
                root = (ClientPeerProtocol) registry.lookup(active[0]);
                // join existing ring
                peer.join((INode) root);
            }
            else {
                System.out.println("I'm first");
                peer.create();
            }

        } catch (AlreadyBoundException e) {
            System.out.println("[R] Object already bound! Rebinding...");
            registry.rebind(service_ap, peer);
        } catch (ConnectException e) {
            System.out.println("[R] Could not connect to RMI!");
            e.printStackTrace();
        } catch (NotBoundException e) {
            e.printStackTrace();
        }
        System.out.println("[#] Peer " + peer.id + " ready");

        // chord stabilization protocol
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            try {
                peer.stabilize();
                peer.fix_fingers();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, 1, 2, TimeUnit.SECONDS);
    }
}
