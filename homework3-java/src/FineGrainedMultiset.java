import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * An implementation of a concurrent {@link Multiset} whose underlying data
 * structure is a linked list composed of independently-lockable nodes.
 * When performing any kind of operation, be it reading or modifying the multiset,
 * the list is traversed locking two elements at a time.
 *
 * @author Orlando Leombruni
 * @see Multiset
 * @see ConcurrentMultiset
 * @see CoarseGrainedMultiset
 */
public class FineGrainedMultiset<T> extends ConcurrentMultiset<T> {

    /**
     * An element of the data structure on which a {@link FineGrainedMultiset}
     * is constructed upon. It is a linked list with sentinels, in which every node can be
     * locked independently from the others.
     * @param <E> The type of the elements of the linked list.
     */
    private static final class MultisetEntry<E> {

        private final E element;
        private volatile int count;
        private volatile MultisetEntry<E> next;

        private ReentrantLock lock;

        /**
         * The constructor used when the object to be created is a non-sentinel node.
         * @param el The element of this list node.
         * @param count The number of occurrences of the element in the multiset.
         * @param next The successor of this node in the list.
         */
        MultisetEntry(E el, int count, MultisetEntry<E> next) {
            this.element = el;
            this.count = count;
            this.next = next;
            this.lock = new ReentrantLock();
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
            return this.element;
        }

        /**
         * Getter method for the number of occurrences of the node's element.
         * @return The number of occurrences of the node's element.
         */
        int getCount() {
            return this.count;
        }

        /**
         * Changes the number of occurrences of the node's element.
         * @param delta The quantity (positive or negative) to be added to the current number of occurrences.
         * @return The number of occurrences before this operation.
         */
        int changeCount(int delta) {
            int oldcount = count;
            this.count += delta;
            return oldcount;
        }

        /**
         * Getter method for the node's successor.
         * @return The successor of the node.
         */
        MultisetEntry<E> getNext() {
            return this.next;
        }

        /**
         * Setter method to change this node's successor.
         * @param next The node which will become this node's successor.
         */
        void setNext(MultisetEntry<E> next) {
            this.next = next;
        }

        /**
         * Tries to acquire the lock to the current element.
         */
        void lock() {
             this.lock.lock();
        }

        /**
         * Unlocks the current element.
         */
        void unlock() {
            this.lock.unlock();
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
            if (this.isSentinel()) return "(sentinel)";
            return "(" + element.toString() + ": " + count + ")";
        }

    }

    private volatile AtomicInteger size;
    private Integer capacity;
    private MultisetEntry<T> head;
    private DoubleLock ml;

    /**
     * The constructor for a FineGrainedMultiset, in which the maximum capacity is specified.
     * @param capacity The maximum capacity of the multiset.
     */
    public FineGrainedMultiset(int capacity) {
        this.size = new AtomicInteger(0);
        this.capacity = capacity;
        MultisetEntry<T> left_sentinel = new MultisetEntry<>();
        MultisetEntry<T> right_sentinel = new MultisetEntry<>();
        this.head = left_sentinel;
        head.setNext(right_sentinel);
        ml = new DoubleLock();
    }

