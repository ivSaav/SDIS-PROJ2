package main.g24.socket.messages;

public abstract class SocketMessageFactory {

    public static ISocketMessage from(String message) {
        String[] args = message.split(" ");

        if (args.length < 1)
            return null;

       return switch (Type.valueOf(args[0])) {
           case ACK -> AckMessage.from(args);
           case BACKUP -> BackupMessage.from(args);
           case REPLICATE -> ReplicateMessage.from(args);
           case DELKEY -> DeleteKeyMessage.from(args);
           case DELCOPY -> DeleteCopyMessage.from(args);
           case REMOVED -> RemovedMessage.from(args);
           case GETFILE -> GetFileMessage.from(args);
           case FILEHERE -> FileHereMessage.from(args);
           case STATE -> StateMessage.from(args);
           default -> null;
       };
    }
}
