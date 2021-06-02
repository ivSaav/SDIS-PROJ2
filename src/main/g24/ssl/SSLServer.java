package main.g24.ssl;

import javax.net.ssl.*;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.security.*;
import java.security.cert.CertificateException;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SSLServer extends SSLPeer {
    private boolean active;
    private final SSLContext context;
    private final Selector selector;

    public static void main(String[] var0) {
        try {
            SSLServer server = new SSLServer("localhost", 8089);
            server.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public SSLServer(String host, int port) throws NoSuchAlgorithmException, IOException, KeyManagementException, KeyStoreException, UnrecoverableKeyException, CertificateException {
        char[] password = "123456".toCharArray();
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(new FileInputStream("./keys/server.keys"), password);
        KeyStore trustStore = KeyStore.getInstance("PKCS12");
        trustStore.load(new FileInputStream("./keys/truststore"), password);
        KeyManagerFactory kmf = KeyManagerFactory.getInstance("PKIX");
        kmf.init(keyStore, password);
        TrustManagerFactory tmf = TrustManagerFactory.getInstance("PKIX");
        tmf.init(trustStore);
        this.context = SSLContext.getInstance("TLS");
        this.context.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
        SSLSession session = this.context.createSSLEngine().getSession();
        this.myAppData = ByteBuffer.allocate(session.getApplicationBufferSize());
        this.myNetData = ByteBuffer.allocate(session.getPacketBufferSize());
        this.peerAppData = ByteBuffer.allocate(session.getApplicationBufferSize());
        this.peerNetData = ByteBuffer.allocate(session.getPacketBufferSize());
        session.invalidate();
        this.selector = SelectorProvider.provider().openSelector();
        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.configureBlocking(false);
        serverSocketChannel.socket().bind(new InetSocketAddress(host, port));
        serverSocketChannel.register(this.selector, SelectionKey.OP_ACCEPT);
        this.active = true;
    }

    public boolean isActive() {
        return this.active;
    }

    public void start() throws Exception {
        this.logger.log(Level.INFO, "Server initialised and waiting for new connection");

        while(this.isActive()) {
            this.selector.select();
            Iterator<SelectionKey> selectedKeys = this.selector.selectedKeys().iterator();

            while(selectedKeys.hasNext()) {
                SelectionKey key = selectedKeys.next();
                System.out.println("SELECT KEY");
                System.out.println(key);
                System.out.println();
                selectedKeys.remove();
                if (key.isValid()) {
                    if (key.isAcceptable()) {
                        this.accept(key);
                    } else if (key.isReadable()) {
                        this.read((SocketChannel)key.channel(), (SSLEngine)key.attachment());
                    }
                }
            }
        }

    }

    public void stop() {
        this.logger.log(Level.INFO, "Closing server...");
        this.active = false;
        this.executor.shutdown();
        this.selector.wakeup();
    }

    private void accept(SelectionKey key) throws IOException {
        this.logger.log(Level.INFO, "New connection request");
        SocketChannel socketChannel = ((ServerSocketChannel)key.channel()).accept();
        socketChannel.configureBlocking(false);
        SSLEngine engine = this.context.createSSLEngine();
        engine.setUseClientMode(false);
        engine.setEnabledCipherSuites(engine.getSupportedCipherSuites());
        engine.setEnabledProtocols(new String[] {"TLSv1.3"});
        engine.beginHandshake();
        if (this.handshake(socketChannel, engine)) {
            socketChannel.register(this.selector, SelectionKey.OP_READ, engine);
        } else {
            socketChannel.close();
            this.logger.log(Level.INFO, "Connection closed due to failure on handshake");
        }

    }

    @Override
    protected void read(SocketChannel socketChannel, SSLEngine engine) throws Exception {
        this.logger.log(Level.INFO, "Reading from client...");
        this.peerNetData.clear();
        int bytesRead = socketChannel.read(this.peerNetData);
        if (bytesRead > 0) {
            this.peerNetData.flip();

            while(this.peerNetData.hasRemaining()) {
                this.peerAppData.clear();
                SSLEngineResult result = engine.unwrap(this.peerNetData, this.peerAppData);
                switch(result.getStatus()) {
                    case OK:
                        this.peerAppData.flip();
                        logger.log(Level.INFO, "Received: " + new String(peerAppData.array()));
                        break;
                    case BUFFER_OVERFLOW:
                        this.peerAppData = this.enlargeApplicationBuffer(engine, this.peerAppData);
                        break;
                    case BUFFER_UNDERFLOW:
                        this.peerNetData = this.handleBufferUnderflow(engine, this.peerNetData);
                        break;
                    case CLOSED:
                        this.logger.log(Level.INFO, "Client closed the connection...");
                        this.closeConnection(socketChannel, engine);
                        this.logger.log(Level.INFO, "Sayonara :)");
                        return;
                    default:
                        throw new IllegalStateException("Invalid SSL status " + result.getStatus());
                }
            }

            this.write(socketChannel, engine, "Ahaha");
        } else if (bytesRead < 0) {
            this.logger.log(Level.WARNING, "Received EOF, closing connection...");
            this.handleEOF(socketChannel, engine);
            this.logger.log(Level.INFO, "Just shup up already. I have nothing more to say to you. You're way too pathetic... I'm done wasting my breath.");
        }
    }

    protected void write(SocketChannel socketChannel, SSLEngine engine, String message) throws Exception {
        this.logger.log(Level.INFO, "Writing to client...");
        this.myAppData.clear();
        this.myAppData.put(message.getBytes());
        this.myAppData.flip();

        while(this.myAppData.hasRemaining()) {
            this.myNetData.clear();
            SSLEngineResult result = engine.wrap(this.myAppData, this.myNetData);
            switch(result.getStatus()) {
                case OK:
                    this.myNetData.compact();
                    while(this.myNetData.hasRemaining()) {
                        socketChannel.write(this.myNetData);
                    }

                    this.logger.log(Level.INFO, "Message sent : " + message);
                    break;
                case BUFFER_OVERFLOW:
                    this.myNetData = this.enlargePacketBuffer(engine, this.myNetData);
                    break;
                case BUFFER_UNDERFLOW:
                    throw new SSLException("Buffer underflow after encrypting data...");
                case CLOSED:
                    this.closeConnection(socketChannel, engine);
                    return;
                default:
                    throw new IllegalStateException("Invalid SSL status " + result.getStatus());
            }
        }

    }
}