    /**
     * The constructor for a FineGrainedMultiset, in which the maximum capacity is left
     * unspecified (no limits on the multiset's size).
     */
    public FineGrainedMultiset() {
        this(0);
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
     * An utility method to search if an element is in the multiset.
     * @param element The element to be found.
     * @return The <i>predecessor</i> node to either <ul>
     * <li>the node that contains the desired element, if this one is contained in the multiset</li>
     * <li>the right (tail) sentinel node, if the element is not contained in the multiset</li>
     * </ul>. Both the returned node and its successor are left locked.
     */
    private MultisetEntry<T> find(Object element) {
        head.lock();
        head.getNext().lock();
        MultisetEntry<T> prev = head;
        MultisetEntry<T> curr = head.getNext();
        while (!curr.isSentinel() && !element.equals(curr.getElement())) {
            prev.unlock();
            prev = curr;
            curr = curr.getNext();
            curr.lock();
        }
        return prev;
    }

    /**
     * {@inheritDoc}
     *
     * <p>This method uses the {@code find} method to transverse the underlying list. When {@code find} returns,
     * the current node (either the right sentinel, or a node that already contains the element to insert) and
     * its predecessor are locked, guaranteeing that concurrent operations won't modify the node list
     * past this point.</p>
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
        int count = 0;
        MultisetEntry<T> prev = null;
        MultisetEntry<T> curr = null;
        try {
            prev = find(element);
            curr = prev.getNext();
            increaseSize(occurrences);
            if (!curr.isSentinel()) {
                count = curr.changeCount(occurrences); // Linearization point of a successful Add call, if
                                                       // the element is already in the multiset
            } else {
                MultisetEntry<T> newEl = new MultisetEntry<>(element, occurrences, curr);
                prev.setNext(newEl); // Linearization point of a successful Add call, if
                                     // the element wasn't in the multiset
            }
        } finally {
            if (prev != null) prev.unlock();
            if (curr != null) curr.unlock();
            if (locked) ml.group2Unlock();
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
        int count = 0;
        MultisetEntry<T> prev = null;
        MultisetEntry<T> curr = null;
        try {
            prev = find(element);
            curr = prev.getNext();
            if (!curr.isSentinel()) {
                count = curr.getCount(); // Linearization point for a successful Count call
            } // Linearization point for an unsuccessful Count call (element not found)
        } finally {
            if (prev != null) prev.unlock();
            if (curr != null) curr.unlock();
        }
        return count;
    }

    /**
     * {@inheritDoc}
     *
     * <p>This method uses the {@code find} method to transverse the underlying list. When {@code find} returns,
     * the current node (either the right sentinel, or a node that contains the element to be removed) and
     * its predecessor are locked, guaranteeing that concurrent operations won't modify the node list
     * past this point.</p>
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
        int removed = 0, prevCount = 0;
        MultisetEntry<T> prev = null;
        MultisetEntry<T> curr = null;
        try {
            prev = find(element);
            curr = prev.getNext();
            if (!curr.isSentinel()) {
                if (curr.getCount() > occurrences) {
                    prevCount = curr.changeCount(-occurrences); // Linearization point of a successful Remove call, if
                                                                // occurrences < count
                    removed = occurrences;
                } else {
                    removed = curr.getCount();
                    prevCount = removed;
                    prev.setNext(curr.getNext()); // Linearization point of a successful Remove call,
                                                  // if occurrences >= count
                }
            } // Linearization point of an unsuccessful Remove call
        } finally {
            if (prev != null) prev.unlock();
            if (curr != null) curr.unlock();
            if (locked) ml.group2Unlock();
        }
        size.addAndGet(-removed);
        return prevCount;
    }

    /**
     * {@inheritDoc}
     *
     * <p>This method scans the entire list from head to tail, locking the current element and the previous one;
     * meanwhile, it builds a new list. For every element of the original list:</p>
     * <ul>
     *     <li>if the element is contained {@literal n >= 1} times in the collection, and the current count for that
     *     element is {@literal m > n}, then the method adds to the new list the element with (m - n) occurrences;</li>
     *     <li>if the element is contained {@literal n >= 1} times in the collection, and the current count for that
     *     element is {@literal m <= n}, then the method won't add the current element to the new list;</li>
     *     <li>if the element is not contained in the collection, it is copied into the new list as is.</li>
     * </ul>
     * <p>At the end, the method sets the new list as the representation of the multiset.</p>
     *
     * <p>Executing this method will put the multiset in a read-only state for the entire duration of its
     * execution. While in this state, Add, Remove and other RemoveAll calls will be queued, while Count, Contains
     * and Size calls will be executed as normal (meaning they are still subject to fine-grained locking) on the
     * old state of the multiset, before the start of the execution of RemoveAll.</p>
     *
     * @param c The collection containing the elements to be removed from the multiset.
     * @return {@code true} if the multiset has been modified by this operation (at least one element was removed).
     * @throws IllegalArgumentException If the collection passed as an argument is a null pointer.
     */
    @Override
    public boolean removeAll(Collection<?> c) throws IllegalArgumentException {
        if (c == null) throw new IllegalArgumentException("The collection must not be null");
        ml.group1Lock();
        head.lock();
        head.getNext().lock();
        boolean removed = false;
        MultisetEntry<T> prev = head;
        MultisetEntry<T> curr = head.getNext();
        try {
            MultisetEntry<T> newHead = new MultisetEntry<T>(), newTail = new MultisetEntry<T>();
            MultisetEntry<T> toAttach = newHead;
            toAttach.setNext(newTail);
            int removedEls = 0;
            while (!curr.isSentinel()) {
                T elem = curr.getElement();
                int occ = Collections.frequency(c, elem);
                int count = curr.getCount();
                if (occ > 0) {
                    removed = true;
                    if (count > occ) {
                        removedEls += occ;
                    } else {
                        removedEls += count;
                    }
                    count -= occ;
                }
                if (count > 0) {
                    MultisetEntry<T> newEntry = new MultisetEntry<T>(elem, count, newTail);
                    toAttach.setNext(newEntry);
                    toAttach = toAttach.getNext();
                }
                prev.unlock();
                prev = curr;
                curr = prev.getNext();
                curr.lock();
            }
            if (removed) {
                head.setNext(newHead.getNext()); // Linearization point for a successful RemoveAll call
                size.getAndAdd(-removedEls);
            }
            return removed; // Linearization point for an unsuccessful RemoveAll call
        } finally {
            prev.unlock();
            curr.unlock();
            ml.group1Unlock();
        }
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
     * Provides a textual representation of the multiset.
     * @return A {@link String} representing a textual representation of the multiset.
     */
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        head.lock();
        head.getNext().lock();
        MultisetEntry<T> prev = head;
        MultisetEntry<T> curr = head.getNext();
        while (!curr.isSentinel()) {
            sb.append(curr.toString());
            if (!curr.getNext().isSentinel()) sb.append("; ");
            prev.unlock();
            prev = curr;
            curr = curr.getNext();
            curr.lock();
        }
        prev.unlock();
        curr.unlock();
        return sb
                .append("]")
                .toString();
    }
}