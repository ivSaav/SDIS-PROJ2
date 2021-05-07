package main.g24.message;

import java.util.Random;

public class ChunkMonitor {

    private boolean chunkSolved = false;
    private String version = "";
    private byte[] data;

    public synchronized boolean await_send() {
        Random r = new Random();
        long timeoutTime = System.currentTimeMillis() + r.nextInt(400);
        while (!chunkSolved && System.currentTimeMillis() < timeoutTime) {
            long timeoutMillis = timeoutTime - System.currentTimeMillis(); // recompute timeout values
            try {
                wait(timeoutMillis);
            } catch (InterruptedException e) {
                return false;
            }
        }

        return this.chunkSolved;
    }

    /**
     * Awaits as the recover peer
     * @return True if the data is available
     */
    public synchronized boolean await_receive() {
        return await_receive(2000);
    }

    /**
     * Awaits as the recover peer
     * @return True if the data is available
     */
    public synchronized boolean await_receive(long timeout) {
        long timeoutTime = System.currentTimeMillis() + timeout;
        while (!chunkSolved && System.currentTimeMillis() < timeoutTime) {
            long timeoutMillis = timeoutTime - System.currentTimeMillis(); // recompute timeout values
            try {
                wait(timeoutMillis);
            } catch (InterruptedException e) {
                return false;
            }
        }

        return this.chunkSolved;
    }

    public synchronized void markSolved(byte[] data, String version) {
        this.version = version;
        this.data = data;
        this.chunkSolved = true;
        notifyAll();
    }

    public synchronized void markSolved(byte[] data) {
        this.data = data;
        this.chunkSolved = true;
        notifyAll();
    }

    public synchronized  void markSolved() {
        this.chunkSolved = true;
        notifyAll();
    }

    public byte[] getData() { return data; }

    public String getVersion() { return version; }
}
