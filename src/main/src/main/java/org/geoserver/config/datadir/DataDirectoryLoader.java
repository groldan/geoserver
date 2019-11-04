/* (c) 2014 - 2015 Open Source Geospatial Foundation - all rights reserved
 * (c) 2001 - 2013 OpenPlans
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.config.datadir;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.Future;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.RecursiveTask;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.CoverageInfo;
import org.geoserver.catalog.CoverageStoreInfo;
import org.geoserver.catalog.DataStoreInfo;
import org.geoserver.catalog.FeatureTypeInfo;
import org.geoserver.catalog.LayerGroupInfo;
import org.geoserver.catalog.NamespaceInfo;
import org.geoserver.catalog.StyleInfo;
import org.geoserver.catalog.WMSLayerInfo;
import org.geoserver.catalog.WMSStoreInfo;
import org.geoserver.catalog.WMTSLayerInfo;
import org.geoserver.catalog.WMTSStoreInfo;
import org.geoserver.catalog.WorkspaceInfo;
import org.geoserver.catalog.impl.CatalogImpl;
import org.geoserver.config.GeoServer;
import org.geoserver.config.GeoServerInfo;
import org.geoserver.config.ResourceErrorHandling;
import org.geoserver.config.util.XStreamPersister;
import org.geoserver.config.util.XStreamPersisterFactory;
import org.geoserver.platform.GeoServerResourceLoader;
import org.geoserver.platform.resource.Resource;
import org.geoserver.platform.resource.Resource.Type;
import org.geoserver.platform.resource.Resources;
import org.geoserver.platform.resource.Resources.ExtensionFilter;
import org.geoserver.util.Filter;
import org.geotools.util.logging.Logging;

/**
 * Initializes GeoServer configuration and catalog on startup.
 *
 * <p>This class post processes the singleton beans {@link Catalog} and {@link GeoServer},
 * populating them from stored configuration.
 *
 * @author Justin Deoliveira, The Open Planning Project
 */
@SuppressWarnings("serial")
public class DataDirectoryLoader {

    static Logger LOGGER = Logging.getLogger("org.geoserver");

    /** Feature Type IO resource mapper */
    static final ResourceLayerMapper FEATURE_LAYER_MAPPER =
            new ResourceLayerMapper("featuretype.xml", "feature type");
    /** Coverage IO resource mapper */
    static final ResourceLayerMapper COVERAGE_LAYER_MAPPER =
            new ResourceLayerMapper("coverage.xml", "coverage");
    /** WMS Layer IO resource mapper */
    static final ResourceLayerMapper WMS_LAYER_MAPPER =
            new ResourceLayerMapper("wmslayer.xml", "wms layer");
    /** WMTS Layer IO resource mapper */
    static final ResourceLayerMapper WMTS_LAYER_MAPPER =
            new ResourceLayerMapper("wmtslayer.xml", "wmts layer");

    static final ExtensionFilter XML_FILTER = new Resources.ExtensionFilter("XML");

    private final LoaderContext context;

    public DataDirectoryLoader(
            GeoServerResourceLoader resourceLoader, XStreamPersisterFactory xpf) {
        Objects.requireNonNull(resourceLoader);
        Objects.requireNonNull(xpf);

        CatalogImpl catalog = new CatalogImpl();
        catalog.setResourceLoader(resourceLoader);

        // XStreamPersister xp = xpf.createXMLPersister();
        // xp.setCatalog(catalog);
        context = new LoaderContext(catalog, xpf);
    }

    public void dispose() {
        this.context.dispose();
    }

    public Future<Catalog> loadCatalog() {
        ForkJoinTask<Catalog> task = context.processingPool().submit(new CatalogLoader(context));
        return task;
    }

    private class CatalogLoader extends RecursiveTask<Catalog> {

        private LoaderContext context;

        public CatalogLoader(LoaderContext context) {
            this.context = context;
        }

