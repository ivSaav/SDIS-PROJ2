package main.g24;

import main.g24.chord.INode;
import main.g24.chord.Node;

import java.io.File;
import java.io.IOException;
import java.rmi.AlreadyBoundException;
import java.rmi.ConnectException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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
        INode succ = this.get_successor();
        INode pred = this.get_predecessor();
        System.out.println("SUCC " + (succ != null ? succ.get_id() : null) + "\n PRED " + (pred != null ? pred.get_id() : null));
        return "failure";
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

    public static void main(String[] args) throws IOException {

        if (args.length < 2) {
            System.out.println("usage: Peer <id> <remote_object_name>");
            throw new IllegalArgumentException("Invalid usage");
        }

        int id = Integer.parseInt(args[0]);
        String service_ap = args[1];

        Peer peer = new Peer(id);

        System.out.println("REG");
        Registry registry = LocateRegistry.getRegistry();
        try {
            String[] active = registry.list();
            ClientPeerProtocol stub = (ClientPeerProtocol) UnicastRemoteObject.exportObject(peer,0);

            //Bind the remote object's stub in the registry
            registry.bind(service_ap, stub); //register peer object with the name in args[0]

            // look for other peers in the ring
            ClientPeerProtocol root = peer;
            if (active.length > 0) {
                root = (ClientPeerProtocol) registry.lookup(active[0]);
                // join existing ring
                peer.join((INode) root);
            }
            else {
                System.out.println("I'm first");
                peer.create();
            }

        } catch (AlreadyBoundException e) {
            System.out.println("[R] Object already bound! Rebinding...");
            registry.rebind(service_ap, peer);
        } catch (ConnectException e) {
            System.out.println("[R] Could not connect to RMI!");
            e.printStackTrace();
        } catch (NotBoundException e) {
            e.printStackTrace();
        }
        System.out.println("[#] Peer " + peer.id + " ready");

        // chord stabilization protocol
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            try {
                peer.stabilize();
                peer.fix_fingers();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, 1, 2, TimeUnit.SECONDS);
    }
}
