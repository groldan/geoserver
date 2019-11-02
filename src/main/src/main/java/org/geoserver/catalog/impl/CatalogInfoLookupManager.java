package org.geoserver.catalog.impl;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.CatalogInfo;
import org.geoserver.catalog.DataStoreInfo;
import org.geoserver.catalog.LayerGroupInfo;
import org.geoserver.catalog.LayerInfo;
import org.geoserver.catalog.MapInfo;
import org.geoserver.catalog.NamespaceInfo;
import org.geoserver.catalog.Predicates;
import org.geoserver.catalog.PublishedInfo;
import org.geoserver.catalog.ResourceInfo;
import org.geoserver.catalog.StoreInfo;
import org.geoserver.catalog.StyleInfo;
import org.geoserver.catalog.WorkspaceInfo;
import org.geoserver.catalog.impl.CatalogInfoLookup.Index;
import org.geotools.feature.NameImpl;
import org.geotools.filter.Filters;
import org.geotools.filter.visitor.DuplicatingFilterVisitor;
import org.geotools.filter.visitor.SimplifyingFilterVisitor;
import org.opengis.feature.type.Name;
import org.opengis.filter.Filter;
import org.opengis.filter.PropertyIsLike;
import org.opengis.filter.expression.Expression;

import com.google.common.base.Stopwatch;

public class CatalogInfoLookupManager {

    /**
     * The name uses the workspace id as it does not need to be updated when the
     * workspace is renamed
     */
    public static final Function<StoreInfo, Name> STORE_NAME_MAPPER = s -> new NameImpl(s.getWorkspace().getId(),
            s.getName());

    /**
     * The name uses the namspace id as it does not need to be updated when the
     * namespace is renamed
     */
    public static final Function<ResourceInfo, Name> RESOURCE_NAME_MAPPER = r -> new NameImpl(r.getNamespace().getId(),
            r.getName());

    /** Like LayerInfo, actually delegates to the resource logic */
    public static final Function<LayerInfo, Name> LAYER_NAME_MAPPER = l -> RESOURCE_NAME_MAPPER.apply(l.getResource());

    /**
     * The name uses the workspace id as it does not need to be updated when the
     * workspace is renamed
     */
    public static final Function<LayerGroupInfo, Name> LAYERGROUP_NAME_MAPPER = lg -> new NameImpl(
            lg.getWorkspace() != null ? lg.getWorkspace().getId() : null, lg.getName());

    public static final Function<NamespaceInfo, Name> NAMESPACE_NAME_MAPPER = n -> new NameImpl(n.getPrefix());

    public static final Function<WorkspaceInfo, Name> WORKSPACE_NAME_MAPPER = w -> new NameImpl(w.getName());

    public static final Function<StyleInfo, Name> STYLE_NAME_MAPPER = s -> new NameImpl(
            s.getWorkspace() != null ? s.getWorkspace().getId() : null, s.getName());

    static final class LayerInfoLookup extends CatalogInfoLookup<LayerInfo> {

        public LayerInfoLookup() {
            super(LayerInfo.class, LAYER_NAME_MAPPER);
        }

        public void update(ResourceInfo proxiedValue) {
            ModificationProxy h = (ModificationProxy) Proxy.getInvocationHandler(proxiedValue);
            ResourceInfo actualValue = (ResourceInfo) h.getProxyObject();

            Name oldName = RESOURCE_NAME_MAPPER.apply(actualValue);
            Name newName = RESOURCE_NAME_MAPPER.apply(proxiedValue);
            if (!oldName.equals(newName)) {
                Index<Name, LayerInfo> names = super.getNames();
                LayerInfo layer = names.get(LayerInfo.class, oldName);
                if (layer != null) {
                    names.remap(oldName, layer);
                }
                // LayerInfo value = names.remove(actualValue);
                // // handle case of feature type without a corresponding layer
                // if (value != null) {
                // names.put(value);
                // }
            }
        }

        @Override
        public LayerInfoLookup setCatalog(Catalog catalog) {
            super.setCatalog(catalog);
            return this;
        }
    }

    protected CatalogInfoLookupFullTextIndex fullTextIndex;

    /** Contains the stores keyed by implementation class */
    protected CatalogInfoLookup<StoreInfo> stores;

    /** The default store keyed by workspace id */
    protected Map<String, DataStoreInfo> defaultStores;

    protected CatalogInfoLookupManager lookupManager;
    /** resources */
    protected CatalogInfoLookup<ResourceInfo> resources;

    /** The default namespace */
    protected volatile NamespaceInfo defaultNamespace;

