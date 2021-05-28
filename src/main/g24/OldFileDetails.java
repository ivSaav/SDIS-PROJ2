package main.g24;

import main.g24.message.ChunkMonitor;

import java.io.IOException;
import java.io.Serial;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class OldFileDetails implements Serializable {
    private String hash;
    private long size;
    private int desiredRepDegree;

    private Map<Integer, Chunk> chunks;

    private Map<Integer, ChunkMonitor> chunkMonitors;

    public OldFileDetails(String hash, long size, int desiredRepDegree) {
        this.hash = hash;
        this.size = size;
        this.desiredRepDegree = desiredRepDegree;

        this.chunks = new ConcurrentHashMap<>();
        this.chunkMonitors = new ConcurrentHashMap<>();
    }

    public String getHash() {
        return hash;
    }

    public long getSize() {
        return size;
    }

    public void addChunk(Chunk chunk) {
        this.chunks.put(chunk.getChunkNo(), chunk);
    }

   public Chunk removeChunk(int chunkNo) {
       return this.chunks.remove(chunkNo);
   }

    public int getDesiredReplication() {
        return desiredRepDegree;
    }
    public Collection<Chunk> getChunks() {
        return chunks.values();
    }

    public Chunk getChunk(int chunkNo) {
        return this.chunks.get(chunkNo);
    }

    public int getChunkReplication(int chunkNo) {
        return chunks.get(chunkNo).getPerceivedReplication();
    }

    public void addChunkReplication(int chunkNo, int peerId) {
        Chunk chunk = this.chunks.get(chunkNo);
        if (chunk != null)
            chunks.get(chunkNo).addReplication(peerId);
    }

    public void clearMonitors() {
        this.chunkMonitors.clear();
    }

    public void removeMonitor(int chunkNo) {
        this.chunkMonitors.remove(chunkNo);
    }

    public ChunkMonitor addMonitor(int chunkNo) {
        ChunkMonitor cm = new ChunkMonitor();
        this.chunkMonitors.put(chunkNo, cm);
        return cm;
    }

    public ChunkMonitor getMonitor(int chunkNo) {
        return this.chunkMonitors.get(chunkNo);
    }

    public Set<Integer> getPeersWithChunks() {
        Set<Integer> peers = new HashSet<>();
        for (Chunk c: chunks.values())
            c.addPeersWithChunk(peers);
        return peers;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o instanceof String) return this.hash.equals(o);
        if (!(o instanceof OldFileDetails)) return false;
        OldFileDetails that = (OldFileDetails) o;
        return hash.equals(that.hash);
    }

    @Override
    public int hashCode() {
        return Objects.hash(hash);
    }

    @Override
    public String toString() {
        return "FileDetails{" +
                "hash='" + hash.substring(0,5) + '\'' +
                ", size=" + size +
                ", desiredRepDegree=" + desiredRepDegree +
                ", chunks=" + chunks +
                '}';
    }

    @Serial
    private void writeObject(java.io.ObjectOutputStream out) throws IOException {
        out.writeUTF(this.hash);
        out.writeLong(this.size);
        out.writeInt(this.desiredRepDegree);
        out.writeObject(this.chunks);
    }

    @Serial
    @SuppressWarnings("unchecked")
    private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
        this.hash = in.readUTF();
        this.size = in.readLong();
        this.desiredRepDegree = in.readInt();
        this.chunks = (ConcurrentHashMap<Integer, Chunk>) in.readObject();
        this.chunkMonitors = new ConcurrentHashMap<>();
    }
}
