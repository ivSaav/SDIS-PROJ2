package main.g24.socket.messages;

public abstract class SocketMessageFactory {

    public static ISocketMessage from(String message) {
        String[] args = message.split(" ");

        if (args.length < 1)
            return null;

       return switch (Type.valueOf(args[0])) {
           case ACK -> null;
           case BACKUP -> BackupMessage.from(args);
           default -> null;
       };
    }
}