        protected @Override Catalog compute() {
            final CatalogImpl catalog = context.catalog();
            // see if we really need to verify stores on startup
            final boolean checkStores = checkStoresOnStartup(context.persister());
            context.setCheckStores(checkStores);
            catalog.setExtendedValidation(checkStores);
            context.persister().setUnwrapNulls(false);
            context.persister().getSecurityManager();

            final GeoServerResourceLoader resourceLoader = context.resourceLoader();

            // global styles
            Resource styles = resourceLoader.get("styles");
            if (styles.getType() == Type.DIRECTORY) {
                invokeAll(new StylesLoader(context, styles));
            }

            // workspaces
            final Resource workspaces = resourceLoader.get("workspaces");
            if (workspaces.getType() == Type.DIRECTORY) {
                invokeAll(new WorkspacesLoader(context, workspaces));
            } else {
                LOGGER.warning("No 'workspaces' directory found, unable to load any stores.");
            }

            // layergroups
            Resource layergroups = resourceLoader.get("layergroups");
            if (layergroups.getType() == Type.DIRECTORY) {
                invokeAll(new LayerGroupsLoader(context, layergroups));
            }

            context.commit();
            context.persister().setUnwrapNulls(true);
            catalog.resolve();

            // re-enable extended validation
            catalog.setExtendedValidation(true);
            return catalog;
        }
    }

    static void log(Level level, String message, Object... args) {
        if (LOGGER.isLoggable(level)) {
            // LOGGER.log(level, String.format(message, args));
        }
    }

    static void log(Level level, Throwable exception, String message, Object... args) {
        if (LOGGER.isLoggable(level)) {
            // LOGGER.log(level, String.format(message, args), exception);
        }
    }

    private abstract static class BaseLoader extends RecursiveAction {
        protected final LoaderContext context;

        public BaseLoader(LoaderContext context) {
            Objects.requireNonNull(context);
            this.context = context;
        }

        public final @Override void compute() {
            log(
                    Level.WARNING,
                    "------------> Running %s on %s",
                    getClass().getSimpleName(),
                    Thread.currentThread().getName());
            computeInternal();
        }

        protected abstract void computeInternal();

        protected <T> Future<List<T>> load(
                Resource root, Filter<Resource> filter, ResourceMapper<T> mapper) {
            //            ForkJoinTask<List<T>> task = context.ioPool().submit(new
            // LoadResourcesTask<>(root, filter, mapper));
            //            return task;
            List<T> list;
            try {
                list = new LoadResourcesTask<>(root, filter, mapper).call();
            } catch (Exception e) {
                return CompletableFuture.completedFuture(null);
            }
            return CompletableFuture.completedFuture(list);
        }
    }

    private class WorkspacesLoader extends BaseLoader {
        private Resource workspacesFolder;

        public WorkspacesLoader(LoaderContext context, Resource workspacesFolder) {
            super(context);
            Objects.requireNonNull(workspacesFolder);
            this.workspacesFolder = workspacesFolder;
        }

        protected @Override void computeInternal() {
            final Future<List<WorkspaceContents>> wslistFuture =
                    load(
                            workspacesFolder,
                            Resources.DirectoryFilter.INSTANCE,
                            new WorkspaceMapper());

            @Nullable WorkspaceInfo defaultWorkspace = loadDefaultWorkspace();

            List<WorkspaceContents> wscontents;
            try {
                wscontents = wslistFuture.get();
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
                return;
            }
            List<WorkspaceLoader> workspaceTasks =
                    wscontents
                            .parallelStream()
                            .map(contents -> new WorkspaceLoader(context, contents))
                            .collect(Collectors.toList());

            invokeAll(workspaceTasks);

            CatalogImpl catalog = context.catalog();
            if (defaultWorkspace == null && !wscontents.isEmpty()) {
                WorkspaceContents defWsContents = wscontents.get(0);
                try {
                    defaultWorkspace =
                            depersist(
                                    context.persister(),
                                    defWsContents.contents,
                                    WorkspaceInfo.class);
                    // create the default.xml file
                    Resource dws = workspacesFolder.get("default.xml");
                    persist(context.persister(), defaultWorkspace, dws);
                } catch (Exception e) {
                    log(
                            Level.WARNING,
                            e,
                            "Failed to persist default workspace '%s'",
                            defWsContents.resource.name());
                }
            }
            // set the default workspace, this value might be null in the case of coming
            // from a 2.0.0 data directory. See
            // https://osgeo-org.atlassian.net/browse/GEOS-3440
            if (defaultWorkspace != null) {
                WorkspaceInfo ws = catalog.getWorkspaceByName(defaultWorkspace.getName());
                NamespaceInfo ns = catalog.getNamespaceByPrefix(defaultWorkspace.getName());
                catalog.setDefaultWorkspace(ws);
                catalog.setDefaultNamespace(ns);
            }
        }

