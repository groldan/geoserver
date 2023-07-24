package org.geoserver.web.catalogstresstool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.CatalogInfo;
import org.geoserver.catalog.LayerInfo;
import org.geoserver.catalog.NamespaceInfo;
import org.geoserver.catalog.ResourceInfo;
import org.geoserver.catalog.StoreInfo;
import org.geoserver.catalog.WorkspaceInfo;
import org.geoserver.catalog.impl.WorkspaceInfoImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

public class DuplicatingCatalogVisitorTest {

    private Catalog mockCatalog;
    private DuplicatingCatalogVisitor visitor;
    private UnaryOperator<String> nameMapper;
    private Consumer<CatalogInfo> listener;

    @BeforeEach
    void setUp() {
        mockCatalog = mock(Catalog.class);
        nameMapper = name -> "copy_" + name; // Example name mapper for testing
        listener = mock(Consumer.class);

        visitor = new DuplicatingCatalogVisitor(mockCatalog, nameMapper, listener);
    }

    @Test
    void testDuplicateWorkspaceInfo() {
        // Arrange
        WorkspaceInfo originalWorkspace = mock(WorkspaceInfo.class);
        when(originalWorkspace.getName()).thenReturn("original_workspace");

        WorkspaceInfoImpl prototypeWorkspace = new WorkspaceInfoImpl();
        when(mockCatalog.getWorkspaceByName("copy_original_workspace")).thenReturn(null);

        // Act
        WorkspaceInfo duplicatedWorkspace = visitor.duplicate(originalWorkspace);

        // Assert
        assertNotNull(duplicatedWorkspace);
        assertEquals("copy_original_workspace", duplicatedWorkspace.getName());
    }

    @Test
    void testDuplicateLayerInfo() {
        // Arrange
        LayerInfo originalLayer = mock(LayerInfo.class);
        ResourceInfo originalResource = mock(ResourceInfo.class);
        StoreInfo originalStore = mock(StoreInfo.class);
        WorkspaceInfo originalWorkspace = mock(WorkspaceInfo.class);

        when(originalLayer.getResource()).thenReturn(originalResource);
        when(originalResource.getStore()).thenReturn(originalStore);
        when(originalStore.getWorkspace()).thenReturn(originalWorkspace);
        when(originalResource.prefixedName()).thenReturn("workspace:resource");
        when(mockCatalog.getLayerByName("workspace:resource")).thenReturn(originalLayer);

        // Act
        LayerInfo duplicatedLayer = visitor.duplicate(originalLayer);

        // Assert
        assertNotNull(duplicatedLayer);
        verify(mockCatalog, atLeastOnce()).getLayerByName("workspace:resource");
    }

    @Test
    void testRecursiveDuplication() {
        // Arrange
        visitor.recursive();

        WorkspaceInfo workspaceInfo = mock(WorkspaceInfo.class);
        when(workspaceInfo.getName()).thenReturn("recursive_workspace");
        when(mockCatalog.getNamespaceByPrefix("recursive_workspace")).thenReturn(mock(NamespaceInfo.class));

        // Act
        WorkspaceInfo duplicatedWorkspace = visitor.duplicate(workspaceInfo);

        // Assert
        assertNotNull(duplicatedWorkspace);
        verify(mockCatalog, times(1)).getNamespaceByPrefix("recursive_workspace");
    }

    @Test
    void testListenerIsCalled() {
        // Arrange
        WorkspaceInfo originalWorkspace = mock(WorkspaceInfo.class);
        when(originalWorkspace.getName()).thenReturn("workspace");

        WorkspaceInfoImpl prototypeWorkspace = new WorkspaceInfoImpl();
        when(mockCatalog.getWorkspaceByName("copy_workspace")).thenReturn(prototypeWorkspace);

        // Act
        visitor.duplicate(originalWorkspace);

        // Assert
        ArgumentCaptor<CatalogInfo> captor = ArgumentCaptor.forClass(CatalogInfo.class);
        verify(listener, times(1)).accept(captor.capture());
        assertEquals("copy_workspace", ((WorkspaceInfo) captor.getValue()).getName());
    }

    @Test
    void testExceptionThrownWhenCopyNotFound() {
        // Arrange
        WorkspaceInfo workspaceInfo = mock(WorkspaceInfo.class);
        when(workspaceInfo.getName()).thenReturn("missing_workspace");
        when(mockCatalog.getWorkspaceByName("copy_missing_workspace")).thenReturn(null);

        // Act & Assert
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            visitor.duplicate(workspaceInfo);
        });

        assertTrue(exception.getMessage().contains("WorkspaceInfo with name copy_missing_workspace not found"));
    }
}
