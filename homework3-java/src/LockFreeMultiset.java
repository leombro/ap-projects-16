import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicMarkableReference;

/**
 * An implementation of a concurrent {@link Multiset} whose underlying data
 * structure is a linked list. The nodes itself don't have any kind of lock; atomic operations of marking them and
 * changing their successor are provided.
 *
 * @author Orlando Leombruni
 * @see Multiset
 * @see ConcurrentMultiset
 */
public class LockFreeMultiset<T> extends ConcurrentMultiset<T> {

    /**
     * An element of the data structure on which a {@link LockFreeMultiset}
     * is constructed upon. It is a linked list with sentinels, with atomic operations
     * for setting the value of a mark and changing the pointer to the successor of a node.
     *
     * @param <E> The type of the elements of the linked list.
     */
    private static final class MultisetEntry<E> extends AtomicMarkableReference<MultisetEntry<E>> {

        private final E element;
        private AtomicInteger count;

        /**
         * The constructor used when the object to be created is a non-sentinel node.
         * @param element The element of this list node.
         * @param count The number of occurrences of the element in the multiset.
         * @param next The successor of this node in the list.
         */
        MultisetEntry(E element, int count, MultisetEntry<E> next) {
            super(next, false);
            this.element = element;
            this.count = new AtomicInteger(count);
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
            return count.get();
        }

        /**
         * Changes the number of occurrences of the node's element.
         * @param delta The quantity (positive or negative) to be added to the current number of occurrences.
         * @return The number of occurrences before this operation.
         */
        int changeCount(int delta) {
            return count.getAndAdd(delta);
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
            if (this.isMarked()) return "(*marked* " + element.toString() + ")";
            return "(" + element.toString() + ": " + count + ")";
        }
    }

    private volatile AtomicInteger size;
    private Integer capacity;
    private MultisetEntry<T> head;
    private DoubleLock ml;

    /**
     * The constructor for a LockFreeMultiset, in which the maximum capacity is specified.
     * @param capacity The maximum capacity of the multiset.
     */
    public LockFreeMultiset(int capacity) {
        this.size = new AtomicInteger(0);
        this.capacity = capacity;
        MultisetEntry<T> left_sentinel = new MultisetEntry<>();
        MultisetEntry<T> right_sentinel = new MultisetEntry<>();
        this.head = left_sentinel;
        head.set(right_sentinel, false);
        ml = new DoubleLock();
    }

    /**
     * The constructor for a LockFreeMultiset, in which the maximum capacity is left
     * unspecified (no limits on the multiset's size).
     */
    public LockFreeMultiset() {
        this(0);
    }

    /**
     * An utility method to increase the size of the multiset, failing when {@code capacity} is reached.
     * @param delta The increment.
     * @throws FullSetException If the maximum capacity is reached.
     */
    private void increaseSize(int delta) throws FullSetException {
        if (delta < 0) throw new IllegalArgumentException("Should be used only to increment size");
        size.addAndGet(delta);
        if (capacity > 0) {
            if (size.get() > capacity) {
                size.addAndGet(-delta);
                throw new FullSetException("Cannot add the requested number of entries");
            }
        }
    }

    /**
     * An utility method to search if an element is in the multiset. It also physically removes any marked node.
     * @param element The element to be found.
     * @return The <i>predecessor</i> node to either <ul>
     * <li>the node that contains the desired element, if this one is contained in the multiset</li>
     * <li>the right (tail) sentinel node, if the element is not contained in the multiset</li>
     * </ul>
     */
    private MultisetEntry<T> find(Object element) {
        MultisetEntry<T> prev = head, curr = head.getReference(), succ;
        boolean[] marked = new boolean[1];
        while(!curr.isSentinel() && (element == null || !element.equals(curr.getElement()))) {
            succ = curr.get(marked);
            if (marked[0]) {
                if (prev.compareAndSet(curr, succ, false, false)) {
                    curr = succ;
                } else {
                    prev = head;
                    curr = head.getReference();
                }
            } else {
                prev = curr;
                curr = succ;
            }
        }
        return prev;
    }

