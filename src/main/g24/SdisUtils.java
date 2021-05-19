package main.g24;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class SdisUtils {

    public static String createFileHash(String path, int initiatorPeerId) {
        try {
            Path file = Paths.get(path);
            BasicFileAttributes attribs = Files.readAttributes(file, BasicFileAttributes.class); // get file metadata

            String originalString = path + attribs.lastModifiedTime() + attribs.creationTime() + initiatorPeerId;

            final MessageDigest digest = MessageDigest.getInstance("SHA3-256");
            final byte[] hashbytes = digest.digest(originalString.getBytes(StandardCharsets.US_ASCII));
            return bytesToHex(hashbytes);
        }
        catch (IOException | NoSuchAlgorithmException e) {
            System.out.println("[X] Couldn't create file hash. " + e);
        }
        return null;
    }

    //Retrieved from: https://www.baeldung.com/sha-256-hashing-java
    public static String bytesToHex(byte[] hash) {
        StringBuilder hexString = new StringBuilder(2 * hash.length);
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    /**
     * @param v1
     * @param v2
     * @return Returns true if v1 is older than v2
     */
    public static boolean isVersionOlder(String v1, String v2) {
        String[] split1 = v1.split("\\.");
        String[] split2 = v2.split("\\.");

        if (Integer.parseInt(split1[0]) < Integer.parseInt(split2[0]))
            return true;

        return Integer.parseInt(split1[1]) < Integer.parseInt(split2[1]);
    }

    public static boolean isInitialVersion(String v) {
        return v.equals("1.0");
    }

    public static String shortenHash(String hash) {
        return hash.substring(0, 6);
    }
}
