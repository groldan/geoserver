/* (c) 2014 Open Source Geospatial Foundation - all rights reserved
 * (c) 2001 - 2013 OpenPlans
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.gwc;

import org.geoserver.platform.resource.LockProvider;
import org.geoserver.platform.resource.Paths;
import org.geoserver.platform.resource.ResourceStore;
import org.geowebcache.GeoWebCacheException;

/**
 * Adapter allowing GeoWebCache to make use of {@link org.geoserver.platform.resource.LockProvider}.
 *
 * <p>This implementation is provided to allow GeoWebCache to make use of the global lock provider used for
 * ResourceStore.
 *
 * @author Jody Garnett (Boundless)
 */
public class GeoServerLockProvider implements org.geowebcache.locks.LockProvider {

    ResourceStore delegate;

    @Override
    public Lock getLock(String lockKey) throws GeoWebCacheException {
        String path = Paths.convert(lockKey);
        LockProvider lockProvider = delegate.getLockProvider();
        final org.geoserver.platform.resource.Resource.Lock lock = lockProvider.acquire(path);
        return new Lock() {

            @Override
            public void release() throws GeoWebCacheException {
                try {
                    lock.release();
                } catch (IllegalArgumentException trouble) {
                    throw new GeoWebCacheException(trouble);
                }
            }

            @Override
            public String toString() {
                return lock.toString();
            }
        };
    }

    public ResourceStore getDelegate() {
        return delegate;
    }

    public void setDelegate(ResourceStore delegate) {
        this.delegate = delegate;
    }
}
