package main.g24.ssl;

public class SSLTest {
    public void run() throws Exception {
        SSLClient var1 = new SSLClient("localhost", 8089);
        var1.connect();
        var1.write("Client 1...");
        var1.read();
        var1.shutdown();
    }

    public static void main(String[] var0) {
        try {
            SSLTest var1 = new SSLTest();
            Thread.sleep(1000L);
            var1.run();
        } catch (Exception var2) {
            var2.printStackTrace();
        }

    }
}
