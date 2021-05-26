package main.g24;

import main.g24.chord.Node;

import java.io.File;
import java.rmi.RemoteException;

public class Peer extends Node implements ClientPeerProtocol {

    int id;

    // PEER
    public Peer(int id) {
        super(id);

        this.id = id;
    }


    // ---------------------------------
    //    PEER INTERFACE OPERATIONS
    // ---------------------------------
    @Override
    public String backup(String path, int repDegree) throws RemoteException {
        return null;
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


    //
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
}
