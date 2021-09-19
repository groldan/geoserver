/*
 * (c) 2021 Open Source Geospatial Foundation - all rights reserved This code is licensed under the
 * GPL 2.0 license, available at the root application directory.
 */
package org.geoserver.jdbcstore.locks;

import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.geoserver.platform.resource.LockAdapter;
import org.geoserver.platform.resource.LockProvider;
import org.geoserver.platform.resource.Resource.Lock;
import org.springframework.integration.support.locks.LockRegistry;

/**
 * Adapts a spring-integration {@link LockRegistry} as a GeoServer {@link LockProvider}
 *
 * <p>Of most interest here is using a {@link LockRegistry} backed by a {@link
 * org.springframework.integration.jdbc.lock.DefaultLockRepository} to provide distributed locking
 * on a clustered environment using the database to hold the locks.
 */
public class LockRegistryAdapter implements LockProvider {

    private static final Logger LOGGER =
            org.geotools.util.logging.Logging.getLogger("org.geoserver.jdbcstore.locks");

    private LockRegistry registry;

    public LockRegistryAdapter(LockRegistry registry) {
        Objects.requireNonNull(registry);
        this.registry = registry;
    }

    @Override
    public Lock acquire(String path) {
        Objects.requireNonNull(path);
        if (LOGGER.isLoggable(Level.FINE)) LOGGER.finer("Acquiring lock on " + path);

        java.util.concurrent.locks.Lock lock = registry.obtain(path);
        lock.lock();

        if (LOGGER.isLoggable(Level.FINE)) LOGGER.fine("Acquired lock on " + path);
        return new LockAdapter(path, lock);
    }
}
