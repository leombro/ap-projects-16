import java.io.*;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;

/**
 * <p>A comprehensive test for {@link Multiset}'s functionality and performance.</p>
 *
 * <p>For each of the four provided implementations ({@link CoarseGrainedMultiset}, {@link FineGrainedMultiset},
 * {@link LazyMultiset}, {@link LockFreeMultiset}) the class tests all major methods (both overloads of {@code add},
 * both overloads of {@code remove}, {@code count}, {@code contains}, {@code removeAll}). First, every method is
 * tested with a thread barrier in order to check their correctness. Then, the barrier is removed and their performances
 * (measured as a function of time) are printed and collected. The whole process is repeated using
 * 1, 10, 100 and 1000 threads.</p>
 *
 * <p>Upon successful completion of all tasks, the test creates a file (named {@code log.txt}) with detailed results
 * on the completion times of the various operations, together with an indication of which kind of implementation
 * provides the fastest results for that particular operation and how much an implementation "scales" up when the
 * number of threads increases.</p>
 *
 * @author Orlando Leombruni
 * @see AddTest
 * @see RemoveAllTest
 * @see RemoveCountContainsTest
 */
public class Test {

    public static int THREAD_NUM = 1; /** Number of threads. */
    public static int SIZE = 10000; /** Number of (unique) elements to insert in the multiset */
    private static int CAPACITY = 40000;

    /**
     * Compares the performance of the various implementations (with regards to a single operation)
     * and produces a string which indicates the name of the implementation which provides the best result.
     * @param arr An array of 4 time measurements (in seconds) for the completion of a multiset operation
     *            according to 4 different implementations, in this order: coarse-grained, fine-grained, lazy, lock-free.
     * @return A string containing the name of the implementation which provided the best result.
     */
    public static String getBest(double[] arr) {
        int best = 0;
        for (int i = 1; i < 4; i++) {
            if (arr[i] < arr[best]) best = i;
        }
        switch (best) {
            case 0: return "COARSE-G";
            case 1: return "FINE-G";
            case 2: return "LAZY";
            case 3: return "LOCKFREE";
            default: return "ERROR";
        }
    }

    /**
     * An utility funtion to print the inside of an ASCII box like this one:
     * {@code | RESULTS (100 Threads)                                |}
     * mantaining the same width regardless of the number of threads printed inside
     * the parentheses.
     *
     * @param thread_num The number of threads to print.
     * @return The formatted string.
     */
    public static String prettyformat(int thread_num) {
        StringBuilder sb = new StringBuilder(String.format("| RESULTS (%d Thread", thread_num));
        if (thread_num > 1) sb.append('s');
        sb.append(")");
        if (thread_num == 1) sb.append("                                   ");
        if (thread_num == 10) sb.append("                                 ");
        if (thread_num == 100) sb.append("                                ");
        if (thread_num == 1000) sb.append("                               ");
        sb.append("|");
        return sb.toString();
    }

    /**
     * An utility function to compute the scalability of an implementation of {@link Multiset}
     * (with regards to a specific operation) given the time needed to complete a task with 1,
     * 10, 100 and 1000 threads.
     *
     * @param a The time measurement (in seconds) for the completion of the operation using 1 thread.
     * @param b The time measurement (in seconds) for the completion of the operation using 10 threads.
     * @param c The time measurement (in seconds) for the completion of the operation using 100 threads.
     * @param d The time measurement (in seconds) for the completion of the operation using 1000 threads.
     * @return A string containing the scalability factors with respect to the sequential (1 thread) case.
     */
    public static String getScalability(double a, double b, double c, double d) {
        double r1 = a/b, r2 = a/c, r3 = a/d;
        return String.format("x10:  %.1f, x100: %.1f, x1000: %.1f", r1, r2, r3);
    }

