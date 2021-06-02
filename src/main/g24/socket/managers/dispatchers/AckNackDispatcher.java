package main.g24.socket.managers.dispatchers;

import main.g24.Peer;
import main.g24.monitors.GeneralMonitor;
import main.g24.socket.managers.ISocketManager;
import main.g24.socket.messages.AckMessage;
import main.g24.socket.messages.ISocketMessage;

import java.nio.channels.SelectionKey;
import java.util.function.Supplier;

public class AckNackDispatcher implements ISocketManagerDispatcher {

    private final Supplier<ISocketManager> onAck, onNack;

    public AckNackDispatcher(Supplier<ISocketManager> onAck, Supplier<ISocketManager> onNack) {
        this.onAck = onAck;
        this.onNack = onNack;
    }

    public static AckNackDispatcher resolveMonitor(GeneralMonitor monitor) {
        return new AckNackDispatcher(
                () -> {
                    monitor.resolve("success");
                    return null;
                },
                () -> {
                    monitor.resolve("failure");
                    return null;
                }
        );
    }

    @Override
    public ISocketManager dispatch(ISocketMessage message, SelectionKey key) {
        return switch (message.get_type()) {
            case ACK -> {
                AckMessage ack = (AckMessage) message;
                yield ack.get_status() ? onAck.get() : onNack.get();
            }
            default -> null;
        };
    }
}
