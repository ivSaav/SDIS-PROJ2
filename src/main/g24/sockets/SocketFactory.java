package main.g24.sockets;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;

public class SocketFactory {

    private static SSLSocketFactory sslSocketFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();

    public static SSLSocket create_socket(String host, int port) throws IOException {
        return (SSLSocket) sslSocketFactory.createSocket(host, port);
    }

}
