/**
 * An exception thrown when an {@code add} operation makes the multiset grow
 * beyond its designed capacity. It is an unchecked exception.
 *
 * @author Orlando Leombruni
 */
public class FullSetException extends RuntimeException {

    /**
     * Constructor for an exception of type FullSetException.
     *
     * @param message An optional message to show to the user.
     */
    public FullSetException(String message) {
        super(message);
    }
}