        private @Nullable WorkspaceInfo loadDefaultWorkspace() {
            WorkspaceInfo defaultWorkspace = null;
            Resource dws = workspacesFolder.get("default.xml");
            if (Resources.exists(dws)) {
                try {
                    defaultWorkspace = depersist(context.persister(), dws, WorkspaceInfo.class);
                    log(Level.INFO, "Loaded default workspace %s", defaultWorkspace.getName());
                } catch (Exception e) {
                    log(Level.WARNING, e, "Failed to load default workspace");
                }
            } else {
                log(Level.WARNING, "No default workspace was found.");
            }
            return defaultWorkspace;
        }
    }

    private class WorkspaceLoader extends BaseLoader {
        private WorkspaceContents contents;

        public WorkspaceLoader(LoaderContext context, WorkspaceContents contents) {
            super(context);
            Objects.requireNonNull(contents);
            this.contents = contents;
        }

        protected @Override void computeInternal() {
            final Resource workspaceResource = contents.resource;
            final CatalogImpl catalog = context.catalog();
            final XStreamPersister persister = context.persister();

            final Future<List<StoreContents>> storeContentsFuture = loadStoreConents();

            WorkspaceInfo ws;
            NamespaceInfo ns;
            try { // load the workspace
                ws = depersist(persister, contents.contents, WorkspaceInfo.class);
            } catch (Exception e) {
                storeContentsFuture.cancel(true);
                log(Level.WARNING, e, "Failed to load workspace '%s'", workspaceResource.name());
                return;
            }
            try { // load the namespace
                ns = depersist(persister, contents.nsContents, NamespaceInfo.class);
            } catch (Exception e) {
                storeContentsFuture.cancel(true);
                log(
                        Level.WARNING,
                        e,
                        "Failed to load namespace for '%s'",
                        workspaceResource.name());
                return;
            }
            catalog.add(ws);
            catalog.add(ns);
            log(Level.INFO, "Loaded workspace '%s'", ws.getName());

            // workspace styles
            {
                Resource workspaceStyles = workspaceResource.get("styles");
                if (Resource.Type.DIRECTORY == workspaceStyles.getType()) {
                    invokeAll(new StylesLoader(context, workspaceStyles));
                }
            }
            // load stores. each store task loads layers, so runs after styles' been loaded
            {
                List<StoreContents> storeContents;
                try {
                    storeContents = storeContentsFuture.get();
                } catch (InterruptedException | ExecutionException e) {
                    e.printStackTrace();
                    return;
                }
                List<StoreLoader> storeTasks =
                        storeContents
                                .stream()
                                .map(sc -> new StoreLoader(context, sc))
                                .collect(Collectors.toList());
                invokeAll(storeTasks);
            }

            // load the layer groups for this workspace once styles and layers are in
            {
                Resource layergroups = workspaceResource.get("layergroups");
                if (Resource.Type.DIRECTORY == layergroups.getType()) {
                    invokeAll(new LayerGroupsLoader(context, layergroups));
                }
            }
        }

        private Future<List<StoreContents>> loadStoreConents() {
            return load(
                    this.contents.resource, Resources.DirectoryFilter.INSTANCE, new StoreMapper());
        }
    }

    private class LayerListLoader extends BaseLoader {

        private List<LayerContents> layerContents;
        private Class<? extends org.geoserver.catalog.ResourceInfo> resourceType;

