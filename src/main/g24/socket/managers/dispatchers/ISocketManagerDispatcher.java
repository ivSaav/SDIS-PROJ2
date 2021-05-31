package main.g24.socket.managers.dispatchers;

import main.g24.socket.managers.ISocketManager;
import main.g24.socket.messages.ISocketMessage;

import java.nio.channels.SelectionKey;

public interface ISocketManagerDispatcher {
    ISocketManager dispatch(ISocketMessage message, SelectionKey key);
}
