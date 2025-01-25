/* (c) 2021 Open Source Geospatial Foundation - all rights reserved
 *
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 *
 * Original from GeoWebCache 1.5.1 under a LGPL license
 */

package org.geoserver.platform.resource;

import static java.util.Objects.requireNonNull;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.geoserver.platform.resource.Resource.Lock;
import org.geotools.util.logging.Logging;

/** A lock provider based on file system locks */
public class DoubleLockProvider implements LockProvider {

    static final Logger LOGGER = Logging.getLogger(DoubleLockProvider.class.getName());

    private final LockProvider first;
    private final LockProvider second;

    public DoubleLockProvider(LockProvider first, LockProvider second) {
        requireNonNull(first);
        requireNonNull(second);
        this.first = first;
        this.second = second;
    }

    @Override
    public Lock acquire(String path) {
        LOGGER.fine(() -> "Acquiring first lock on %s from %s"
                .formatted(path, first.getClass().getSimpleName()));
        Lock firstLock = first.acquire(path);
        try {
            LOGGER.fine(() -> "Acquiring second lock on %s from %s"
                    .formatted(path, first.getClass().getSimpleName()));
            Lock secondLock = second.acquire(path);
            LOGGER.fine(() -> "Acquired both locks on %s".formatted(path));
            return new DoubleLock(firstLock, secondLock);
        } catch (RuntimeException e) {
            LOGGER.log(
                    Level.WARNING,
                    "Error acquiring lock on %s from %s. Releasing lock from %s"
                            .formatted(
                                    path,
                                    second.getClass().getSimpleName(),
                                    first.getClass().getSimpleName()),
                    e);
            firstLock.release();
            LOGGER.warning("Released lock on %s from %s"
                    .formatted(path, second.getClass().getSimpleName()));
            throw e;
        }
    }

    /**
     * Double lock, releases the "second" lock before releasing the "first" lock, which is the opposite order than
     * {@link DoubleLockProvider#acquire(String)}
     */
    private static class DoubleLock implements Lock {

        private final Lock first;
        private final Lock second;

        DoubleLock(Lock first, Lock second) {
            this.first = first;
            this.second = second;
        }

        @Override
        public void release() {
            try {
                second.release();
            } finally {
                first.release();
            }
        }
    }
}
