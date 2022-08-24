/* (c) 2022 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.catalog.datadir;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.CatalogInfo;
import org.geoserver.catalog.Info;
import org.geoserver.catalog.LayerGroupInfo;
import org.geoserver.catalog.LayerInfo;
import org.geoserver.catalog.NamespaceInfo;
import org.geoserver.catalog.ResourceInfo;
import org.geoserver.catalog.StoreInfo;
import org.geoserver.catalog.StyleInfo;
import org.geoserver.catalog.WorkspaceInfo;
import org.geoserver.catalog.datadir.DataDirectoryWalker.LayerDirectory;
import org.geoserver.catalog.datadir.DataDirectoryWalker.StoreDirectory;
import org.geoserver.catalog.datadir.DataDirectoryWalker.WorkspaceDirectory;
import org.geoserver.catalog.impl.CatalogImpl;
import org.geoserver.config.GeoServer;
import org.geoserver.config.GeoServerInfo;
import org.geoserver.config.LoggingInfo;
import org.geoserver.config.ServiceInfo;
import org.geoserver.config.SettingsInfo;
import org.geoserver.config.impl.GeoServerImpl;
import org.geoserver.config.util.XStreamPersister;
import org.geoserver.config.util.XStreamPersisterFactory;
import org.geoserver.config.util.XStreamServiceLoader;
import org.geoserver.platform.GeoServerExtensions;
import org.geoserver.platform.resource.FileSystemResourceStore;
import org.geoserver.platform.resource.Resource;
import org.geoserver.platform.resource.Resources;
import org.geotools.util.logging.Logging;
import org.opengis.filter.Filter;
import org.springframework.util.StringUtils;

class DataDirectoryLoader {

    private static final String PARALLELISM_CONFIG_KEY = "DATADIR_LOAD_PARALLELISM";

    private static final Logger LOGGER = Logging.getLogger(DataDirectoryLoader.class);

    private static final XStreamPersisterFactory xpf = new XStreamPersisterFactory();

    private static final ThreadLocal<XStreamPersister> XP =
            ThreadLocal.withInitial(xpf::createXMLPersister);

    private final AtomicLong readFileCount = new AtomicLong();

    private final DataDirectoryWalker fileWalk;
    private Catalog catalog;

    private boolean catalogLoaded, geoserverLoaded;

    private FileSystemResourceStore resourceStore;
    private List<XStreamServiceLoader<ServiceInfo>> serviceLoaders;

    public DataDirectoryLoader(
            FileSystemResourceStore resourceStore,
            List<XStreamServiceLoader<ServiceInfo>> serviceLoaders) {

        this.resourceStore = resourceStore;
        this.serviceLoaders = serviceLoaders;
        Path dataDirRoot = resourceStore.get("").dir().toPath();
        List<String> serviceFileNames =
                serviceLoaders.stream()
                        .map(XStreamServiceLoader::getFilename)
                        .collect(Collectors.toList());
        this.fileWalk = new DataDirectoryWalker(dataDirRoot, serviceFileNames);
    }

    public CatalogImpl loadCatalog() throws Exception {
        final int parallelism = determineParallelism();
        ExecutorService executor = new ForkJoinPool(parallelism);
        Future<CatalogImpl> loadTask = executor.submit(this::readCatalog);
        try {
            CatalogImpl catalog = loadTask.get();
            catalogLoaded = true;
            this.catalog = null;
            tryDispose();
            return catalog;
        } finally {
            executor.shutdownNow();
        }
    }

    public GeoServerImpl loadGeoServer(Catalog realCatalog) {
        Objects.requireNonNull(realCatalog);
        readFileCount.set(0);

        GeoServerImpl gs = new GeoServerImpl();
        // required when depersisting workspace settings and services
        gs.setCatalog(realCatalog);
        this.catalog = realCatalog;

        fileWalk.gsGlobal()
                .map(this::depersist)
                .map(GeoServerInfo.class::cast)
                .ifPresent(gs::setGlobal);

        fileWalk.gsLogging()
                .map(this::depersist)
                .map(LoggingInfo.class::cast)
                .ifPresent(gs::setLogging);

        loadSettings(gs);
        loadServices(gs);

        geoserverLoaded = true;
        tryDispose();

        LOGGER.config(String.format("Depersisted %,d Config files.%n", readFileCount.get()));
        return gs;
    }

    private void loadServices(GeoServerImpl gs) {
        loadRootServices(gs);
        fileWalk.workspaces().stream().parallel().forEach(ws -> loadServices(ws, gs));
    }

    private void loadRootServices(GeoServer geoServer) {
        Resource baseDirectory = resourceStore.get("");

        for (XStreamServiceLoader<ServiceInfo> loader : serviceLoaders) {
            loadService(loader, baseDirectory, geoServer).ifPresent(geoServer::add);
        }
        serviceLoaders.forEach(l -> loadService(l, baseDirectory, geoServer));
    }

    private void loadServices(WorkspaceDirectory ws, GeoServerImpl gs) {
        final Set<String> serviceFiles = ws.serviceInfoFileNames;
        if (serviceFiles.isEmpty()) return;

        final String wsName = ws.workspaceFile.getParent().getFileName().toString();
        final Resource wsdir = resourceStore.get("workspaces").get(wsName);

        for (XStreamServiceLoader<ServiceInfo> loader : serviceLoaders) {
            // filter loaders on available service files for the workspace
            if (!serviceFiles.contains(loader.getFilename())) continue;

            loadService(loader, wsdir, gs)
                    .ifPresent(
                            s -> {
                                WorkspaceInfo workspace = s.getWorkspace();
                                if (null != workspace) {
                                    gs.add(s);
                                } else {
                                    LOGGER.warning(
                                            String.format(
                                                    "Service %s on workspace directory '%s' has no workspace attached,"
                                                            + "service not loaded",
                                                    s.getName(), wsdir.name()));
                                }
                            });
        }
    }

    Optional<ServiceInfo> loadService(
            XStreamServiceLoader<ServiceInfo> loader, Resource directory, GeoServer geoServer) {
        ServiceInfo s = null;
        try {
            s = loader.load(geoServer, directory);
            LOGGER.config(
                    "Loaded service '"
                            + s.getId()
                            + "', "
                            + (s.isEnabled() ? "enabled" : "disabled"));
        } catch (Throwable t) {
            if (Resources.exists(directory)) {
                LOGGER.log(
                        Level.SEVERE,
                        "Failed to load the service configuration in directory: "
                                + directory
                                + " with loader for "
                                + loader.getServiceClass(),
                        t);
            } else {
                LOGGER.log(
                        Level.SEVERE,
                        "Failed to load the root service configuration with loader for "
                                + loader.getServiceClass(),
                        t);
            }
        }
        return Optional.ofNullable(s);
    }

    private void loadSettings(GeoServerImpl gs) {

        fileWalk.workspaces().stream()
                .parallel()
                .map(wd -> wd.settings)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(this::depersist)
                .map(SettingsInfo.class::cast)
                .forEach(gs::add);
    }

    private void tryDispose() {
        if (catalogLoaded && geoserverLoaded) {
            fileWalk.dispose();
        }
    }

    private CatalogImpl readCatalog() throws Exception {
        readFileCount.set(0);
        CatalogImpl catalogImpl = new CatalogImpl();
        catalogImpl.setExtendedValidation(false);
        this.catalog = catalogImpl;

        loadStyles(fileWalk.globalStyles().stream());
        loadWorkspaces(fileWalk.workspaces().stream());
        loadLayerGroups(fileWalk.globalLayerGroups().stream());

        LOGGER.config(String.format("Depersisted %,d Catalog files.%n", readFileCount.get()));
        return catalogImpl;
    }

    private void loadWorkspaces(Stream<WorkspaceDirectory> stream) {
        stream.parallel().forEach(this::loadWorkspace);
    }

    private void loadWorkspace(WorkspaceDirectory wsdir) {
        WorkspaceInfo wsinfo = depersist(wsdir.workspaceFile);
        NamespaceInfo nsinfo = depersist(wsdir.namespaceFile);
        catalog.add(wsinfo);
        catalog.add(nsinfo);

        loadStyles(wsdir.styles().stream());
        loadStores(wsdir.stores());
        loadLayerGroups(wsdir.layerGroups().stream());
    }

    private void loadStyles(Stream<Path> stream) {
        depersist(stream).map(StyleInfo.class::cast).forEach(catalog::add);
    }

    private void loadStores(Stream<StoreDirectory> stream) {
        stream.parallel()
                .forEach(
                        storeDir -> {
                            StoreInfo store = depersist(storeDir.storeFile);
                            catalog.add(store);
                            loadLayers(storeDir.layers());
                        });
    }

    private void loadLayers(Stream<LayerDirectory> layers) {
        layers.parallel()
                .forEach(
                        layerDir -> {
                            ResourceInfo resource = depersist(layerDir.resourceFile);
                            catalog.add(resource);
                            LayerInfo layer = depersist(layerDir.layerFile);
                            catalog.add(layer);
                        });
    }

    private void loadLayerGroups(Stream<Path> stream) {
        depersist(stream).map(LayerGroupInfo.class::cast).forEach(catalog::add);
    }

    private Stream<CatalogInfo> depersist(Stream<Path> stream) {
        return stream.parallel().map(this::depersist);
    }

    private <C extends Info> C depersist(Path file) {
        return depersist(file, this.catalog);
    }

    @SuppressWarnings("unchecked")
    private <C extends Info> C depersist(Path file, Catalog catalog) {
        try {
            try (InputStream in = Files.newInputStream(file, StandardOpenOption.READ)) {
                XStreamPersister xp = XP.get();
                xp.setCatalog(catalog);
                xp.setUnwrapNulls(false);
                // disable password decrypt at this stage, or xp will use GeoServerExtensions to
                // lookup the
                // GeoServerSecurityManager, dead-locking on the main thread
                xp.setEncryptPasswordFields(false);
                Info depersisted = xp.load(in, Info.class);
                if (null == depersisted) {
                    throw new IllegalStateException(file + " depersisted to null");
                }
                readFileCount.incrementAndGet();
                return (C) depersisted;
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private int determineParallelism() {
        String configuredParallelism = GeoServerExtensions.getProperty(PARALLELISM_CONFIG_KEY);
        final int processors = Runtime.getRuntime().availableProcessors();
        final int defParallelism = Math.min(processors, 16);
        int parallelism = defParallelism;
        if (StringUtils.hasText(configuredParallelism)) {
            boolean parseFail = false;
            try {
                parallelism = Integer.parseInt(configuredParallelism);
            } catch (NumberFormatException nfe) {
                parseFail = true;
            }
            if (parseFail || parallelism < 1) {
                parallelism = defParallelism;
                LOGGER.log(
                        Level.WARNING,
                        () ->
                                String.format(
                                        "Configured parallelism is invalid: %s=%s, using default of %d",
                                        PARALLELISM_CONFIG_KEY,
                                        configuredParallelism,
                                        defParallelism));
            }
        }
        return parallelism;
    }

    static void logCatalog(final String loaderName, final Catalog catalog) {
        int workspaces = catalog.count(WorkspaceInfo.class, Filter.INCLUDE);
        int namespaces = catalog.count(NamespaceInfo.class, Filter.INCLUDE);
        int stores = catalog.count(StoreInfo.class, Filter.INCLUDE);
        int resources = catalog.count(ResourceInfo.class, Filter.INCLUDE);
        int layers = catalog.count(LayerInfo.class, Filter.INCLUDE);
        int layergroups = catalog.count(LayerGroupInfo.class, Filter.INCLUDE);
        int styles = catalog.count(StyleInfo.class, Filter.INCLUDE);
        LOGGER.info(
                String.format(
                        "%s:\n\t"
                                + "workspaces: %,d\n\t"
                                + "namespaces: %,d\n\t"
                                + "stores: %,d\n\t"
                                + "resources: %,d\n\t"
                                + "layers: %,d\n\t"
                                + "layerGroups: %,d\n\t"
                                + "styles: %,d\n",
                        loaderName,
                        workspaces,
                        namespaces,
                        stores,
                        resources,
                        layers,
                        layergroups,
                        styles));
    }
}
