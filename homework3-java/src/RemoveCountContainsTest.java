import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

/**
 * A test class which removes elements from a multiset.
 *
 * @author Orlando Leombruni
 * @see Test
 * @see AddTest
 * @see RemoveAllTest
 */
public class RemoveCountContainsTest extends Thread {

        private int start;
        private boolean testing;
        private Multiset<Integer> multiset;
        private CyclicBarrier barrier;

        /**
         * The constructor for the class.
         *
         * @param start The starting element to be removed from the multiset
         * @param testing Whether or not the thread must wait for a barrier synchronization with other threads
         * @param multiset The multiset to be modified
         * @param barrier The barrier needed for thread synchronization (not meaningful if {@code testing == false})
         */
        public RemoveCountContainsTest(int start, boolean testing, Multiset<Integer> multiset, CyclicBarrier barrier) {
            this.start = start;
            this.testing = testing;
            this.multiset = multiset;
            this.barrier = barrier;
        }

        /**
         * The main method of the class. It tries to remove four occurrences of each of
         * ({@link Test}{@code .SIZE - start})/{@link Test}{@code .THREAD_NUM}
         * elements in {@code multiset}, waiting for {@code barrier} synchronization if {@code testing == true}.
         *
         * It calls the multiset's {@code contains} and {@code counts} methods before removing the elements, in order
         * to have a more comprehensive test.
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
                    if (multiset.contains(i)) {
                        if (multiset.count(i) >= 2) {
                            multiset.remove(i, 2);
                        } else System.out.println("boop1 " + i);
                    } else System.out.println("boop2 " + i);
                    if (multiset.contains(i)) {
                        if (multiset.count(i) >= 1) {
                            multiset.remove(i);
                        } else System.out.println("boop3 " + i);
                    } else System.out.println("boop4 " + i);
                    if (multiset.contains(i)) {
                        if (multiset.count(i) >= 1) {
                            multiset.remove(i, 1);
                        } else System.out.println("boop5 " + i);
                    } else System.out.println("boop6 " + i);
                } catch (IllegalArgumentException e1) {
                    e1.printStackTrace();
                }
            }
        }

}
