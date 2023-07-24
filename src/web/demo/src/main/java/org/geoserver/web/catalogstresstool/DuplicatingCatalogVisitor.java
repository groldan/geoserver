package org.geoserver.web.catalogstresstool;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.CatalogInfo;
import org.geoserver.catalog.CatalogVisitor;
import org.geoserver.catalog.CoverageInfo;
import org.geoserver.catalog.CoverageStoreInfo;
import org.geoserver.catalog.DataStoreInfo;
import org.geoserver.catalog.FeatureTypeInfo;
import org.geoserver.catalog.LayerGroupInfo;
import org.geoserver.catalog.LayerInfo;
import org.geoserver.catalog.NamespaceInfo;
import org.geoserver.catalog.PublishedInfo;
import org.geoserver.catalog.ResourceInfo;
import org.geoserver.catalog.StoreInfo;
import org.geoserver.catalog.StyleInfo;
import org.geoserver.catalog.WMSLayerInfo;
import org.geoserver.catalog.WMSStoreInfo;
import org.geoserver.catalog.WMTSLayerInfo;
import org.geoserver.catalog.WMTSStoreInfo;
import org.geoserver.catalog.WorkspaceInfo;
import org.geoserver.catalog.impl.CoverageInfoImpl;
import org.geoserver.catalog.impl.CoverageStoreInfoImpl;
import org.geoserver.catalog.impl.DataStoreInfoImpl;
import org.geoserver.catalog.impl.FeatureTypeInfoImpl;
import org.geoserver.catalog.impl.LayerGroupInfoImpl;
import org.geoserver.catalog.impl.LayerInfoImpl;
import org.geoserver.catalog.impl.StyleInfoImpl;
import org.geoserver.catalog.impl.WMSLayerInfoImpl;
import org.geoserver.catalog.impl.WMSStoreInfoImpl;
import org.geoserver.catalog.impl.WMTSLayerInfoImpl;
import org.geoserver.catalog.impl.WMTSStoreInfoImpl;
import org.geoserver.catalog.impl.WorkspaceInfoImpl;
import org.geoserver.ows.util.OwsUtils;
import org.geoserver.security.SecureCatalogImpl;

class DuplicatingCatalogVisitor implements CatalogVisitor {

    private final Catalog catalog;
    private final boolean recursive;
    private Function<CatalogInfo, String> nameMapper;

    private WorkspaceInfo targetWorkspace;
    private NamespaceInfo targetNamespace;
    private StoreInfo targetStore;
    private ResourceInfo targetResource;

    public DuplicatingCatalogVisitor(
            Catalog catalog, boolean recursive, Function<CatalogInfo, String> nameMapper) {
        this.catalog = (Catalog) SecureCatalogImpl.unwrap(catalog);
        this.recursive = recursive;
        this.nameMapper = nameMapper;
    }

    @Override
    public void visit(Catalog catalog) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void visit(WorkspaceInfo orig) {
        WorkspaceInfo copy = prototype(orig);
        catalog.add(copy);

        targetWorkspace = catalog.getWorkspaceByName(copy.getName());

        NamespaceInfo ns = catalog.getNamespaceByPrefix(orig.getName());
        ns.accept(this);
        if (recursive) {
            copyStyles(orig);
            copyStores(orig);
            copyLayerGroups(orig);
        }
        targetWorkspace = null;
    }

    @Override
    public void visit(NamespaceInfo orig) {
        NamespaceInfo copy = prototype(orig);
        if (targetWorkspace != null) {
            copy.setPrefix(targetWorkspace.getName());
        }
        copy.setURI(orig.getURI() + copy.getPrefix());
        catalog.add(copy);
        targetNamespace = catalog.getNamespaceByPrefix(copy.getPrefix());
    }

    private void copyStyles(WorkspaceInfo orig) {
        List<StyleInfo> styles = catalog.getStylesByWorkspace(orig);
        for (StyleInfo s : styles) {
            s.accept(this);
        }
    }

    private void copyStores(WorkspaceInfo orig) {
        List<StoreInfo> stores = catalog.getStoresByWorkspace(orig, StoreInfo.class);
        for (StoreInfo store : stores) {
            store.accept(this);
        }
    }

