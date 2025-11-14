/*
 * (c) 2021 Open Source Geospatial Foundation - all rights reserved This code is licensed under the
 * GPL 2.0 license, available at the root application directory.
 */
package org.geoserver.platform.resource;

import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.geoserver.platform.resource.Resource.Lock;

/** Adapts a {@link java.util.concurrent.locks.Lock} as a {@link org.geoserver.platform.resource.Resource.Lock} */
public class LockAdapter implements Lock {

    private static final Logger LOGGER = org.geotools.util.logging.Logging.getLogger(LockAdapter.class);

    private String key;
    private java.util.concurrent.locks.Lock lock;
    private boolean released;

    public LockAdapter(String key, java.util.concurrent.locks.Lock lock) {
        Objects.requireNonNull(lock);
        this.key = key;
        this.lock = lock;
    }

    @Override
    public void release() {
        if (!released) {
            released = true;
            if (LOGGER.isLoggable(Level.FINE)) LOGGER.finer("Releasing lock on " + key);
            this.lock.unlock();
            if (LOGGER.isLoggable(Level.FINE)) LOGGER.fine("Released lock on " + key);
        }
    }
}
