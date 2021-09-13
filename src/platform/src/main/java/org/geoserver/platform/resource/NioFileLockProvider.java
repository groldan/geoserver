/* (c) 2014 Open Source Geospatial Foundation - all rights reserved
 *
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 *
 * Original from GeoWebCache 1.5.1 under a LGPL license
 */

package org.geoserver.platform.resource;

import static java.lang.String.format;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.util.Objects;
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
class NioFileLockProvider implements LockProvider {

    static final Logger LOGGER = Logging.getLogger(NioFileLockProvider.class.getName());

    private Supplier<File> locksDirectory;
    /** The wait to occur in case the lock cannot be acquired */
    private int waitBeforeRetry = 20;
    /** max lock attempts */
    private int maxLockAttempts = 120 * 1000 / waitBeforeRetry;

    public NioFileLockProvider(Supplier<File> locksFilesDirectory) {
        this.locksDirectory = locksFilesDirectory;
    }

    @Override
    public Resource.Lock acquire(final String lockKey) {
        final File file = getFile(lockKey);
        final NioFileLock fileLock = new NioFileLock(lockKey, file);

        for (int count = 0; count < maxLockAttempts; count++) {
            try {
                fileLock.acquire();
                return fileLock;
            } catch (IOException e) {
                try {
                    Thread.sleep(waitBeforeRetry);
                } catch (InterruptedException ie) {
                    // ok, moving on
                }
            }
        }

        throw new IllegalStateException(
                "Failed to get a lock on key "
                        + lockKey
                        + " after "
                        + maxLockAttempts
                        + " attempts");
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

    private File getLocksDirectory() {
        final File locksDir = locksDirectory.get();
        Objects.requireNonNull(locksDir, "Locks directory not provided");
        if (!locksDir.isDirectory()) {
            throw new IllegalStateException(
                    "Locks directory does not exist or is not a directory: " + locksDir);
        }
        return locksDir;
    }

    private File getFile(String lockKey) {
        File locks = getLocksDirectory();
        String sha1 = DigestUtils.sha1Hex(lockKey);
        File file = new File(locks, sha1 + ".lock");
        if (LOGGER.isLoggable(Level.FINE))
            LOGGER.fine(
                    format(
                            "Mapped lock key %s to lock file %s. Attempting to lock on it.",
                            lockKey, file));
        return file;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " " + locksDirectory;
    }

    static class NioFileLock implements Resource.Lock {

        private final String lockKey;
        private final File file;
        private FileOutputStream fileStream;
        private FileLock fileLock;

        NioFileLock(String lockKey, File file) {
            this.lockKey = lockKey;
            this.file = file;
        }

        void acquire() throws IOException {
            // The file output stream can also fail to be acquired due to the other
            // nodes deleting the file, in which case the IOException is propagated for the caller
            // to retry
            this.fileStream = new FileOutputStream(file);
            try { // try to lock.
                this.fileLock = this.fileStream.getChannel().lock();
            } catch (OverlappingFileLockException | IOException e) {
                IOUtils.closeQuietly(this.fileStream);
                if (e instanceof IOException) {
                    throw (IOException) e;
                }
                throw new IOException(e);
            } // this one is also thrown with a message "avoided fs deadlock"

            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine(
                        format(
                                "Lock %s acquired by thread %s on file %s",
                                lockKey, Thread.currentThread().getId(), file));
            }
        }

        @Override
        @SuppressWarnings({"PMD.CloseResource", "PMD.UseTryWithResources"})
        public void release() {
            final FileLock lock = this.fileLock;
            final FileOutputStream fileStream = this.fileStream;
            this.fileLock = null;
            this.fileStream = null;
            if (lock == null) {
                return;
            }

            try {
                if (lock.isValid()) {
                    lock.release();
                    file.delete();
                } else if (LOGGER.isLoggable(Level.FINE)) {
                    // do not crap out, locks usage is only there to prevent duplication of work
                    LOGGER.fine(
                            "Lock key "
                                    + lockKey
                                    + " for releasing lock is unknown, it means "
                                    + "this lock was never acquired, or was released twice. "
                                    + "Current thread is: "
                                    + Thread.currentThread().getId()
                                    + ". "
                                    + "Are you running two instances in the same JVM using NIO locks? "
                                    + "This case is not supported and will generate exactly this error message");
                }
            } catch (IOException e) {
                throw new IllegalStateException(
                        "Failure while trying to release lock for key " + lockKey, e);
            } finally {
                IOUtils.closeQuietly(fileStream);
            }
        }

        @Override
        public String toString() {
            return "FileLock " + file.getName();
        }
    }
}
