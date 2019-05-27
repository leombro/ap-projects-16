import java.util.concurrent.locks.ReentrantLock;

/**
 * A double lock.
 * It is used to allow access to a data structure from different entities, that can be divided in two groups:
 * <ol>
 *     <li>One group has a "hard" lock, i.e. a normal lock. When an entity in this group acquires the lock,
 *     every other entity (from either group) are prohibited access to the data structure;</li>
 *     <li>Another group has a "soft" lock; every entity in this group can freely access the data structure, provided
 *     that no entity in the other group is currently holding the lock. When at least one of the entities in this
 *     group has acquired the lock, no entity in the other group can enter the critical section.</li>
 * </ol>
 * A lock of this type is used in three {@link Multiset} implementations to block Add and Remove operations (group 2)
 * from being executed when a RemoveAll operation (group 1) is currently in execution.
 */
public class DoubleLock {
    private ReentrantLock lock1, lock2;

    /**
     * The constructor.
     */
    public DoubleLock() {
        lock1 = new ReentrantLock(); // lock for group 1
        lock2 = new ReentrantLock(); // lock for group 2
    }

    /**
     * <p>Tries to acquire the group 2 (soft) lock.</p>
     * <p>Note that a group 2 object can enter in the critical section even when it hasn't acquired the lock, since
     * that means that another group 2 object is currently holding it.</p>
     * @return true if the lock is acquired, false otherwise.
     */
    synchronized boolean group2Lock() {
        lock1.lock();
        boolean returned = lock2.tryLock();
        lock1.unlock();
        return returned;
    }

    /**
     * Releases the group 2 lock.
     */
    void group2Unlock() {
        if (lock2.isHeldByCurrentThread()) lock2.unlock();
    }

    /**
     * Acquires the group 1 lock.
     */
    synchronized void group1Lock() {
        lock2.lock();
        lock1.lock();
        lock2.unlock();
    }

    /**
     * Releases the group 1 lock.
     */
    void group1Unlock() {
        lock1.unlock();
    }
}