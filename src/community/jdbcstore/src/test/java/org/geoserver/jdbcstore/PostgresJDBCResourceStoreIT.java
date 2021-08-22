/* (c) 2021 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.jdbcstore;

import org.geoserver.jdbcstore.it.PostgresContainerTestSupport;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Uses testcontainers to set up the database for each test
 *
 * @see PostgresContainerTestSupport
 */
public class PostgresJDBCResourceStoreIT extends AbstractJDBCResourceStoreTest {
    private static final String POSTGRES_TEST_IMAGE = "postgres:13-alpine";

    public static @ClassRule PostgreSQLContainer<?> container =
            new PostgreSQLContainer<>(POSTGRES_TEST_IMAGE);

    public @Rule PostgresContainerTestSupport containerSupport =
            new PostgresContainerTestSupport(container);

    @Before
    public void setUp() throws Exception {
        super.support = containerSupport;
    }
}
