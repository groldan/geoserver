/* (c) 2014 Open Source Geospatial Foundation - all rights reserved
 * (c) 2014 OpenPlans
 * (c) 2008-2010 GeoSolutions
 *
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 *
 * Original from GeoWebCache 1.5.1 under a LGPL license
 */

package org.geoserver.platform.resource;

import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.codec.digest.DigestUtils;
import org.geoserver.util.IOUtils;
import org.geotools.util.logging.Logging;

/**
 * A lock provider based exclusively on {@link FileLock file system locks}, hence useful for
 * inter-process locking but unsuitable for locking across threads on the same JVM instance.
 *
 * @see FileLockProvider
 * @see DoubleLockProvider
 */
class NioFileLockProvider implements LockProvider, Closeable {

    static final Logger LOGGER = Logging.getLogger(NioFileLockProvider.class.getName());

    private static final int MAX_LOCKS = 1024;

    private Supplier<File> locksFile;

    private FileChannel channel;

    /** The wait to occur in case the lock cannot be acquired */
    private int waitBeforeRetry = 50;
    /** max lock attempts */
    private int maxLockAttempts = 120 * 1000 / waitBeforeRetry;

    public NioFileLockProvider(Supplier<File> locksFilesDirectory) {
        this.locksFile = locksFilesDirectory;
    }

    @Override
    public Resource.Lock acquire(final String lockKey) {
        final int bucket = getBucket(lockKey);
        final File file = getFile();
        finer("Mapped lock key %s to bucket %,d on locks file %s", lockKey, bucket, file);

        for (int count = 0; count < maxLockAttempts; count++) {
            Optional<FileLock> lock = acquire(bucket);
            if (lock.isPresent()) {
                fine("Acquired lock on %s", lockKey);
                return new FileLockAdapter(lockKey, lock.get());
            }
            finest(
                    "Unable to lock on %s (bucket %,d), retrying in %dms (attempt %d/%d)...",
                    lockKey, bucket, waitBeforeRetry, count + 1, maxLockAttempts);
            sleep(waitBeforeRetry);
        }

        throw new IllegalStateException(
                "Failed to get a lock on key "
                        + lockKey
                        + " after "
                        + maxLockAttempts
                        + " attempts");
    }

    @Override
    public void close() {
        FileChannel channel = this.channel;
        this.channel = null;
        IOUtils.closeQuietly(channel);
    }

    private static void fine(String msg, Object... msgArgs) {
        log(Level.FINE, msg, msgArgs);
    }

    private static void finer(String msg, Object... msgArgs) {
        log(Level.FINER, msg, msgArgs);
    }

    private static void finest(String msg, Object... msgArgs) {
        log(Level.FINEST, msg, msgArgs);
    }

    private static void log(Level level, String msg, Object... msgArgs) {
        if (LOGGER.isLoggable(level)) {
            LOGGER.log(level, String.format(msg, msgArgs));
        }
    }

    private void sleep(long waitBeforeRetry) {
        try {
            Thread.sleep(waitBeforeRetry);
        } catch (InterruptedException ie) {
            // ok, moving on
        }
    }

    private Optional<FileLock> acquire(final int bucket) {
        FileChannel channel = getChannel();
        final long size = Integer.BYTES;
        final long position = size * bucket;
        final boolean shared = false;
        FileLock lock = null;
        try {
            // if tryLock returns null, the lock is held by another process
            lock = channel.tryLock(position, size, shared);
        } catch (OverlappingFileLockException heldByAnotherThreadOnThisJVM) {
            fine("FileLock is held by another thread");
        } catch (ClosedChannelException e) {
            throw new IllegalStateException("Locks file channel was closed unexpectedly", e);
        } catch (IOException e) {
            throw new IllegalStateException("Unexpected error acquiring lock", e);
        }
        return Optional.ofNullable(lock);
    }

    private FileChannel getChannel() {
        FileChannel channel = this.channel;
        if (channel == null || !channel.isOpen()) {
            final File file = getFile();
            synchronized (this) {
                if (this.channel == null) {
                    channel = openChannel(file);
                    this.channel = channel;
                } else {
                    channel = this.channel;
                }
            }
        }
        return channel;
    }

    @SuppressWarnings("resource")
    private FileChannel openChannel(File file) {
        try {
            return new RandomAccessFile(file, "rw").getChannel();
        } catch (FileNotFoundException e) {
            throw new IllegalStateException("Locks file does not exist: " + file, e);
        }
    }

    private int getBucket(String lockKey) {
        // Simply hashing the lock key generated a significant number of collisions,
        // doing the SHA1 digest of it provides a much better distribution
        int idx = Math.abs(DigestUtils.sha1Hex(lockKey).hashCode() % MAX_LOCKS);
        return idx;
    }

    public void setWaitBeforeRetry(int millis) {
        if (millis <= 0)
            throw new IllegalArgumentException("waitBeforeRetry must be positive or zero");
        this.waitBeforeRetry = millis;
    }

    public void setMaxLockAttempts(int maxAttempts) {
        if (maxAttempts <= 0)
            throw new IllegalArgumentException("maxLockAttempts must be positive or zero");
        this.maxLockAttempts = maxAttempts;
    }

    private File getFile() {
        final File locksFile = this.locksFile.get();
        Objects.requireNonNull(locksFile, "Locks file not provided");
        if (!locksFile.exists()) {
            File parent = locksFile.getParentFile();
            parent.mkdirs();
            if (!parent.isDirectory()) {
                throw new IllegalStateException(
                        "Locks directory does not exist or is not a directory: " + parent);
            }
            try {
                locksFile.createNewFile();
            } catch (IOException e) {
                throw new IllegalStateException("Error creating locks file " + locksFile, e);
            }
        }
        if (!locksFile.isFile()) {
            throw new IllegalStateException(
                    "Locks file is not a file or cannot be created: " + locksFile);
        }
        return locksFile;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " " + locksFile;
    }

    static class FileLockAdapter implements Resource.Lock {

        private final String lockKey;
        private FileLock fileLock;

        FileLockAdapter(String lockKey, FileLock fileLock) {
            this.lockKey = lockKey;
            this.fileLock = fileLock;
        }

        @Override
        public void release() {
            FileLock lock = this.fileLock;
            this.fileLock = null;
            if (lock != null) {
                if (lock.isValid()) {
                    try {
                        finer("Releasing lock on %s", lockKey);
                        lock.release();
                        finest("Released lock on %s", lockKey);
                    } catch (IOException e) {
                        fine("Error released lock on %s: %s", lockKey, e.getMessage());
                        throw new IllegalStateException(
                                "Failure while trying to release lock for key " + lockKey, e);
                    }
                }
            }
        }

        @Override
        public String toString() {
            return "FileLock " + lockKey;
        }
    }
}
