package main.g24.chord;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Node implements INode {

    private static final int CHORD_BITS = 8, CHORD_SIZE = (int) Math.pow(2,  CHORD_BITS);

    private final int id;
    private int nextFingerCheck = 0;
    private INode predecessor, successor;

    List<INode> fingers;

    public Node(int id) {
        this.id = id;
        this.fingers = new ArrayList<>(Arrays.asList(new INode[CHORD_BITS+1]));
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

    private boolean is_in_successor_range(int id) throws RemoteException {
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
        else
            return successor.find_successor(id);
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
        for (int i = CHORD_BITS-1; i > 0; i--) {
            INode finger = fingers.get(i);

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
        successor = node.closest_preceding_node(node.get_id());
    }

    /**
     * Called periodically.
     * Asks the successor about its predecessor, verifies if n's immediate
     * successor is consistent, and tells the successor about this
     * @return
     */
    public void stabilize() throws RemoteException {
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
        nextFingerCheck++;
        if (nextFingerCheck >= CHORD_BITS)
            nextFingerCheck = 0;

        fingers.set(nextFingerCheck, find_successor(id + CHORD_SIZE));
    }

    /**
     * Called periodically.
     * Checks whether predecessor has failed.
     */
    @Override
    public void check_predecessor() throws RemoteException {
        if (!predecessor.alive()) {
            predecessor = null;
            on_predecessor_death();
        }
    }

    protected void on_predecessor_death() {}

    @Override
    public String toString() {
        try {
            return "Node {" +
                    "id=" + id +
                    ", nextFingerCheck=" + nextFingerCheck +
                    ", predecessor=" + (predecessor != null ? predecessor.get_id() : null) +
                    ", successor=" + (successor != null ? successor.get_id() : null) +
                    '}';
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return "";
    }
}
