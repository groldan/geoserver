/*
 * (c) 2014 - 2016 Open Source Geospatial Foundation - all rights reserved (c) 2001 - 2013 OpenPlans
 * This code is licensed under the GPL 2.0 license, available at the root application directory.
 */
package org.geoserver.data.test;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.geoserver.data.test.CiteTestData.CDF_PREFIX;
import static org.geoserver.data.test.CiteTestData.CDF_TYPENAMES;
import static org.geoserver.data.test.CiteTestData.CDF_URI;
import static org.geoserver.data.test.CiteTestData.CGF_PREFIX;
import static org.geoserver.data.test.CiteTestData.CGF_TYPENAMES;
import static org.geoserver.data.test.CiteTestData.CGF_URI;
import static org.geoserver.data.test.CiteTestData.CITE_PREFIX;
import static org.geoserver.data.test.CiteTestData.CITE_TYPENAMES;
import static org.geoserver.data.test.CiteTestData.CITE_URI;
import static org.geoserver.data.test.CiteTestData.COVERAGES;
import static org.geoserver.data.test.CiteTestData.DEFAULT_PREFIX;
import static org.geoserver.data.test.CiteTestData.DEFAULT_RASTER_STYLE;
import static org.geoserver.data.test.CiteTestData.DEFAULT_URI;
import static org.geoserver.data.test.CiteTestData.DEFAULT_VECTOR_STYLE;
import static org.geoserver.data.test.CiteTestData.SF_PREFIX;
import static org.geoserver.data.test.CiteTestData.SF_TYPENAMES;
import static org.geoserver.data.test.CiteTestData.SF_URI;
import static org.geoserver.data.test.CiteTestData.WCS_PREFIX;
import static org.geoserver.data.test.CiteTestData.WCS_TYPENAMES;
import static org.geoserver.data.test.CiteTestData.WCS_URI;
import java.io.File;
import javax.xml.namespace.QName;
import org.easymock.EasyMock;
import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.LayerGroupInfo;
import org.geoserver.catalog.LayerInfo;
import org.geoserver.catalog.ResourceInfo;
import org.geoserver.catalog.ResourcePool;
import org.geoserver.catalog.StoreInfo;
import org.geoserver.catalog.StyleInfo;
import org.geoserver.catalog.WorkspaceInfo;
import org.geoserver.catalog.impl.CatalogFactoryImpl;
import org.geoserver.data.test.MockCatalogBuilder.Callback;
import org.geoserver.platform.GeoServerExtensionsHelper;
import org.geoserver.platform.GeoServerResourceLoader;

/**
 * Helper class used to creat mock objects during GeoServer testing.
 *
 * <p>
 * Utility methods are provided to create many common configuration and resource access objects.
 */
public class MockCreator implements Callback {

    /**
     * Creates GeoServerResouceLoader around provided test data.
     *
     * <p>
     * Created bean is registered with GeoServerExtensions as the singleton resourceLoader.
     *
     * @param testData Used to access base directory
     * @return GeoServerResourceLoader (registered with GeoServerExtensions)
     */
    public GeoServerResourceLoader createResourceLoader(MockTestData testData) throws Exception {
        File data = testData.getDataDirectoryRoot();
        GeoServerResourceLoader loader = new GeoServerResourceLoader(data);

        GeoServerExtensionsHelper.singleton("resourceLoader", loader); // treat as singleton

        return loader;
    }

    public Catalog createCatalog(MockTestData testData) throws Exception {
        GeoServerResourceLoader loader = createResourceLoader(testData);

        final Catalog catalog = createMock(Catalog.class);
        expect(catalog.getFactory()).andReturn(new CatalogFactoryImpl(catalog)).anyTimes();
        expect(catalog.getResourceLoader()).andReturn(loader).anyTimes();

        catalog.removeListeners(EasyMock.anyObject());
        expectLastCall().anyTimes();

        catalog.addListener(EasyMock.anyObject());
        expectLastCall().anyTimes();

        expect(catalog.getResourcePool()).andAnswer(() -> ResourcePool.create(catalog)).anyTimes();
        MockCatalogBuilder b = new MockCatalogBuilder(catalog, loader.getBaseDirectory());
        b.setCallback(this);

        b.style(DEFAULT_VECTOR_STYLE);
        b.style("generic");

        createWorkspace(DEFAULT_PREFIX, DEFAULT_URI, null, b);
        createWorkspace(CGF_PREFIX, CGF_URI, CGF_TYPENAMES, b);
        createWorkspace(CDF_PREFIX, CDF_URI, CDF_TYPENAMES, b);
        createWorkspace(SF_PREFIX, SF_URI, SF_TYPENAMES, b);
        createWorkspace(CITE_PREFIX, CITE_URI, CITE_TYPENAMES, b);

        if (testData.isInludeRaster()) {
            b.style(DEFAULT_RASTER_STYLE);

            createWorkspace(WCS_PREFIX, WCS_URI, null, WCS_TYPENAMES, b);
        }

        addToCatalog(catalog, b);
        b.commit();
        return catalog;
    }

    protected void addToCatalog(Catalog catalog, MockCatalogBuilder b) {}

    protected void createWorkspace(String wsName, String nsURI, QName[] typeNames, MockCatalogBuilder b) {
        createWorkspace(wsName, nsURI, typeNames, null, b);
    }

    protected void createWorkspace(String wsName, String nsURI, QName[] ftTypeNames, QName[] covTypeNames,
            MockCatalogBuilder b) {
        b.workspace(wsName, nsURI);

        if (ftTypeNames != null && ftTypeNames.length > 0) {
            b.dataStore(wsName);
            for (QName typeName : ftTypeNames) {
                String local = typeName.getLocalPart();
                b.style(local);
                b.featureType(local);
            }
            b.commit().commit();
        }
        if (covTypeNames != null && covTypeNames.length > 0) {
            for (QName typeName : covTypeNames) {
                String local = typeName.getLocalPart();
                String[] fileNameAndFormat = COVERAGES.get(typeName);

                b.coverageStore(local, fileNameAndFormat[0], fileNameAndFormat[1]);
                b.coverage(typeName, fileNameAndFormat[0], null, null);
                b.commit();
            }
            b.commit();
        }
    }

    @Override
    public void onWorkspace(String name, WorkspaceInfo ws, MockCatalogBuilder b) {}

    @Override
    public void onStore(String name, StoreInfo st, WorkspaceInfo ws, MockCatalogBuilder b) {}

    @Override
    public void onResource(String name, ResourceInfo r, StoreInfo s, MockCatalogBuilder b) {}

    @Override
    public void onLayer(String name, LayerInfo l, MockCatalogBuilder b) {}

    @Override
    public void onStyle(String name, StyleInfo s, MockCatalogBuilder b) {}

    @Override
    public void onLayerGroup(String name, LayerGroupInfo lg, MockCatalogBuilder b) {}

}