    /** namespaces */
    protected CatalogInfoLookup<NamespaceInfo> namespaces;

    /** The default workspace */
    protected volatile WorkspaceInfo defaultWorkspace;

    /** workspaces */
    protected CatalogInfoLookup<WorkspaceInfo> workspaces;

    /** layers */
    protected LayerInfoLookup layers;

    /** maps */
    protected List<MapInfo> maps = new CopyOnWriteArrayList<MapInfo>();

    /** layer groups */
    protected CatalogInfoLookup<LayerGroupInfo> layerGroups;

    /** styles */
    protected CatalogInfoLookup<StyleInfo> styles;

    public CatalogInfoLookupManager() {
        try {
            Path tempDirectory = Files.createTempDirectory("gs_full_text");
            fullTextIndex = new CatalogInfoLookupFullTextIndex(tempDirectory);
            fullTextIndex.open();
        } catch (IOException e) {
            throw new RuntimeException();
        }
        defaultStores = new ConcurrentHashMap<>();
        workspaces = new CatalogInfoLookup<>(WorkspaceInfo.class, WORKSPACE_NAME_MAPPER);
        namespaces = new CatalogInfoLookup<>(NamespaceInfo.class, NAMESPACE_NAME_MAPPER);
        namespaces.addIndex("uri", String.class, false, false, false, NamespaceInfo::getURI);
        stores = new CatalogInfoLookup<>(StoreInfo.class, STORE_NAME_MAPPER);
        layers = new LayerInfoLookup();
        styles = new CatalogInfoLookup<>(StyleInfo.class, STYLE_NAME_MAPPER);
        resources = new CatalogInfoLookup<>(ResourceInfo.class, RESOURCE_NAME_MAPPER);
        layers = new LayerInfoLookup();
        layerGroups = new CatalogInfoLookup<>(LayerGroupInfo.class, LAYERGROUP_NAME_MAPPER);
    }

    public void dispose() {
        stores.clear();
        defaultStores.clear();
        resources.clear();
        namespaces.clear();
        workspaces.clear();
        layers.clear();
        layerGroups.clear();
        maps.clear();
        styles.clear();
        try {
            fullTextIndex.close();
        } catch (IOException e) {
            throw new RuntimeException();
        }
    }

