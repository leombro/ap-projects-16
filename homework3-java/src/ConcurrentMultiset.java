/**
 * A common implementation for some of the operations provided by a multiset.
 * @param <T> The type of the elements of the multiset.
 */
public abstract class ConcurrentMultiset<T> implements Multiset<T> {
    /**
     * {@inheritDoc}
     *
     * <p>This method simply calls {@code add(element, 1)} and returns {@code true} (because the insert
     * operation never fails in a multiset).</p>
     * @param element The element to be inserted.
     * @return true (always).
     * @throws IllegalArgumentException If {@code element == null}.
     * @throws FullSetException If this operation causes the multiset to grow beyond its maximum capacity.
     */
    @Override
    public boolean add(T element) throws IllegalArgumentException, FullSetException {
        this.add(element, 1);
        return true;
    }

    /**
     * {@inheritDoc}
     *
     * <p>This method simply calls {@code remove(element, 1)}.</p>
     * @param element The element to be removed.
     * @return true if an occurrence of the element is removed from the multiset, false otherwise.
     * @throws IllegalArgumentException If {@code element == null}.
     */
    @Override
    public boolean remove(Object element) throws IllegalArgumentException {
        return (this.remove(element, 1) > 0);
    }

    /**
     * {@inheritDoc}
     *
     * <p>This method simply checks if the occurrences of {@code element} are a positive number, since
     * if an element is contained in a multiset then its number of occurrences is at least 1.</p>
     * @param element The element to be found.
     * @return true if there's at least one occurrence of {@code element} in the multiset, false otherwise.
     */
    @Override
    public boolean contains(Object element) {
        return (count(element) > 0);
    }
}
