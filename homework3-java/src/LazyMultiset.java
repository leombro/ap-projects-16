import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * An implementation of a concurrent {@link Multiset} whose underlying data
 * structure is a linked list composed of independently-lockable nodes.
 * The nodes are locked only when performing operations that modify the multiset, and
 * only at the locus of modification; additional measures must be taken to ensure
 * consistency when performing a read-only operation concurrently with a modification.
 *
 * @author Orlando Leombruni
 * @see Multiset
 * @see ConcurrentMultiset
 */
public class LazyMultiset<T> extends ConcurrentMultiset<T> {

    /**
     * An element of the data structure on which a {@link LazyMultiset}
     * is constructed upon. It is a linked list with sentinels, in which every node can be
     * locked independently from the others. Every node can also be marked logically;
     * this is needed since read-only operations do not lock the elements, thus potentially
     * accessing a node that has been deleted. In that case, the presence of the mark
     * signals that the result is incorrect and the read-only operation should be repeated.
     *
     * @param <E> The type of the elements of the linked list.
     */
    private static final class MultisetEntry<E> {
        private final E element;
        private volatile int count;
        private volatile MultisetEntry<E> next;
        private ReentrantLock lock;
        private volatile boolean mark;

        /**
         * The constructor used when the object to be created is a non-sentinel node.
         * @param element The element of this list node.
         * @param count The number of occurrences of the element in the multiset.
         * @param next The successor of this node in the list.
         */
        MultisetEntry(E element, int count, MultisetEntry<E> next) {
            this.element = element;
            this.count = count;
            this.next = next;
            lock = new ReentrantLock();
            mark = false;
        }

        /**
         * The constructor used when the object to be created is a sentinel node.
         */
        MultisetEntry() {
            this(null, -1, null);
        }

        /**
         * Getter method for the element of the node.
         * @return The element of the node.
         */
        E getElement() {
            return element;
        }

        /**
         * Getter method for the number of occurrences of the node's element.
         * @return The number of occurrences of the node's element.
         */
        int getCount() {
            return count;
        }

        /**
         * Changes the number of occurrences of the node's element.
         * @param delta The quantity (positive or negative) to be added to the current number of occurrences.
         * @return The number of occurrences before this operation.
         */
        int changeCount(int delta) {
            int tempcount = this.count;
            this.count += delta;
            return tempcount;
        }

        /**
         * Tries to acquire the lock to the current element.
         */
        void lock() {
            lock.lock();
        }

        /**
         * Unlocks the current element.
         */
        void unlock() {
            lock.unlock();
        }

        /**
         * Getter method for the node's successor.
         * @return The successor of the node.
         */
        MultisetEntry<E> getNext() {
            return next;
        }

        /**
         * Setter method to change this node's successor.
         * @param next The node which will become this node's successor.
         */
        void setNext(MultisetEntry<E> next) {
            this.next = next;
        }

        /**
         * Method to check if the node is logically marked as deleted.
         * @return true if the element is marked.
         */
        boolean isMarked() {
            return mark;
        }

        /**
         * Marks the node as deleted.
         */
        void setMark() {
            this.mark = true;
        }

        /**
         * Checks if this node is a sentinel.
         * @return {@code true} if this node is a sentinel.
         */
        boolean isSentinel() {
            return (element == null);
        }

        /**
         * Provides a textual representation of this node.
         * @return A {@link String} representing a textual representation of this node.
         */
        @Override
        public String toString() {
            if (mark) return "(marked " + element.toString() + ")";
            else return "(" + element.toString() + ": " + count + ")";
        }
    }

    private volatile AtomicInteger size;
    private Integer capacity;
    private MultisetEntry<T> head;
    private DoubleLock ml;

    /**
     * The constructor for a LazyMultiset, in which the maximum capacity is specified.
     * @param capacity The maximum capacity of the multiset.
     */
    public LazyMultiset(int capacity) {
        size = new AtomicInteger(0);
        this.capacity = capacity;
        head = new MultisetEntry<>();
        head.setNext(new MultisetEntry<>());
        ml = new DoubleLock();
    }

    /**
     * The constructor for a LazyMultiset, in which the maximum capacity is left
     * unspecified (no limits on the multiset's size).
     */
    public LazyMultiset() {
        this(0);
    }