        public LayerListLoader(
                LoaderContext context,
                List<LayerContents> layerContents,
                Class<? extends org.geoserver.catalog.ResourceInfo> resourceType) {
            super(context);
            Objects.requireNonNull(layerContents);
            Objects.requireNonNull(resourceType);
            this.resourceType = resourceType;
            this.layerContents = layerContents;
        }

        protected @Override void computeInternal() {
            LayerLoader<? extends org.geoserver.catalog.ResourceInfo> layerLoader =
                    new LayerLoader<>(resourceType, context.persister(), context.catalog());
            layerContents.forEach(layerLoader::accept);
        }
    }

    private class StoreLoader extends BaseLoader {

        private StoreContents storeContents;

        public StoreLoader(LoaderContext context, StoreContents contents) {
            super(context);
            Objects.requireNonNull(contents);
            this.storeContents = contents;
        }

        @Override
        protected void computeInternal() {
            final String resourceName = storeContents.resource.name();
            final CatalogImpl catalog = context.catalog();
            final XStreamPersister xp = context.persister();
            final Future<List<LayerContents>> layersFuture;
            final Class<? extends org.geoserver.catalog.ResourceInfo> resourceType;
            log(
                    Level.WARNING,
                    "Loading store %s on %s",
                    storeContents.resource.path(),
                    Thread.currentThread().getName());
            switch (resourceName) {
                case "datastore.xml":
                    layersFuture = loadDataStore(storeContents, catalog, xp, context.checkStores());
                    resourceType = FeatureTypeInfo.class;
                    break;
                case "coveragestore.xml":
                    layersFuture = loadCoverageStore(storeContents, catalog, xp);
                    resourceType = CoverageInfo.class;
                    break;
                case "wmsstore.xml":
                    layersFuture = loadWmsStore(storeContents, catalog, xp);
                    resourceType = WMSLayerInfo.class;
                    break;
                case "wmtsstore.xml":
                    layersFuture = loadWmtsStore(storeContents, catalog, xp);
                    resourceType = WMTSLayerInfo.class;
                    break;
                default:
                    log(
                            Level.WARNING,
                            "Ignoring store directory '%s'",
                            storeContents.resource.name());
                    return;
            }

            List<LayerContents> layers;
            try {
                layers = layersFuture.get();
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
                return;
            }

            final int threshold = 100;
            if (layers.size() > threshold) {
                List<LayerListLoader> tasks =
                        Lists.partition(layers, threshold)
                                .stream()
                                .map(sublist -> new LayerListLoader(context, sublist, resourceType))
                                .collect(Collectors.toList());
                invokeAll(tasks);
            } else {
                new LayerListLoader(context, layers, resourceType).compute();
            }
        }

        private Future<List<LayerContents>> loadDataStore(
                StoreContents storeContents,
                CatalogImpl catalog,
                XStreamPersister xp,
                boolean checkStores) {
            final Resource storeResource = storeContents.resource;
            final Future<List<LayerContents>> layerContentsFuture =
                    load(
                            storeResource.parent(),
                            Resources.DirectoryFilter.INSTANCE,
                            FEATURE_LAYER_MAPPER);
            try {
                DataStoreInfo ds = depersist(xp, storeContents.contents, DataStoreInfo.class);
                catalog.add(ds);
                log(
                        Level.INFO,
                        "Loaded data store '%s', %s",
                        ds.getName(),
                        (ds.isEnabled() ? "enabled" : "disabled"));
                if (checkStores && ds.isEnabled()) {
                    // connect to the datastore to determine if we should disable it
                    try {
                        ds.getDataStore(null);
                    } catch (Throwable t) {
                        log(Level.WARNING, "Error connecting to '%s'. Disabling.", ds.getName());
                        LOGGER.log(Level.INFO, "", t);
                        ds.setError(t);
                        ds.setEnabled(false);
                    }
                }
            } catch (Exception e) {
                layerContentsFuture.cancel(true);
                log(
                        Level.WARNING,
                        e,
                        "Failed to load data store '%s:%s'",
                        storeResource.parent().name(),
                        storeResource.name());
            }

            // load feature types
            return layerContentsFuture;
        }

