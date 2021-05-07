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
        Definitions.Operation operation = Definitions.Operation.valueOf(args[1]);

        try {
            Registry registry = LocateRegistry.getRegistry();
            ClientPeerProtocol stub = (ClientPeerProtocol) registry.lookup(peer_ap);

            String response;
            if (operation == Definitions.Operation.BACKUP) {
                filename = args[2];
                repDegree = Integer.parseInt(args[3]);
                response = stub.backup(filename, repDegree);
            }
            else if (operation == Definitions.Operation.DELETE) {
                filename = args[2];
                response = stub.delete(filename);
            }
            else if (operation == Definitions.Operation.RECLAIM) {
                max_size = Integer.parseInt(args[2]);
                response = stub.reclaim(max_size);
            } else if (operation == Definitions.Operation.RESTORE) {
                filename = args[2];
                response = stub.restore(filename);
            }
            else if (operation == Definitions.Operation.STATE) {
                response = stub.state();
            }
            else {
                System.out.println("ERROR invalid operation:" + args[2]);
                return;
            }
            System.out.println("response: " + response);

        } catch (NotBoundException | RemoteException e) {
            System.out.println("Couldn't perform specified operation " + operation);
            e.printStackTrace();
        }
    }
}
