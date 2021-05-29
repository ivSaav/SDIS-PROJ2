package main.g24;

import java.io.Serializable;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class FileDetails implements Serializable {

    private final int initID; // the id fo the initiator peer
    private final String hash;
    private final long size;
    private final int desiredRepDegree;

    private final List<Integer> copies;

    // File details on initiator
    public FileDetails(int initID, String hash, long size, int desiredRepDegree) {
        this.initID = initID;
        this.hash = hash;
        this.size = size;
        this.desiredRepDegree = desiredRepDegree;
        this.copies = new CopyOnWriteArrayList<>();
    }

    // File Details on store
    public FileDetails(int initID, String hash, long size) {
        this.initID = initID;
        this.hash = hash;
        this.size = size;
        this.desiredRepDegree = -1;
        this.copies = null;
    }

    public String getHash() {
        return hash;
    }

    public long getSize() {
        return size;
    }

    public int getDesiredReplication() { return desiredRepDegree; }

    public int getPerceivedReplication() { return copies.size(); }

    public int getInitID() { return initID; }

    public List<Integer> getFileCopies() { return copies; }

    public void addCopy(int peerID) { this.copies.add(peerID); }

    public void removeCopy(int peerID) {
        int idx = this.copies.indexOf(peerID);
        this.copies.remove(idx);
    }

    public int getLastCopy() {
        return this.copies.isEmpty() ? -1 : this.copies.get(copies.size()-1);
    }
}
