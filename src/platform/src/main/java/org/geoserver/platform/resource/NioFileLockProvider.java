/*
 * (c) 2014 Open Source Geospatial Foundation - all rights reserved (c) 2014 OpenPlans (c) 2008-2010
 * GeoSolutions
 *
 * This code is licensed under the GPL 2.0 license, available at the root application directory.
 *
 * Original from GeoWebCache 1.5.1 under a LGPL license
 */

package org.geoserver.platform.resource;

import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import org.geoserver.util.IOUtils;
import org.geotools.util.logging.Logging;

/**
 * A lock provider based exclusively on {@link FileLock file system locks}, hence useful for inter-process locking but
 * unsuitable for locking across threads on the same JVM instance.
 *
 * @see FileLockProvider
 * @see DoubleLockProvider
 */
class NioFileLockProvider implements LockProvider, Closeable {

    static final Logger LOGGER = Logging.getLogger(NioFileLockProvider.class.getName());

    /** The wait to occur in case the lock cannot be acquired */
    private int waitBeforeRetry = 50;
    /** max lock attempts */
    private int maxLockAttempts = 120 * 1000 / waitBeforeRetry;

    /** Holds the lock key by bucket index to recognize hash collisions */
    Map<Long, String> bucketsHeldForKey = new ConcurrentHashMap<>();
    /** Holds lock counter by lock key to aid in reentrancy */
    Map<String, AtomicInteger> threadIdHoldingKey = new ConcurrentHashMap<>();

    private File locksFile;

    private FileChannel channel;

    private static final ThreadLocal<Map<String, NioFileLock>> LOCKS = ThreadLocal.withInitial(ConcurrentHashMap::new);

    public NioFileLockProvider(File locksFile) {
        if (!locksFile.isFile()) throw new IllegalArgumentException(locksFile.getAbsolutePath() + " is not a file");
        if (!locksFile.canWrite()) throw new IllegalArgumentException(locksFile.getAbsolutePath() + " is not writable");
        this.locksFile = locksFile;
    }

    @Override
    public Resource.Lock acquire(final String lockKey) {
        NioFileLock lock = getLock(lockKey);
        return lock.lock();
    }

    private NioFileLock getLock(String lockKey) {
        return LOCKS.get().computeIfAbsent(lockKey, key -> new NioFileLock(key, this));
    }

    @Override
    public void close() {
        FileChannel channel = this.channel;
        this.channel = null;
        IOUtils.closeQuietly(channel);
    }

    FileChannel getChannel() {
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

    public void setWaitBeforeRetry(int millis) {
        if (millis <= 0) throw new IllegalArgumentException("waitBeforeRetry must be positive or zero");
        this.waitBeforeRetry = millis;
    }

    int getWaitBeforeRetry() {
        return this.waitBeforeRetry;
    }

    public void setMaxLockAttempts(int maxAttempts) {
        if (maxAttempts <= 0) throw new IllegalArgumentException("maxLockAttempts must be positive or zero");
        this.maxLockAttempts = maxAttempts;
    }

    int getMaxLockAttempts() {
        return this.maxLockAttempts;
    }

    File getFile() {
        final File locksFile = this.locksFile;
        Objects.requireNonNull(locksFile, "Locks file not provided");
        if (!locksFile.exists()) {
            File parent = locksFile.getParentFile();
            parent.mkdirs();
            if (!parent.isDirectory()) {
                throw new IllegalStateException("Locks directory does not exist or is not a directory: " + parent);
            }
            try {
                locksFile.createNewFile();
            } catch (IOException e) {
                throw new IllegalStateException("Error creating locks file " + locksFile, e);
            }
        }
        if (!locksFile.isFile()) {
            throw new IllegalStateException("Locks file is not a file or cannot be created: " + locksFile);
        }
        return locksFile;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " " + locksFile;
    }
}
