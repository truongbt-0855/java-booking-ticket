package org.ticket.infrastructure.distributed.redisson;

import java.util.concurrent.TimeUnit;

public interface RedisDistributedLocker {

    /**
     * Attempts to acquire the distributed lock, waiting up to the specified time
     * to become available and, if acquired, holding it for at most the given lease time.
     *
     * @param waitTime  the maximum time to wait for the lock to be acquired
     * @param leaseTime the maximum time to hold the lock once acquired before it is automatically released
     * @param unit      the time unit of the {@code waitTime} and {@code leaseTime} arguments
     * @return {@code true} if the lock was successfully acquired within the wait time;
     *         {@code false} if the waiting time elapsed before the lock could be acquired
     * @throws InterruptedException if the current thread is interrupted while waiting to acquire the lock
     */
    boolean tryLock(long waitTime, long leaseTime, TimeUnit unit) throws InterruptedException;

    /**
     * Acquires the distributed lock, blocking until it becomes available, and then
     * holds it for at most the given lease time before it is automatically released.
     *
     * @param leaseTime the maximum time to hold the lock before it is automatically released
     * @param unit      the time unit of the {@code leaseTime} argument
     */
    void lock(long leaseTime, TimeUnit unit);

    /**
     * Releases the distributed lock if it is currently held by the calling context.
     * <p>
     * Implementations may ignore this call if the lock is not held by the caller.
     * </p>
     */
    void unlock();

    /**
     * Checks whether the distributed lock is currently held by any thread or process.
     *
     * @return {@code true} if the lock is currently held; {@code false} otherwise
     */
    boolean isLocked();

    /**
     * Checks whether the distributed lock is currently held by the specified thread.
     *
     * @param threadId the identifier of the thread to check
     * @return {@code true} if the lock is held by the given thread; {@code false} otherwise
     */
    boolean isHeldByThread(long threadId);

    /**
     * Checks whether the distributed lock is currently held by the calling thread.
     *
     * @return {@code true} if the lock is held by the current thread; {@code false} otherwise
     */
    boolean isHeldByCurrentThread();
}