    /**
     * {@inheritDoc}
     *
     * <p>This method uses the {@code find} method to transverse the underlying list, until the right sentinel or
     * a node with the same element to insert is found. If the element is already present, its occurrences count
     * is increased; otherwise, a new node is inserted, retrying until necessary if the predecessor node has been
     * modified by a concurrent operation.</p>
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
        boolean inserted = false;
        while (!inserted) {
            MultisetEntry<T> prev = find(element);
            MultisetEntry<T> curr = prev.getReference();
            if (!curr.isSentinel() && element.equals(curr.getElement())) {
                count = curr.changeCount(occurrences);  // Linearization point of a successful Add call, if
                                                        // the element is already in the multiset
                increaseSize(occurrences);
                inserted = true;
            } else {
                if (curr.isSentinel()) {
                    increaseSize(occurrences);
                    inserted = prev.compareAndSet(curr,
                                    new MultisetEntry<>(element, occurrences, curr), false, false);
                    // Linearization point of a successful Add call, if the element wasn't in the multiset
                    if (!inserted) {
                        size.addAndGet(-occurrences);
                    }
                }
            }
        }
        if (locked) ml.group2Unlock();
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
        if (element == null) return 0;
        int count = 0;
        boolean removed = false;
        boolean[] marked = new boolean[1];
        while (!removed) {
            MultisetEntry<T> curr = find(element).get(marked);
            if (!curr.isSentinel() && !marked[0]) {
                if (element.equals(curr.getElement())) {
                    count = curr.getCount(); // Linearization point for a successful Count call
                    removed = true;
                }
            } else if (curr.isSentinel()) removed = true;
        }
        return count;
    }

    /**
     * {@inheritDoc}
     *
     * <p>This method uses the {@code find} method to transverse the underlying list until it finds a node
     * containing the desired element or the right sentinel. If a node contained the element is found, its occurrences
     * are reduced, and if they become zero or less after this operation, the node is removed from the list
     * by marking it.</p>
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
    public int remove(Object element, int occurrences) {
        if (element == null) throw new IllegalArgumentException("Null elements are not permitted");
        if (occurrences < 1) throw new IllegalArgumentException("Occurrences must be a positive number");
        boolean locked = ml.group2Lock();
        int count = 0;
        boolean removed = false;
        while (!removed) {
            MultisetEntry<T> prev = find(element);
            MultisetEntry<T> curr = prev.getReference();
            if (curr != null
                    && !curr.isSentinel()
                    && element.equals(curr.getElement())
                    && !curr.isMarked()) {
                if (curr.getCount() > occurrences) {
                    count = curr.changeCount(-occurrences); // Linearization point of a successful Remove call, if
                                                            // occurrences < count
                    size.addAndGet(-occurrences);
                    removed = true;
                } else {
                    MultisetEntry<T> succ = curr.getReference();
                    removed = curr.compareAndSet(succ,
                            succ, false, true);
                    // Linearization point of a successful Remove call,
                    // if occurrences >= count
                    if (removed){
                        count = curr.getCount();
                        size.addAndGet(-count);
                    }
                }
            } else {
                removed = true; // Linearization point of an unsuccessful Remove call
            }
        }
        if (locked) ml.group2Unlock();
        return count;
    }

    /**
     * {@inheritDoc}
     *
     * <p>This method scans the entire list from head to tail, without locking any node;
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
     * <p>Note that the implementation is virtually identical to the {@link LazyMultiset} one, as neither
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
        try {
            boolean removed = false;
            MultisetEntry<T> newH = new MultisetEntry<T>(),
                    newT = new MultisetEntry<T>(),
                    toAppend = newH;
            toAppend.set(newT, false);
            ml.group1Lock();
            MultisetEntry<T> curr = head.getReference();
            int removedEls = 0;
            while (!curr.isSentinel()) {
                if (!curr.isMarked()) {
                    T elem = curr.getElement();
                    int count = curr.getCount(),
                        occ = Collections.frequency(c, elem);
                    if (occ > 0) {
                        if (count > occ) {
                            removedEls += occ;
                            removed = true;
                            count -= occ;
                            MultisetEntry<T> newel = new MultisetEntry<T>(elem, count, newT);
                            toAppend.set(newel, false);
                            toAppend = toAppend.getReference();
                        } else {
                            removedEls += count;
                            removed = true;
                        }
                        curr = curr.getReference();
                    } else {
                        MultisetEntry<T> newel = new MultisetEntry<T>(elem, count, newT);
                        toAppend.set(newel, false);
                        toAppend = toAppend.getReference();
                        curr = curr.getReference();
                    }
                } else {
                    curr = curr.getReference();
                }
            }
            if (removed) {
                MultisetEntry<T> newsucc = newH.getReference();
                head.set(newsucc, false); // Linearization point for a successful RemoveAll call
                size.addAndGet(-removedEls);
            }
            return removed; // Linearization point for an unsuccessful RemoveAll call
        } finally {
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
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        MultisetEntry<T> curr = head.getReference();
        while (!curr.isSentinel()) {
            sb.append(curr.toString());
            if (!curr.getReference().isSentinel()) sb.append("; ");
            curr = curr.getReference();
        }
        return sb
                .append("]")
                .toString();
    }
}
