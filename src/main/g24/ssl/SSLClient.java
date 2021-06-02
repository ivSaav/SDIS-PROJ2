package main.g24.ssl;

import javax.net.ssl.*;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.security.*;
import java.security.cert.CertificateException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SSLClient extends SSLPeer {

    private SSLEngine sslEngine;
    private SocketChannel socketChannel;
    private String remoteAddr;
    private int port;

    public SSLClient(String remoteAddr, int port) throws NoSuchAlgorithmException, KeyManagementException, KeyStoreException, UnrecoverableKeyException, IOException, CertificateException {
        this.remoteAddr = remoteAddr;
        this.port = port;
        char[] password = "123456".toCharArray();
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(new FileInputStream("./keys/client.keys"), password);
        KeyStore trustStore = KeyStore.getInstance("PKCS12");
        trustStore.load(new FileInputStream("./keys/truststore"), password);
        KeyManagerFactory kmf = KeyManagerFactory.getInstance("PKIX");
        kmf.init(keyStore, password);
        TrustManagerFactory tmf = TrustManagerFactory.getInstance("PKIX");
        tmf.init(trustStore);
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
        this.sslEngine = context.createSSLEngine(remoteAddr, port);
        this.sslEngine.setUseClientMode(true);
        this.sslEngine.setEnabledCipherSuites(sslEngine.getSupportedCipherSuites());
        this.sslEngine.setEnabledProtocols(new String[] {"TLSv1.3"});


        SSLSession session = this.sslEngine.getSession();
        this.myAppData = ByteBuffer.allocate(session.getApplicationBufferSize());
        this.myNetData = ByteBuffer.allocate(session.getPacketBufferSize());
        this.peerAppData = ByteBuffer.allocate(session.getApplicationBufferSize());
        this.peerNetData = ByteBuffer.allocate(session.getPacketBufferSize());
    }

    public boolean connect() throws IOException {
        this.socketChannel = SocketChannel.open();
        this.socketChannel.configureBlocking(false);
        this.socketChannel.connect(new InetSocketAddress(this.remoteAddr, this.port));

        while(!this.socketChannel.finishConnect()) {
        }

        this.sslEngine.beginHandshake();
        return this.handshake(this.socketChannel, this.sslEngine);
    }

    public void shutdown() throws IOException {
        this.logger.log(Level.INFO, "Closing connection...");
        this.closeConnection(this.socketChannel, this.sslEngine);
        this.executor.shutdown();
        this.logger.log(Level.INFO, "Closed.");
    }

    public void read() throws Exception {
        this.read(this.socketChannel, this.sslEngine);
    }

    @Override
    protected void read(SocketChannel socketChannel, SSLEngine engine) throws Exception {
        this.logger.log(Level.INFO, "Reading from server...");
        this.peerNetData.clear();
        boolean exit = false;
        int delay    = 50;

        while(!exit) {
            int bytesRead = socketChannel.read(this.peerNetData);
            this.logger.log(Level.INFO, "Received " + bytesRead + " bytes");
            if (bytesRead > 0) {
                this.peerNetData.flip();

                while(this.peerNetData.hasRemaining()) {
                    this.peerAppData.clear();
                    SSLEngineResult result = engine.unwrap(this.peerNetData, this.peerAppData);
                    switch(result.getStatus()) {
                        case OK:
                            this.peerNetData.compact();
                            logger.log(Level.INFO, "Response: " + new String(this.peerAppData.array()));
                            exit = true;
                            break;
                        case BUFFER_OVERFLOW:
                            this.peerAppData = this.enlargeApplicationBuffer(engine, this.peerAppData);
                            break;
                        case BUFFER_UNDERFLOW:
                            this.peerNetData = this.handleBufferUnderflow(engine, this.peerNetData);
                            break;
                        case CLOSED:
                            this.closeConnection(socketChannel, engine);
                            return;
                        default:
                            throw new IllegalStateException("Invalid SSL status " + result.getStatus());
                    }
                }
            } else if (bytesRead < 0) {
                this.handleEOF(socketChannel, engine);
                return;
            }
            Thread.sleep(delay);
        }
    }

    public void write(String message) throws Exception {
        this.write(this.socketChannel, this.sslEngine, message);
    }

    @Override
    protected void write(SocketChannel socketChannel, SSLEngine engine, String message) throws Exception {
        this.logger.log(Level.INFO, "Writing to server...");
        this.myAppData.clear();
        this.myAppData.put(message.getBytes());
        this.myAppData.flip();

        while(this.myAppData.hasRemaining()) {
            this.myNetData.clear();
            SSLEngineResult result = engine.wrap(this.myAppData, this.myNetData);
            switch(result.getStatus()) {
                case OK:
                    this.myAppData.compact();

                    while(this.myNetData.hasRemaining()) {
                        socketChannel.write(this.myNetData);
                    }

                    this.logger.log(Level.INFO, "Message sent : " + message);
                    break;
                case BUFFER_OVERFLOW:
                    this.myNetData = this.enlargePacketBuffer(engine, this.myNetData);
                    break;
                case BUFFER_UNDERFLOW:
                    throw new SSLException("Buffer underflow on encryption, closing connection...");
                case CLOSED:
                    this.closeConnection(socketChannel, engine);
                    return;
                default:
                    throw new IllegalStateException("Invalid SSL status " + result.getStatus());
            }
        }
    }
}