    private void copyLayerGroups(WorkspaceInfo orig) {
        List<LayerGroupInfo> groups = catalog.getLayerGroupsByWorkspace(orig);
        for (LayerGroupInfo lg : groups) {
            lg.accept(this);
        }
    }

    @Override
    public void visit(DataStoreInfo orig) {
        copyStore(orig);
    }

    @Override
    public void visit(CoverageStoreInfo orig) {
        copyStore(orig);
    }

    @Override
    public void visit(WMSStoreInfo orig) {
        copyStore(orig);
    }

    @Override
    public void visit(WMTSStoreInfo orig) {
        copyStore(orig);
    }

    private void copyStore(StoreInfo orig) {
        StoreInfo copy = prototype(orig);
        if (null != targetWorkspace) {
            copy.setWorkspace(targetWorkspace);
        }
        if (orig instanceof DataStoreInfo) {
            // reset the cache, or we might stumble into a error about too many connections
            // while cloning many jdbc stores
            catalog.getResourcePool().dispose();
        }
        catalog.add(copy);
        if (!recursive) return;

        targetStore = copy;
        List<ResourceInfo> resources = catalog.getResourcesByStore(orig, ResourceInfo.class);
        for (ResourceInfo resource : resources) {
            resource.accept(this);
        }
        targetStore = null;
    }

    @Override
    public void visit(FeatureTypeInfo orig) {
        copyResourceInfo(orig);
    }

    @Override
    public void visit(CoverageInfo orig) {
        copyResourceInfo(orig);
    }

    @Override
    public void visit(WMSLayerInfo orig) {
        copyResourceInfo(orig);
    }

    @Override
    public void visit(WMTSLayerInfo orig) {
        copyResourceInfo(orig);
    }

    private ResourceInfo copyResourceInfo(ResourceInfo orig) {
        ResourceInfo copy = prototype(orig);
        copy.setNativeName(orig.getNativeName());

        StoreInfo store = orig.getStore();
        NamespaceInfo ns = orig.getNamespace();
        if (targetStore != null) {
            store = targetStore;
            ns = catalog.getNamespaceByPrefix(store.getWorkspace().getName());
        }
        copy.setStore(store);
        copy.setNamespace(ns);
        catalog.add(copy);
        copy = catalog.getResource(copy.getId(), ResourceInfo.class);
        targetResource = copy;
        LayerInfo layer = catalog.getLayerByName(orig.prefixedName());
        if (null != layer) layer.accept(this);
        targetResource = null;
        return copy;
    }