    /**
     * An utility method to search if an element is in the multiset, without locking them.
     * @param elem The element to be found.
     * @return The <i>predecessor</i> node to either <ul>
     * <li>the node that contains the desired element, if this one is contained in the multiset</li>
     * <li>the right (tail) sentinel node, if the element is not contained in the multiset</li>
     * </ul>
     */
    private MultisetEntry<T> find(Object elem) {
        MultisetEntry<T> prev = head;
        MultisetEntry<T> curr = head.getNext();
        while (!curr.isSentinel() && !elem.equals(curr.getElement())) {
            prev = curr;
            curr = curr.getNext();
        }
        return prev;
    }

    /**
     * Utility method to check that a node and its connection to the list are still valid
     * (i.e. the predecessor is not marked, the node is still the predecessor's successor,
     * and the node contains the element that is expected).
     *
     * @param elem The element expected to be contained in the current node.
     * @param prev The node's predecessor.
     * @param curr The current node.
     * @return true if the node is valid as per the above metrics.
     */
    private boolean validate(Object elem, MultisetEntry<T> prev, MultisetEntry<T> curr) {
        return
                !prev.isMarked()
                && curr == prev.getNext()
                && (curr.isSentinel() || elem.equals(curr.getElement()));
    }

    /**
     * An utility method to increase the size of the multiset, failing when {@code capacity} is reached.
     * @param delta The increment.
     * @throws FullSetException If the maximum capacity is reached.
     */
    private void increaseSize(int delta) throws FullSetException {
        size.addAndGet(delta);
        if (capacity > 0) {
            if (size.get() > capacity) {
                size.addAndGet(-delta);
                throw new FullSetException("Cannot add the requested number of entries");
            }
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>This method uses the {@code find} method to transverse the underlying list and then locks
     * the elements at the point of the insertion/modification.</p>
     *
     * <p>Note that if a RemoveAll operation is currently being executed, this method waits for its
     * completion before doing anything.</p>
     *
     * @param element The element to be inserted.
     * @param occurrences The number of occurrences.
     * @return The number of occurrences of the element that were present in the multiset
     * before this operation (zero if the element wasn't in the multiset).
     * @throws IllegalArgumentException If {@code element == null} or {@code occurrences} is 0 or less.
     * @throws FullSetException If this operation causes the multiset to grow beyond its maximum capacity.
     */
    @Override
    public int add(T element, int occurrences) throws IllegalArgumentException, FullSetException {
        if (element == null) throw new IllegalArgumentException("Null elements are not permitted");
        if (occurrences < 1) throw new IllegalArgumentException("Occurrences must be a positive number");
        boolean locked = ml.group2Lock();
        boolean valid = false;
        int count = 0;
        MultisetEntry<T> prev = null, curr = null;
        while (!valid) {
            try {
                prev = find(element);
                curr = prev.getNext();
                prev.lock();
                curr.lock();
                valid = validate(element, prev, curr);
                if (valid) {
                    increaseSize(occurrences);
                    if (curr.isSentinel()) {
                        MultisetEntry<T> newEl = new MultisetEntry<>(element, occurrences, curr);
                        prev.setNext(newEl); // Linearization point of a successful Add call, if
                                             // the element wasn't in the multiset
                    } else {
                        count = curr.changeCount(occurrences);  // Linearization point of a successful Add call, if
                                                                // the element is already in the multiset
                    }
                }
            } finally {
                if (prev != null) prev.unlock();
                if (curr != null) curr.unlock();
                if (locked) ml.group2Unlock();
            }
        }
        return count;
    }

    /**
     * {@inheritDoc}
     *
     * <p>This method uses the {@code find} method to transverse the underlying list and then locks
     * the elements at the point of the removal.</p>
     *
     * <p>Note that if a RemoveAll operation is currently being executed, this method waits for its
     * completion before doing anything.</p>
     *
     * @param element The element to be removed.
     * @param occurrences The number of occurrences.
     * @return The number of occurrences of the element before this operation, if any.
     * @throws IllegalArgumentException If {@code element == null} or {@code occurrences} is 0 or less.
     */
    @Override
    public int remove(Object element, int occurrences) throws IllegalArgumentException {
        if (element == null) throw new IllegalArgumentException("Null elements are not permitted");
        if (occurrences < 1) throw new IllegalArgumentException("Occurrences must be a positive number");
        boolean locked = ml.group2Lock();
        int count = 0;
        boolean valid = false;
        MultisetEntry<T> prev = null, curr = null;
        while (!valid) {
            try {
                prev = find(element);
                curr = prev.getNext();
                prev.lock();
                curr.lock();
                valid = validate(element, prev, curr);
                if (valid) {
                    if (!curr.isSentinel() && element.equals(curr.getElement())) {
                        count = curr.getCount();
                        if (count > occurrences) {
                            curr.changeCount(-occurrences); // Linearization point of a successful Remove call, if
                                                            // occurrences < count
                            size.addAndGet(-occurrences);
                        } else {
                            size.addAndGet(-count);
                            curr.setMark();
                            prev.setNext(curr.getNext()); // Linearization point of a successful Remove call,
                                                          // if occurrences >= count
                        }
                    } // Linearization point of an unsuccessful Remove call
                }
            } finally {
                if (prev != null) prev.unlock();
                if (curr != null) curr.unlock();
                if (locked) ml.group2Unlock();
            }
        }
        return count;
    }

    /**
     * {@inheritDoc}
     *
     * @param element The element to be checked.
     * @return The number of occurrences of the element (zero if {@code contains(element) == false}).
     */
    @Override
    public int count(Object element) {
        if (element == null) return 0; // Linearization point for an unsuccessful Count call (null element)
        MultisetEntry<T> curr = find(element).getNext();
        if (!curr.isSentinel()
                && !curr.isMarked()
                && element.equals(curr.getElement()))
            return curr.getCount(); // Linearization point for a successful Count call
        else return 0; // Linearization point for an unsuccessful Count call (element not found)
    }

    /**
     * {@inheritDoc}
     *
     * @return The size of the multiset.
     */
    @Override
    public int size() {
        return size.get();
    }

    /**
     * {@inheritDoc}
     *
     * <p>This method scans the entire list from head to tail, without locking any node;
     * meanwhile, it builds a new list. For every element of the original list:
     * <ul>
     *     <li>if the element is contained {@literal n >= 1} times in the collection, and the current count for that
     *     element is {@literal m > n}, then the method adds to the new list the element with (m - n) occurrences;</li>
     *     <li>if the element is contained {@literal n >= 1} times in the collection, and the current count for that
     *     element is {@literal m <= n}, then the method won't add the current element to the new list;</li>
     *     <li>if the element is not contained in the collection, it is copied into the new list as is.</li>
     * </ul>
     * <p>At the end, the method sets the new list as the representation of the multiset.</p>
     *
     * <p>Note that the implementation is virtually identical to the {@link LockFreeMultiset} one, as neither
     * implementation makes use of locks or other implementation-dependent features.</p>
     *
     * <p>Executing this method will put the multiset in a read-only state for the entire duration of its
     * execution. While in this state, Add, Remove and other RemoveAll calls will be queued, while Count, Contains
     * and Size calls will be executed as normal on the  old state of the multiset, before the start of
     * the execution of RemoveAll.</p>
     *
     * @param c The collection containing the elements to be removed from the multiset.
     * @return {@code true} if the multiset has been modified by this operation (at least one element was removed).
     * @throws IllegalArgumentException If the collection passed as an argument is a null pointer.
     */
    @Override
    public boolean removeAll(Collection<?> c) throws IllegalArgumentException {
        if (c == null) throw new IllegalArgumentException("Collection must not be null");
        boolean removed = false;
        MultisetEntry<T> curr;
        MultisetEntry<T> newH = new MultisetEntry<T>(), newT = new MultisetEntry<T>(), toAppend = newH;
        ml.group1Lock();
        int removedEls = 0;
        try {
            curr = head.getNext();
            newH.setNext(newT);
            while (!curr.isSentinel()) {
                if (!curr.isMarked()) {
                    T elem = curr.getElement();
                    int occ = Collections.frequency(c, elem);
                    int count = curr.getCount();
                    if (occ > 0) {
                        if (count > occ) {
                            removedEls += occ;
                        } else {
                            removedEls += count;
                        }
                        count -= occ;
                        removed = true;
                    }
                    if (count > 0) {
                        MultisetEntry<T> newEntry = new MultisetEntry<T>(elem, count, newT);
                        toAppend.setNext(newEntry);
                        toAppend = toAppend.getNext();
                    }
                }
                curr = curr.getNext();
            }
            if (removed) {
                head.setNext(newH.getNext()); // Linearization point for a successful RemoveAll call
                size.addAndGet(-removedEls);
            } // Linearization point for an unsuccessful RemoveAll call
            return removed;
        } finally {
            ml.group1Unlock();
        }
    }

    /**
     * Provides a textual representation of the multiset.
     * @return A {@link String} representing a textual representation of the multiset.
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        MultisetEntry<T> curr = head.getNext();
        while (!curr.isSentinel()) {
            curr.lock();
            sb.append(curr.toString());
            if (!curr.getNext().isSentinel()) sb.append("; ");
            curr.unlock();
            curr = curr.getNext();
        }
        return sb
                .append("]")
                .toString();
    }
}
