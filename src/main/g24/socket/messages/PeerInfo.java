package main.g24.socket.messages;

import java.io.Serializable;
import java.util.Map;
import java.util.Set;

import main.g24.FileDetails;
import main.g24.Peer;

public class PeerInfo implements Serializable {

    public int id;
    public Set<String> storedFiles;
    public Map<Integer, Map<String, FileDetails>> fileKeys;


    public static PeerInfo from(Peer peer) {
        PeerInfo info = new PeerInfo();
        info.id = peer.get_id();
        info.storedFiles = peer.getStoredFiles();
        info.fileKeys = peer.getFileKeys();


        return info;
    }
}
