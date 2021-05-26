package main.g24;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Serial;
import java.io.Serializable;
import java.nio.*;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
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

    public void store(OldPeer peer, byte[] contents) {

        Path path = Paths.get(peer.getStoragePath(filehash) + chunkNo);

        try {
            // create parent dirs
            Files.createDirectories(path.getParent());

            Files.createFile(path);

            ByteBuffer buffer = ByteBuffer.wrap(contents);
            FileChannel fileChannel = FileChannel.open(path, StandardOpenOption.WRITE);

            fileChannel.write(buffer);
            fileChannel.close();

            this.addReplication(peer.getId());
        }
        catch (IOException e) {
            System.out.println("[!] Couldn't locate specified file " + path);
            e.printStackTrace();
            System.exit(1);
        }
    }

    public byte[] retrieve(OldPeer peer) {
        // fetch backed up chunk
        byte[] body = new byte[this.size];
        Path path = Paths.get(peer.getStoragePath(this.filehash) + chunkNo);

        try {
            ByteBuffer buffer = ByteBuffer.wrap(body);
            FileChannel fileChannel = FileChannel.open(path, StandardOpenOption.READ);

            fileChannel.read(buffer);
            fileChannel.close();

        } catch (FileNotFoundException e) {
            System.out.println("[!] Couldn't locate file (retrieve): " + path);
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return body;
    }

    public boolean removeStorage(OldPeer peer) {
        Path path = Paths.get(peer.getStoragePath(this.filehash) + chunkNo);

        try {
            if (!Files.deleteIfExists(path)) {
                System.out.printf("[!] Couldn't locate %s \n", path);
                return false;
            }
            else {
                peer.decreaseDiskUsage(this.size);
            }

        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }

    public static boolean removeFileDir(OldPeer peer, String filehash) {

        Path path = Paths.get(peer.getStoragePath(filehash));

        try {
            // try to delete storage dir
            return Files.deleteIfExists(path);
        } catch (IOException e) {
            System.out.println("Couldn't delete storage dir: " + path);
            e.printStackTrace();
            return false;
        }
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