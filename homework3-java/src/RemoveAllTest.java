import java.util.Collection;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

/**
 * A test class which removes all elements of a collection from a multiset.
 *
 * @author Orlando Leombruni
 * @see Test
 * @see RemoveCountContainsTest
 * @see AddTest
 */
public class RemoveAllTest extends Thread {

    private boolean testing;
    private Multiset<Integer> multiset;
    private CyclicBarrier barrier;
    private Collection<?> coll;
    private boolean removing;

    /**
     * The constructor for the class.
     *
     * @param type Whether this thread should execute a removeAll (0) or add (1) operation.
     * @param testing Whether or not the thread must wait for a barrier synchronization with other threads
     * @param multiset The multiset to be modified
     * @param barrier The barrier needed for thread synchronization (not meaningful if {@code testing == false})
     * @param c The collection whose elements must be removed from {@code multiset}
     */
    public RemoveAllTest(int type, boolean testing, Multiset<Integer> multiset, CyclicBarrier barrier, Collection<?> c) {
        this.testing = testing;
        this.multiset = multiset;
        this.barrier = barrier;
        this.coll = c;
        this.removing = type == 0;
    }

    /**
     * The main method of the class. It removes all of {@code c}'s elements
     * from {@code multiset}, after waiting for {@code barrier} synchronization if {@code testing == true}.
     *
     * @see Thread
     */
    public void run() {
        if (testing) {
            try {
                barrier.await();
            } catch (InterruptedException | BrokenBarrierException e) {
                e.printStackTrace();
            }
        }
        try {
            if (removing) {
                multiset.removeAll(coll);
            }
            else {
                multiset.add(100000);
            }
        } catch (IllegalArgumentException | AssertionError e1) {
            e1.printStackTrace();
        }
    }

}

