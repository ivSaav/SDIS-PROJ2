package main.g24;

import java.io.*;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class Chunk implements Serializable {

    private String filehash;
    private int chunkNo;
    private int size;
    private Set<Integer> replications; // set with ids of the peers who have a replication of this chunk


    public Chunk(String filehash, int chunkNo, int size) {
        this.filehash = filehash;
        this.chunkNo = chunkNo;
        this.size = size;
        this.replications = new HashSet<>();
    }

    public String getFilehash() { return filehash; }
    public int getChunkNo() { return this.chunkNo; }
    public int getSize() { return this.size; }


    public int getPerceivedReplication() {
        return this.replications.size();
    }

    public Set<Integer> getReplications() {
        return replications;
    }

    public synchronized void addReplication(int peerId) {
        this.replications.add(peerId);
//        System.out.println(this.replications);
    }

    public synchronized void removeReplication(int peerId) {
        this.replications.remove(peerId);
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void store(Peer peer, byte[] contents) {
        File file = new File(peer.getStoragePath(filehash) + chunkNo);
        try {
            file.getParentFile().mkdirs();
            file.createNewFile();
            FileOutputStream out = new FileOutputStream(file);
            out.write(contents);
            out.flush();
            out.close();

            this.addReplication(peer.getId());
        }
        catch (IOException e) {
            System.out.println("[!] Couldn't locate specified file " + file.getName());
            e.printStackTrace();
            System.exit(1);
        }
    }

    public byte[] retrieve(Peer peer) {
        // fetch backed up chunk
        byte[] body = new byte[this.size];
        File file = new File(peer.getStoragePath(this.filehash) + chunkNo);
        try {
            FileInputStream fstream = new FileInputStream(file);
            int num_read = fstream.read(body);
            fstream.close();
        } catch (FileNotFoundException e) {
            System.out.println("[!] Couldn't locate file (retrieve)");
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return body;
    }

    public boolean removeStorage(Peer peer) {
        File file = new File(peer.getStoragePath(filehash) + this.chunkNo);
        if (!file.exists()) {
            System.out.printf("[!] Couldn't locate %s \n", file.getPath());
            return false;
        }
        boolean wasDeleted = file.delete();
        if (wasDeleted)
            peer.decreaseDiskUsage(this.size);
        return wasDeleted;
    }

    public static boolean removeFileDir(Peer peer, String filehash) {
        File file = new File(peer.getStoragePath(filehash));
        if (file.exists() && file.isDirectory() && Objects.requireNonNull(file.list()).length == 0) {
            return file.delete();
        }
        return false;
    }

    public void addPeersWithChunk(Set<Integer> peers) {
        peers.addAll(this.replications);
    }

    @Override
    public String toString() {
        return "Chunk{" +
                "filehash='" + filehash.substring(0, 5) + '\'' +
                ", chunkNo=" + chunkNo +
                ", size=" + size +
                ", replications=" + replications +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Chunk)) return false;
        Chunk chunk = (Chunk) o;
        return chunkNo == chunk.chunkNo && filehash.equals(chunk.filehash);
    }

    @Override
    public int hashCode() {
        return Objects.hash(filehash, chunkNo);
    }

    @Serial
    private void writeObject(java.io.ObjectOutputStream out) throws IOException {
        out.writeUTF(this.filehash);
        out.writeInt(this.chunkNo);
        out.writeInt(this.size);
        out.writeObject(this.replications);
    }

    @Serial
    @SuppressWarnings("unchecked")
    private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
        this.filehash = in.readUTF();
        this.chunkNo = in.readInt();
        this.size = in.readInt();
        this.replications = (Set<Integer>) in.readObject();
    }
}