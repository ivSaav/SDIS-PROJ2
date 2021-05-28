package main.g24.chord;

import java.net.InetAddress;
import java.rmi.Remote;
import java.rmi.RemoteException;

public interface INode extends Remote {
    int get_id() throws RemoteException;

    INode get_predecessor() throws RemoteException;
    INode get_successor() throws RemoteException;

    INode find_successor(int id) throws RemoteException;
    INode closest_preceding_node(int id) throws RemoteException;

    void create() throws RemoteException;
    void join(INode node) throws RemoteException;

    void stabilize() throws RemoteException;
    void notify(INode node) throws RemoteException;
    void fix_fingers() throws RemoteException;

    /**
     * Checks if an INode is alive
     * @return true whenever the INode is alive
     */
    default boolean alive() throws RemoteException {
        return true;
    }

    void check_predecessor() throws RemoteException;


    /* =============================
                Methods
       =============================
     */
     InetAddress get_address() throws RemoteException;
     int get_port() throws RemoteException;
     String getStoragePath(String fileHash) throws RemoteException;
     String getPeerPath() throws RemoteException;

     void storeFile(int initId, String fileHash, long fileSize) throws RemoteException;

     void removeFile(String file) throws RemoteException;
}
