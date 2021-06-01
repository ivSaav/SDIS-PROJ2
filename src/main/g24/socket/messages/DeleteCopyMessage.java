package main.g24.socket.messages;

import main.g24.chord.INode;
import java.rmi.RemoteException;


public class DeleteCopyMessage extends DeleteMessage {
    // <PROTOCOL> <SENDER_ID> <SENDER_IP> <SENDER_PORT> <FILEHASH>


    public DeleteCopyMessage(int sender_id, String sender_ip, int sender_port, String filehash) {
        super(sender_id, sender_ip, sender_port, filehash);
    }

    public static DeleteCopyMessage from(INode node, String filehash) {
        try {
            return new DeleteCopyMessage(
                    node.get_id(),
                    node.get_address().getHostName(),
                    node.get_port(),
                    filehash
            );
        } catch (RemoteException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public Type get_type() {
        return Type.DELCOPY;
    }

    @Override
    public String gen_header() {
        return String.format("DELCOPY %d %s %d %s\r\n\r\n", sender_id, sender_ip, sender_port, filehash);
    }

    @Override
    public String toString() {
        return "DELCOPY " + sender_id + " " + filehash.substring(0, 6);
    }

    public static ISocketMessage from(String[] args) {
        if (args.length < 4)
            return null;

        return new DeleteCopyMessage(
                Integer.parseInt(args[1]), // sender id
                args[2], // sender ip
                Integer.parseInt(args[3]), // sender port
                args[4] // filehash
        );
    }
}
