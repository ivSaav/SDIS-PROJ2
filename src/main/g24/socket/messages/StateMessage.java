package main.g24.socket.messages;

import java.rmi.RemoteException;

import main.g24.chord.INode;

public class StateMessage implements ISocketMessage {
   // <PROTOCOL> <SENDER_ID> <SENDER_IP> <SENDER_PORT> <FILEHASH> <FILE_SIZE>

   public final int sender_id, sender_port;
   public final String sender_ip;

   public StateMessage(int sender_id, String sender_ip, int sender_port) {
       this.sender_id = sender_id;
       this.sender_port = sender_port;
       this.sender_ip = sender_ip;
   }

   public static StateMessage from(INode node) {
       try {
           return new StateMessage(
                   node.get_id(),
                   node.get_address().getHostName(),
                   node.get_port()
           );
       } catch (RemoteException e) {
           e.printStackTrace();
           return null;
       }
   }

   @Override
   public Type get_type() {
       return Type.STATE;
   }

   @Override
   public String gen_header() {
       return String.format("STATE %d %s %d", sender_id, sender_ip, sender_port);
   }

   @Override
   public String toString() {
       return "STATE " + sender_id;
   }

   public static ISocketMessage from(String[] args) {
       if (args.length < 3)
           return null;

       return new StateMessage(
               Integer.parseInt(args[1]), // sender id
               args[2], // sender ip
               Integer.parseInt(args[3]) // sender port
       );
   }
}
