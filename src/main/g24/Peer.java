package main.g24;

import main.g24.chord.INode;
import main.g24.chord.Node;
import main.g24.socket.ServerSocketHandler;
import main.g24.socket.managers.SendFileSocket;
import main.g24.socket.messages.BackupMessage;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.rmi.AlreadyBoundException;
import java.rmi.ConnectException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.*;

public class Peer extends Node implements ClientPeerProtocol {

    public static final int BLOCK_SIZE = 1024 * 128;

    private final ServerSocketHandler selector;

    private final Set<String> stored;
    private final Map<Integer, Map<String, FileDetails>> fileKeys;

    private long maxSpace; // max space in KBytes (1000 bytes)
    private long diskUsage; // disk usage in KBytes (1000 bytes)

    // PEER
    public Peer(InetAddress addr, int port) {
        super(addr, port);

        this.maxSpace = 1000000; // 1GB space in the beginning
        this.diskUsage = 0;
        this.selector = new ServerSocketHandler(this);

        this.stored = ConcurrentHashMap.newKeySet();
        this.fileKeys = new ConcurrentHashMap<>();
    }

    public void increaseDiskUsage(long size) {
        this.diskUsage += size;
    }

    public void decreaseDiskUsage(long size) {
        this.diskUsage -= size;
    }

    public boolean hasCapacity(long size) {
        return (this.maxSpace - this.diskUsage) >= size;
    }

    private void init(String service_ap) throws RemoteException {

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
        }, 500, 1000, TimeUnit.MILLISECONDS);

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
            long size = Files.size(filePath);

            ByteBuffer buffer = ByteBuffer.allocate(BLOCK_SIZE);

            String fileHash = SdisUtils.createFileHash(path, id);
//            this.filenameHashes.put(path, fileHash);
//            FileDetails fileDetails = new FileDetails(this.id, fileHash, size, repDegree);
//            this.initedFiles.put(fileHash, fileDetails);
            if (fileHash == null)
                return "failure";

            BackupMessage message = BackupMessage.from(this, fileHash, repDegree, size);
            if (message == null)
                return "failure";

            int file_key = chordID(fileHash);
            INode key_owner = find_successor(file_key);

            SocketChannel socket = SocketChannel.open();
            socket.connect(key_owner.get_socket_address());

            message.send(socket);

            SendFileSocket sf = new SendFileSocket(filePath);
            sf.init();
            selector.register(socket, sf);

            return "success";
        }
        catch (IOException ioe) {
            ioe.printStackTrace();
        }

        return "failure";
    }

//    @Override
//    public void removeFile(String fileHash) throws RemoteException {
//
//        String filePath = this.getStoragePath(fileHash);
//        Path path = Paths.get(filePath);
//        try {
//            Files.deleteIfExists(path);
//            // no stored files (remove subdirectories)
//            if (this.storedFiles.isEmpty()) {
//                Files.deleteIfExists(path.getParent());
//                Files.deleteIfExists(path.getParent().getParent());
//            }
//
//            this.decreaseDiskUsage(this.storedFiles.get(fileHash).getSize());
//            this.storedFiles.remove(fileHash);
//        }
//        catch (IOException e) {
//            e.printStackTrace();
//        }
//    }

//    @Override
//    public void handleFileRemoval(int peerID, String fileHash) throws RemoteException {
//        FileDetails fileDetails = this.initedFiles.get(fileHash);
//        fileDetails.removeCopy(peerID);
//
//        int lastCopy = fileDetails.getLastCopy();
//
//        if (lastCopy < 0) {
//            System.out.println("[!] Couldn't maintain desired replication degree");
//            return;
//        }
//
//        // get first successor of last peer that stored this file
//        INode nextPeer = this.find_successor(lastCopy);
//        int newCopy = nextPeer.copyStoredFile(fileHash);
//
//        if (newCopy > 0) {
//            fileDetails.addCopy(newCopy);
//        }
//        else {
//            System.out.println("[!] Couldn't maintain desired replication degree");
//        }
//    }

