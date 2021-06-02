package main.g24.chord;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.rmi.RemoteException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Node implements INode {

    protected static final int CHORD_BITS = 8, CHORD_SIZE = (int) Math.pow(2,  CHORD_BITS);

    protected final int id;
    private int nextFingerCheck = 1;
    private INode predecessor, successor;
    protected final InetAddress addr;
    protected final int port;

    protected List<INode> fingers;

    public Node(InetAddress addr, int port) {
        this.id = chordID(addr.getHostName(), port);
        this.addr = addr;
        this.port = port;
        this.fingers = new ArrayList<>(Arrays.asList(new INode[CHORD_BITS+1]));

        for (int i = 1; i < fingers.size(); i++) {
            fingers.set(i, this);
        }
    }

    public static int chordID(String ip, int port) {
        return chordID(ip + ":" + port);
    }

    public static int chordID(String s) {
        final MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA3-256");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return 0;
        }
        final byte[] hashbytes = digest.digest(s.getBytes(StandardCharsets.US_ASCII));
        return new BigInteger(hashbytes).mod(BigInteger.valueOf(CHORD_SIZE)).intValue();
    }

    @Override
    public int get_id() {
        return id;
    }

    @Override
    public INode get_predecessor() {
        return predecessor;
    }

    @Override
    public INode get_successor() {
        return successor;
    }

    @Override
    public InetAddress get_address() throws RemoteException { return this.addr; }

    @Override
    public InetSocketAddress get_socket_address() throws RemoteException {
        return new InetSocketAddress(this.addr, this.port);
    }

    @Override
    public int get_port() throws RemoteException { return this.port; }

    private boolean is_in_successor_range(int id) throws RemoteException {
        if (!isAlive(successor))
            successor = this.find_next_live();
            
        int succ_id = successor.get_id();
        if (succ_id > this.id) {
            // Regular interval
            return id > this.id && id <= succ_id;
        } else {
            // Interval loops around
            return id > this.id  || id <= succ_id;
        }
    }

    /**
     * Ask node n to find the successor of id
     * @param id
     * @return
     */
    @Override
    public INode find_successor(int id) throws RemoteException {
        if (is_in_successor_range(id))
            return successor;
        else {
            INode n0 = this.closest_preceding_node(id);
            return n0.find_successor(id);
        }
    }

    private boolean is_in_range(int start, int end, int id) {
        if (end > start) {
            // Regular interval
            return id > start && id < end;
        } else {
            // Interval loops around
            return id > start  || id < end;
        }
    }

    /**
     * Search the local table for the highest predecessor of id
     * @param id
     * @return
     */
    @Override
    public INode closest_preceding_node(int id) throws RemoteException {
        for (int i = CHORD_BITS; i > 0; i--) {
            INode finger = fingers.get(i);

            if (!isAlive(finger))
                continue;

            if (finger != null && is_in_range(this.id, id, finger.get_id()))
                return finger;
        }
        return this;
    }

    /**
     * Create a new Chord ring.
     */
    @Override
    public void create() {
        predecessor = null;
        successor = this;
    }

    /**
     * Join a Chord ring containing node n
     * @param node
     */
    @Override
    public void join(INode node) throws RemoteException {
        predecessor = null;
        successor = node.find_successor(node.get_id());
    }

    public INode find_next_live() {
        for (int i = 1; i<this.fingers.size(); i++) {
            if (isAlive(fingers.get(i)))
                return fingers.get(i);
        }
        return this;
    }

    /**
     * Called periodically.
     * Asks the successor about its predecessor, verifies if n's immediate
     * successor is consistent, and tells the successor about this
     * @return
     */
    public void stabilize() throws RemoteException {

        if (!isAlive(successor))
            successor = this.find_next_live();

        INode x = successor.get_predecessor();

        if (x != null && is_in_range(id, successor.get_id(), x.get_id())) {
            successor = x;
            on_new_successor();
        }

        successor.notify(this);
    }

    protected void on_new_successor() {}

    /**
     * Node thinks it might be our predecessor
     * @param node
     */
    @Override
    public void notify(INode node) throws RemoteException {
        if (predecessor == null || is_in_range(predecessor.get_id(), id, node.get_id())) {
            predecessor = node;
            on_new_predecessor();
        }
    }

    protected void on_new_predecessor() {}

    /**
     * Called periodically.
     * Refreshes finger table entries.
     * nextFingerCheck stores the index of the finger to fix
     */
    @Override
    public void fix_fingers() throws RemoteException {
        nextFingerCheck = (nextFingerCheck + 1) % CHORD_BITS + 1;

        if (!isAlive(successor))
            successor = this.find_next_live();

        fingers.set(nextFingerCheck, find_successor(id + (int) Math.pow(2, nextFingerCheck)));
    }

    /**
     * Called periodically.
     * Checks whether predecessor has failed.
     */
    @Override
    public void check_predecessor() throws RemoteException {
        if (predecessor != null && !isAlive(predecessor)) {
            predecessor = null;
            on_predecessor_death();
        }
    }

    protected void on_predecessor_death() {}

    protected static boolean isAlive(INode node) {
        try {
            node.alive();
        } catch (Exception e) {
            return false;
        }
        return true;
    }
}