        private Future<List<LayerContents>> loadCoverageStore(
                StoreContents storeContents, CatalogImpl catalog, XStreamPersister xp) {
            final Resource storeResource = storeContents.resource;
            Future<List<LayerContents>> layersFuture =
                    load(
                            storeResource.parent(),
                            Resources.DirectoryFilter.INSTANCE,
                            COVERAGE_LAYER_MAPPER);
            try {
                CoverageStoreInfo cs =
                        depersist(xp, storeContents.contents, CoverageStoreInfo.class);
                catalog.add(cs);
                log(
                        Level.INFO,
                        "Loaded coverage store '%s', %s",
                        cs.getName(),
                        (cs.isEnabled() ? "enabled" : "disabled"));
            } catch (Exception e) {
                layersFuture.cancel(true);
                log(Level.WARNING, e, "Failed to load coverage store '%s'", storeResource.name());
            }

            // load coverages
            return layersFuture;
        }

        private Future<List<LayerContents>> loadWmsStore(
                StoreContents storeContents, CatalogImpl catalog, XStreamPersister xp) {
            final Resource storeResource = storeContents.resource;
            Future<List<LayerContents>> layersFuture =
                    load(
                            storeResource.parent(),
                            Resources.DirectoryFilter.INSTANCE,
                            WMS_LAYER_MAPPER);
            try {
                WMSStoreInfo wms = depersist(xp, storeContents.contents, WMSStoreInfo.class);
                catalog.add(wms);
                LOGGER.info(
                        "Loaded wmsstore '"
                                + wms.getName()
                                + "', "
                                + (wms.isEnabled() ? "enabled" : "disabled"));
            } catch (Exception e) {
                layersFuture.cancel(true);
                LOGGER.log(
                        Level.WARNING,
                        "Failed to load wms store '" + storeResource.name() + "'",
                        e);
            }
            // load wms layers
            return layersFuture;
        }

        private Future<List<LayerContents>> loadWmtsStore(
                StoreContents storeContents, CatalogImpl catalog, XStreamPersister xp) {
            final Resource storeResource = storeContents.resource;
            Future<List<LayerContents>> layersFuture =
                    load(
                            storeResource.parent(),
                            Resources.DirectoryFilter.INSTANCE,
                            WMTS_LAYER_MAPPER);
            try {
                WMTSStoreInfo wmts = depersist(xp, storeContents.contents, WMTSStoreInfo.class);
                catalog.add(wmts);
                LOGGER.info("Loaded wmtsstore '" + wmts.getName() + "'");
            } catch (Exception e) {
                layersFuture.cancel(true);
                LOGGER.log(
                        Level.WARNING,
                        "Failed to load wmts store '" + storeResource.name() + "'",
                        e);
            }

            // load wmts layers
            return layersFuture;
        }
    }

    private class LayerGroupsLoader extends BaseLoader {

        private Resource layergroupsFolder;
        private List<byte[]> lgroupContents;

        public LayerGroupsLoader(LoaderContext context, Resource layergroups) {
            super(context);
            Objects.requireNonNull(layergroups);
            this.layergroupsFolder = layergroups;
        }

        public LayerGroupsLoader(LoaderContext context, List<byte[]> lgroupContents) {
            super(context);
            Objects.requireNonNull(lgroupContents);
            this.lgroupContents = lgroupContents;
        }

        protected @Override void computeInternal() {
            if (lgroupContents == null) {
                try {
                    lgroupContents =
                            load(layergroupsFolder, XML_FILTER, r -> r.getContents()).get();
                } catch (InterruptedException | ExecutionException e) {
                    e.printStackTrace();
                    return;
                }
            }
            final int threshold = 100;
            if (lgroupContents.size() > threshold) {
                List<LayerGroupsLoader> tasks =
                        Lists.partition(lgroupContents, threshold)
                                .stream()
                                .map(sublist -> new LayerGroupsLoader(context, sublist))
                                .collect(Collectors.toList());
                invokeAll(tasks);
            } else {
                XStreamPersister xp = context.persister();
                CatalogImpl catalog = context.catalog();
                for (byte[] contents : lgroupContents) {
                    try {
                        LayerGroupInfo lg = depersist(xp, contents, LayerGroupInfo.class);
                        if (lg.getLayers() == null || lg.getLayers().size() == 0) {
                            log(
                                    Level.WARNING,
                                    "Skipping empty layer group '%s', it is invalid",
                                    lg.getName());
                            continue;
                        }
                        catalog.add(lg);
                        log(Level.INFO, "Loaded layer group '%s'", lg.getName());
                    } catch (Exception e) {
                        log(Level.WARNING, e, "Failed to load layer group");
                    }
                }
            }
        }
    }

