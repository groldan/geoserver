/* (c) 2021 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.jdbcstore;

import static org.easymock.EasyMock.replay;
import org.geoserver.jdbcstore.JDBCResourceStore;
import org.geoserver.jdbcstore.cache.SimpleResourceCache;
import org.geoserver.jdbcstore.internal.JDBCResourceStoreProperties;
import org.geoserver.jdbcstore.it.PostgresContainerTestSupport;
import org.geoserver.platform.resource.NullLockProvider;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Uses testcontainers to set up the database for each test
 *
 * @see PostgresContainerTestSupport
 */
public class PostgresJDBCResourceTheoryIT extends AbstractJDBCResourceTheoryTest {

    private static final String POSTGRES_TEST_IMAGE = "postgres:13-alpine";

    public static @ClassRule PostgreSQLContainer<?> container =
            new PostgreSQLContainer<>(POSTGRES_TEST_IMAGE);

    public @Rule PostgresContainerTestSupport containerSupport =
            new PostgresContainerTestSupport(container);

    private JDBCResourceStore store;

    @Override
    protected JDBCResourceStore getStore() {
        return store;
    }

    @Before
    public void setUp() throws Exception {
        super.support = containerSupport;
        standardData();

        JDBCResourceStoreProperties config = mockConfig(true, false);
        replay(config);

        store = new JDBCResourceStore(support.getDataSource(), config);
        store.setLockProvider(new NullLockProvider());
        store.setCache(new SimpleResourceCache(folder.getRoot()));
    }
}
