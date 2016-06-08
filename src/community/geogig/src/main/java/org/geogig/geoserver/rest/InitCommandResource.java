/* (c) 2016 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geogig.geoserver.rest;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.util.Map;

import org.geogig.geoserver.config.GeoGigInitializer;
import org.geogig.geoserver.config.RepositoryInfo;
import org.geogig.geoserver.config.RepositoryManager;
import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.DataStoreInfo;
import org.geoserver.catalog.NamespaceInfo;
import org.geoserver.catalog.WorkspaceInfo;
import org.locationtech.geogig.api.plumbing.ResolveRepositoryName;
import org.locationtech.geogig.geotools.data.GeoGigDataStoreFactory;
import org.locationtech.geogig.rest.repository.CommandResource;
import org.restlet.data.Request;
import org.restlet.data.Status;
import org.restlet.resource.Representation;
import org.restlet.resource.Variant;

import com.google.common.base.Throwables;

public class InitCommandResource extends CommandResource {

    @Override
    protected String getCommandName() {
        return "init";
    }

    @Override
    protected Representation runCommand(Variant variant, Request request) {
        Representation representation = super.runCommand(variant, request);

        if (getResponse().getStatus() == Status.SUCCESS_CREATED) {
            String repositoryName = geogig.get().command(ResolveRepositoryName.class).call();
            // save the repo in the Manager
            RepositoryInfo repoInfo = saveRepository();
            Catalog catalog = RepositoryManager.get().getCatalog();
            setUpDataStore(catalog, repositoryName, repoInfo);
        }
        return representation;
    }

    private RepositoryInfo saveRepository() {
        // repo was just created, need to register it with an ID in the manager
        // cretae a RepositoryInfo object
        RepositoryInfo repoInfo = new RepositoryInfo();
        URI location = geogig.get().getRepository().getLocation();
        if ("file".equals(location.getScheme())) {
            // need the parent
            File parentDir = new File(location).getParentFile();
            location = parentDir.toURI();
        }
        // set the URI
        repoInfo.setLocation(location);
        // save the repo, this will set a UUID
        return RepositoryManager.get().save(repoInfo);
    }

    public DataStoreInfo setUpDataStore(Catalog catalog, String storeName, RepositoryInfo repoInfo) {
        NamespaceInfo ns = catalog.getDefaultNamespace();
        WorkspaceInfo ws = catalog.getDefaultWorkspace();
        DataStoreInfo ds = catalog.getFactory().createDataStore();
        ds.setEnabled(true);
        ds.setDescription("GeoGIG repository");
        ds.setName(storeName);
        ds.setType(GeoGigDataStoreFactory.DISPLAY_NAME);
        ds.setWorkspace(ws);
        Map<String, Serializable> connParams = ds.getConnectionParameters();

        connParams.put(GeoGigDataStoreFactory.REPOSITORY.key, repoInfo.getId());
        connParams.put(GeoGigDataStoreFactory.DEFAULT_NAMESPACE.key, ns.getURI());
        connParams.put(GeoGigDataStoreFactory.RESOLVER_CLASS_NAME.key,
                GeoGigInitializer.REPO_RESOLVER_CLASSNAME);
        catalog.add(ds);

        try {
            DataStoreInfo dsInfo = catalog.getDataStoreByName(ws, storeName);
            String repoId = (String) dsInfo.getConnectionParameters()
                    .get(GeoGigDataStoreFactory.REPOSITORY.key);
            RepositoryInfo info = RepositoryManager.get().get(repoId);
        } catch (IOException e) {
            Throwables.propagate(e);
        }
        return ds;
    }
}
