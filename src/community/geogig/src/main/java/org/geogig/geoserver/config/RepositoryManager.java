/* (c) 2016 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geogig.geoserver.config;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.geoserver.catalog.Predicates.and;
import static org.geoserver.catalog.Predicates.equal;
import static org.geoserver.catalog.Predicates.isNull;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import javax.annotation.Nullable;

import org.geoserver.catalog.CascadeDeleteVisitor;
import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.CatalogInfo;
import org.geoserver.catalog.DataStoreInfo;
import org.geoserver.catalog.FeatureTypeInfo;
import org.geoserver.catalog.LayerInfo;
import org.geoserver.catalog.util.CloseableIterator;
import org.geoserver.platform.GeoServerExtensions;
import org.geoserver.platform.resource.Resource;
import org.geoserver.web.GeoServerApplication;
import org.locationtech.geogig.api.Context;
import org.locationtech.geogig.api.ContextBuilder;
import org.locationtech.geogig.api.GeoGIG;
import org.locationtech.geogig.api.GlobalContextBuilder;
import org.locationtech.geogig.api.Ref;
import org.locationtech.geogig.api.Remote;
import org.locationtech.geogig.api.porcelain.BranchListOp;
import org.locationtech.geogig.api.porcelain.InitOp;
import org.locationtech.geogig.cli.CLIContextBuilder;
import org.locationtech.geogig.geotools.data.GeoGigDataStoreFactory;
import org.locationtech.geogig.remote.IRemoteRepo;
import org.locationtech.geogig.remote.RemoteUtils;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.RepositoryResolver;
import org.opengis.filter.Filter;

import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.base.Supplier;
import com.google.common.collect.Lists;

public class RepositoryManager {
    static {
        if (GlobalContextBuilder.builder() == null
                || GlobalContextBuilder.builder().getClass().equals(ContextBuilder.class)) {
            GlobalContextBuilder.builder(new CLIContextBuilder());
        }
    }

    private static class StaticSupplier implements Supplier<RepositoryManager>, Serializable {
        private static final long serialVersionUID = 3706728433275296134L;

        @Override
        public RepositoryManager get() {
            return RepositoryManager.get();
        }
    }

    private final ConfigStore store;

    private final RepositoryCache repoCache;

    private static RepositoryManager INSTANCE;
    
    private Catalog catalog = null;

    public static synchronized RepositoryManager get() {
        if (INSTANCE == null) {
            INSTANCE = GeoServerExtensions.bean(RepositoryManager.class);
            Preconditions.checkState(INSTANCE != null);
        }
        return INSTANCE;
    }

    public static void close() {
        if (INSTANCE != null) {
            INSTANCE.repoCache.invalidateAll();
            INSTANCE = null;
        }
    }

    public static Supplier<RepositoryManager> supplier() {
        return new StaticSupplier();
    }

    public RepositoryManager(ConfigStore store) {
        checkNotNull(store);
        this.store = store;
        this.repoCache = new RepositoryCache(this);
    }

    public List<RepositoryInfo> getAll() {
        return store.getRepositories();
    }
    
    public void invalidate(final String repoId) {
    	this.repoCache.invalidate(repoId);
    }
    
    public GeoGIG createRepo(final Hints hints) {
        // get the Config store location
        // only generate a location if no URI is set in the hints
        if (!hints.get(Hints.REPOSITORY_URL).isPresent()) {
            Optional<Serializable> repositoryName = hints.get(Hints.REPOSITORY_NAME);
            // use the name from the Hints. It should never be null, but in case it is, generate one
            String repoName = repositoryName.isPresent() ? repositoryName.get().toString() :
                    UUID.randomUUID().toString();
            // no location set yet, generate one
            Resource root = store.getConfigRoot();
            File parent = root.parent().dir().getAbsoluteFile();
            File f = new File(parent, repoName);
            final URI repoURI = f.toURI().normalize();
            hints.set(Hints.REPOSITORY_URL, repoURI);
        }

        Context context = GlobalContextBuilder.builder().build(hints);

        GeoGIG geogig = new GeoGIG(context);

        return geogig;
    }

    public RepositoryInfo get(final String repoId) throws IOException {
        try {
            return store.load(repoId);
        } catch (FileNotFoundException e) {
            throw new NoSuchElementException("No repository named " + repoId + " exists");
        }
    }

    public List<DataStoreInfo> findGeogigStores() {
        return findGeogigStores(this.catalog);
    }

    public Catalog getCatalog() {
        return this.catalog;
    }

    public void setCatalog(Catalog catalog) {
    	this.catalog = catalog;
    }

    static List<DataStoreInfo> findGeogigStores(Catalog catalog) {
        org.opengis.filter.Filter filter = equal("type", GeoGigDataStoreFactory.DISPLAY_NAME);
        return findGeoGigStores(catalog, filter);
    }

    static List<DataStoreInfo> findGeogigStoresWithOldConfiguration(Catalog catalog) {
        org.opengis.filter.Filter filter = and(equal("type", GeoGigDataStoreFactory.DISPLAY_NAME),
                isNull("connectionParameters." + GeoGigDataStoreFactory.RESOLVER_CLASS_NAME.key));
        return findGeoGigStores(catalog, filter);
    }

    private static List<DataStoreInfo> findGeoGigStores(Catalog catalog,
            org.opengis.filter.Filter filter) {
        List<DataStoreInfo> geogigStores;
        CloseableIterator<DataStoreInfo> stores = catalog.list(DataStoreInfo.class, filter);
        try {
            geogigStores = Lists.newArrayList(stores);
        } finally {
            stores.close();
        }

        return geogigStores;
    }

    public List<DataStoreInfo> findDataStores(final String repoId) {
        Filter filter = equal("type", GeoGigDataStoreFactory.DISPLAY_NAME);

        String locationKey = "connectionParameters." + GeoGigDataStoreFactory.REPOSITORY.key;
        filter = and(filter, equal(locationKey, repoId));
        List<DataStoreInfo> dependent;
        try (CloseableIterator<DataStoreInfo> stores = this.catalog.list(DataStoreInfo.class,
                filter)) {
            dependent = Lists.newArrayList(stores);
        }
        return dependent;
    }

    public List<? extends CatalogInfo> findDependentCatalogObjects(final String repoId) {
        Filter filter = equal("type", GeoGigDataStoreFactory.DISPLAY_NAME);

        String locationKey = "connectionParameters." + GeoGigDataStoreFactory.REPOSITORY.key;
        filter = and(filter, equal(locationKey, repoId));
        List<DataStoreInfo> stores = findDataStores(repoId);
        List<CatalogInfo> dependent = new ArrayList<CatalogInfo>(stores);
        for (DataStoreInfo store : stores) {
            List<FeatureTypeInfo> ftypes = this.catalog.getFeatureTypesByDataStore(store);
            dependent.addAll(ftypes);
            for (FeatureTypeInfo ftype : ftypes) {
                dependent.addAll(this.catalog.getLayers(ftype));
            }
        }

        return dependent;
    }

    public List<LayerInfo> findLayers(DataStoreInfo store) {
        Filter filter = equal("resource.store.id", store.getId());
        try (CloseableIterator<LayerInfo> it = this.catalog.list(LayerInfo.class, filter)) {
            return Lists.newArrayList(it);
        }
    }

    public List<FeatureTypeInfo> findFeatureTypes(DataStoreInfo store) {
        Filter filter = equal("store.id", store.getId());
        try (CloseableIterator<FeatureTypeInfo> it = this.catalog.list(FeatureTypeInfo.class,
                filter)) {
            return Lists.newArrayList(it);
        }
    }

    public static boolean isGeogigDirectory(final File file) {
        if (file == null) {
            return false;
        }
        final File geogigDir = new File(file, ".geogig");
        final boolean isGeogigDirectory = geogigDir.exists() && geogigDir.isDirectory();
        return isGeogigDirectory;
    }

    public RepositoryInfo save(RepositoryInfo info) {
        Preconditions.checkNotNull(info.getLocation());
        if (info.getId() == null) {
            create(info);
        }
        // so far we don't need to invalidate the GeoGIG instance from the cache here... re-evaluate
        // if any configuration option would require so in the future
        return store.save(info);
    }

    private void create(final RepositoryInfo repoInfo) {
        // File targetDirectory = new File(repoInfo.getLocation());
        // Preconditions.checkArgument(!isGeogigDirectory(targetDirectory));

        URI repoURI = repoInfo.getLocation();
        RepositoryResolver resolver = RepositoryResolver.lookup(repoURI);
        if (!resolver.repoExists(repoURI)) {
            Hints hints = new Hints();
            hints.set(Hints.REPOSITORY_URL, repoURI);
            Context context = GlobalContextBuilder.builder().build(hints);
            GeoGIG geogig = new GeoGIG(context);
            try {
                Repository repository = geogig.command(InitOp.class).call();
                Preconditions.checkState(repository != null);
            } finally {
                geogig.close();
            }
        }
    }

    public List<Ref> listBranches(final String repositoryId) throws IOException {
        GeoGIG geogig = getRepository(repositoryId);
        List<Ref> refs = geogig.command(BranchListOp.class).call();
        return refs;
    }

    public GeoGIG getRepository(String repositoryId) throws IOException {
        return repoCache.get(repositoryId);
    }

    public void delete(final String repoId) {
        List<DataStoreInfo> repoStores = findDataStores(repoId);
        CascadeDeleteVisitor deleteVisitor = new CascadeDeleteVisitor(this.catalog);
        for (DataStoreInfo storeInfo : repoStores) {
            storeInfo.accept(deleteVisitor);
        }
        try {
            this.store.delete(repoId);
        } finally {
            this.repoCache.invalidate(repoId);
        }
    }

    RepositoryInfo findOrCreateByLocation(final URI repositoryURI) {
        List<RepositoryInfo> repos = getAll();
        for (RepositoryInfo info : repos) {
            if (Objects.equal(info.getLocation(), repositoryURI)) {
                return info;
            }
        }
        RepositoryInfo info = new RepositoryInfo();
        info.setLocation(repositoryURI);
        return save(info);
    }

    /**
     * Utility class to connect to a remote to see if its alive and we're able to connect.
     * 
     * @return the remote's head ref if succeeded
     * @throws Exception if can't connect for any reason; the exception message should be indicative
     *         of the problem
     */
    public static Ref pingRemote(final String location, @Nullable String user,
            @Nullable String password) throws Exception {

        if (Strings.isNullOrEmpty(location)) {
            throw new IllegalArgumentException("Please indicate the remote repository URL");
        }
        Remote remote;
        {
            String fetchurl = location;
            String pushurl = location;
            String name = "tempremote";
            String fetch = "+" + Ref.HEADS_PREFIX + "*:" + Ref.REMOTES_PREFIX + name + "/*";
            boolean mapped = false;
            String mappedBranch = null;
            remote = new Remote(name, fetchurl, pushurl, fetch, mapped, mappedBranch, user,
                    password);
        }

        return pingRemote(remote);
    }

    private static Ref pingRemote(Remote remote) throws Exception {

        Optional<IRemoteRepo> remoteRepo;
        try {
            Hints hints = Hints.readOnly();
            Repository localRepo = GlobalContextBuilder.builder().build(hints).repository();
            remoteRepo = RemoteUtils.newRemote(localRepo, remote, null);
            if (!remoteRepo.isPresent()) {
                throw new IllegalArgumentException("Repository not found or not reachable");
            } else {
                IRemoteRepo repo = remoteRepo.get();
                try {
                    repo.open();
                    Ref head = repo.headRef();
                    return head;
                } finally {
                    repo.close();
                }
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Unable to connect: " + e.getMessage(), e);
        }
    }
}
