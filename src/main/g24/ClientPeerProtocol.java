package main.g24;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface ClientPeerProtocol extends Remote {
    String backup(String path, int repDegree) throws RemoteException;
    String delete(String file) throws RemoteException;
    String reclaim(int new_capacity) throws RemoteException;
    String restore(String file) throws RemoteException;
    String state() throws RemoteException;
}