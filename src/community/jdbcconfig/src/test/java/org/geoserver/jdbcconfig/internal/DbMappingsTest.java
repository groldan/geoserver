/* (c) 2014 Open Source Geospatial Foundation - all rights reserved
 * (c) 2001 - 2013 OpenPlans
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.jdbcconfig.internal;

import static org.junit.Assert.assertTrue;

import com.google.common.collect.MapDifference;
import com.google.common.collect.MapDifference.ValueDifference;
import com.google.common.collect.Maps;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.sql.DataSource;
import org.geoserver.catalog.Info;
import org.geoserver.catalog.impl.CoverageInfoImpl;
import org.geoserver.catalog.impl.CoverageStoreInfoImpl;
import org.geoserver.catalog.impl.DataStoreInfoImpl;
import org.geoserver.catalog.impl.FeatureTypeInfoImpl;
import org.geoserver.catalog.impl.LayerGroupInfoImpl;
import org.geoserver.catalog.impl.LayerInfoImpl;
import org.geoserver.catalog.impl.NamespaceInfoImpl;
import org.geoserver.catalog.impl.StyleInfoImpl;
import org.geoserver.catalog.impl.WMSLayerInfoImpl;
import org.geoserver.catalog.impl.WMSStoreInfoImpl;
import org.geoserver.catalog.impl.WMTSLayerInfoImpl;
import org.geoserver.catalog.impl.WMTSStoreInfoImpl;
import org.geoserver.catalog.impl.WorkspaceInfoImpl;
import org.geoserver.jdbcconfig.JDBCConfigTestSupport;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@RunWith(Parameterized.class)
public class DbMappingsTest {

    private JDBCConfigTestSupport testSupport;

    public DbMappingsTest(JDBCConfigTestSupport.DBConfig dbConfig) {
        testSupport = new JDBCConfigTestSupport(dbConfig);
    }

    @Parameterized.Parameters(name = "{0}")
    public static Iterable<Object[]> data() {
        return JDBCConfigTestSupport.parameterizedDBConfigs();
    }

    @Before
    public void setUp() throws Exception {
        testSupport.setUp();
    }

    @After
    public void tearDown() throws Exception {
        testSupport.tearDown();
    }

    @Test
    public void testInitDb() throws Exception {
        DataSource dataSource = testSupport.getDataSource();
        NamedParameterJdbcTemplate template = new NamedParameterJdbcTemplate(dataSource);
        DbMappings dbInit = new DbMappings(new Dialect());
        dbInit.initDb(template);
    }

    /**
     * May jdbcconfig be used in a clustered environment, make sure no race condition at startup
     * time leaves instances with mismatching property mappings
     */
    @Test
    public void testInitDb_multiple_instances() throws Exception {
        final int nThreads = Math.min(16, 2 * Runtime.getRuntime().availableProcessors());

        // revert testSupport.setUp()'s initialization of dbMappings
        testSupport.dropDb();
        testSupport.initDb();

        final ExecutorService executor = Executors.newFixedThreadPool(nThreads);
        List<DbMappings> mappings = new ArrayList<>();
        try {
            List<Callable<DbMappings>> tasks = new ArrayList<>();
            for (int i = 0; i < nThreads; i++) {
                tasks.add(() -> newInitializedDbMappings());
            }
            for (Future<DbMappings> finishedTask : executor.invokeAll(tasks)) {
                mappings.add(finishedTask.get());
            }
        } finally {
            executor.shutdownNow();
        }

        assertSamePropertyIds(mappings, new WorkspaceInfoImpl());
        assertSamePropertyIds(mappings, new NamespaceInfoImpl());
        assertSamePropertyIds(mappings, new DataStoreInfoImpl(null));
        assertSamePropertyIds(mappings, new CoverageStoreInfoImpl(null));
        assertSamePropertyIds(mappings, new WMSStoreInfoImpl(null));
        assertSamePropertyIds(mappings, new WMTSStoreInfoImpl(null));
        assertSamePropertyIds(mappings, new FeatureTypeInfoImpl(null));
        assertSamePropertyIds(mappings, new CoverageInfoImpl(null));
        assertSamePropertyIds(mappings, new WMSLayerInfoImpl(null));
        assertSamePropertyIds(mappings, new WMTSLayerInfoImpl(null));
        assertSamePropertyIds(mappings, new LayerGroupInfoImpl());
        assertSamePropertyIds(mappings, new StyleInfoImpl(null));

        LayerInfoImpl l = new LayerInfoImpl();
        l.setResource(new FeatureTypeInfoImpl(null));
        l.getResource().setStore(new DataStoreInfoImpl(null));
        l.getResource().getStore().setWorkspace(new WorkspaceInfoImpl());
        l.getResource().getStore().getWorkspace().setName("avoid");
        l.getResource().setName("NPE");
        assertSamePropertyIds(mappings, l);
    }

    private void assertSamePropertyIds(List<DbMappings> mappings, Info info) {
        List<Map<String, PropertyType>> maps = new ArrayList<>();
        for (DbMappings dbMappings : mappings) {
            Map<String, PropertyType> props = new TreeMap<>();
            maps.add(props);
            Iterable<Property> allProperties = dbMappings.allProperties(info);
            for (Property p : allProperties) {
                props.put(p.getPropertyName(), p.getPropertyType());
            }
        }
        Map<String, PropertyType> first = maps.get(0);
        for (int i = 1; i < maps.size(); i++) {

            MapDifference<String, PropertyType> diff = Maps.difference(first, maps.get(i));
            Map<String, ValueDifference<PropertyType>> differing = diff.entriesDiffering();
            Map<String, PropertyType> onlyOnLeft = diff.entriesOnlyOnLeft();
            Map<String, PropertyType> onlyOnRight = diff.entriesOnlyOnRight();
            if (!differing.isEmpty()) {
                assertTrue(differing.toString(), differing.isEmpty());
            }
            if (!onlyOnLeft.isEmpty()) {
                assertTrue(onlyOnLeft.toString(), differing.isEmpty());
            }
            if (!onlyOnRight.isEmpty()) {
                assertTrue(onlyOnRight.toString(), differing.isEmpty());
            }
        }
    }

    private DbMappings newInitializedDbMappings() throws IOException {
        final DataSource dataSource = testSupport.newDataSource();
        NamedParameterJdbcTemplate template = new NamedParameterJdbcTemplate(dataSource);
        DbMappings dbMappings = new DbMappings(new Dialect());
        dbMappings.initDb(template);
        return dbMappings;
    }

    @Test
    public void testProperties() throws Exception {
        DataSource dataSource = testSupport.getDataSource();
        NamedParameterJdbcTemplate template = new NamedParameterJdbcTemplate(dataSource);
        // Getting the DB mappings
        DbMappings db = new DbMappings(new Dialect());
        db.initDb(template);
        // Getting the properties for the LayerInfo class
        // Initial mock classes
        LayerInfoImpl info = new LayerInfoImpl();
        CoverageInfoImpl resource = new CoverageInfoImpl(null);
        resource.setName("test");
        resource.setTitle("test");
        CoverageStoreInfoImpl store = new CoverageStoreInfoImpl(null);
        store.setName("test");
        WorkspaceInfoImpl workspace = new WorkspaceInfoImpl();
        workspace.setName("test");
        store.setWorkspace(workspace);
        resource.setStore(store);
        info.setResource(resource);

        Iterable<Property> properties = db.properties(info);

        boolean titleExists = false;
        boolean prefixedNameExists = false;
        // Iterate on the properties
        for (Property prop : properties) {
            if (prop.getPropertyName().equals("title")) {
                titleExists = true;
            } else if (prop.getPropertyName().equals("prefixedName")) {
                prefixedNameExists = true;
            }
        }
        // Assertions
        assertTrue("title property not found", titleExists);
        assertTrue("prefixedName property not found", prefixedNameExists);
    }
}
