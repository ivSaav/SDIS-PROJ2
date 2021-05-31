package main.g24.socket.handlers;

import java.nio.channels.SelectionKey;

public interface ISocketManager {
    void onSelect(SelectionKey key);
    void init();
    int interestOps();
}