    @Override
    public void visit(StyleInfo orig) {
        StyleInfo copy = prototype(orig);

        String fileName = copy.getName() + "." + FilenameUtils.getExtension(orig.getFilename());
        copy.setFilename(fileName);

        // copy over the style contents
        try (BufferedReader reader = catalog.getResourcePool().readStyle(orig)) {
            ByteArrayInputStream in =
                    new ByteArrayInputStream(IOUtils.toByteArray(reader, "UTF-8"));
            catalog.getResourcePool().writeStyle(copy, in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void visit(LayerInfo orig) {
        if (targetResource == null) {
            orig.getResource().accept(this);
            return;
        }

        LayerInfo copy = prototype(orig);
        copy.setResource(targetResource);

        if (recursive) {
            WorkspaceInfo backup = targetWorkspace;
            targetWorkspace = targetResource.getStore().getWorkspace();

            copy.setDefaultStyle(cloneIfNotGlobal(orig.getDefaultStyle()));

            copy.getStyles().clear();
            orig.getStyles().stream().map(this::cloneIfNotGlobal).forEach(copy.getStyles()::add);

            targetWorkspace = backup;
        }

        catalog.add(copy);
    }

    private StyleInfo cloneIfNotGlobal(StyleInfo orig) {
        if (null == orig) return null;
        if (null == orig.getWorkspace()) return orig;
        String newName = nameMapper.apply(orig);
        StyleInfo copy = catalog.getStyleByName(targetWorkspace, newName);
        if (copy == null) {
            orig.accept(this);
            copy = catalog.getStyleByName(targetWorkspace, newName);
            Objects.requireNonNull(copy);
        }
        return copy;
    }

    @Override
    public void visit(LayerGroupInfo orig) {
        cloneLayerGroup(orig);
    }

    private LayerGroupInfo cloneLayerGroup(LayerGroupInfo orig) {
        LayerGroupInfo copy = prototype(orig);
        if (recursive) {
            copy.setRootLayerStyle(cloneIfNotGlobal(orig.getRootLayerStyle()));
            copy.getStyles().clear();
            orig.getStyles().stream().map(this::cloneIfNotGlobal).forEach(copy.getStyles()::add);

            copy.setRootLayer(cloneIfNeeded(orig.getRootLayer()));
            copy.getLayers().clear();
            orig.getLayers().stream().map(this::cloneIfNeeded).forEach(copy.getLayers()::add);
            orig.getLayerGroupStyles(); // ??
        }
        catalog.add(copy);
        return catalog.getLayerGroup(copy.getId());
    }

    @SuppressWarnings("unchecked")
    private <T extends PublishedInfo> T cloneIfNeeded(T orig) {
        if (orig instanceof LayerGroupInfo) return (T) cloneLayerGroup((LayerGroupInfo) orig);
        return (T) cloneLayer((LayerInfo) orig);
    }

    private LayerInfo cloneLayer(LayerInfo orig) {
        // TODO Auto-generated method stub
        return null;
    }

    private <T extends CatalogInfo> T prototype(T original) {
        Class<T> clazz = interfaceOf(original);
        return prototype(original, clazz);
    }

    private <T extends CatalogInfo> T prototype(T original, Class<T> clazz) {
        T prototype;
        if (original instanceof WorkspaceInfo) {
            prototype = clazz.cast(new WorkspaceInfoImpl());
        } else if (original instanceof DataStoreInfo) {
            prototype = clazz.cast(new DataStoreInfoImpl(catalog));
        } else if (original instanceof CoverageStoreInfo) {
            prototype = clazz.cast(new CoverageStoreInfoImpl(catalog));
        } else if (original instanceof WMSStoreInfo) {
            prototype = clazz.cast(new WMSStoreInfoImpl(catalog));
        } else if (original instanceof WMTSStoreInfo) {
            prototype = clazz.cast(new WMTSStoreInfoImpl(catalog));
        } else if (original instanceof FeatureTypeInfo) {
            prototype = clazz.cast(new FeatureTypeInfoImpl(catalog));
        } else if (original instanceof CoverageInfo) {
            prototype = clazz.cast(new CoverageInfoImpl(catalog));
        } else if (original instanceof WMSLayerInfo) {
            prototype = clazz.cast(new WMSLayerInfoImpl(catalog));
        } else if (original instanceof WMTSLayerInfo) {
            prototype = clazz.cast(new WMTSLayerInfoImpl(catalog));
        } else if (original instanceof LayerInfo) {
            prototype = clazz.cast(new LayerInfoImpl());
        } else if (original instanceof LayerGroupInfo) {
            prototype = clazz.cast(new LayerGroupInfoImpl());
        } else if (original instanceof StyleInfo) {
            prototype = clazz.cast(new StyleInfoImpl(catalog));
        } else {
            throw new IllegalArgumentException(original.toString());
        }

        OwsUtils.copy(original, prototype, clazz);
        OwsUtils.set(prototype, "id", null);
        final String newName = nameMapper.apply(original);
        String nameProp = NamespaceInfo.class.equals(clazz) ? "prefix" : "name";
        OwsUtils.set(prototype, nameProp, newName);

        return prototype;
    }

    @SuppressWarnings("unchecked")
    private <T extends CatalogInfo> Class<T> interfaceOf(T original) {
        Class<?>[] interfaces = {
            LayerGroupInfo.class,
            LayerInfo.class,
            NamespaceInfo.class,
            WorkspaceInfo.class,
            StyleInfo.class,
            CoverageStoreInfo.class,
            DataStoreInfo.class,
            WMSStoreInfo.class,
            WMTSStoreInfo.class,
            CoverageInfo.class,
            FeatureTypeInfo.class,
            WMSLayerInfo.class,
            WMTSLayerInfo.class
        };
        for (Class<?> c : interfaces) {
            if (c.isAssignableFrom(original.getClass())) {
                return (Class<T>) c;
            }
        }
        throw new IllegalArgumentException();
    }
}
