package main.g24.socket.handlers;

import main.g24.Peer;

import java.nio.channels.SelectionKey;

public interface ISocketManager {
    void onSelect(SelectionKey key);
    void addPeer(Peer peer);
    default boolean validate() { return true; }
}
