package main.g24.monitors;

public class GeneralMonitor {

    private String resolve = null;
    private boolean resolved;

    public synchronized boolean await_resolution(long timeout) {
        long timeoutTime = System.currentTimeMillis() + timeout;
        while (!resolved && System.currentTimeMillis() < timeoutTime) {
            long timeoutMillis = timeoutTime - System.currentTimeMillis(); // recompute timeout values
            try {
                wait(timeoutMillis);
            } catch (InterruptedException e) {
                return false;
            }
        }

        return this.resolved;
    }

    public synchronized void resolve(String resolve) {
        this.resolve = resolve;
        this.resolved = true;
        notifyAll();
    }

    public String get_message() {
        return resolve;
    }

}
