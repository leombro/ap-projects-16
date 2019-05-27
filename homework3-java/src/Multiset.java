import java.util.Collection;

/**
 * The interface for a multiset (a set which allows repetitions of the same element).
 * @param <T> The type of the elements of the multiset.
 * @author Orlando Leombruni
 * @see CoarseGrainedMultiset
 * @see FineGrainedMultiset
 * @see LazyMultiset
 * @see LockFreeMultiset
 */
public interface Multiset<T> {

    /**
     * Adds an occurrence of the specified element into the multiset.
     * @param element The element to be inserted.
     * @return {@code true} if the operation is successful.
     */
    boolean add(T element);

    /**
     * Check if an element is present (at least once) in the multiset.
     * @param element The element to be found.
     * @return {@code true} if the multiset contains the element, {@code false} otherwise.
     */
    boolean contains(Object element);

    /**
     * Returns the number of occurrences of the specified element in the multiset.
     * @param element The element to be checked.
     * @return The number of occurrences of the element (zero if {@code contains(element) == false}).
     */
    int count(Object element);

    /**
     * Removes an occurrence of the specified element from the multiset.
     * @param element The element to be removed.
     * @return {@code true} if the element is removed from the multiset.
     */
    boolean remove(Object element);

    /**
     * Adds multiple occurrences of the specified element into the multiset.
     *
     * @param element The element to be inserted.
     * @param occurrences The number of occurrences.
     * @return The number of occurrences of the element that were present in the multiset
     * before this operation (zero if the element wasn't in the multiset).
     */
    int add(T element, int occurrences);

    /**
     * Removes (up to) multiple occurrences of the specified element into the multiset.
     *
     * @param element The element to be removed.
     * @param occurrences The number of occurrences.
     * @return The number of occurrences of the element that were present in the multiset
     * before this operation (zero if the element wasn't in the multiset).
     */
    int remove(Object element, int occurrences);

    /**
     * Removes from the multiset all the elements which are both in the collection passed as argument
     * and in the multiset itself.
     * @param c The collection containing the elements to be removed from the multiset.
     * @return {@code true} if the multiset has been modified by this operation (at least one element was removed).
     */
    boolean removeAll(Collection<?> c);

    /**
     * Returns the multiset's size (counting multiple occurrences of the same element).
     * @return The size of the multiset.
     */
    int size();

}
