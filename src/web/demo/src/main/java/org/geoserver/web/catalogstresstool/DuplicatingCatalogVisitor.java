/* (c) 2024 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.web.catalogstresstool;

import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;
import static org.geoserver.catalog.Predicates.acceptAll;
import static org.geoserver.catalog.Predicates.equal;
import static org.geoserver.catalog.Predicates.isNull;

import com.google.common.collect.Streams;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.CatalogFactory;
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
import org.geoserver.catalog.ResourcePool;
import org.geoserver.catalog.StoreInfo;
import org.geoserver.catalog.StyleInfo;
import org.geoserver.catalog.WMSLayerInfo;
import org.geoserver.catalog.WMSStoreInfo;
import org.geoserver.catalog.WMTSLayerInfo;
import org.geoserver.catalog.WMTSStoreInfo;
import org.geoserver.catalog.WorkspaceInfo;
import org.geoserver.catalog.util.CloseableIterator;
import org.geoserver.ows.util.OwsUtils;
import org.geoserver.security.SecureCatalogImpl;
import org.geotools.api.filter.Filter;

class DuplicatingCatalogVisitor implements CatalogVisitor {

    private final @Nonnull Catalog sourceCatalog;
    private @Nonnull Catalog targetCatalog;

    private @Nonnull UnaryOperator<String> nameMapper;
    private @Nonnull BiConsumer<CatalogInfo, CatalogInfo> beforeListener = (orig, copy) -> {};
    private @Nonnull BiConsumer<CatalogInfo, CatalogInfo> afterListener = (orig, copy) -> {};
    private boolean recursive;

    /** The result of {@link #duplicate(CatalogInfo)} */
    private CatalogInfo copy;

    public DuplicatingCatalogVisitor(Catalog catalog) {
        this(catalog, UnaryOperator.identity());
    }

    public DuplicatingCatalogVisitor(Catalog catalog, UnaryOperator<String> nameMapper) {
        this.sourceCatalog = (Catalog) SecureCatalogImpl.unwrap(requireNonNull(catalog));
        this.targetCatalog = this.sourceCatalog;
        this.nameMapper = requireNonNull(nameMapper);
    }

    public DuplicatingCatalogVisitor targetCatalog(Catalog targetCatalog) {
        this.targetCatalog = requireNonNull(targetCatalog);
        return this;
    }

    public DuplicatingCatalogVisitor nameMapper(UnaryOperator<String> nameMapper) {
        this.nameMapper = requireNonNull(nameMapper);
        return this;
    }

    public DuplicatingCatalogVisitor beforeListener(BiConsumer<CatalogInfo, CatalogInfo> beforeListener) {
        this.beforeListener = requireNonNull(beforeListener);
        return this;
    }

    public DuplicatingCatalogVisitor afterListener(BiConsumer<CatalogInfo, CatalogInfo> afterListener) {
        this.afterListener = requireNonNull(afterListener);
        return this;
    }

    public DuplicatingCatalogVisitor recursive(boolean recursive) {
        this.recursive = recursive;
        return this;
    }

    @SuppressWarnings("unchecked")
    public <C extends CatalogInfo> C duplicate(C info) {
        requireNonNull(info).accept(this);
        return (C) copy;
    }

    /** Clones a full catalog. */
    @Override
    public void visit(Catalog catalog) {

        try (Stream<StyleInfo> globalStyles = list(StyleInfo.class, isNull("workspace"))) {
            globalStyles.forEach(this::duplicate);
        }

        try (Stream<WorkspaceInfo> workspaces = list(WorkspaceInfo.class, acceptAll())) {
            workspaces.forEach(this::duplicate);
        }

        try (Stream<LayerGroupInfo> globalLayerGroups = list(LayerGroupInfo.class, isNull("workspace"))) {
            globalLayerGroups.forEach(this::duplicate);
        }
        this.copy = targetCatalog;
    }

    @Override
    public void visit(WorkspaceInfo orig) {
        WorkspaceInfo newWorkspace = addToTargetCatalog(orig, prototype(orig));
        NamespaceInfo ns = sourceCatalog.getNamespaceByPrefix(orig.getName());
        if (ns != null) {
            cloneIfAbsent(ns);
        }
        if (recursive) {
            recurseWorkspace(orig, newWorkspace);
        }
        this.copy = newWorkspace;
    }

    @Override
    public void visit(NamespaceInfo orig) {
        NamespaceInfo newNs = prototype(orig);
        String uri = orig.getURI();
        // newNs.setURI(uri + "-" + newNs.getPrefix());
        newNs = addToTargetCatalog(orig, newNs);

        cloneIfAbsent(sourceCatalog.getWorkspaceByName(newNs.getPrefix()));

        if (recursive) {
            // rely on NamespaceWorkspaceConsistencyListener to create the appropriate
            // WorkspaceInfo if missing
            WorkspaceInfo origWs = requireNonNull(sourceCatalog.getWorkspaceByName(orig.getPrefix()));
            WorkspaceInfo targetWs = getCopy(origWs);
            recurseWorkspace(origWs, targetWs);
        }
        this.copy = newNs;
    }

    @Override
    public void visit(DataStoreInfo store) {
        visit((StoreInfo) store);
    }

    @Override
    public void visit(CoverageStoreInfo store) {
        visit((StoreInfo) store);
    }

    @Override
    public void visit(WMSStoreInfo store) {
        visit((StoreInfo) store);
    }

    @Override
    public void visit(WMTSStoreInfo store) {
        visit((StoreInfo) store);
    }

    void visit(StoreInfo orig) {
        StoreInfo newStore = prototype(orig);
        newStore.setWorkspace(getCopy(orig.getWorkspace()));

        if (orig instanceof DataStoreInfo) {
            // reset the cache, or we might stumble into a error about too many connections
            // while cloning many jdbc stores
            sourceCatalog.getResourcePool().dispose();
        }
        this.copy = addToTargetCatalog(orig, newStore);
    }

    @Override
    public void visit(FeatureTypeInfo resource) {
        visit((ResourceInfo) resource);
    }

    @Override
    public void visit(CoverageInfo resource) {
        visit((ResourceInfo) resource);
    }

    @Override
    public void visit(WMSLayerInfo resource) {
        visit((ResourceInfo) resource);
    }

    @Override
    public void visit(WMTSLayerInfo resource) {
        visit((ResourceInfo) resource);
    }

    void visit(ResourceInfo orig) {
        ResourceInfo newResource = copyResource(orig);

        LayerInfo layer = sourceCatalog.getLayerByName(orig.prefixedName());
        if (null != layer) {
            layer.accept(this);
        }

        this.copy = newResource;
    }

    private ResourceInfo copyResource(ResourceInfo orig) {
        ResourceInfo newResource = prototype(orig);
        newResource.setNativeName(orig.getNativeName());

        StoreInfo store = getCopy(orig.getStore());
        newResource.setStore(store);

        NamespaceInfo ns =
                sourceCatalog.getNamespaceByPrefix(store.getWorkspace().getName());
        newResource.setNamespace(ns);

        return addToTargetCatalog(orig, newResource);
    }

    /**
     * Clones the layer, and if the target layer belongs to a different workspace, also the styles used that belong to
     * the original layer's workspace
     */
    @Override
    public void visit(LayerInfo orig) {

        ResourceInfo resourceCopy = findCopy(orig.getResource()).orElseGet(() -> copyResource(orig.getResource()));

        LayerInfo layerCopy = prototype(orig);
        layerCopy.setResource(resourceCopy);

        // copy styles that are on a different workspace or the layer gets broken (shall
        // not use global styles from a different workspace)
        WorkspaceInfo origWorkspace = orig.getResource().getStore().getWorkspace();
        WorkspaceInfo targetWorkspace = layerCopy.getResource().getStore().getWorkspace();
        if (!origWorkspace.getName().equals(targetWorkspace.getName())) {
            StyleInfo defaultStyle = cloneIfNotGlobal(layerCopy.getDefaultStyle());
            layerCopy.setDefaultStyle(defaultStyle);

            Set<StyleInfo> origStyles = new LinkedHashSet<>(orig.getStyles());
            Set<StyleInfo> newStyles = layerCopy.getStyles();
            newStyles.clear();
            origStyles.stream().map(this::cloneIfNotGlobal).forEach(newStyles::add);
        }
        this.copy = addToTargetCatalog(orig, layerCopy);
    }

    /**
     * Clones the layer group, and if the target layer group belongs to a different workspace, also the layers and
     * styles used that belong to the original workspace
     */
    @Override
    public void visit(LayerGroupInfo orig) {
        if (true) return;
        this.copy = cloneLayerGroup(orig);
    }

    @Override
    public void visit(StyleInfo orig) {

        @Nullable WorkspaceInfo target = getCopy(orig.getWorkspace());
        StyleInfo newStyle = prototype(orig);
        newStyle.setWorkspace(target);

        final String fileName = newStyle.getName() + "." + FilenameUtils.getExtension(orig.getFilename());
        newStyle.setFilename(fileName);

        // copy over the style contents
        ResourcePool resourcePool = sourceCatalog.getResourcePool();
        try (BufferedReader sldReader = resourcePool.readStyle(orig)) {
            ByteArrayInputStream in = new ByteArrayInputStream(IOUtils.toByteArray(sldReader, UTF_8));
            resourcePool.writeStyle(newStyle, in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        this.copy = addToTargetCatalog(orig, newStyle);
    }

    private void recurseWorkspace(WorkspaceInfo orig, WorkspaceInfo targetWorkspace) {
        recurseStyles(orig, targetWorkspace);
        recurseStoresAndLayers(orig, targetWorkspace);
        recurseLayerGroups(orig, targetWorkspace);
    }

    private void recurseStyles(WorkspaceInfo orig, WorkspaceInfo targetWorkspace) {
        try (Stream<StyleInfo> stream = list(StyleInfo.class, equal("workspace.id", orig.getId()))) {
            stream.forEach(this::duplicate);
        }
    }

    private void recurseStoresAndLayers(WorkspaceInfo orig, WorkspaceInfo targetWorkspace) {
        try (Stream<StoreInfo> stream = list(StoreInfo.class, equal("workspace.id", orig.getId()))) {
            stream.forEach(this::duplicate);
        }
    }

    private void recurseResourcesAndLayers(StoreInfo orig, StoreInfo targetStore) {
        try (Stream<ResourceInfo> stream = list(ResourceInfo.class, equal("store.id", orig.getId()))) {
            stream.forEach(this::duplicate);
        }
    }

    private void recurseLayerGroups(WorkspaceInfo orig, WorkspaceInfo targetWorkspace) {
        try (Stream<LayerGroupInfo> stream = list(LayerGroupInfo.class, equal("workspace.id", orig.getId()))) {
            stream.forEach(this::duplicate);
        }

        List<LayerGroupInfo> groups = sourceCatalog.getLayerGroupsByWorkspace(orig);
        for (LayerGroupInfo lg : groups) {
            lg.accept(this);
        }
    }

    private StyleInfo cloneIfNotGlobal(StyleInfo orig) {
        if (null == orig) return null;
        if (null == orig.getWorkspace()) return orig;

        WorkspaceInfo target = getCopy(orig.getWorkspace());
        String newName = getCopyName(orig);
        StyleInfo existing = targetCatalog.getStyleByName(target, newName);
        if (existing == null) {
            return duplicate(orig);
        }
        return requireNonNull(existing);
    }

    protected LayerGroupInfo cloneLayerGroup(LayerGroupInfo orig) {
        LayerGroupInfo lgCopy = prototype(orig);
        if (null != orig.getWorkspace()) {
            WorkspaceInfo origWorkspace = orig.getWorkspace();
            WorkspaceInfo targetWorkspace = getCopy(origWorkspace);
            lgCopy.setWorkspace(targetWorkspace);

            if (!origWorkspace.getName().equals(targetWorkspace.getName())) {
                lgCopy.setRootLayerStyle(cloneIfNotGlobal(orig.getRootLayerStyle()));

                Set<StyleInfo> origStyles = new LinkedHashSet<>(orig.getStyles());
                Set<StyleInfo> newStyles = null; // ayerCopy.getStyles();
                newStyles.clear();
                origStyles.stream().map(this::getCopy).forEach(newStyles::add);
            }
        }
        if (recursive) {
            lgCopy.setRootLayerStyle(cloneIfNotGlobal(orig.getRootLayerStyle()));
            lgCopy.getStyles().clear();
            orig.getStyles().stream().map(this::cloneIfNotGlobal).forEach(lgCopy.getStyles()::add);

            lgCopy.setRootLayer(cloneIfNeeded(orig.getRootLayer()));
            lgCopy.getLayers().clear();
            orig.getLayers().stream().map(this::cloneIfNeeded).forEach(lgCopy.getLayers()::add);
            orig.getLayerGroupStyles(); // ??
        }
        return addToTargetCatalog(orig, lgCopy);
    }

    private <T extends CatalogInfo> T cloneIfAbsent(T orig) {
        return Optional.ofNullable(orig).flatMap(this::findCopy).orElseGet(() -> duplicate(orig));
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

    protected <C extends CatalogInfo> C addToTargetCatalog(C original, C copy) {
        beforeListener.accept(original, copy);
        C stored = add(copy, targetCatalog);
        afterListener.accept(original, stored);
        return stored;
    }

    @SuppressWarnings("unchecked")
    static <C extends CatalogInfo> C add(C info, Catalog target) {
        CatalogInfo stored;
        if (info instanceof WorkspaceInfo ws) {
            target.add(ws);
            stored = target.getWorkspaceByName(ws.getName());
        } else if (info instanceof NamespaceInfo ns) {
            target.add(ns);
            stored = target.getNamespaceByPrefix(ns.getPrefix());
        } else if (info instanceof StoreInfo store) {
            target.add(store);
            stored = target.getStore(store.getId(), StoreInfo.class);
        } else if (info instanceof ResourceInfo res) {
            target.add(res);
            stored = target.getResource(res.getId(), ResourceInfo.class);
        } else if (info instanceof LayerInfo layer) {
            target.add(layer);
            stored = target.getLayer(layer.getId());
        } else if (info instanceof LayerGroupInfo lg) {
            target.add(lg);
            stored = target.getLayerGroup(lg.getId());
        } else if (info instanceof StyleInfo style) {
            target.add(style);
            stored = target.getStyle(style.getId());
        } else {
            throw new IllegalArgumentException(info.toString());
        }
        requireNonNull(stored);
        return (C) stored;
    }

    private <T extends CatalogInfo> T prototype(T original) {
        Class<T> clazz = interfaceOf(original);
        return prototype(original, clazz);
    }

    private <T extends CatalogInfo> T prototype(T original, Class<T> clazz) {
        T prototype = create(clazz, targetCatalog.getFactory());
        OwsUtils.copy(original, prototype, clazz);
        OwsUtils.set(prototype, "id", null);

        final String nameProp = nameProperty(original);
        final String newName = getCopyName(original);
        OwsUtils.set(prototype, nameProp, newName);

        prototype.setDateCreated(null);
        prototype.setDateModified(null);
        prototype.setModifiedBy(null);

        return prototype;
    }

    /** Note: this could be the default implementation of {@link CatalogFactory#create(Class)} */
    public <T extends CatalogInfo> T create(Class<T> iface, CatalogFactory factory) {
        if (WorkspaceInfo.class.equals(iface)) {
            return iface.cast(factory.createWorkspace());
        } else if (NamespaceInfo.class.equals(iface)) {
            return iface.cast(factory.createNamespace());
        } else if (DataStoreInfo.class.equals(iface)) {
            return iface.cast(factory.createDataStore());
        } else if (CoverageStoreInfo.class.equals(iface)) {
            return iface.cast(factory.createCoverageStore());
        } else if (WMSStoreInfo.class.equals(iface)) {
            return iface.cast(factory.createWebMapServer());
        } else if (WMTSStoreInfo.class.equals(iface)) {
            return iface.cast(factory.createWebMapTileServer());
        } else if (FeatureTypeInfo.class.equals(iface)) {
            return iface.cast(factory.createFeatureType());
        } else if (CoverageInfo.class.equals(iface)) {
            return iface.cast(factory.createCoverage());
        } else if (WMSLayerInfo.class.equals(iface)) {
            return iface.cast(factory.createWMSLayer());
        } else if (WMTSLayerInfo.class.equals(iface)) {
            return iface.cast(factory.createWMTSLayer());
        } else if (LayerInfo.class.equals(iface)) {
            return iface.cast(factory.createLayer());
        } else if (LayerGroupInfo.class.equals(iface)) {
            return iface.cast(factory.createLayerGroup());
        } else if (StyleInfo.class.equals(iface)) {
            return iface.cast(factory.createStyle());
        }
        throw new IllegalArgumentException(iface.getCanonicalName() + " is not a CatalogInfo interface");
    }

    protected String getCopyName(CatalogInfo info) {
        final String oldName = getName(info);
        return nameMapper.apply(oldName);
    }

    protected String getName(CatalogInfo info) {
        String nameProp = nameProperty(info);
        return (String) OwsUtils.get(info, nameProp);
    }

    protected String nameProperty(CatalogInfo info) {
        return info instanceof NamespaceInfo ? "prefix" : "name";
    }

    @SuppressWarnings("unchecked")
    private <T extends CatalogInfo> Class<T> interfaceOf(T original) {
        return (Class<T>) Stream.of(
                        CoverageInfo.class,
                        CoverageStoreInfo.class,
                        DataStoreInfo.class,
                        FeatureTypeInfo.class,
                        LayerGroupInfo.class,
                        LayerInfo.class,
                        NamespaceInfo.class,
                        StyleInfo.class,
                        WMSLayerInfo.class,
                        WMSStoreInfo.class,
                        WMTSLayerInfo.class,
                        WMTSStoreInfo.class,
                        WorkspaceInfo.class)
                .filter(i -> i.isInstance(original))
                .findFirst()
                .orElseThrow();
    }

    @SuppressWarnings("unchecked")
    protected <C extends CatalogInfo> Optional<C> findCopy(C info) {
        final String copyName = getCopyName(info);
        CatalogInfo found = null;
        if (info instanceof WorkspaceInfo) {
            found = targetCatalog.getWorkspaceByName(copyName);
        } else if (info instanceof NamespaceInfo) {
            found = targetCatalog.getNamespaceByPrefix(copyName);
        } else if (info instanceof StoreInfo) {
            StoreInfo store = (StoreInfo) info;
            WorkspaceInfo targetWorkspace = getCopy(store.getWorkspace());
            found = targetCatalog.getStoreByName(targetWorkspace, copyName, StoreInfo.class);
        } else if (info instanceof ResourceInfo) {
            ResourceInfo resource = (ResourceInfo) info;
            StoreInfo targetStore = getCopy(resource.getStore());
            found = targetCatalog.getResourceByStore(targetStore, copyName, ResourceInfo.class);
        } else if (info instanceof LayerInfo) {
            LayerInfo layer = (LayerInfo) info;
            ResourceInfo targetResource = getCopy(layer.getResource());
            String wsName = targetResource.getStore().getWorkspace().getName();
            String prefixedCopyName = String.format("%s:%s", wsName, copyName);
            found = targetCatalog.getLayerByName(prefixedCopyName);
        } else if (info instanceof LayerGroupInfo) {
            LayerGroupInfo layerGroup = (LayerGroupInfo) info;
            @Nullable WorkspaceInfo targetWorkspace = getCopy(layerGroup.getWorkspace());
            found = targetCatalog.getLayerGroupByName(targetWorkspace, copyName);
        } else if (info instanceof StyleInfo) {
            StyleInfo style = (StyleInfo) info;
            @Nullable WorkspaceInfo targetWorkspace = getCopy(style.getWorkspace());
            found = targetCatalog.getStyleByName(targetWorkspace, copyName);
        } else {
            throw new IllegalArgumentException(info.toString());
        }
        return Optional.ofNullable((C) found);
    }

    /**
     * @return {@code null} if {@code orig == null}, its copy in the target Catalog otherwise, looked up by applying the
     *     name mapping function to {@code orig}'s name
     * @throws IllegalStateException if the duplicate of {@code orig} is not found in the target catalog (which may be
     *     the same as the source catalog)
     */
    protected <C extends CatalogInfo> C getCopy(@Nullable C orig) {
        if (null == orig) return null;

        return findCopy(orig).orElseThrow(() -> {
            String type = interfaceOf(orig).getSimpleName();
            String copyName = getCopyName(orig);
            return new IllegalStateException(format("%s with name %s not found in target catalog", type, copyName));
        });
    }

    private <C extends CatalogInfo> Stream<C> list(Class<C> type, Filter filter) {
        CloseableIterator<C> iterator = sourceCatalog.list(type, filter);
        return Streams.stream(iterator).onClose(iterator::close);
    }
}
