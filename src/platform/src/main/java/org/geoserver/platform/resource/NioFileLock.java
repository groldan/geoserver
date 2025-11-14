/*
 * (c) 2022 Open Source Geospatial Foundation - all rights reserved (c) 2014 OpenPlans (c) 2008-2010
 * GeoSolutions
 *
 * This code is licensed under the GPL 2.0 license, available at the root application directory.
 *
 * Original from GeoWebCache 1.5.1 under a LGPL license
 */
package org.geoserver.platform.resource;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.codec.digest.DigestUtils;
import org.geotools.util.logging.Logging;

class NioFileLock implements Resource.Lock {
    static final Logger LOGGER = Logging.getLogger(NioFileLock.class.getName());

    private static final int LOCK_SIZE = Byte.BYTES;
    private static final int MAX_LOCKS = 1024 * 1024;

    private final NioFileLockProvider provider;
    private final String lockKey;

    private FileLock fileLock;
    private int lockCount; // handle reentrancy
    private long bucket = -1L;

    NioFileLock(final String lockKey, final NioFileLockProvider provider) {
        this.lockKey = lockKey;
        this.provider = provider;
    }

    /** @throws IllegalStateException if failed to acquire a lock after max attempts */
    public NioFileLock lock() {
        if (lockCount > 0) {
            ++lockCount;
        } else {
            this.bucket = getBucket(lockKey);
            final File file = provider.getFile();
            finest("Mapped lock key %s to bucket %,d on locks file %s", lockKey, bucket, file);

            final int maxLockAttempts = provider.getMaxLockAttempts();
            final int waitBeforeRetry = provider.getWaitBeforeRetry();

            for (int count = 0; count < maxLockAttempts; count++) {
                Optional<FileLock> lock = acquire(bucket, lockKey);
                if (lock.isPresent()) {
                    this.fileLock = lock.get();
                    this.lockCount = 1;
                    break;
                }
                finest(
                        "Unable to lock on %s (bucket %,d), retrying in %dms (attempt %d/%d)...",
                        lockKey, bucket, waitBeforeRetry, count + 1, maxLockAttempts);
                sleep(waitBeforeRetry);
            }
            if (lockCount == 0) {
                throw new IllegalStateException(
                        String.format("Failed to get a lock on key %s after %d attempts", lockKey, maxLockAttempts));
            }
        }
        finer("Acquired lock on %s, bucket %d, lock counter: %d", lockKey, this.bucket, this.lockCount);
        return this;
    }

    @Override
    public void release() {
        finer("Releasing lock on %s, lock counter: %d", lockKey, lockCount);
        --lockCount;
        if (lockCount == 0) {
            FileLock lock = this.fileLock;
            this.fileLock = null;
            if (lock != null && lock.isValid()) {
                final String lockKey = this.lockKey;
                final long bucket = this.bucket;
                try {
                    lock.release();
                    String heldKey = provider.bucketsHeldForKey.remove(bucket);
                    provider.threadIdHoldingKey.remove(lockKey);
                    if (null != heldKey && !lockKey.equals(heldKey)) {
                        warning(
                                "Released lock on %s for bucket %d but it was registered for key %s, lock counter: %d",
                                lockKey, bucket, heldKey, lockCount);
                    } else {
                        finest("Released lock on %s, lock counter: %d", lockKey, lockCount);
                    }
                } catch (IOException e) {
                    finer("Error releasing lock on %s: %s", lockKey, e.getMessage());
                    throw new IllegalStateException("Failure while trying to release lock for key " + lockKey, e);
                }
            }
        } else {
            finer("Released lock reentrancy on %s, lock counter: %d", lockKey, lockCount);
        }
    }

    private Optional<FileLock> acquire(final long bucket, final String lockKey) {
        FileChannel channel = provider.getChannel();
        final long size = LOCK_SIZE;
        final long position = size * bucket;
        final boolean shared = false;
        FileLock lock = null;
        try {
            // if tryLock returns null, the lock is held by another process
            lock = channel.tryLock(position, size, shared);
        } catch (OverlappingFileLockException heldByAnotherThreadOnThisJVM) {
            String lockedKey = provider.bucketsHeldForKey.get(bucket);
            if (!lockKey.equals(lockedKey)) {
                String msg = String.format(
                        "Lock collision: lock for bucket %d is held for key %s. Can't lock key %s.",
                        bucket, lockedKey, lockKey);
                severe(msg);
                throw new IllegalStateException(msg);
            } else {
                finer("FileLock is held by another thread");
            }
        } catch (ClosedChannelException e) {
            throw new IllegalStateException("Locks file channel was closed unexpectedly", e);
        } catch (IOException e) {
            throw new IllegalStateException("Unexpected error acquiring lock", e);
        }
        return Optional.ofNullable(lock);
    }

    private long getBucket(String lockKey) {
        // Simply hashing the lock key generated a significant number of collisions,
        // doing the SHA1 digest of it provides a much better distribution
        byte[] sha1 = DigestUtils.sha1(lockKey);
        long hash = ByteBuffer.wrap(sha1).getLong();
        long idx = Math.abs(hash) % MAX_LOCKS;
        return idx;
    }

    private static void severe(String msg, Object... msgArgs) {
        log(Level.SEVERE, msg, msgArgs);
    }

    private static void warning(String msg, Object... msgArgs) {
        log(Level.WARNING, msg, msgArgs);
    }

    private static void finer(String msg, Object... msgArgs) {
        log(Level.FINER, msg, msgArgs);
    }

    private static void finest(String msg, Object... msgArgs) {
        log(Level.FINEST, msg, msgArgs);
    }

    private static void log(Level level, String msg, Object... msgArgs) {
        if (LOGGER.isLoggable(level)) {
            String message = String.format(msg, msgArgs);
            if ("true".equals(System.getProperty("locks.log-stack-trace"))) {
                Throwable trace = new Exception("debugging aid stack trace").fillInStackTrace();
                LOGGER.log(level, message, trace);
            } else {
                LOGGER.log(level, message);
            }
        }
    }

    private void sleep(long waitBeforeRetry) {
        try {
            Thread.sleep(waitBeforeRetry);
        } catch (InterruptedException ie) {
            // ok, moving on
        }
    }
}