    public <T extends CatalogInfo> void add(T info) {
        final CatalogInfo resolvedValue = ModificationProxy.unwrap(info);
        @SuppressWarnings("unchecked")
        CatalogInfoLookup<T> container = lookupFor((Class<T>) resolvedValue.getClass());
        container.add(info);
        try {
            this.fullTextIndex.add(info);
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public <T extends CatalogInfo> void update(T info) {
        final CatalogInfo resolvedValue = ModificationProxy.unwrap(info);
        @SuppressWarnings("unchecked")
        CatalogInfoLookup<T> container = lookupFor((Class<T>) resolvedValue.getClass());
        container.update(info);
        try {
            this.fullTextIndex.update(info);
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public <T extends CatalogInfo> void remove(T info) {
        final CatalogInfo resolvedValue = ModificationProxy.unwrap(info);
        @SuppressWarnings("unchecked")
        CatalogInfoLookup<T> container = lookupFor((Class<T>) resolvedValue.getClass());
        container.remove(info);
        try {
            this.fullTextIndex.remove(info.getId());
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public <T extends CatalogInfo> int count(Class<T> type, Filter filter) {
        Objects.requireNonNull(type, "type is null, use CatalogInfo.class instead");
        Objects.requireNonNull(filter, "filter is null, use Filter.INCLUDE instead");
        int count;
        Stopwatch sw = Stopwatch.createStarted();
        if (filter == Filter.INCLUDE) {
            count = lookupFor(type).size();
            System.err.printf("CatalogInfoLookupManager.count(): lookupFor().size() count: %s, type: %s, filter: %s%n", sw.stop(), type.getSimpleName(), filter);
        }else {
            final boolean hasFullTextSearch = Filters.propertyNames(filter).contains(Predicates.ANY_TEXT);
            if (hasFullTextSearch) {
                try {
                    count = (int) stream(type, filter).count();
                    System.err.printf("CatalogInfoLookupManager.count(): stream count: %s, type: %s, filter: %s%n", sw.stop(), type.getSimpleName(), filter);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            } else {
                count = lookupFor(type).count(type, toPredicate(filter));
                System.err.printf("CatalogInfoLookupManager.count(): lookupFor count: %s, type: %s, filter: %s%n", sw.stop(), type.getSimpleName(), filter);
            }
        }
        return count;
    }

    public <T extends CatalogInfo> Stream<T> stream(final Class<T> type, Filter filter) throws IOException {
        Objects.requireNonNull(type, "type is null, use CatalogInfo.class instead");
        Objects.requireNonNull(filter, "filter is null, use Filter.INCLUDE instead");
        Stream<T> stream;

        if (Filter.INCLUDE == filter && !PublishedInfo.class.equals(type)) {
            return lookupFor(type).getNames().stream(type, toPredicate(filter));
        }

        final boolean hasFullTextSearch = Filters.propertyNames(filter).contains(Predicates.ANY_TEXT);
        if (hasFullTextSearch) {
            Set<String> terms = new HashSet<>();
            filter = replaceFullTextFilter(filter, terms);
            Stopwatch sw = Stopwatch.createStarted();
            sw.stop();
            Predicate<? super T> predicate = toPredicate(filter);

            if (PublishedInfo.class.equals(type)) {
                Stream<String> layerIds = this.fullTextIndex.search(LayerInfo.class, terms);
                Stream<String> groupIds = this.fullTextIndex.search(LayerGroupInfo.class, terms);
                Index<String, LayerInfo> layersIndex = lookupFor(LayerInfo.class).getIds();
                Index<String, LayerGroupInfo> groupsIndex = lookupFor(LayerGroupInfo.class).getIds();
                Stream<T> layers = layerIds.map(id -> layersIndex.get(type, id)).filter(info -> info != null)
                        .filter(predicate);
                Stream<T> groups = groupIds.map(id -> groupsIndex.get(type, id)).filter(info -> info != null)
                        .filter(predicate);
                stream = Stream.concat(layers, groups);
            } else {
                Stream<String> matchIds = this.fullTextIndex.search(type, terms);
                CatalogInfoLookup<T> container = lookupFor(type);
                Index<String, T> idIndex = container.getIds();
                stream = matchIds.map(id -> idIndex.get(type, id)).filter(info -> info != null).filter(predicate);
            }
        } else {
            stream = lookupFor(type).stream(type, toPredicate(filter), "name");
        }
        return stream;
    }

    private Filter replaceFullTextFilter(Filter filter, Set<String> terms) {
        FullTextExtractFilterVisitor visitor = new FullTextExtractFilterVisitor();
        Filter duplicate = (Filter) filter.accept(visitor, terms);
        SimplifyingFilterVisitor simplify = new SimplifyingFilterVisitor();
        Filter simplified = (Filter) duplicate.accept(simplify, null);
        return simplified;
    }

    private static class FullTextExtractFilterVisitor extends DuplicatingFilterVisitor {

        public @Override Object visit(PropertyIsLike filter, Object extraData) {

            Expression expr = visit(filter.getExpression(), extraData);
            if (Predicates.ANY_TEXT.equals(expr)) {
                @SuppressWarnings("unchecked")
                Collection<String> terms = (Collection<String>) extraData;
                String pattern = filter.getLiteral();
                terms.add(pattern);
                return Filter.INCLUDE;
            }
            return filter;
        }
    }

    private <T> Predicate<T> toPredicate(Filter filter) {
        if (filter == null || filter == Filter.INCLUDE) {
            return CatalogInfoLookup.alwaysTrue();
        }
        return o -> filter.evaluate(o);
    }

    @SuppressWarnings("unchecked")
    private <T extends CatalogInfo> CatalogInfoLookup<T> lookupFor(final Class<T> type) {
        if (NamespaceInfo.class.isAssignableFrom(type)) {
            return (CatalogInfoLookup<T>) namespaces;
        }
        if (WorkspaceInfo.class.isAssignableFrom(type)) {
            return (CatalogInfoLookup<T>) workspaces;
        }
        if (StoreInfo.class.isAssignableFrom(type)) {
            return (CatalogInfoLookup<T>) stores;
        }
        if (ResourceInfo.class.isAssignableFrom(type)) {
            return (CatalogInfoLookup<T>) resources;
        }
        if (LayerInfo.class.isAssignableFrom(type)) {
            return (CatalogInfoLookup<T>) layers;
        }
        if (LayerGroupInfo.class.isAssignableFrom(type)) {
            return (CatalogInfoLookup<T>) layerGroups;
        }
        if (PublishedInfo.class.isAssignableFrom(type)) {
            return (CatalogInfoLookup<T>) CatalogInfoLookup.combineAsImmutable(PublishedInfo.class, layers,
                    layerGroups);
        }
        if (StyleInfo.class.isAssignableFrom(type)) {
            return (CatalogInfoLookup<T>) styles;
        }
        throw new IllegalArgumentException("Unknown type: " + type);
    }
}
