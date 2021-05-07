package main.g24.message;

import main.g24.SdisUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Message {
    private static final byte CR = 0xD, LF = 0xA, SEP = ' ';
    private static final byte[] CRLF = new byte[] {CR, LF};

    // <Version> <MessageType> <SenderId> <FileId> <ChunkNo> <ReplicationDeg> <CRLF>
    public String version;
    public MessageType type;
    public int senderId;
    public String fileId;
    public int chunkNo, replicationDegree;
    public byte[] body;


    public Message(String version, MessageType type, int senderId, String fileId, int chunkNo, int replicationDegree, byte[] body) {
        this.version = version;
        this.type = type;
        this.senderId = senderId;
        this.fileId = fileId;
        this.chunkNo = chunkNo;
        this.replicationDegree = replicationDegree;
        this.body = body;
    }

    public static Message parse(byte[] data, int size) {
        List<byte[]> headers = new ArrayList<>();
        byte[] body = Message.split(data, size, headers);

        // Common args
        String version = new String(headers.get(0));
        MessageType type = MessageType.valueOf(new String(headers.get(1)));
        int senderId = Integer.parseInt(new String(headers.get(2)));

        // Default values
        String fileId = "";
        int chunkNo = -1, replicationDegree = -1;

        if (headers.size() >= 4)
            fileId = new String(headers.get(3)).toLowerCase();

        if (headers.size() >= 5)
            chunkNo = Integer.parseInt(new String(headers.get(4)));

        if (headers.size() >= 6)
            replicationDegree = Integer.parseInt(new String(headers.get(5)));

        return new Message(version, type, senderId, fileId, chunkNo, replicationDegree, body);
    }

    private static boolean containsCRLF(byte[] data, int start, int i) {
        return i - start == 4 && (
                data[start] == Message.CR
                && data[start + 1] == Message.LF
                && data[start + 2] == Message.CR
                && data[start + 3] == Message.LF
        );
    }

    public static byte[] split(byte[] data, int size, List<byte[]> headers) {
        boolean found_arg = false;

        int start = 0;
        for (int i = 0; i < data.length; i++) {
            if (!found_arg) {
                start = i;
                if (data[i] != Message.SEP) {
                    found_arg = true;
                }
            } else {
                if (containsCRLF(data, start, i)) {
                    // Return either the body or an empty byte array
                    return Arrays.copyOfRange(data, i, size);
                }

                if (data[i] == Message.SEP) {
                    headers.add(Arrays.copyOfRange(data, start, i));
                    found_arg = false;
                }
            }
        }

        // Should NOT get here
        return new byte[]{};
    }

    public static byte[] createMessage(String version, MessageType type, int senderId) {
        return createMessage(version, type, senderId, "", -1, -1, new byte[] {});
    }

    public static byte[] createMessage(String version, MessageType type, int senderId,
                               String fileId) {
        return createMessage(version, type, senderId, fileId, -1, -1, new byte[] {});
    }

    public static byte[] createMessage(String version, MessageType type, int senderId,
                               String fileId, int chunkNo) {
        return createMessage(version, type, senderId, fileId, chunkNo, -1, new byte[] {});
    }

    public static byte[] createMessage(String version, MessageType type, int senderId,
                               String fileId, int chunkNo, byte[] body) {
        return createMessage(version, type, senderId, fileId, chunkNo, -1, body);
    }

    public static byte[] createMessage(String version, MessageType type, int senderId,
                               String fileId, int chunkNo, int replicationDegree, byte[] body) {
        String header = version + " " + type + " " + senderId + " " + fileId + " "  + (chunkNo != -1 ? chunkNo + " " : "") + (replicationDegree != -1 ? replicationDegree + " " : "") + "\r\n\r\n";

        if (body.length == 0) {
            return header.getBytes(StandardCharsets.US_ASCII);
        }
        int messageSize = header.length() + body.length;
        byte[] result = new byte[messageSize];
        System.arraycopy(header.getBytes(StandardCharsets.US_ASCII), 0, result, 0, header.length());
        System.arraycopy(body, 0, result, header.length(), body.length);

        return result;
    }

    @Override
    public String toString() {
        return "Message{" +
                "v=" + version +
                ", " + type +
                ", sender=" + senderId +
                (fileId.isEmpty() ? "" : ", file='" + SdisUtils.shortenHash(fileId) + "'") +
                (chunkNo == -1 ? "" : ", chunkNo=" + chunkNo) +
                (replicationDegree == -1 ? "" : ", repDeg=" + replicationDegree) +
                (body.length == 0 ? "" : ", body=" + body.length) +
                '}';
    }
}
