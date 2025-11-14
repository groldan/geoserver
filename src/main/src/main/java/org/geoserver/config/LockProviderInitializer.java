/* (c) 2017 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.config;

import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import org.geoserver.platform.GeoServerExtensions;
import org.geoserver.platform.resource.LockProvider;
import org.geoserver.platform.resource.NullLockProvider;
import org.geoserver.platform.resource.ResourceStore;
import org.geotools.util.logging.Logging;

/**
 * Initializes LockProvider based on configuration settings.
 *
 * <p>
 *
 * @author Jody Garnett (Boundless)
 */
public class LockProviderInitializer implements GeoServerInitializer {

    private static final Logger LOGGER = Logging.getLogger(LockProviderInitializer.class);

    ConfigurationListenerAdapter listener = new ConfigurationListenerAdapter() {
        @Override
        public void handleGlobalChange(
                GeoServerInfo global, List<String> propertyNames, List<Object> oldValues, List<Object> newValues) {
            boolean reload = false;
            String lockProviderName = null;
            if (propertyNames.contains("lockProviderName")) {
                lockProviderName = (String) newValues.get(propertyNames.indexOf("lockProviderName"));
                reload = true;
            }
            if (reload) {
                try {
                    setLockProvider(lockProviderName);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }
    };

    @Override
    public void initialize(GeoServer geoServer) throws Exception {
        // Consider moving earlier to make use of the requested LockProvider during initial
        // configuration
        String lockProviderName = geoServer.getGlobal().getLockProviderName();
        if (lockProviderName != null) {
            LOGGER.info("Setting Lock provider " + lockProviderName + " as instructed by configuration");
            setLockProvider(lockProviderName);
        }

        geoServer.addListener(listener);
    }

    public static void setLockProvider(@Nullable String lockProviderName) {
        ResourceStore store = (ResourceStore) GeoServerExtensions.bean("resourceStore");
        Objects.requireNonNull(store);

        final LockProvider lockProvider;
        if (lockProviderName == null) {
            if (store.getLockProvider() != null) {
                // a NullLockProvider, unless explicitly requested, would do more harm than good,
                // given the ResourceStore implementation should know what kind of locking to use by
                // default depending on its specific needs/underlying platform.
                LOGGER.warning("Ignoring request to set ResourceStore lock provider to null, it's currently using "
                        + store.getLockProvider().getClass().getSimpleName());
                return;
            }
            // for backwards compatibility
            lockProvider = NullLockProvider.instance();
        } else {
            Object provider = GeoServerExtensions.bean(lockProviderName);
            if (provider == null) {
                throw new IllegalStateException(
                        "Could not find " + lockProviderName + " lock provider in spring application context");
            } else if (!(provider instanceof LockProvider)) {
                throw new IllegalStateException("Found "
                        + lockProviderName
                        + "("
                        + provider.getClass().getName()
                        + ") in application context, but it was not a LockProvider");
            }
            lockProvider = (LockProvider) provider;
        }

        if (lockProvider != store.getLockProvider()) {
            store.setLockProvider(lockProvider);
        }
    }
}
