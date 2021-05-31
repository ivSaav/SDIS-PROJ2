package main.g24.socket.handlers;

import java.io.IOException;
import java.nio.channels.SelectionKey;

public interface ISocketManager {
    void onSelect(SelectionKey key);
    void init() throws IOException;
    int interestOps();
}
