package main.g24.socket.managers.dispatchers;

import main.g24.socket.managers.ISocketManager;
import main.g24.socket.messages.ISocketMessage;

import java.nio.channels.SelectionKey;

public class BackupDispatcher implements ISocketManagerDispatcher {

    @Override
    public ISocketManager dispatch(ISocketMessage message, SelectionKey key) {
        return null;
    }

//    public static boolean initiate() {
//
//    }
}
