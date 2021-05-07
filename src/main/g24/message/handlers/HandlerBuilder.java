package main.g24.message.handlers;

import main.g24.Peer;
import main.g24.message.Message;
import main.g24.message.MessageType;

import java.util.EnumMap;
import java.util.Map;

public abstract class HandlerBuilder {

    @FunctionalInterface
    private interface HandlerBuilderFI {
        Handler constructor(Peer p, Message m);
    }

    private static final Map<MessageType, HandlerBuilderFI> constructors = Map.ofEntries(
            new EnumMap.SimpleEntry<MessageType, HandlerBuilderFI>(MessageType.PUTCHUNK, PutchunkHandler::new),
            new EnumMap.SimpleEntry<MessageType, HandlerBuilderFI>(MessageType.GETCHUNK, GetchunkHandler::new),
            new EnumMap.SimpleEntry<MessageType, HandlerBuilderFI>(MessageType.CHUNK, ChunkHandler::new),
            new EnumMap.SimpleEntry<MessageType, HandlerBuilderFI>(MessageType.STORED, StoredHandler::new),
            new EnumMap.SimpleEntry<MessageType, HandlerBuilderFI>(MessageType.DELETE, DeleteHandler::new),
            new EnumMap.SimpleEntry<MessageType, HandlerBuilderFI>(MessageType.REMOVED, RemovedHandler::new),
            new EnumMap.SimpleEntry<MessageType, HandlerBuilderFI>(MessageType.INIT, InitHandler::new),
            new EnumMap.SimpleEntry<MessageType, HandlerBuilderFI>(MessageType.DELETED, DeletedHandler::new)
    );

    public static Handler build(Peer peer, Message message) {
        HandlerBuilderFI pb = constructors.get(message.type);
        return pb == null ? null : pb.constructor(peer, message);
    }
}
