import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

/**
 * A test class which adds elements to a multiset.
 *
 * @author Orlando Leombruni
 * @see Test
 * @see RemoveCountContainsTest
 * @see RemoveAllTest
 */
public class AddTest extends Thread {

    private int start;
    private boolean testing;
    private Multiset<Integer> multiset;
    private CyclicBarrier barrier;

    /**
     * The constructor for the class.
     *
     * @param start The starting element to insert into the multiset
     * @param testing Whether or not the thread must wait for a barrier synchronization with other threads
     * @param multiset The multiset to be modified
     * @param barrier The barrier needed for thread synchronization (not meaningful if {@code testing == false})
     */
    public AddTest(int start, boolean testing, Multiset<Integer> multiset, CyclicBarrier barrier) {
        this.start = start;
        this.testing = testing;
        this.multiset = multiset;
        this.barrier = barrier;
    }

    /**
     * The main method of the class. It inserts four occurrences of each of
     * ({@link Test}{@code .SIZE - start})/{@link Test}{@code .THREAD_NUM}
     * elements in {@code multiset}, waiting for {@code barrier} synchronization if {@code testing == true}.
     *
     * @see Thread
     */
    public void run() {
        for (int i = start; i < Test.SIZE; i += Test.THREAD_NUM) {
            if (testing) {
                try {
                    barrier.await();
                } catch (InterruptedException | BrokenBarrierException e) {
                    e.printStackTrace();
                }
            }
            try {
                multiset.add(i);
                multiset.add(i, 3);
            } catch (IllegalArgumentException e1) {
                e1.printStackTrace();
            } catch (FullSetException e2) {
                System.out.println(this.getName() + " signals that the multiset is full");
                e2.printStackTrace();
            }
        }
    }
}
