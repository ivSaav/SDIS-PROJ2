package main.g24;

import main.g24.message.ChunkMonitor;
import main.g24.message.Message;
import main.g24.message.MessageType;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.rmi.AlreadyBoundException;
import java.rmi.ConnectException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.*;


public class OldPeer implements ClientPeerProtocol, Serializable {

    private final int id;
    private final String version;
    private long maxSpace; // max space in Bytes
    private long diskUsage; // disk usage in Bytes
    private Map<String, String> filenameHashes; // filename --> fileHash
    private Map<String, FileDetails> initiatedFiles; // filehash --> FileDetail
    private Map<String, FileDetails> storedFiles; // filehash --> Chunks
    private Map<Integer, Set<String>> undeletedFiles; // Peer id --> Undeleted Chunks
    private Map<String, Set<Integer>> reclaimedChunks; // fileHash --> chunkNos

    private final MulticastChannel backupChannel;
    private final MulticastChannel restoreChannel;
    private final MulticastChannel controlChannel;

    private final ScheduledThreadPoolExecutor scheduledPool;

    private boolean hasChanges; // if current state is saved or not

    public OldPeer(String version, int id, String MC, String MDB, String MDR) {
        this.id = id;
        this.version = version;
        this.maxSpace = 2000000; // 2GB space in the beginning
        this.diskUsage = 0; // current used space
        this.filenameHashes = new ConcurrentHashMap<>();
        this.initiatedFiles = new ConcurrentHashMap<>();
        this.storedFiles = new ConcurrentHashMap<>();
        this.undeletedFiles = new ConcurrentHashMap<>();
        this.reclaimedChunks = new ConcurrentHashMap<>();

        String[] vals = MC.split(":"); // MC
        String mcAddr = vals[0];
        int mcPort = Integer.parseInt(vals[1]);

        vals = MDB.split(":");
        String mdbAddr = vals[0];
        int mdbPort = Integer.parseInt(vals[1]);

        vals = MDR.split(":");
        String mdrAddr = vals[0];
        int mdrPort = Integer.parseInt(vals[1]);

        scheduledPool = new ScheduledThreadPoolExecutor(5);

        ThreadPoolExecutor sparseChannelsPool = new ThreadPoolExecutor(2, 10, 2500, TimeUnit.MILLISECONDS, new SynchronousQueue<>());
        ThreadPoolExecutor controlChannelPool =  new ThreadPoolExecutor(10, 20, 5000, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());

        this.backupChannel = new MulticastChannel(this, mdbAddr, mdbPort, sparseChannelsPool);
        this.controlChannel = new MulticastChannel(this, mcAddr, mcPort, controlChannelPool);
        this.restoreChannel = new MulticastChannel(this, mdrAddr, mdrPort, sparseChannelsPool);

        this.hasChanges = false;
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

    @Override
    public String backup(String path, int repDegree) {
        if (this.filenameHashes.containsKey(path)) { // checking for a previous version of this file
            System.out.println("[-] Found previous version of: " + path);
            // removing all reclaimed chunks
            // because file is being deleted before reupload
            this.reclaimedChunks.remove(filenameHashes.get(path));

            this.delete(path); // removing previous version
        }

        String fileHash = SdisUtils.createFileHash(path, id);
        if (fileHash == null)
            return "failure";

        // In case the same file was BACKUP > DELETE > BACKUP again
        // If one of the other peers was offline during the deletion and inited
        // then in the future we would send the request to DELETE the file and the
        // newer backup would be lost in a tragic accident
        cleanUndeletedFileRecords(fileHash);

        try {
            File file = new File(path);
            int num_chunks = (int) Math.floor( (double) file.length() / (double) Definitions.CHUNK_SIZE) + 1;

            // Save filename and its generated hash
            FileDetails fd = new FileDetails(fileHash, file.length(), repDegree);
            this.filenameHashes.put(path, fileHash);
            this.initiatedFiles.put(fileHash, fd);

            FileInputStream fstream = new FileInputStream(file);
            byte[] chunk_data = new byte[Definitions.CHUNK_SIZE];
            int num_read, last_num_read = -1, chunkNo = 0;

            // Read file chunks and send them
            while ((num_read = fstream.read(chunk_data)) != -1) {
                // Send message
                byte[] message = num_read == Definitions.CHUNK_SIZE ?
                        Message.createMessage(this.version, MessageType.PUTCHUNK, this.id, fileHash, chunkNo, repDegree, chunk_data)
                        : Message.createMessage(this.version, MessageType.PUTCHUNK, this.id, fileHash, chunkNo, repDegree, Arrays.copyOfRange(chunk_data, 0, num_read));

                fd.addChunk(new Chunk(fileHash, chunkNo, num_read));

                backupChunk(fd, chunkNo, message, num_read);

                last_num_read = num_read;
                chunkNo++;
            }

            // Case of last chunk being size 0
            if (last_num_read == Definitions.CHUNK_SIZE) {
                fd.addChunk(new Chunk(fileHash, chunkNo, 0));
                byte[] message = Message.createMessage(this.version, MessageType.PUTCHUNK, this.id, fileHash, chunkNo, repDegree, new byte[] {});
                backupChunk(fd, chunkNo, message, 0);
            }

            fd.clearMonitors();

            fstream.close();
            System.out.println("[-] Created " + num_chunks + " chunks");
            this.hasChanges = true; // flag for peer backup
        }
        catch (IOException e) {
            System.out.println("[!] Couldn't store file: " + path);
            e.printStackTrace();
            return "failure";
        }
        return "success";
    }

    private void backupChunk(FileDetails fd, int chunkNo, byte[] message, int num_read) {
        int max_putchunk_tries = 5;
        int attempts = 0;
        while (attempts < max_putchunk_tries) {
            System.out.printf("[-] MDB: chunkNo %d ; size %d\n", chunkNo, num_read);
            backupChannel.multicast(message);
            ChunkMonitor monitor = fd.addMonitor(chunkNo);
            if (monitor.await_receive((long) (Math.pow(2, attempts) * 1000)))
                break;
            System.out.println("[!] Couldn't achieve desired replication. Resending...");
            attempts++;
        }
    }

    @Override
    public String delete(String file) {
        // checking if this peer was the initiator for file backup
        if (this.filenameHashes.containsKey(file)) {
            String hash = this.filenameHashes.get(file);

            // send delete message to other peers
            FileDetails fileInfo = initiatedFiles.get(hash);

            addFileAsUndeleted(fileInfo);

            byte[] message = Message.createMessage(this.version, MessageType.DELETE, this.id, fileInfo.getHash());
            controlChannel.multicast(message);
            System.out.printf("[-] DELETE %s\n", file);

            //remove all data regarding this file
            this.removeInitiatedFile(file);

            this.hasChanges = true; // flag for peer backup
            return "success";
        }
        return "failure";
    }

    @Override
    public String reclaim(int new_capacity) {
        System.out.println("[-] RECLAIM max_size: " + new_capacity);

        this.maxSpace = new_capacity * 1000L;

        List<FileDetails> stored = new ArrayList<>(this.storedFiles.values());

        while (this.diskUsage > this.maxSpace) {
            FileDetails fileDetails = stored.remove(0);
                for (Chunk chunk : fileDetails.getChunks()) {
                    fileDetails.removeChunk(chunk.getChunkNo()); // remove chunk from file
                    chunk.removeStorage(this); // remove storage

                    fileDetails.getChunks().remove(chunk); // remove chunk from stored file

                    byte[] message = Message.createMessage(this.version, MessageType.REMOVED, this.id, chunk.getFilehash(), chunk.getChunkNo());
                    controlChannel.multicast(message);
                    this.setChangesFlag();

                    if (this.diskUsage <= this.maxSpace)
                        return "success";
                }
        }
        return "failure";
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    public String restore(String file) throws RemoteException {
        // checking if this peer was the initiator for file backup
        if (!this.filenameHashes.containsKey(file))
            return "failure";

        String hash = this.filenameHashes.get(file);
        // send delete message to other peers
        FileDetails fileInfo = initiatedFiles.get(hash);

        String fileName = new File(file).getName();

        File restored = new File(getRestorePath(file));
        FileOutputStream fstream;
        restored.getParentFile().mkdirs();

        try {
            restored.createNewFile();
            fstream = new FileOutputStream(restored);

            byte[] message;
            boolean lastChunk = false;
            int chunkNo = 0;
            while (!lastChunk) {
                ChunkMonitor cm = fileInfo.addMonitor(chunkNo);
                message = Message.createMessage(this.version, MessageType.GETCHUNK, this.id, fileInfo.getHash(), chunkNo);

                int i;
                for (i = 0; i < 3; i++) {  // 3 retries per chunk
                    // send GETCHUNK message to other peers
                    controlChannel.multicast(message);
                    System.out.printf("[-] RESTORE %s %d\n", file, chunkNo);

                    if (!cm.await_receive())
                        continue;

                    fileInfo.removeMonitor(chunkNo);

                    byte[] data;
                    if (SdisUtils.isInitialVersion(version) || SdisUtils.isInitialVersion(cm.getVersion())) {
                        // Original
                        data = cm.getData();
                    } else{
                        // Enhancement
                        String[] tcp_details = new String(cm.getData(), StandardCharsets.US_ASCII).split(":");
                        Socket socket = new Socket(InetAddress.getByName(tcp_details[0]), Integer.parseInt(tcp_details[1]));
                        InputStream is = socket.getInputStream();

                        data = is.readAllBytes();

                        socket.close();
                    }

                    fstream.write(data);
                    if (data.length != Definitions.CHUNK_SIZE)
                        lastChunk = true;
                    break;
                }

                if (i >= 3) {
                    fstream.close();
                    return "failure";
                }

                chunkNo++;
            }

            fstream.close();

        } catch (IOException e) {
            e.printStackTrace();
            return "failure";
        }

        return "success";
    }

    public String state() throws RemoteException {
        StringBuilder ret = new StringBuilder("\n========== INFO ==========\n");

        ret.append(String.format("peerID: %d \nversion: %s \nmax capacity: %d KB\nused: %d KB\n", this.getId(), this.version, this.maxSpace / 1000, this.diskUsage / 1000));

        if (!this.initiatedFiles.isEmpty()) {
            ret.append("\n========== INITIATED ===========\n");
            for (Map.Entry<String, String> entry : this.filenameHashes.entrySet()) {
                String filename = entry.getKey();
                String hash = entry.getValue();
                FileDetails fd = this.initiatedFiles.get(hash);
                ret.append(
                        String.format("filename: %s \tid: %s \tdesired replication: %d\n", filename, fd.getHash(), fd.getDesiredReplication())
                );

                for (Chunk chunk : fd.getChunks()) {
                    ret.append(
                            String.format(" - chunkNo: %d \tperceived replication: %d\n", chunk.getChunkNo(), chunk.getPerceivedReplication())
                    );
                }
            }
        }

        if (!this.storedFiles.isEmpty()) {
            ret.append("\n========== STORED ==========\n");
            for (FileDetails details : this.storedFiles.values())
                for (Chunk chunk : details.getChunks())
                    ret.append(
                            String.format("chunkID: %s \tsize: %d KB\tdesired replication: %d \tperceived replication: %d\n",
                                    chunk.getFilehash() + "_" + chunk.getChunkNo(), chunk.getSize() / 1000, details.getDesiredReplication(), chunk.getPerceivedReplication()
                            ));
        }

        return ret.toString();
    }

    public static void main(String[] args) throws IOException{

        if (args.length < 1) {
            System.out.println("usage: Peer <remote_object_name>");
            throw new IOException("Invalid usage");
        }

        String version = args[0];
        int id = Integer.parseInt(args[1]);
        String service_ap = args[2];

        String MC = args[3], MDB = args[4], MDR = args[5];

        OldPeer peer = new OldPeer(version, id, MC, MDB, MDR);
        PeerRecovery recovery = new PeerRecovery(peer); //recover previously saved peer data

        Registry registry = LocateRegistry.getRegistry();
        try {
            ClientPeerProtocol stub = (ClientPeerProtocol) UnicastRemoteObject.exportObject(peer,0);

            //Bind the remote object's stub in the registry
            registry.bind(service_ap, stub); //register peer object with the name in args[0]
        } catch (AlreadyBoundException e) {
            System.out.println("[R] Object already bound! Rebinding...");
            registry.rebind(service_ap, peer);
        } catch (ConnectException e) {
            System.out.println("[R] Could not connect to RMI!");
        }
        System.out.println("[#] Peer " + peer.id + " ready");

        peer.backupChannel.start();
        peer.controlChannel.start();
        peer.restoreChannel.start();

        // save current peer state every 30 seconds
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(recovery, 15, 30, TimeUnit.SECONDS);

        if (!SdisUtils.isInitialVersion(peer.version)) {
            byte[] initMessage = Message.createMessage(peer.version, MessageType.INIT, peer.id);
            peer.controlChannel.multicast(initMessage);
        }
    }

    public OldPeer increaseDiskUsage(long space) {
        this.diskUsage += space;
        return this;
    }

    public OldPeer decreaseDiskUsage(long space) {
        this.diskUsage -= space;
        return this;
    }

    public ScheduledThreadPoolExecutor getScheduledPool() {
        return scheduledPool;
    }

    public String getVersion() { return version; }

    public MulticastChannel getBackupChannel() { return backupChannel; }

    public MulticastChannel getControlChannel() { return controlChannel; }

    public MulticastChannel getRestoreChannel() { return restoreChannel; }

    public int getId() { return id; }

    public Map<String, FileDetails> getStoredFiles() { return storedFiles; }

    public void clearChangesFlag() { this.hasChanges = false; }

    public boolean hasChanges() { return hasChanges; }

    public void setChangesFlag() { this.hasChanges = true; }

    public FileDetails getFileDetails(String fileHash) {
        FileDetails fileDetails = this.initiatedFiles.get(fileHash);
        if (fileDetails != null)
            return fileDetails;
        return this.storedFiles.get(fileHash);
    }

    public void addReclaimedChunk(String fileHash, int chunkNo) {
        this.reclaimedChunks.computeIfAbsent(fileHash, k -> new HashSet<>());
        this.reclaimedChunks.get(fileHash).add(chunkNo);
    }

    public Map<String, Set<Integer>> getReclaimedChunks() {
        return reclaimedChunks;
    }

    public boolean isReclaimedChunk(String fileHash, int chunkNo) {
        Set<Integer> reclaimed = this.reclaimedChunks.get(fileHash);
        return reclaimed != null && reclaimed.contains(chunkNo);
    }

    /**
     * Marks a chunk as resolved when the desired replication degree has been reached
     * Used in the backup subprotocol
     * @param fileHash - file id
     * @param chunkNo - chunk number
     */
    public void resolveInitiatedChunk(String fileHash, int chunkNo) {
        FileDetails file = this.initiatedFiles.get(fileHash);
        if (file == null) // only used on initiator peer
            return;

        Chunk chunk = file.getChunk(chunkNo);

        if (chunk.getPerceivedReplication() >= file.getDesiredReplication()) {
            ChunkMonitor monitor = file.getMonitor(chunkNo);
            if (monitor != null)
                monitor.markSolved();
        }
    }

    /**
     * Marks a chunk as resolved when a PUTCHUNK message is received
     * Used in the REMOVED protocol
     * Prevents a PUTCHUNK message from being sent (section - 3.5 Space reclaiming subprotocol)
     * @param fileHash - file id
     * @param chunkNo - chunk number
     */
    public void resolveRemovedChunk(String fileHash, int chunkNo) {
        FileDetails file = this.storedFiles.get(fileHash);
        if (file != null) {
            ChunkMonitor monitor = file.getMonitor(chunkNo);
            if (monitor != null)
                monitor.markSolved();
        }
    }

    /**
     * Checking if there is enough space to store a given chunk
     * @param chunkSize - chunk size
     * @return boolean
     */
    public boolean hasDiskSpace(long chunkSize) {
        return (this.maxSpace - this.diskUsage) >= chunkSize;
    }

    /**
     * Checking if peer has already backed up this chunk
     * @return boolean
     */
    public boolean hasStoredChunk(String filehash, int chunkNo) {
        FileDetails details = this.storedFiles.get(filehash);
        return details != null && (details.getChunk(chunkNo) != null);
    }

    public boolean isInitiator(String fileHash) {
        return this.initiatedFiles.get(fileHash) != null;
    }

    public void addPerceivedReplication(int peerId, String fileHash, int chunkNo) {
        FileDetails fileDetails = getFileDetails(fileHash);
        if (fileDetails != null)
            fileDetails.addChunkReplication(chunkNo, peerId);
    }

    public void addStoredChunk(Chunk chunk, int desiredReplication) {
        FileDetails file = this.storedFiles.computeIfAbsent(chunk.getFilehash(), v -> new FileDetails(chunk.getFilehash(),0, desiredReplication));
        this.increaseDiskUsage(chunk.getSize());
        file.addChunk(chunk);
    }

    public void removeStoredChunk(String fileHash, int chunkNo) {
        FileDetails file = this.storedFiles.get(fileHash);
        Chunk c = file.removeChunk(chunkNo);
        this.decreaseDiskUsage(c.getSize());
    }

    public Chunk getFileChunk(String fileHash, int chunkNo) {

        //find chunk in stored chunks list
        FileDetails file = this.storedFiles.get(fileHash);

        if (file == null)
            file = this.initiatedFiles.get(fileHash);

        return file == null ? null : file.getChunk(chunkNo);
    }

    public int getFileReplication(String fileHash) {
        //find chunk in stored chunks list
        FileDetails file = this.storedFiles.get(fileHash);

        if (file == null)
            file = this.initiatedFiles.get(fileHash);

        return file == null ? 0 : file.getDesiredReplication();
    }

    public void restoreState(OldPeer previous) {
        this.diskUsage = previous.diskUsage;
        this.maxSpace = previous.maxSpace;
        this.storedFiles = previous.storedFiles;
        this.initiatedFiles = previous.initiatedFiles;
        this.filenameHashes = previous.filenameHashes;
        this.undeletedFiles = previous.undeletedFiles;
        this.reclaimedChunks = previous.reclaimedChunks;
    }

    public void removeInitiatedFile(String filename) {
        String hash = this.filenameHashes.get(filename);

        this.initiatedFiles.remove(hash);
        this.filenameHashes.remove(filename);
    }

    public void removeStoredFile(String hash) {
        this.storedFiles.remove(hash);
    }

    // UNDELETED FILES
    public Iterator<String> getPeerUndeletedFiles(int peerId) {
        Set<String> files = this.undeletedFiles.get(peerId);
        return files == null ? Collections.emptyIterator() : files.iterator();
    }

    public void removeUndeletedFile(int peerId, String fileHash) {
        Set<String> files = this.undeletedFiles.get(peerId);
        if (files == null)
            return;
        files.remove(fileHash);
    }

    private void cleanUndeletedFileRecords(String hash) {
        for (Set<String> files: this.undeletedFiles.values())
            files.remove(hash);
    }

    private void addFileAsUndeleted(FileDetails fileDetails) {
        Set<Integer> peers = fileDetails.getPeersWithChunks();

        for (Integer peerId: peers) {
            Set<String> undeletedFiles = this.undeletedFiles.computeIfAbsent(peerId, k -> ConcurrentHashMap.newKeySet());
            undeletedFiles.add(fileDetails.getHash());
        }
    }

    @Override
    public String toString() {
        return "Peer{" +
                "id=" + id +
                ", version='" + version + '\'' +
                ", max_space=" + maxSpace +
                ", disk_usage=" + diskUsage +
                ", filenameHashes=" + filenameHashes +
                ", initiatedFiles=" + initiatedFiles +
                ", storedFiles=" + storedFiles +
                ", backupChannel=" + backupChannel +
                ", restoreChannel=" + restoreChannel +
                ", controlChannel=" + controlChannel +
                ", hasChanges=" + hasChanges +
                '}';
    }

    @Serial
    private void writeObject(java.io.ObjectOutputStream out) throws IOException {
        out.writeLong(this.diskUsage);
        out.writeLong(this.maxSpace);
        out.writeObject(this.filenameHashes);
        out.writeObject(this.initiatedFiles);
        out.writeObject(this.storedFiles);
        out.writeObject(this.undeletedFiles);
        out.writeObject(this.reclaimedChunks);
    }

    @Serial
    @SuppressWarnings("unchecked")
    private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
        this.diskUsage = in.readLong();
        this.maxSpace = in.readLong();
        this.filenameHashes = (ConcurrentHashMap<String, String>) in.readObject();
        this.initiatedFiles = (ConcurrentHashMap<String, FileDetails>) in.readObject();
        this.storedFiles = (ConcurrentHashMap<String, FileDetails>) in.readObject();
        this.undeletedFiles = (ConcurrentHashMap<Integer, Set<String>>) in.readObject();
        this.reclaimedChunks = (ConcurrentHashMap<String, Set<Integer>>) in.readObject();
    }
}
