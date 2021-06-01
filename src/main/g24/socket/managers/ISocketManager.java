package main.g24.socket.managers;

import java.io.IOException;
import java.nio.channels.SelectionKey;

public interface ISocketManager {
    void onSelect(SelectionKey key);
    void init() throws IOException;
    int interestOps();

    @SuppressWarnings("MagicConstant")
    static void transitionTo(SelectionKey key, ISocketManager manager) throws IOException {
        manager.init();
        key.interestOps(manager.interestOps());
        key.attach(manager);
    }
}
