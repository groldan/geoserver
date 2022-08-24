/* (c) 2022 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.catalog.datadir;

import static org.geoserver.catalog.impl.ModificationProxy.unwrap;

import com.google.common.base.Stopwatch;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.LayerGroupInfo;
import org.geoserver.catalog.LayerInfo;
import org.geoserver.catalog.NamespaceInfo;
import org.geoserver.catalog.ResourceInfo;
import org.geoserver.catalog.ResourcePool;
import org.geoserver.catalog.StoreInfo;
import org.geoserver.catalog.StyleInfo;
import org.geoserver.catalog.WorkspaceInfo;
import org.geoserver.catalog.event.CatalogListener;
import org.geoserver.catalog.impl.CatalogImpl;
import org.geoserver.catalog.impl.ModificationProxy;
import org.geoserver.config.ConfigurationListener;
import org.geoserver.config.GeoServer;
import org.geoserver.config.GeoServerConfigPersister;
import org.geoserver.config.GeoServerLoader;
import org.geoserver.config.GeoServerLoaderListener;
import org.geoserver.config.GeoServerResourcePersister;
import org.geoserver.config.ServiceInfo;
import org.geoserver.config.ServicePersister;
import org.geoserver.config.SettingsInfo;
import org.geoserver.config.impl.GeoServerImpl;
import org.geoserver.config.util.XStreamPersister;
import org.geoserver.config.util.XStreamServiceLoader;
import org.geoserver.platform.GeoServerExtensions;
import org.geoserver.platform.GeoServerResourceLoader;
import org.geoserver.platform.resource.FileSystemResourceStore;
import org.geoserver.platform.resource.ResourceStore;
import org.geoserver.security.GeoServerSecurityManager;
import org.geoserver.security.password.ConfigurationPasswordEncryptionHelper;
import org.geotools.util.logging.Logging;
import org.opengis.filter.Filter;
import org.opengis.filter.IncludeFilter;

public class DataDirectoryGeoServerLoader extends GeoServerLoader {

    static Logger LOGGER =
            Logging.getLogger(DataDirectoryGeoServerLoader.class.getPackage().getName());

    private DataDirectoryLoader loader;

    private GeoServerSecurityManager securityManager;

    /**
     * @param resourceLoader
     * @param securityManager
     * @throws IllegalArgumentException if the resource loader's {@link ResourceStore} is not a
     *     {@link FileSystemResourceStore}
     */
    public DataDirectoryGeoServerLoader(
            GeoServerResourceLoader resourceLoader, GeoServerSecurityManager securityManager) {
        super(resourceLoader);
        this.securityManager = securityManager;
        this.loader = createLoader();
    }

    @Override
    public void reload() throws Exception {
        this.loader = createLoader();
        super.reload();
    }

    @Override
    protected void loadGeoServer(GeoServer geoServer, XStreamPersister xp) throws Exception {

        GeoServerImpl loaded = loader.loadGeoServer(geoServer.getCatalog());

        transferContents(loaded, geoServer, xp);

        getLoaderListener().loadGeoServer(geoServer, xp);
    }

    private void transferContents(GeoServerImpl source, GeoServer target, XStreamPersister xp) {
        removePersisterListeners(target);

        sync(source, target);

        restorePersisterListeners(target, xp);
    }

    private void sync(GeoServerImpl source, GeoServer target) {
        clear(target);

        target.setGlobal(unwrap(source.getGlobal()));
        target.setLogging(unwrap(source.getLogging()));

        // getServices() returns only the root services
        source.getServices().stream().map(ModificationProxy::unwrap).forEach(target::add);

        List<WorkspaceInfo> workspaces = target.getCatalog().getWorkspaces();

        workspaces.stream()
                .map(source::getSettings)
                .filter(Objects::nonNull)
                .map(ModificationProxy::unwrap)
                .forEach(target::add);

        workspaces.stream()
                .map(source::getServices)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .map(ModificationProxy::unwrap)
                .forEach(target::add);
    }

    private void clear(GeoServer target) {
        target.getServices().forEach(target::remove);

        target.getCatalog().getWorkspaces().stream()
                .forEach(
                        ws -> {
                            SettingsInfo settings = target.getSettings(ws);
                            if (null != settings) target.remove(settings);
                            target.getServices(ws).forEach(target::remove);
                        });
    }

    private void removePersisterListeners(GeoServer geoServer) {
        // avoid having the persister write down new config files while we read the
        // config,
        // otherwise it'll dump it back in xml files
        removeListener(geoServer, GeoServerConfigPersister.class);
        // avoid re-dumping all service config files during load, we'll attach it back
        // once done
        removeListener(geoServer, ServicePersister.class);
    }

    private void restorePersisterListeners(GeoServer geoServer, XStreamPersister xp) {
        geoServer.addListener(new GeoServerConfigPersister(resourceLoader, xp));

        // add event listener which persists changes
        List<XStreamServiceLoader<ServiceInfo>> loaders = findServiceLoaders();
        geoServer.addListener(new ServicePersister(loaders, geoServer));
    }

    private void removeListener(GeoServer geoServer, Class<? extends ConfigurationListener> type) {
        findListener(type, geoServer.getListeners()).ifPresent(geoServer::removeListener);
    }

    private <T> Optional<T> findListener(Class<T> type, Collection<?> listeners) {
        return listeners.stream()
                .filter(l -> type.isAssignableFrom(l.getClass()))
                .map(type::cast)
                .findFirst();
    }

    /**
     * @param targetCatalog the actual catalog bean to load config objects into
     * @param xp not used for loading {@link DataDirectoryLoader} creates one {@code
     *     XStreamPersister} per loading thread.
     */
    @Override
    protected void loadCatalog(Catalog targetCatalog, XStreamPersister xp) throws Exception {
        Stopwatch startedStopWatch = logStart();
        final CatalogImpl newlyLoadedCatalog = loader.loadCatalog();
        logStop(startedStopWatch.stop(), newlyLoadedCatalog);

        decryptDataStorePasswords(newlyLoadedCatalog);

        transferContents(newlyLoadedCatalog, targetCatalog, xp);

        getLoaderListener().loadCatalog(targetCatalog, xp);
    }

    private void decryptDataStorePasswords(CatalogImpl catalog) {
        ConfigurationPasswordEncryptionHelper helper =
                securityManager.getConfigPasswordEncryptionHelper();
        catalog.getDataStores().forEach(helper::decode);
    }

    /**
     * @throws UnsupportedOperationException, this method is defined in {@link GeoServerLoader} but
     *     not called by it not this subclass
     */
    @Override
    protected void readCatalog(Catalog catalog, XStreamPersister xp) throws Exception {
        throw new UnsupportedOperationException();
    }

    private DataDirectoryLoader createLoader() {
        FileSystemResourceStore resourceStore = resolveResourceStore(resourceLoader);
        List<XStreamServiceLoader<ServiceInfo>> serviceLoaders = findServiceLoaders();
        return new DataDirectoryLoader(resourceStore, serviceLoaders);
    }

    public static List<XStreamServiceLoader<ServiceInfo>> findServiceLoaders() {
        @SuppressWarnings({"unchecked", "rawtypes"})
        List<XStreamServiceLoader<ServiceInfo>> loaders =
                (List) GeoServerExtensions.extensions(XStreamServiceLoader.class);
        return loaders;
    }

    private FileSystemResourceStore resolveResourceStore(GeoServerResourceLoader resourceLoader) {
        ResourceStore resourceStore = resourceLoader.getResourceStore();
        if (!(resourceStore instanceof FileSystemResourceStore)) {
            throw new IllegalArgumentException(
                    "Expected ResourceStore to be FileSystemResourceStore, got "
                            + resourceStore.getClass().getName());
        }
        return (FileSystemResourceStore) resourceStore;
    }

    private void transferContents(CatalogImpl source, Catalog target, XStreamPersister xp) {
        List<CatalogListener> preservedListeners = removePersisterListeners(target);

        sync(source, target);

        restoreListeners(target, preservedListeners, xp);
    }

    private void sync(CatalogImpl source, Catalog target) {
        if (target instanceof CatalogImpl) {
            // make to remove the old resource pool catalog listener, CatalogImpl.sync()
            // will replace the target catalog's resource pool by the source's
            target.removeListeners(ResourcePool.CacheClearingListener.class);
            ((CatalogImpl) target).sync(source);
            target.setResourceLoader(super.resourceLoader);
        } else {
            source.getStyles().forEach(target::add);
            source.getNamespaces().forEach(target::add);
            source.getWorkspaces().forEach(target::add);
            source.getStores(StoreInfo.class).forEach(target::add);
            source.getResources(ResourceInfo.class).forEach(target::add);
            source.getLayers().forEach(target::add);
            source.getLayerGroups().forEach(target::add);
            source.getMaps().forEach(target::add);
        }
    }

    private void restoreListeners(
            Catalog target, List<CatalogListener> preservedListeners, XStreamPersister xp) {
        // attach back the old listeners
        preservedListeners.forEach(target::addListener);

        // add the listener which will persist changes
        target.addListener(new GeoServerConfigPersister(resourceLoader, xp));
        // and the one that handles other resource synchronizations such as sld file
        // names when styles are renamed
        target.addListener(new GeoServerResourcePersister(target));
    }

    private List<CatalogListener> removePersisterListeners(Catalog catalog) {
        // we are going to synch up the catalogs and need to preserve listeners,
        // but these two fellas are attached to the new catalog as well
        catalog.removeListeners(GeoServerConfigPersister.class);
        catalog.removeListeners(GeoServerResourcePersister.class);

        return new ArrayList<>(catalog.getListeners());
    }

    private GeoServerLoaderListener getLoaderListener() {
        /** Loads the registered listener from Spring application context if exists. */
        GeoServerLoaderListener bean = GeoServerExtensions.bean(GeoServerLoaderListener.class);
        return bean == null ? GeoServerLoaderListener.EMPTY_LISTENER : bean;
    }

    private Stopwatch logStart() {
        LOGGER.log(Level.INFO, "Loading catalog from %s", resourceLoader.getBaseDirectory());
        return Stopwatch.createStarted();
    }

    private void logStop(Stopwatch stoppedSw, final Catalog catalog) {
        LOGGER.log(Level.INFO, "Catalog loaded in %s", stoppedSw);
        LOGGER.config(
                () -> {
                    String msg =
                            "Loaded Catalog contents: "
                                    + "workspaces: %,d, "
                                    + "namespaces: %,d, "
                                    + "styles: %,d, "
                                    + "stores: %,d, "
                                    + "resources: %,d, "
                                    + "layers: %,d, "
                                    + "layer groups: %,d.";

                    final IncludeFilter all = Filter.INCLUDE;
                    return String.format(
                            msg,
                            catalog.count(WorkspaceInfo.class, all),
                            catalog.count(NamespaceInfo.class, all),
                            catalog.count(StyleInfo.class, all),
                            catalog.count(StoreInfo.class, all),
                            catalog.count(ResourceInfo.class, all),
                            catalog.count(LayerInfo.class, all),
                            catalog.count(LayerGroupInfo.class, all));
                });
    }
}
