package main.g24.ssl;

import javax.net.ssl.*;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class SSLPeer {
    protected Logger logger = Logger.getLogger("main.g24.ssl");
    protected ByteBuffer myAppData;
    protected ByteBuffer myNetData;
    protected ByteBuffer peerAppData;
    protected ByteBuffer peerNetData;
    protected ExecutorService executor = Executors.newSingleThreadExecutor();

    protected abstract void read(SocketChannel socketChannel, SSLEngine engine) throws Exception;

    protected abstract void write(SocketChannel socketChannel, SSLEngine engine, String message) throws Exception;

    protected boolean handshake(SocketChannel socketChannel, SSLEngine engine) throws IOException {
        this.logger.log(Level.INFO, "Starting handshake protocol...");
        int appSize = engine.getSession().getApplicationBufferSize();
        ByteBuffer myAppData = ByteBuffer.allocate(appSize);
        ByteBuffer peerAppData = ByteBuffer.allocate(appSize);
        this.myNetData.clear();
        this.peerNetData.clear();
        SSLEngineResult.HandshakeStatus hs = engine.getHandshakeStatus();

        while(hs != SSLEngineResult.HandshakeStatus.FINISHED && hs != SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING) {
            SSLEngineResult result;
            switch(hs) {
                case NEED_UNWRAP:
                    if (socketChannel.read(this.peerNetData) < 0) {
                        if (engine.isInboundDone() && engine.isOutboundDone()) {
                            return false;
                        }

                        try {
                            engine.closeInbound();
                        } catch (SSLException e) {
                            this.logger.log(Level.SEVERE, "Engine forced to close inbound without receiving proper SSL close notification message due to end of stream");
                        }

                        engine.closeOutbound();
                        hs = engine.getHandshakeStatus();
                        break;
                    } else {
                        this.peerNetData.flip();
                        try {
                            result = engine.unwrap(this.peerNetData, peerAppData);
                            this.peerNetData.compact();
                            hs = engine.getHandshakeStatus();
                        } catch (SSLException e) {
                            this.logger.log(Level.SEVERE, "Error occurred when processing data, closing connection...");
                            e.printStackTrace();
                            engine.closeOutbound();
                            hs = engine.getHandshakeStatus();
                            break;
                        }

                        switch(result.getStatus()) {
                            case OK:
                                break;
                            case BUFFER_OVERFLOW:
                                peerAppData = this.enlargeApplicationBuffer(engine, peerAppData);
                                break;
                            case BUFFER_UNDERFLOW:
                                this.peerNetData = this.handleBufferUnderflow(engine, this.peerNetData);
                                break;
                            case CLOSED:
                                if (engine.isOutboundDone())
                                    return false;

                                engine.closeOutbound();
                                hs = engine.getHandshakeStatus();
                                break;
                            default:
                                throw new IllegalStateException("Invalid SSL status " + result.getStatus());
                        }
                    }
                    break;
                case NEED_WRAP:
                    this.myNetData.clear();

                    try {
                        result = engine.wrap(myAppData, this.myNetData);
                        hs = engine.getHandshakeStatus();
                    } catch (SSLException e) {
                        this.logger.log(Level.SEVERE, "Problem when wrapping data, closing connection...");
                        e.printStackTrace();
                        engine.closeOutbound();
                        hs = engine.getHandshakeStatus();
                        break;
                    }

                    switch(result.getStatus()) {
                        case OK:
                            this.myNetData.flip();

                            while(this.myNetData.hasRemaining()) {
                                socketChannel.write(this.myNetData);
                            }
                            break;
                        case BUFFER_OVERFLOW:
                            this.myNetData = this.enlargePacketBuffer(engine, this.myNetData);
                            break;
                        case BUFFER_UNDERFLOW:
                            throw new SSLException("Buffer underflow occurred after a wrap, closing connection...");
                        case CLOSED:
                            try {
                                this.myNetData.flip();

                                while(this.myNetData.hasRemaining()) {
                                    socketChannel.write(this.myNetData);
                                }

                                this.peerNetData.clear();
                            } catch (Exception e) {
                                this.logger.log(Level.WARNING, "Failed to send CLOSE message due to socket failure.");
                                hs = engine.getHandshakeStatus();
                            }
                            break;
                        default:
                            throw new IllegalStateException("Invalid SSL status " + result.getStatus());
                    }
                    break;
                case NEED_TASK:
                    Runnable task;
                    while((task = engine.getDelegatedTask()) != null) {
                        this.executor.execute(task);
                    }

                    hs = engine.getHandshakeStatus();
                    break;
                case FINISHED:
                case NOT_HANDSHAKING:
                    break;
                default:
                    throw new IllegalStateException("Invalid SSL status " + hs);
            }
        }

        return true;
    }

    protected ByteBuffer enlargeApplicationBuffer(SSLEngine engine, ByteBuffer buffer) {
        return this.enlargeBuffer(buffer, engine.getSession().getApplicationBufferSize());
    }

    protected ByteBuffer enlargePacketBuffer(SSLEngine engine, ByteBuffer buffer) {
        return this.enlargeBuffer(buffer, engine.getSession().getPacketBufferSize());
    }

    private ByteBuffer enlargeBuffer(ByteBuffer buffer, int size) {
        if (size > buffer.capacity()) {
            buffer = ByteBuffer.allocate(size);
        } else {
            buffer = buffer.compact();
        }

        return buffer;
    }

    protected ByteBuffer handleBufferUnderflow(SSLEngine engine, ByteBuffer buffer) {
        if (engine.getSession().getPacketBufferSize() < buffer.limit()) {
            return buffer;
        } else {
            return this.enlargePacketBuffer(engine, buffer);
        }
    }

    protected void handleEOF(SocketChannel socketChannel, SSLEngine engine) throws IOException {
        try {
            engine.closeInbound();
        } catch (Exception e) {
            this.logger.log(Level.WARNING, "SSL Engine forced to close inbound without receiving proper SSL close notification due to end of stream.");
        }

        this.closeConnection(socketChannel, engine);
    }

    protected void closeConnection(SocketChannel socketChannel, SSLEngine engine) throws IOException {
        engine.closeOutbound();
        this.handshake(socketChannel, engine);
        socketChannel.close();
    }

    protected KeyManager[] createKeyManagers(String var1, String var2, String var3) throws KeyStoreException, IOException, CertificateException, NoSuchAlgorithmException, UnrecoverableKeyException {
        KeyStore var4 = KeyStore.getInstance("JKS");
        FileInputStream var5 = new FileInputStream(var1);

        try {
            var4.load(var5, var2.toCharArray());
        } catch (Throwable var9) {
            try {
                var5.close();
            } catch (Throwable var8) {
                var9.addSuppressed(var8);
            }

            throw var9;
        }

        var5.close();
        KeyManagerFactory var10 = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        var10.init(var4, var3.toCharArray());
        return var10.getKeyManagers();
    }

    protected TrustManager[] createTrustManagers(String var1, String var2) throws IOException, KeyStoreException, CertificateException, NoSuchAlgorithmException {
        KeyStore var3 = KeyStore.getInstance("JKS");
        FileInputStream var4 = new FileInputStream(var1);

        try {
            var3.load(var4, var2.toCharArray());
        } catch (Throwable var8) {
            try {
                var4.close();
            } catch (Throwable var7) {
                var8.addSuppressed(var7);
            }

            throw var8;
        }

        var4.close();
        TrustManagerFactory var9 = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        var9.init(var3);
        return var9.getTrustManagers();
    }

}
