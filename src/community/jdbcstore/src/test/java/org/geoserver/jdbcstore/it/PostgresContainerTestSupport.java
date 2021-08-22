/* (c) 2021 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.jdbcstore.it;

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;

import com.google.common.base.Optional;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.geoserver.jdbcconfig.internal.Util;
import org.geoserver.jdbcstore.DatabaseTestSupport;
import org.geoserver.jdbcstore.internal.JDBCResourceStoreProperties;
import org.geoserver.platform.resource.URIs;
import org.geotools.util.logging.Logging;
import org.junit.rules.ExternalResource;
import org.junit.runner.Description;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

public class PostgresContainerTestSupport extends ExternalResource implements DatabaseTestSupport {

    private static final Logger LOGGER = Logging.getLogger(PostgresContainerTestSupport.class);

    private static final String INSERT =
            "INSERT INTO resources (name, parent, content) VALUES (?, ?, ?) RETURNING oid;";

    private final PostgreSQLContainer<?> container;

    private String testName;

    private DataSource dataSource;

    public PostgresContainerTestSupport(PostgreSQLContainer<?> container) {
        this.container = container;
    }

    ///// ExternalResource set up/tear down container ////////

    @Override
    public org.junit.runners.model.Statement apply(
            org.junit.runners.model.Statement base, Description description) {
        this.testName = description.getMethodName();
        return super.apply(base, description);
    }

    protected @Override void before() throws Throwable {
        this.dataSource = createDataSource(testName);
    }

    protected @Override void after() {
        dataSource = null; // non-pooling, no need to close it
    }

    /////////// DatabaseTestSupport methods, reuse container ////////////
    @Override
    public void close() throws SQLException {
        dropSchema(testName);
    }

    @Override
    public void initialize() throws Exception {
        NamedParameterJdbcTemplate template = new NamedParameterJdbcTemplate(getDataSource());
        try (InputStream in =
                JDBCResourceStoreProperties.class.getResourceAsStream("init.postgres.sql")) {
            Util.runScript(in, template.getJdbcOperations(), null);
        }
    }

    @Override
    public int addFile(String name, int parent, byte[] content) throws SQLException {
        return addResource(name, parent, content);
    }

    @Override
    public int addDir(String name, int parent) throws SQLException {
        return addResource(name, parent, null);
    }

    private int addResource(String name, int parent, byte[] content) throws SQLException {
        try (Connection conn = getConnection();
                PreparedStatement insert = conn.prepareStatement(INSERT)) {
            insert.setString(1, name);
            insert.setInt(2, parent);
            insert.setBytes(3, content);
            ResultSet rs = insert.executeQuery();
            if (rs.next()) {
                return rs.getInt("oid");
            } else {
                throw new IllegalStateException("Could not add test file " + name);
            }
        }
    }

    @Override
    public int getRoot() {
        return 0;
    }

    @Override
    public DataSource getDataSource() {
        return dataSource;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return getDataSource().getConnection();
    }

    @Override
    public void stubConfig(JDBCResourceStoreProperties config) {
        expect(config.getInitScript())
                .andStubReturn(
                        URIs.asResource(
                                JDBCResourceStoreProperties.class.getResource(
                                        "init.postgres.sql")));
        expect(config.getJdbcUrl())
                .andStubReturn(Optional.of("jdbc:postgresql://localhost:5432/jdbcstoretest"));
        expect(config.getJndiName()).andStubReturn(Optional.<String>absent());
        expect(config.getProperty(eq("username"))).andStubReturn("jdbcstore");
        expect(config.getProperty(eq("username"), (String) anyObject())).andStubReturn("jdbcstore");
        expect(config.getProperty(eq("password"))).andStubReturn("jdbcstore");
        expect(config.getProperty(eq("password"), (String) anyObject())).andStubReturn("jdbcstore");
        expect(config.getProperty(eq("driverClassName"))).andStubReturn("org.postgresql.Driver");
        expect(config.getProperty(eq("driverClassName"), (String) anyObject()))
                .andStubReturn("org.postgresql.Driver");
    }

    private DataSource createDataSource(String testName) throws SQLException {
        createSchema(testName);

        PGSimpleDataSource ds = new PGSimpleDataSource();
        String jdbcUrl = container.getJdbcUrl();
        jdbcUrl += jdbcUrl.contains("?") ? "&" : "?";
        jdbcUrl += "currentSchema=" + testName;

        ds.setUrl(jdbcUrl);
        ds.setUser(container.getUsername());
        ds.setPassword(container.getPassword());
        return ds;
    }

    private void createSchema(String testName) throws SQLException {
        LOGGER.info("Creating schema '" + testName + "'...");
        try (Connection c = this.container.createConnection("?currentSchema=public");
                Statement st = c.createStatement()) {
            st.execute("CREATE SCHEMA " + testName);
        }
    }

    private void dropSchema(String testName) throws SQLException {
        LOGGER.info("Drop schema '" + testName + "'...");
        try (Connection c = this.container.createConnection("?currentSchema=public");
                Statement st = c.createStatement()) {
            st.execute("DROP SCHEMA " + testName + " CASCADE");
        }
    }
}