//    @Override
//    public int copyStoredFile(String fileHash) throws RemoteException {
//        FileDetails fileDetails = this.storedFiles.get(fileHash);
//        // fetch first successor
//        INode firstSucc = this.find_successor(this.id +1);
//
//        System.out.println(this.id);
//
//        if (firstSucc == null)
//            return -1;
//        if (firstSucc.get_id() == fileDetails.getInitID())
//            return -1;
//
//        // start connection
//        firstSucc.storeFile(fileDetails.getInitID(), fileHash, firstSucc.getStoragePath(fileHash), fileDetails.getSize());
//
//        SocketChannel socket = null;
//        try {
//            socket = SocketChannel.open();
//            socket.connect(new InetSocketAddress(firstSucc.get_address(), firstSucc.get_port()));
//        } catch (IOException e) {
//            e.printStackTrace();
//            return -1;
//        }
//
//        try {
//            Path path = Paths.get(this.getStoragePath(fileHash));
//            FileChannel fileChannel = FileChannel.open(path);
//
//            ByteBuffer buffer = ByteBuffer.allocate(BLOCK_SIZE);
//
//            System.out.println("[!] Copying file to: " + firstSucc.get_id());
//
//            int n;
//            while ((n = fileChannel.read(buffer)) > 0) {
//                // flip before writing
//                buffer.flip();
//
//                // write buffer to channel
//                while (buffer.hasRemaining())
//                    socket.write(buffer);
//
//                buffer.clear();
//            }
//
//        } catch (IOException e) {
//            e.printStackTrace();
//            return -1;
//        }
//
//        return firstSucc.get_id();
//        return 0;
//    }

    @Override
    public String delete(String file) throws RemoteException {

//        if (this.filenameHashes.containsKey(file)) {
//            String fileHash = this.filenameHashes.get(file);
//
//            FileDetails details = this.initedFiles.get(fileHash);
//
//            for (int id : details.getFileCopies()) {
//                INode succ = this.find_successor(id);
//                succ.removeFile(fileHash);
//            }
//
//            return "success";
//        }
        return "failure";
    }

    @Override
    public String reclaim(int new_capacity) throws RemoteException {
//        this.maxSpace = new_capacity;
//
//        ConcurrentLinkedQueue<FileDetails> stored = new ConcurrentLinkedQueue<>(this.storedFiles.values());
//
//        // selecting files
//        while (this.diskUsage > maxSpace) {
//            // extract head
//            FileDetails file = stored.remove();
//
//            INode init = this.find_successor(file.getInitID());
//            if (init.alive()) {
//                // remove file from storage
//                this.removeFile(file.getHash());
//
//                // notify initiator peer
//                init.handleFileRemoval(this.id, file.getHash());
//            }
//            else {
//                // TODO find next live peer
//                // initiator peer wasn't alive (file wasn't removed)
//                stored.add(file);
//            }
//        }
//
//        return "success";
        return "failure";
    }

    @Override
    public String restore(String file) throws RemoteException {

        SocketChannel socket = null;
        try {
            socket = SocketChannel.open();
            socket.connect(new InetSocketAddress(this.addr, 19));
            byte[] b = "BACKUP hasharoo\r\n\r\n".getBytes();
            socket.write(ByteBuffer.wrap(b));
        } catch (IOException e) {
            e.printStackTrace();
        }

        return "failure";
    }

    @Override
    public String state() throws RemoteException {
//        StringBuilder ret = new StringBuilder("\n========== INFO ==========\n");
//
//        ret.append(String.format("peerID: %d \nmax capacity: %d KB\nused: %d KB\n", this.id, this.maxSpace, this.diskUsage));
//
//        if (!this.initedFiles.isEmpty()) {
//            ret.append("\n========== INITIATED ===========\n");
//            for (Map.Entry<String, String> entry : this.filenameHashes.entrySet()) {
//                String filename = entry.getKey();
//                String hash = entry.getValue();
//                FileDetails fd = this.initedFiles.get(hash);
//                ret.append(
//                        String.format("filename: %s \tid: %s \tdesired replication: %d \tcopies: %s\n", filename, fd.getHash(), fd.getDesiredReplication(), fd.getFileCopies())
//                );
//            }
//        }
//
//        if (!this.storedFiles.isEmpty()) {
//            ret.append("\n========== STORED ==========\n");
//            for (FileDetails file : this.storedFiles.values())
//                ret.append("init: " + file.getInitID()).append(file.getHash()).append(" - ").append(file.getSize() + " KB").append("\n");
//        }
//
//        return ret.toString();
        return "";
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

        Peer peer = new Peer(addr, port);
        peer.init(service_ap);
    }
}
