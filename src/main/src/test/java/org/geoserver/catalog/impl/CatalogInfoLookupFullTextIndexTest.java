package org.geoserver.catalog.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.geoserver.catalog.MetadataMap;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class CatalogInfoLookupFullTextIndexTest {

    private Path indexPath;
    private CatalogInfoLookupFullTextIndex index;

    public @Rule TemporaryFolder tmpFolder = new TemporaryFolder();

    public @Before void before() {
        indexPath = tmpFolder.getRoot().toPath().resolve("shouldCreateIt");
        index = new CatalogInfoLookupFullTextIndex(indexPath);
    }

    public @After void after() throws IOException {
        index.close();
    }

    public @Test void testOpenClose() throws IOException {
        index.open();
        index.open();
        index.close();
        index.close();
    }

    public @Test void testWorkspace() throws IOException {
        index.open();
        WorkspaceInfoImpl ws1 = newWorkspace("workspace1");
        WorkspaceInfoImpl ws2 = newWorkspace("workspace2");

        index.add(ws1);
        index.add(ws2);
        index.commit();
        
        List<?> resultIds = index.search("*workspace*").collect(Collectors.toList());
        assertEquals(2, resultIds.size());
        assertTrue(resultIds.contains(ws1.getId()));
        assertTrue(resultIds.contains(ws2.getId()));

        resultIds = index.search("*1*").collect(Collectors.toList());
        assertNotNull(resultIds);
        assertEquals(1, resultIds.size());
        assertTrue(resultIds.contains(ws1.getId()));

        resultIds = index.search("*2*").collect(Collectors.toList());
        assertNotNull(resultIds);
        assertEquals(1, resultIds.size());
        assertTrue(resultIds.contains(ws2.getId()));

        resultIds = index.search("*2*", "*1*").collect(Collectors.toList());
        assertNotNull(resultIds);
        assertEquals(2, resultIds.size());
        assertTrue(resultIds.contains(ws1.getId()));
        assertTrue(resultIds.contains(ws2.getId()));
    }

    private WorkspaceInfoImpl newWorkspace(String name) {
        WorkspaceInfoImpl ws = new WorkspaceInfoImpl();
        ws.setId(UUID.randomUUID().toString());
        ws.setName(name);
        MetadataMap metadata = new MetadataMap();
        metadata.put("key1", "value1");
        ws.setMetadata(metadata);
        return ws;
    }
}
