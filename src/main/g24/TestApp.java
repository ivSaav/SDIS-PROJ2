package main.g24;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class TestApp {

    private TestApp() {}

    public static void main(String[] args) {
        String peer_ap = args[0];
        String filename;
        int repDegree;
        int max_size;
        ClientPeerOperation operation = ClientPeerOperation.valueOf(args[1]);

        try {
            Registry registry = LocateRegistry.getRegistry();
            ClientPeerProtocol stub = (ClientPeerProtocol) registry.lookup(peer_ap);

            String response;
            switch (operation) {
                case BACKUP:
                    filename = args[2];
                    repDegree = Integer.parseInt(args[3]);
                    response = stub.backup(filename, repDegree);
                    break;
                case DELETE:
                    filename = args[2];
                    response = stub.delete(filename);
                    break;
                case RECLAIM:
                    max_size = Integer.parseInt(args[2]);
                    response = stub.reclaim(max_size);
                    break;
                case RESTORE:
                    filename = args[2];
                    response = stub.restore(filename);
                    break;
                case STATE:
                    response = stub.state();
                    break;
                default:
                    System.out.println("ERROR invalid operation:" + args[2]);
                    return;
            }
            System.out.println("Response: " + response);

        } catch (NotBoundException | RemoteException e) {
            System.out.println("Couldn't perform specified operation " + operation);
            e.printStackTrace();
        }
    }
}
