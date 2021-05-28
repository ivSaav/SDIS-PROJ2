package main.g24;

import java.io.Serializable;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class FileDetails {

    private final String hash;
    private final long size;
    private final int desiredRepDegree;

    // TODO send copies when storing
    private final Set<Integer> copies;

    public FileDetails(String hash, long size, int desiredRepDegree) {
        this.hash = hash;
        this.size = size;
        this.desiredRepDegree = desiredRepDegree;
        this.copies = ConcurrentHashMap.newKeySet();
    }

    public String getHash() {
        return hash;
    }

    public long getSize() {
        return size;
    }

    public int getDesiredReplication() { return desiredRepDegree; }

    public int getPerceivedReplication() { return copies.size(); }

    public Set<Integer> getFileCopies() { return copies; }

    public void addCopy(int peerID) { this.copies.add(peerID); }


}
