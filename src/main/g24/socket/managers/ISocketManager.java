package main.g24.socket.managers;

import java.io.IOException;
import java.nio.channels.SelectionKey;

public interface ISocketManager {
    void onSelect(SelectionKey key);
    void init() throws IOException;
    int interestOps();
}
