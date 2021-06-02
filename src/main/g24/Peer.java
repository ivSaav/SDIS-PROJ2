package main.g24;

import main.g24.chord.INode;
import main.g24.chord.Node;
import main.g24.monitors.GeneralMonitor;
import main.g24.socket.ServerSocketHandler;
import main.g24.socket.managers.ISocketManager;
import main.g24.socket.managers.SendFileSocket;
import main.g24.socket.managers.SocketManager;
import main.g24.socket.managers.dispatchers.AckNackDispatcher;
import main.g24.socket.managers.dispatchers.RestoreDispatcher;
import main.g24.socket.messages.BackupMessage;
import main.g24.socket.messages.DeleteCopyMessage;
import main.g24.socket.messages.DeleteKeyMessage;
import main.g24.socket.messages.DeleteMessage;
import main.g24.socket.messages.RemovedMessage;

import java.io.File;
import java.io.IOException;
import java.net.*;
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

    private final Map<String, GeneralMonitor> monitors;

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

        this.monitors = new ConcurrentHashMap<>();
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

            String fileHash = SdisUtils.createFileHash(path, id);
            if (fileHash == null)
                return "failure";

            System.out.println("[-] Backing up file [" + fileHash.substring(0, 6) + "] with key [" + chordID(fileHash) + "]");

            BackupMessage message = BackupMessage.from(this, fileHash, repDegree, size);
            if (message == null)
                return "failure";

            int file_key = chordID(fileHash);
            INode key_owner = find_successor(file_key);

            SocketChannel socket = SocketChannel.open();
            socket.configureBlocking(false);
            socket.connect(key_owner.get_socket_address());

            ISocketManager resolutionSocketManager = new SocketManager(AckNackDispatcher.resolveFilehash(this, fileHash));
            SendFileSocket sf = new SendFileSocket(filePath, resolutionSocketManager);
            ISocketManager iSocketManager = new SocketManager(() -> {
                try {
                    message.send(socket);
                } catch (IOException e) {
                    e.printStackTrace();
                    return null;
                }
                return sf;
            });
            selector.register(socket, iSocketManager);

            // Wait for success or failure :)
            GeneralMonitor monitor = new GeneralMonitor();
            monitors.put(fileHash, monitor);

            if (monitor.await_resolution(5000))
                return monitor.get_message();

            return "timeout";
        }
        catch (IOException ioe) {
            ioe.printStackTrace();
        }
        return "failure";
    }


    public void addFileToKey(String filehash, long size, int rep_degree, int stored_at_id) {
        int key = chordID(filehash);
        Map<String, FileDetails> nodes = this.fileKeys.computeIfAbsent(key, k -> new ConcurrentHashMap<>());
        FileDetails fd = nodes.get(filehash);

        if (fd != null) {
            System.err.println("FILE SOMEHOW EXISTS");
        } else {
            fd = new FileDetails(filehash, size, rep_degree);
            fd.addCopy(stored_at_id);
            nodes.put(filehash, fd);
        }
    }

    public FileDetails removeFileFromKey(String filehash) {
        return removeFileFromKey(filehash, chordID(filehash));
    }

    public FileDetails removeFileFromKey(String filehash, int key) {
        Map<String, FileDetails> nodes = this.fileKeys.get(key);
        return nodes == null ? null : nodes.remove(filehash);
    }

    public FileDetails removeTrackedCopy(String fileHash, int copyPeerID) {
        int fileKey = chordID(fileHash);
        Map<String, FileDetails> nodes = this.fileKeys.get(fileKey);
        if (nodes != null) {
            FileDetails fileDetails = nodes.get(fileHash);

            if (fileDetails != null) {
                fileDetails.removeCopy(copyPeerID);
                return fileDetails;
            }
        }
        return null;
    }

    public void addStoredFile(String filehash) {
        this.stored.add(filehash);
    }

    @Override
    public String delete(String file) throws RemoteException {

        String fileHash = SdisUtils.createFileHash(file, id);
        if (fileHash == null)
            return "failure";

        int fileKey = chordID(fileHash);

        System.out.println("[-] Deleting file [" + fileHash.substring(0, 6) + "] with key [" + fileKey + "]");

        INode respNode = this.find_successor(fileKey);
        if (!respNode.alive())
            return "failure";

        DeleteMessage message = DeleteKeyMessage.from(this, fileHash);
        if (message == null)
            return "failure";

        try {
            // send delete message to the responsible peer
            SocketChannel socket = SocketChannel.open();
            socket.configureBlocking(false);
            socket.connect(respNode.get_socket_address());

            ISocketManager resolutionSocketManager = new SocketManager(() -> {
                try {
                    message.send(socket);
                } catch (IOException e) {
                    e.printStackTrace();
                    return null;
                }

                return new SocketManager(AckNackDispatcher.resolveFilehash(this, fileHash));
            });

            selector.register(socket, resolutionSocketManager);

            // Wait for success or failure :)
            GeneralMonitor monitor = new GeneralMonitor();
            monitors.put(fileHash, monitor);

            if (monitor.await_resolution(5000))
                return monitor.get_message();

            return "timeout";
        }
        catch (IOException e) {
            e.printStackTrace();
            return "failure";
        }

    }

    public boolean isResponsible(String filehash) {
        return isResponsible(chordID(filehash));
    }

    public boolean isResponsible(int fileKey) {
        return this.fileKeys.containsKey(fileKey);
    }

    public void addResponsible(String filehash, int node_id) {
        int key = chordID(filehash);
        Map<String, FileDetails> files = fileKeys.get(key);
        if (files == null)
            return;

        FileDetails fd = files.get(filehash);
        if (fd == null)
            return;

        fd.addCopy(node_id);
    }

    public boolean storesFile(String filehash) {
        return this.stored.contains(filehash);
    }

    public Collection<Integer> findWhoStores(String filehash) {
        int key = chordID(filehash);
        Map<String, FileDetails> files = fileKeys.get(key);
        if (files == null)
            return null;

        FileDetails fd = files.get(filehash);
        return fd == null ? null : fd.getFileCopies();
    }

    /**
     * Removes a file from storage
     * Sends DELCOPY messages to peers who have this file
     * Only called on DELKEY requests
     * @param fileHash hash of file to delete copies of
     * @return
     */
    public boolean deleteFileCopies(String fileHash) {

        int fileKey = chordID(fileHash);
        boolean responsible = isResponsible(fileKey);

        // Not responsible for this file (Should not have been called)
        if (!responsible)
            return false;

        // remove file key holder map
        FileDetails fileDetails = removeFileFromKey(fileHash, fileKey);
        if (fileDetails == null)
            return false;

        if (this.stored.contains(fileHash)) {
            deleteFile(fileHash);
            // remove self from fileDetails
            // thus preventing messages to the same peer
            fileDetails.removeCopy(this.id); 
        }

        // sending DELCOPY messages to the other peers
        try {
            // send DELETE messages to copy holders
            for (int i : fileDetails.getFileCopies()) {
                INode succCopy = this.find_successor(i);

                if (!isAlive(succCopy))
                    continue;

                DeleteCopyMessage message = DeleteCopyMessage.from(this, fileHash);
                if (message == null)
                    continue;

                SocketChannel socket = SocketChannel.open();
                socket.connect(succCopy.get_socket_address());
                message.send(socket);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
        return true;
    }

    /**
     * Delete a file stored in this peer
     * @param fileHash file to be removed
     * @return
     */
    public boolean deleteFile(String fileHash) {

        Path path = Paths.get(this.getStoragePath(fileHash));

        try {
            long size = Files.size(path);
            Files.delete(path);
            this.decreaseDiskUsage(size);
            this.stored.remove(fileHash);

            // no stored files (remove subdirectories)
            if (this.stored.isEmpty()) {
                Files.deleteIfExists(path.getParent());
                Files.deleteIfExists(path.getParent().getParent());
            }

            return true;
        }
        catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public String reclaim(int new_capacity) throws RemoteException {

        // TODO might want to do this in a thread
        this.maxSpace = new_capacity;
        ConcurrentLinkedQueue<String> storedFiles = new ConcurrentLinkedQueue<>(this.stored);

        // selecting files
        while (this.diskUsage > maxSpace && !storedFiles.isEmpty()) {
            // extract head
            String fileHash = storedFiles.remove();

            int fileKey = chordID(fileHash);

            INode respNode = this.find_successor(fileKey);
            
            if (isAlive(respNode)) {

                RemovedMessage message = RemovedMessage.from(this, fileHash);
                if (message == null)
                    continue;

                ISocketManager resolutionSocketManager = new SocketManager(AckNackDispatcher.resolveFilehash(this, fileHash));

                try {
                    // connect to node responsible for this file
                    SocketChannel socket = SocketChannel.open();
                    socket.connect(respNode.get_socket_address());
                    message.send(socket);

                    resolutionSocketManager.init();
                    selector.register(socket, resolutionSocketManager);

                    // Wait for success or failure :)
                    GeneralMonitor monitor = new GeneralMonitor();
                    monitors.put(fileHash, monitor);

                    // only delete file if responsible peer acknowledges
                    if (monitor.await_resolution(1000)) {
                        this.deleteFile(fileHash);
                    }
//                    else // couldn't reach responsible peer (ignore)
//                        continue;
                }
                catch (IOException e) {
                    e.printStackTrace();
                    continue;
                }
            }
        }
       return this.diskUsage > this.maxSpace ? "failure" : "success";
    }

    @Override
    public String restore(String filename) throws RemoteException {
        Path path = Paths.get(getRecoverPath(filename));

        String fileHash = SdisUtils.createFileHash(filename, id);
        if (fileHash == null)
            return "failure";

        System.out.println("[-] Restoring file [" + fileHash.substring(0, 6) + "] with key [" + chordID(fileHash) + "]");

        GeneralMonitor monitor = new GeneralMonitor();
        monitors.put(fileHash, monitor);

        if (!RestoreDispatcher.initiateRestore(this, fileHash, path, monitor))
            return "failure";

        // Wait for success or failure :)
        if (monitor.await_resolution(5000))
            return monitor.get_message();

        return "timeout";
    }

    @Override
    public String state() throws RemoteException {
        StringBuilder ret = new StringBuilder("\n=========== INFO ===========\n");

        ret.append(String.format("peerID: %d \nmax capacity: %d KB\nused: %d KB\n", this.id, this.maxSpace, this.diskUsage));

        if (!this.fileKeys.isEmpty()) {
            ret.append("\n======= OWNED  KEYS ========\n");
            for (Map.Entry<Integer, Map<String, FileDetails>> entry : this.fileKeys.entrySet()) {
                ret.append(entry.getKey()).append("\n");

                Map<String, FileDetails> files = entry.getValue();

                for (FileDetails fd: files.values()) {
                    ret.append("\t").append(fd.getHash(), 0, 6).append("\n");
                    ret.append("\tStored ").append(fd.getFileCopies().size()).append(" times at: ");

                    for (int stored_id: fd.getFileCopies())
                        ret.append(stored_id).append(" ");
                    ret.append("\n");
                }
                ret.append("\n");
            }
        }

        if (!this.stored.isEmpty()) {
            ret.append("\n========== STORED ==========\n");
            int col = 1;
            for (String hash : this.stored) {
                if (col > 3) {
                    ret.append(hash, 0, 6).append("\n");
                    col = 1;
                } else {
                    ret.append(hash, 0, 6).append("   ");
                    col++;
                }
            }
        }

        return ret.toString();
    }

    public ServerSocketHandler getSelector() {
        return selector;
    }

    public GeneralMonitor getMonitor(String key) {
        return monitors.get(key);
    }

    public String getPeerPath() {
        return "peers" + File.separator + "p" + this.id + File.separator;
    }

    public String getStoragePath(String fileHash) {
        return getPeerPath() + "storage" + File.separator + fileHash;
    }

    public String getRecoverPath(String fileName) {
        Path path = Paths.get(fileName);
        return getPeerPath() + "recovery" + File.separator + path.getFileName();
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
