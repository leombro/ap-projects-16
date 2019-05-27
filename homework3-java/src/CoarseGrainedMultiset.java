import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * An implementation of a concurrent {@link Multiset} which locks the
 * entire underlying data structure when performing any kind of operation.
 * It is used as a "benchmark" of sorts for comparisons with other implementations.
 *
 * @author Orlando Leombruni
 * @see Multiset
 * @see ConcurrentMultiset
 * @see FineGrainedMultiset
 */
public class CoarseGrainedMultiset<T> extends ConcurrentMultiset<T> {

    /**
     * An element of the data structure on which a {@link CoarseGrainedMultiset}
     * is constructed upon. To maintain consistency with other implementations, this
     * data structure is a linked list with sentinels.
     * @param <E> The type of the elements of the linked list.
     */
    private static final class MultisetEntry<E> {

        private final E element;
        private volatile int count;
        private MultisetEntry<E> next;

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
         * Setter method to change this node's successor.
         * @param next The node which will become this node's successor.
         */
        void setNext(MultisetEntry<E> next) {
            this.next = next;
        }

        /**
         * Getter method for the node's successor.
         * @return The successor of the node.
         */
        MultisetEntry<E> getNext() {
            return next;
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
            return "(" + element.toString() + ": " + count + ")";
        }
    }

    private MultisetEntry<T> head;
    private volatile AtomicInteger size;
    private Integer capacity;
    private ReentrantLock lock;

    /**
     * The constructor for a CoarseGrainedMultiset, in which the maximum capacity is specified.
     * @param capacity The maximum capacity of the multiset.
     */
    public CoarseGrainedMultiset(int capacity) {
        MultisetEntry<T> ls = new MultisetEntry<>();
        MultisetEntry<T> rs = new MultisetEntry<>();
        this.head = ls;
        head.setNext(rs);
        this.capacity = capacity;
        this.size = new AtomicInteger(0);
        this.lock = new ReentrantLock();
    }

    /**
     * The constructor for a CoarseGrainedMultiset, in which the maximum capacity is left
     * unspecified (no limits on the multiset's size).
     */
    public CoarseGrainedMultiset() {
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
     * @param el The element to be found.
     * @return The <i>predecessor</i> node to either <ul>
     * <li>the node that contains the desired element, if this one is contained in the multiset</li>
     * <li>the right (tail) sentinel node, if the element is not contained in the multiset</li>
     * </ul>
     */
    private MultisetEntry<T> search(Object el) {
        MultisetEntry<T> prev = head;
        MultisetEntry<T> curr = head.getNext();
        while (!curr.isSentinel() && !el.equals(curr.getElement())) {
            prev = curr;
            curr = curr.getNext();
        }
        return prev;
    }

    /**
     * {@inheritDoc}
     *
     * <p>First of all, the method locks the entire underlying list. Then, using the {@code search} method,
     * it checks if the element is already in the multiset; if so, it adds {@code occurrences} to
     * the {@code count} field of the node containing the element, else a new node is inserted.</p>
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
        int prev = 0;
        try {
            lock.lock();
            MultisetEntry<T> pred = search(element), curr = pred.getNext();
            increaseSize(occurrences);
            if (curr.isSentinel()) {
                MultisetEntry<T> newEntry = new MultisetEntry<>(element, occurrences, curr);
                pred.setNext(newEntry);
            } else {
                prev = curr.changeCount(occurrences);
            }
            return prev;
        } finally {
            lock.unlock(); // Linearization point for the Add method
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>First of all, the method locks the entire underlying list. Then, using the {@code search} method,
     * it checks if the element is in the multiset; if so, it removes {@code occurrences} to
     * the {@code count} field of the node containing the element. If {@code count} is 0 or less at the end
     * of this operation, the node is removed from the list.</p>
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
        int prev = 0;
        lock.lock();
        try {
            MultisetEntry<T> pred = search(element), curr = pred.getNext();
            if (!curr.isSentinel()) {
                prev = curr.getCount();
                if (prev > occurrences) {
                    curr.changeCount(-occurrences);
                    size.addAndGet(-occurrences);
                } else {
                    size.addAndGet(-prev);
                    pred.setNext(curr.getNext());
                }
            }
            return prev;
        } finally {
            lock.unlock(); // Linearization point for the Remove method
        }
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
        lock.lock();
        try {
            MultisetEntry<T> curr = search(element).getNext();
            if (curr.isSentinel()) return 0;
            else return curr.getCount();
        } finally {
            lock.unlock(); // Linearization point for the Count method
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>As with other methods of this implementation, the entire underlying data structure is locked while
     * performing this operation. It scans the entire list from head to tail, and for every element of the
     * multiset it checks if that element is contained (at least once) in the collection, removing an occurrence
     * if so. If the collection contains the element n times, n occurrences will be removed from the multiset.</p>
     *
     * @param c The collection containing the elements to be removed from the multiset.
     * @return {@code true} if the multiset has been modified by this operation (at least one element was removed).
     * @throws IllegalArgumentException If the collection passed as an argument is a null pointer.
     */
    @Override
    public boolean removeAll(Collection<?> c) throws IllegalArgumentException {
        if (c == null) throw new IllegalArgumentException("The collection must not be null");
        lock.lock();
        boolean modified = false, removed= false;
        MultisetEntry<T> prev = head, curr = head.getNext();
        List<Object> lis = new ArrayList<>(c);
        while (!curr.isSentinel()) {
            T elem = curr.getElement();
            while (lis.contains(elem) && curr.getCount() > 0) {
                size.decrementAndGet();
                lis.remove(elem);
                modified = true;
                if (curr.getCount() > 1) {
                    curr.changeCount(-1);
                } else {
                    prev.setNext(curr.getNext());
                    removed = true;
                }
            }
            if (!removed) prev = curr;
            curr = curr.getNext();
            removed = false;
        }
        lock.unlock(); // Linearization point for the RemoveAll method
        return modified;
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
        MultisetEntry<T> curr = head.getNext();
        while (!curr.isSentinel()) {
            sb.append(curr.toString());
            curr = curr.getNext();
        }
        sb.append("]");
        return sb.toString();
    }
}