    /**
     * The method which gets executed. More info on the tests is in the description of the class.
     * @param args The command-line arguments.
     */
    @SuppressWarnings("unchecked")
    public static void main(String[] args) {
        Multiset<Integer> multiset = null;
        CyclicBarrier barr;
        List<Thread> threads;
        long time;
        double secs;
        double[][] resultsAdd = new double[4][4],
                resultsRemove = new double[4][4],
                resultsRAll = new double[4][4];

        System.out.println("All tests are passed if no exception is raised");

        for (int m = 0; m < 4; m++) {

            /* Setup */

            THREAD_NUM = (int)Math.pow(10, m);
            barr = new CyclicBarrier(THREAD_NUM);
            for (int i = 0; i < 4; i++) {
                switch (i) {
                    case 0:
                        System.out.println("TEST: COARSE-GRAINED (" + THREAD_NUM + " thread(s))");
                        multiset = new CoarseGrainedMultiset<>(CAPACITY);
                        break;
                    case 1:
                        System.out.println("TEST: FINE-GRAINED (" + THREAD_NUM + " thread(s))");
                        multiset = new FineGrainedMultiset<>(CAPACITY);
                        break;
                    case 2:
                        System.out.println("TEST: LAZY (" + THREAD_NUM + " thread(s))");
                        multiset = new LazyMultiset<>(CAPACITY);
                        break;
                    case 3:
                        System.out.println("TEST: LOCK-FREE (" + THREAD_NUM + " thread(s))");
                        multiset = new LockFreeMultiset<>(CAPACITY);
                        break;
                }

                // Tests for the add method

                System.out.println("Testing ADD...(" + THREAD_NUM + " thread(s))");

                threads = new LinkedList<>();

                for (int j = 0; j < THREAD_NUM; j++) {
                    threads.add(new AddTest(j, true, multiset, barr));
                }

                for (Thread t : threads) t.start();

                for (Thread t : threads) {
                    try {
                        t.join();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

                assert (multiset.size() == (SIZE * 4)) : multiset.size();

                // Tests for the remove/contains/count methods

                System.out.println("Testing REMOVE...(" + THREAD_NUM + " thread(s))");

                threads = new LinkedList<>();

                for (int j = 0; j < THREAD_NUM; j++) {
                    threads.add(new RemoveCountContainsTest(j, true, multiset, barr));
                }

                for (Thread t : threads) t.start();

                for (Thread t : threads) {
                    try {
                        t.join();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

                assert (multiset.size() == 0) : multiset.toString();

                // Tests for the removeAll method

                System.out.println("Testing REMOVEALL...(" + THREAD_NUM + " thread(s))");

                List<Integer>[] toRemove = (List<Integer>[]) new LinkedList[THREAD_NUM];
                threads = new LinkedList<>();

                for (int j = 0; j < THREAD_NUM; j++) {
                    toRemove[j] = new LinkedList<>();
                    threads.add(new RemoveAllTest(j%2, true, multiset, barr, toRemove[j]));
                }

                for (int j = 0; j < SIZE; j++) {
                    if (j % 2 == 0) {
                        toRemove[(j % THREAD_NUM)].add(j);
                        multiset.add(j);
                    }
                }


                for (Thread t : threads) t.start();

                for (Thread t : threads) {
                    try {
                        t.join();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

                int expected = THREAD_NUM == 1 ? 0 : THREAD_NUM/2;
                if (multiset.size() != expected) System.out.println(multiset);
                assert (multiset.size() == expected) : multiset.size();

                while (multiset.size() > 0) {
                    multiset.remove(100000);
                }

                // Performance of add

                System.out.println("Testing ADD's performance...(" + THREAD_NUM + " thread(s))");

                threads = new LinkedList<>();

                for (int j = 0; j < THREAD_NUM; j++) {
                    threads.add(new AddTest(j, false, multiset, barr));
                }

                time = System.nanoTime();

                for (Thread t : threads) t.start();

                for (Thread t : threads) {
                    try {
                        t.join();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

                time = System.nanoTime() - time;
                secs = time / (Math.pow(10, 9));
                resultsAdd[m][i] = secs;
                System.out.println(String.format("ADD completed in %.2fs", secs));

                // Performance of REMOVE

                System.out.println("Testing REMOVE's performance...(" + THREAD_NUM + " thread(s))");

                threads = new LinkedList<>();

                for (int j = 0; j < THREAD_NUM; j++) {
                    threads.add(new RemoveCountContainsTest(j, false, multiset, barr));
                }

                time = System.nanoTime();

                for (Thread t : threads) t.start();

                for (Thread t : threads) {
                    try {
                        t.join();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

                time = System.nanoTime() - time;
                secs = time / (Math.pow(10, 9));
                resultsRemove[m][i] = secs;
                System.out.println(String.format("REMOVE completed in %.2fs", secs));

                // Performance of REMOVEALL

                System.out.println("Testing performance of REMOVEALL...(" + THREAD_NUM + " thread(s))");

                List<Integer>[] toRemove2 = (List<Integer>[]) new LinkedList[THREAD_NUM];
                threads = new LinkedList<>();

                for (int j = 0; j < THREAD_NUM; j++) {
                    toRemove2[j] = new LinkedList<>();
                    threads.add(new RemoveAllTest(j%2, false, multiset, barr, toRemove2[j]));
                }

                for (int j = 0; j < SIZE; j++) {
                    if (j % 2 == 0) {
                        toRemove2[(j % THREAD_NUM)].add(j);
                        multiset.add(j);
                    }
                }

                time = System.nanoTime();

                for (Thread t : threads) t.start();

                for (Thread t : threads) {
                    try {
                        t.join();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

                time = System.nanoTime() - time;
                secs = time / (Math.pow(10, 9));
                resultsRAll[m][i] = secs;
                System.out.println(String.format("REMOVEALL completed in %.2fs", secs));
            }

        }
        try (PrintWriter w = new PrintWriter(new BufferedWriter(new FileWriter(new File("log.txt"))))) {
            for (int m = 0; m < 4; m++) {
                THREAD_NUM = (int) Math.pow(10, m);
                w.println("+------------------------------------------------------+");
                w.println(prettyformat(THREAD_NUM));
                w.println("+------------------------------------------------------+");
                w.println("        COARSE-G FINE-G  LAZY    LOCKFREE   BEST");
                w.println(String.format("ADD     %.3f    %.3f   %.3f   %.3f      %s",
                        resultsAdd[m][0], resultsAdd[m][1],
                        resultsAdd[m][2], resultsAdd[m][3], getBest(resultsAdd[m])));
                w.println(String.format("REMOVE  %.3f    %.3f   %.3f   %.3f      %s",
                        resultsRemove[m][0], resultsRemove[m][1],
                        resultsRemove[m][2], resultsRemove[m][3], getBest(resultsRemove[m])));
                w.println(String.format("REMALL  %.3f    %.3f   %.3f   %.3f      %s",
                        resultsRAll[m][0], resultsRAll[m][1],
                        resultsRAll[m][2], resultsRAll[m][3], getBest(resultsRAll[m])));
            }

            w.println("+------------------------------------------------------+");
            w.println("| Scalabilities                                        |");
            w.println("+--------+---------------------------------------------+");
            w.println("| ADD    |");
            w.println("+--------+");
            w.println("Scalability of COARSE-G: " + getScalability(resultsAdd[0][0], resultsAdd[1][0],
                    resultsAdd[2][0], resultsAdd[3][0]));
            w.println("Scalability of FINE-G: " + getScalability(resultsAdd[0][1], resultsAdd[1][1],
                    resultsAdd[2][1], resultsAdd[3][1]));
            w.println("Scalability of LAZY: " + getScalability(resultsAdd[0][2], resultsAdd[1][2],
                    resultsAdd[2][2], resultsAdd[3][2]));
            w.println("Scalability of LOCKFREE: " + getScalability(resultsAdd[0][3], resultsAdd[1][3],
                    resultsAdd[2][3], resultsAdd[3][3]));

            w.println("+--------+");
            w.println("| REMOVE |");
            w.println("+--------+");
            w.println("Scalability of COARSE-G: " + getScalability(resultsRemove[0][0], resultsRemove[1][0],
                    resultsRemove[2][0], resultsRemove[3][0]));
            w.println("Scalability of FINE-G: " + getScalability(resultsRemove[0][1], resultsRemove[1][1],
                    resultsRemove[2][1], resultsRemove[3][1]));
            w.println("Scalability of LAZY: " + getScalability(resultsRemove[0][2], resultsRemove[1][2],
                    resultsRemove[2][2], resultsRemove[3][2]));
            w.println("Scalability of LOCKFREE: " + getScalability(resultsRemove[0][3], resultsRemove[1][3],
                    resultsRemove[2][3], resultsRemove[3][3]));

            w.println("+--------+");
            w.println("| REMALL |");
            w.println("+--------+");
            w.println("Scalability of COARSE-G: " + getScalability(resultsRAll[0][0], resultsRAll[1][0],
                    resultsRAll[2][0], resultsRAll[3][0]));
            w.println("Scalability of FINE-G: " + getScalability(resultsRAll[0][1], resultsRAll[1][1],
                    resultsRAll[2][1], resultsRAll[3][1]));
            w.println("Scalability of LAZY: " + getScalability(resultsRAll[0][2], resultsRAll[1][2],
                    resultsRAll[2][2], resultsRAll[3][2]));
            w.println("Scalability of LOCKFREE: " + getScalability(resultsRAll[0][3], resultsRAll[1][3],
                    resultsRAll[2][3], resultsRAll[3][3]));
            System.out.println("Detailed results are now available in the log.txt file");
        } catch (IOException e) {
            System.err.println("Could not write logfile");
        }
    }
}