    private class StylesLoader extends BaseLoader {
        private Resource stylesFolder;
        private List<byte[]> stylesContents;

        public StylesLoader(LoaderContext context, Resource stylesResource) {
            super(context);
            Objects.requireNonNull(stylesResource);
            this.stylesFolder = stylesResource;
        }

        public StylesLoader(LoaderContext context, List<byte[]> stylesContents) {
            super(context);
            Objects.requireNonNull(stylesContents);
            this.stylesContents = stylesContents;
        }

        protected @Override void computeInternal() {
            Filter<Resource> styleFilter =
                    r ->
                            XML_FILTER.accept(r)
                                    && !Resources.exists(stylesFolder.get(r.name() + ".xml"));

            if (this.stylesContents == null) {
                try {
                    this.stylesContents =
                            load(stylesFolder, styleFilter, r -> r.getContents()).get();
                } catch (InterruptedException | ExecutionException e) {
                    e.printStackTrace();
                    return;
                }
            }
            final int threshold = 1_000;
            if (stylesContents.size() < threshold) {
                CatalogImpl catalog = context.catalog();
                for (byte[] rawStyle : stylesContents) {
                    try {
                        StyleInfo style = depersist(context.persister(), rawStyle, StyleInfo.class);
                        catalog.add(style);
                        log(Level.INFO, "Loaded style '%s'", style.getName());
                    } catch (Exception e) {
                        log(Level.WARNING, e, "Failed to load style");
                    }
                }
            } else {
                List<StylesLoader> tasks = new ArrayList<>();
                Iterables.partition(stylesContents, threshold)
                        .forEach(sublist -> tasks.add(new StylesLoader(context, sublist)));
                invokeAll(tasks);
            }
        }
    }

    /** Helper method which uses xstream to persist an object as xml on disk. */
    static void persist(XStreamPersister xp, Object obj, Resource f) throws Exception {
        BufferedOutputStream out = new BufferedOutputStream(f.out());
        xp.save(obj, out);
        out.flush();
        out.close();
    }

    /** Helper method which uses xstream to depersist an object as xml from disk. */
    public static <T> T depersist(XStreamPersister xp, Resource f, Class<T> clazz)
            throws IOException {
        return depersist(xp, f.getContents(), clazz);
    }

    /** Helper method which uses xstream to depersist an object as xml from disk. */
    public static <T> T depersist(XStreamPersister xp, byte[] contents, Class<T> clazz)
            throws IOException {
        try (InputStream in = new ByteArrayInputStream(contents)) {
            return xp.load(in, clazz);
        }
    }

    boolean checkStoresOnStartup(XStreamPersister xp) {
        Resource f = context.resourceLoader().get("global.xml");
        if (Resources.exists(f)) {
            try {
                GeoServerInfo global = depersist(xp, f, GeoServerInfo.class);
                final ResourceErrorHandling resourceErrorHandling =
                        global.getResourceErrorHandling();
                return resourceErrorHandling != null
                        && !ResourceErrorHandling.SKIP_MISCONFIGURED_LAYERS.equals(
                                resourceErrorHandling);
            } catch (IOException e) {
                LOGGER.log(
                        Level.INFO,
                        "Failed to determine the capabilities resource error handling",
                        e);
            }
        }
        return true;
    }

    /**
     * Some config directories in GeoServer are used to store workspace specific configurations,
     * identify them so that we don't log complaints about their existence
     *
     * @param f
     */
    static boolean isConfigDirectory(Resource dir) {
        String name = dir.name();
        boolean result = "styles".equals(name) || "layergroups".equals(name);
        return result;
    }
}
