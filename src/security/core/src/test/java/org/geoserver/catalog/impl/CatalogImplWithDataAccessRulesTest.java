/* (c) 2014 - 2015 Open Source Geospatial Foundation - all rights reserved
 * (c) 2001 - 2013 OpenPlans
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.catalog.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.CatalogException;
import org.geoserver.catalog.CatalogFactory;
import org.geoserver.catalog.CoverageInfo;
import org.geoserver.catalog.CoverageStoreInfo;
import org.geoserver.catalog.DataStoreInfo;
import org.geoserver.catalog.FeatureTypeInfo;
import org.geoserver.catalog.LayerGroupInfo;
import org.geoserver.catalog.LayerInfo;
import org.geoserver.catalog.NamespaceInfo;
import org.geoserver.catalog.ResourceInfo;
import org.geoserver.catalog.StyleInfo;
import org.geoserver.catalog.WMSLayerInfo;
import org.geoserver.catalog.WMSStoreInfo;
import org.geoserver.catalog.WMTSLayerInfo;
import org.geoserver.catalog.WMTSStoreInfo;
import org.geoserver.catalog.WorkspaceInfo;
import org.geoserver.catalog.event.CatalogAddEvent;
import org.geoserver.catalog.event.CatalogListener;
import org.geoserver.catalog.event.CatalogModifyEvent;
import org.geoserver.catalog.event.CatalogPostModifyEvent;
import org.geoserver.catalog.event.CatalogRemoveEvent;
import org.geoserver.platform.GeoServerResourceLoader;
import org.geoserver.security.AccessMode;
import org.geoserver.security.GeoServerSecurityTestSupport;
import org.geoserver.security.SecuredResourceNameChangeListener;
import org.geoserver.security.impl.DataAccessRule;
import org.geoserver.security.impl.DataAccessRuleDAO;
import org.junit.Before;
import org.junit.Test;

public class CatalogImplWithDataAccessRulesTest extends GeoServerSecurityTestSupport {

    private Catalog catalog;

    private WorkspaceInfo ws;

    private NamespaceInfo ns;

    private DataStoreInfo ds;

    private CoverageStoreInfo cs;
    private WMSStoreInfo wms;
    private WMTSStoreInfo wmtss;
    private FeatureTypeInfo ft;
    private CoverageInfo cv;
    private WMSLayerInfo wl;
    private WMTSLayerInfo wmtsl;
    private LayerInfo l;
    private StyleInfo s;
    private StyleInfo defaultLineStyle;
    private LayerGroupInfo lg;

    @Before
    public void setUp() throws Exception {
        catalog = createCatalog();
        catalog.setResourceLoader(new GeoServerResourceLoader());

        CatalogFactory factory = catalog.getFactory();

        ns = factory.createNamespace();
        // ns prefix has to match workspace name, until we break that relationship
        // ns.setPrefix( "nsPrefix" );
        ns.setPrefix("wsName");
        ns.setURI("nsURI");

        ws = factory.createWorkspace();
        ws.setName("wsName");

        ds = factory.createDataStore();
        ds.setEnabled(true);
        ds.setName("dsName");
        ds.setDescription("dsDescription");
        ds.setWorkspace(ws);

        ft = factory.createFeatureType();
        ft.setEnabled(true);
        ft.setName("ftName");
        ft.setAbstract("ftAbstract");
        ft.setDescription("ftDescription");
        ft.setStore(ds);
        ft.setNamespace(ns);

        cs = factory.createCoverageStore();
        cs.setName("csName");
        cs.setType("fakeCoverageType");
        cs.setURL("file://fake");

        cv = factory.createCoverage();
        cv.setName("cvName");
        cv.setStore(cs);

        wms = factory.createWebMapServer();
        wms.setName("wmsName");
        wms.setType("WMS");
        wms.setCapabilitiesURL("http://fake.url");
        wms.setWorkspace(ws);

        wl = factory.createWMSLayer();
        wl.setEnabled(true);
        wl.setName("wmsLayer");
        wl.setStore(wms);
        wl.setNamespace(ns);

        wmtss = factory.createWebMapTileServer();
        wmtss.setName("wmtsName");
        wmtss.setType("WMTS");
        wmtss.setCapabilitiesURL("http://fake.wmts.url");
        wmtss.setWorkspace(ws);

        wmtsl = factory.createWMTSLayer();
        wmtsl.setEnabled(true);
        wmtsl.setName("wmtsLayer");
        wmtsl.setStore(wmtss);
        wmtsl.setNamespace(ns);

        s = factory.createStyle();
        s.setName("styleName");
        s.setFilename("styleFilename");

        defaultLineStyle = factory.createStyle();
        defaultLineStyle.setName(StyleInfo.DEFAULT_LINE);
        defaultLineStyle.setFilename(StyleInfo.DEFAULT_LINE + ".sld");

        l = factory.createLayer();
        l.setResource(ft);
        l.setEnabled(true);
        l.setDefaultStyle(s);

        lg = factory.createLayerGroup();
        lg.setName("layerGroup");
        lg.getLayers().add(l);
        lg.getStyles().add(s);
    }

    protected Catalog createCatalog() {
        return new CatalogImpl();
    }

    protected void addWorkspace() {
        catalog.add(ws);
    }

    protected void addNamespace() {
        catalog.add(ns);
    }

    protected void addDataStore() {
        addWorkspace();
        catalog.add(ds);
    }

    protected void addCoverageStore() {
        addWorkspace();
        catalog.add(cs);
    }

    protected void addWMSStore() {
        addWorkspace();
        catalog.add(wms);
    }

    protected void addWMTSStore() {
        addWorkspace();
        catalog.add(wmtss);
    }

    protected void addFeatureType() {
        addDataStore();
        addNamespace();
        catalog.add(ft);
    }

    protected void addCoverage() {
        addCoverageStore();
        addNamespace();
        catalog.add(cv);
    }

    protected void addWMSLayer() {
        addWMSStore();
        addNamespace();
        catalog.add(wl);
    }

    protected void addWMTSLayer() {
        addWMTSStore();
        addNamespace();
        catalog.add(wmtsl);
    }

    protected void addStyle() {
        catalog.add(s);
    }

    protected void addDefaultStyle() {
        catalog.add(defaultLineStyle);
    }

    protected void addLayer() {
        addFeatureType();
        addStyle();
        catalog.add(l);
    }

    protected void addLayerGroup() {
        addLayer();
        catalog.add(lg);
    }

    @Test
    public void testRemoveLayerAndAssociatedDataRules() throws IOException {
        DataAccessRuleDAO dao = DataAccessRuleDAO.get();
        CatalogListener listener = new SecuredResourceNameChangeListener(catalog, dao);
        addLayer();
        assertEquals(1, catalog.getLayers().size());

        String workspaceName = l.getResource().getStore().getWorkspace().getName();
        addLayerAccessRule(workspaceName, l.getName(), AccessMode.WRITE, "*");
        assertTrue(layerHasSecurityRule(dao, workspaceName, l.getName()));
        catalog.remove(l);
        assertTrue(catalog.getLayers().isEmpty());
        dao.reload();
        assertFalse(layerHasSecurityRule(dao, workspaceName, l.getName()));
        catalog.removeListener(listener);
    }

    private boolean layerHasSecurityRule(
            DataAccessRuleDAO dao, String workspaceName, String layerName) {

        List<DataAccessRule> rules = dao.getRules();
        for (DataAccessRule rule : rules) {
            if (rule.getRoot().equalsIgnoreCase(workspaceName)
                    && rule.getLayer().equalsIgnoreCase(layerName)) return true;
        }

        return false;
    }

    protected int GET_LAYER_BY_ID_WITH_CONCURRENT_ADD_TEST_COUNT = 500;

    @Test
    public void testRemoveLayerGroupAndAssociatedDataRules() throws IOException {
        DataAccessRuleDAO dao = DataAccessRuleDAO.get();
        CatalogListener listener = new SecuredResourceNameChangeListener(catalog, dao);
        addLayer();
        CatalogFactory factory = catalog.getFactory();
        LayerGroupInfo lg = factory.createLayerGroup();
        String lgName = "MyFakeWorkspace:layerGroup";
        lg.setName(lgName);
        lg.setWorkspace(ws);
        lg.getLayers().add(l);
        lg.getStyles().add(s);
        catalog.add(lg);
        String workspaceName = ws.getName();
        assertNotNull(catalog.getLayerGroupByName(workspaceName, lg.getName()));

        addLayerAccessRule(workspaceName, lg.getName(), AccessMode.WRITE, "*");
        assertTrue(layerHasSecurityRule(dao, workspaceName, lg.getName()));
        catalog.remove(lg);
        assertNull(catalog.getLayerGroupByName(workspaceName, lg.getName()));
        assertFalse(layerHasSecurityRule(dao, workspaceName, lg.getName()));
        catalog.removeListener(listener);
    }

    static class TestListener implements CatalogListener {
        public List<CatalogAddEvent> added = new CopyOnWriteArrayList<>();
        public List<CatalogModifyEvent> modified = new CopyOnWriteArrayList<>();
        public List<CatalogPostModifyEvent> postModified = new CopyOnWriteArrayList<>();
        public List<CatalogRemoveEvent> removed = new CopyOnWriteArrayList<>();

        @Override
        public void handleAddEvent(CatalogAddEvent event) {
            added.add(event);
        }

        @Override
        public void handleModifyEvent(CatalogModifyEvent event) {
            modified.add(event);
        }

        @Override
        public void handlePostModifyEvent(CatalogPostModifyEvent event) {
            postModified.add(event);
        }

        @Override
        public void handleRemoveEvent(CatalogRemoveEvent event) {
            removed.add(event);
        }

        @Override
        public void reloaded() {}
    }

    static class ExceptionThrowingListener implements CatalogListener {

        public boolean throwCatalogException;

        @Override
        public void handleAddEvent(CatalogAddEvent event) throws CatalogException {
            if (throwCatalogException) {
                throw new CatalogException();
            } else {
                throw new RuntimeException();
            }
        }

        @Override
        public void handleModifyEvent(CatalogModifyEvent event) throws CatalogException {}

        @Override
        public void handlePostModifyEvent(CatalogPostModifyEvent event) throws CatalogException {}

        @Override
        public void handleRemoveEvent(CatalogRemoveEvent event) throws CatalogException {}

        @Override
        public void reloaded() {}
    }

    class LayerAddRunner extends RunnerBase {

        private int idx;

        protected LayerAddRunner(CountDownLatch ready, CountDownLatch done, int idx) {
            super(ready, done);
            this.idx = idx;
        }

        @Override
        protected void runInternal() throws Exception {
            CatalogFactory factory = catalog.getFactory();
            for (int i = 0; i < GET_LAYER_BY_ID_WITH_CONCURRENT_ADD_TEST_COUNT; i++) {
                // GR: Adding a new feature type info too, we can't really add multiple layers per
                // feature type yet. Setting the name of the layer changes the name of the resource,
                // then all previous layers for that resource get screwed
                String name = "LAYER-" + i + "-" + idx;
                FeatureTypeInfo resource = factory.createFeatureType();
                resource.setName(name);
                resource.setNamespace(ns);
                resource.setStore(ds);
                catalog.add(resource);

                LayerInfo layer = factory.createLayer();
                layer.setResource(resource);
                layer.setName(name);
                catalog.add(layer);
            }
        }
    };

    protected LayerInfo newLayer(
            ResourceInfo resource, StyleInfo defStyle, StyleInfo... extraStyles) {
        LayerInfo l2 = catalog.getFactory().createLayer();
        l2.setResource(resource);
        l2.setDefaultStyle(defStyle);
        if (extraStyles != null) {
            for (StyleInfo es : extraStyles) {
                l2.getStyles().add(es);
            }
        }
        return l2;
    }

    protected StyleInfo newStyle(String name, String fileName) {
        StyleInfo s2 = catalog.getFactory().createStyle();
        s2.setName(name);
        s2.setFilename(fileName);
        return s2;
    }

    protected FeatureTypeInfo newFeatureType(String name, DataStoreInfo ds) {
        FeatureTypeInfo ft2 = catalog.getFactory().createFeatureType();
        ft2.setNamespace(ns);
        ft2.setName(name);
        ft2.setStore(ds);
        return ft2;
    }
}
