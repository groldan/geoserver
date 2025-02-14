package org.geoserver.web.catalogstresstool;

import static org.geoserver.catalog.impl.ModificationProxy.unwrap;
import static org.junit.Assert.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.CatalogInfo;
import org.geoserver.catalog.NamespaceInfo;
import org.geoserver.catalog.NamespaceWorkspaceConsistencyListener;
import org.geoserver.catalog.WorkspaceInfo;
import org.geoserver.catalog.faker.CatalogFaker;
import org.geoserver.catalog.impl.CatalogImpl;
import org.geoserver.ows.util.OwsUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class DuplicatingCatalogVisitorTest {

    private Catalog catalog;
    private CatalogFaker faker;

    private DuplicatingCatalogVisitor visitor;

    @BeforeEach
    void setUp() {
        catalog = new CatalogImpl();
        catalog.addListener(new NamespaceWorkspaceConsistencyListener(catalog));
        faker = new CatalogFaker(catalog);
        visitor = new DuplicatingCatalogVisitor(catalog);
    }

    private void assertEqualsExceptId(CatalogInfo orig, CatalogInfo dup) {
        // mind equals() on ModificationProxy won't work
        orig = unwrap(orig);
        dup = unwrap(dup);
        String dupId = dup.getId();
        assertNotEquals(orig.getId(), dupId);
        OwsUtils.set(dup, "Id", orig.getId());
        try {
            assertEquals(orig, dup);
        } finally {
            OwsUtils.set(dup, "Id", dupId);
        }
    }

    <T extends CatalogInfo> T add(T info) {
        return add(info, catalog);
    }

    <T extends CatalogInfo> T add(T info, Catalog target) {
        return DuplicatingCatalogVisitor.add(info, catalog);
    }

    @Test
    void testDuplicateWorkspaceAlsoDuplicatesNamespace() {
        WorkspaceInfo ws = faker.workspaceInfo();
        NamespaceInfo ns = faker.namespaceInfo(ws);
        ws = add(ws, catalog);
        ns = add(ns, catalog);

        Catalog targetCatalog = new CatalogImpl();
        visitor.targetCatalog(targetCatalog);
        WorkspaceInfo dup = unwrap(visitor.duplicate(ws));
        assertNotNull(dup);
        assertNotSame(unwrap(ws), unwrap(dup));
        assertEqualsExceptId(ws, dup);

        NamespaceInfo dupNs = targetCatalog.getNamespaceByPrefix(ws.getName());
        assertNotNull(dupNs);
        assertEqualsExceptId(ns, dupNs);
    }

    @Test
    void testDuplicateNamespaceAlsoDuplicatesWorkspace() {
        WorkspaceInfo ws = faker.workspaceInfo();
        NamespaceInfo ns = faker.namespaceInfo(ws);
        ws = add(ws, catalog);
        ns = add(ns, catalog);

        Catalog targetCatalog = new CatalogImpl();
        visitor.targetCatalog(targetCatalog);
        NamespaceInfo dup = unwrap(visitor.duplicate(ns));
        assertNotNull(dup);
        assertNotSame(unwrap(ns), unwrap(dup));
        assertEqualsExceptId(ns, dup);

        WorkspaceInfo dupWs = targetCatalog.getWorkspaceByName(ns.getPrefix());
        assertNotNull(dupWs);
        assertEqualsExceptId(ws, dupWs);
    }
}
