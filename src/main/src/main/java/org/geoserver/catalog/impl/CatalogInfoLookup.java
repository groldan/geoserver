/* (c) 2017 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.catalog.impl;

import com.google.common.base.Objects;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.CatalogInfo;
import org.geoserver.catalog.LayerGroupInfo;
import org.geoserver.catalog.LayerInfo;
import org.geoserver.catalog.MapInfo;
import org.geoserver.catalog.NamespaceInfo;
import org.geoserver.catalog.ResourceInfo;
import org.geoserver.catalog.StoreInfo;
import org.geoserver.catalog.StyleInfo;
import org.geoserver.catalog.WorkspaceInfo;
import org.geoserver.ows.util.OwsUtils;
import org.geotools.feature.NameImpl;
import org.geotools.util.logging.Logging;
import org.opengis.feature.type.Name;

/**
 * A support index for {@link DefaultCatalogFacade}, can perform fast lookups of {@link CatalogInfo}
 * objects by id or by "name", where the name is defined by a a user provided mapping function.
 *
 * <p>The lookups by predicate have been tested and optimized for performance, in particular the
 * current for loops turned out to be significantly faster than building and returning streams
 *
 * @param <T>
 */
class CatalogInfoLookup<T extends CatalogInfo> {
    static final Logger LOGGER = Logging.getLogger(CatalogInfoLookup.class);

    /**
     * Name mapper for {@link MapInfo}, uses simple name mapping on {@link MapInfo#getName()} as it
     * doesn't have a namespace component
     */
    static final Function<MapInfo, Name> MAP_NAME_MAPPER = m -> new NameImpl(m.getName());

    /**
     * The name uses the workspace id as it does not need to be updated when the workspace is
     * renamed
     */
    static final Function<StoreInfo, Name> STORE_NAME_MAPPER =
            s -> new NameImpl(s.getWorkspace().getId(), s.getName());

    /**
     * The name uses the namspace id as it does not need to be updated when the namespace is renamed
     */
    static final Function<ResourceInfo, Name> RESOURCE_NAME_MAPPER =
            r -> new NameImpl(r.getNamespace().getId(), r.getName());

    /** Like LayerInfo, actually delegates to the resource logic */
    static final Function<LayerInfo, Name> LAYER_NAME_MAPPER =
            l -> RESOURCE_NAME_MAPPER.apply(l.getResource());

    /**
     * The name uses the workspace id as it does not need to be updated when the workspace is
     * renamed
     */
    static final Function<LayerGroupInfo, Name> LAYERGROUP_NAME_MAPPER =
            lg ->
                    new NameImpl(
                            lg.getWorkspace() != null ? lg.getWorkspace().getId() : null,
                            lg.getName());

    static final Function<NamespaceInfo, Name> NAMESPACE_NAME_MAPPER =
            n -> new NameImpl(n.getPrefix());

    static final Function<WorkspaceInfo, Name> WORKSPACE_NAME_MAPPER =
            w -> new NameImpl(w.getName());

    static final Function<StyleInfo, Name> STYLE_NAME_MAPPER =
            s ->
                    new NameImpl(
                            s.getWorkspace() != null ? s.getWorkspace().getId() : null,
                            s.getName());

    ConcurrentMap<Class<T>, ConcurrentMap<String, T>> idMultiMap = new ConcurrentHashMap<>();
    ConcurrentMap<Class<T>, ConcurrentMap<Name, T>> nameMultiMap = new ConcurrentHashMap<>();
    ConcurrentMap<Class<T>, ConcurrentMap<String, Name>> idToMameMultiMap =
            new ConcurrentHashMap<>();

    Function<T, Name> nameMapper;
    static final Predicate TRUE = x -> true;

    public CatalogInfoLookup(Function<T, Name> nameMapper) {
        super();
        this.nameMapper = nameMapper;
    }

    <K, V> ConcurrentMap<K, V> getMapForValue(
            ConcurrentMap<Class<T>, ConcurrentMap<K, V>> maps, T value) {
        @SuppressWarnings("unchecked")
        Class<T> vc = (Class<T>) value.getClass();
        return getMapForType(maps, vc);
    }

    @SuppressWarnings("unchecked")
    protected <K, V> ConcurrentMap<K, V> getMapForType(
            ConcurrentMap<Class<T>, ConcurrentMap<K, V>> maps, Class vc) {
        return maps.computeIfAbsent(vc, k -> new ConcurrentSkipListMap<K, V>());
    }

    private void checkNotAProxy(T value) {
        if (Proxy.isProxyClass(value.getClass())) {
            throw new IllegalArgumentException(
                    "Proxy values shall not be passed to CatalogInfoLookup");
        }
    }

    public T add(T value) {
        checkNotAProxy(value);
        Map<String, T> idMap = getMapForValue(idMultiMap, value);
        Map<Name, T> nameMap = getMapForValue(nameMultiMap, value);
        Map<String, Name> idToName = getMapForValue(idToMameMultiMap, value);
        Name name = nameMapper.apply(value);
        nameMap.put(name, value);
        idToName.put(value.getId(), name);
        return idMap.put(value.getId(), value);
    }

    public Collection<T> values() {
        List<T> result = new ArrayList<>();
        for (Map<String, T> v : idMultiMap.values()) {
            result.addAll(v.values());
        }

        return result;
    }

    public T remove(T value) {
        checkNotAProxy(value);
        Map<String, T> idMap = getMapForValue(idMultiMap, value);
        T removed = idMap.remove(value.getId());
        if (removed != null) {
            Name name = getMapForValue(idToMameMultiMap, value).remove(value.getId());
            getMapForValue(nameMultiMap, value).remove(name);
        }
        return removed;
    }

    /** Updates the value in the name map. */
    public void update(T value) {
        checkNotAProxy(value);
        CatalogInfo oldValue = this.findById(value.getId(), value.getClass());
        if (oldValue == null) {
            throw new NoSuchElementException(
                    value.getClass().getSimpleName()
                            + " with id "
                            + value.getId()
                            + " does not exist");
        }
        ConcurrentMap<String, Name> idToName = getMapForValue(idToMameMultiMap, value);
        Name oldName = idToName.get(value.getId());
        Name newName = nameMapper.apply(value);
        if (!Objects.equal(oldName, newName)) {
            Map<Name, T> nameMap = getMapForValue(nameMultiMap, value);
            nameMap.remove(oldName);
            nameMap.put(newName, value);
            idToName.put(value.getId(), newName);
        }
    }

    public void clear() {
        idMultiMap.clear();
        nameMultiMap.clear();
        idToMameMultiMap.clear();
    }

    /**
     * Looks up objects by class and matching predicate.
     *
     * <p>This method is significantly faster than creating a stream and the applying the predicate
     * on it. Just using this approach instead of the stream makes the overall startup of GeoServer
     * with 20k layers go down from 50s to 44s (which is a lot, considering there is a lot of other
     * things going on)
     */
    <U extends CatalogInfo> List<U> list(Class<U> clazz, Predicate<U> predicate) {
        ArrayList<U> result = new ArrayList<U>();
        for (Class<T> key : nameMultiMap.keySet()) {
            if (clazz.isAssignableFrom(key)) {
                Map<Name, T> valueMap = nameMultiMap.get(key);
                if (valueMap != null) {
                    for (T v : valueMap.values()) {
                        final U u = clazz.cast(v);
                        if (predicate.test(u)) {
                            result.add(u);
                        }
                    }
                }
            }
        }

        return result;
    }

    /** Looks up a CatalogInfo by class and identifier */
    public <U extends CatalogInfo> U findById(String id, Class<U> clazz) {
        for (Class<T> key : idMultiMap.keySet()) {
            if (clazz.isAssignableFrom(key)) {
                Map<String, T> valueMap = idMultiMap.get(key);
                if (valueMap != null) {
                    T t = valueMap.get(id);
                    if (t != null) {
                        return clazz.cast(t);
                    }
                }
            }
        }

        return null;
    }

    /** Looks up a CatalogInfo by class and name */
    public <U extends CatalogInfo> U findByName(Name name, Class<U> clazz) {
        for (Class<T> key : nameMultiMap.keySet()) {
            if (clazz.isAssignableFrom(key)) {
                Map<Name, T> valueMap = nameMultiMap.get(key);
                if (valueMap != null) {
                    T t = valueMap.get(name);
                    if (t != null) {
                        return clazz.cast(t);
                    }
                }
            }
        }

        return null;
    }

    /**
     * Looks up objects by class and matching predicate.
     *
     * <p>This method is significantly faster than creating a stream and the applying the predicate
     * on it. Just using this approach instead of the stream makes the overall startup of GeoServer
     * with 20k layers go down from 50s to 44s (which is a lot, considering there is a lot of other
     * things going on)
     */
    <U extends CatalogInfo> U findFirst(Class<U> clazz, Predicate<U> predicate) {
        for (Class<T> key : nameMultiMap.keySet()) {
            if (clazz.isAssignableFrom(key)) {
                Map<Name, T> valueMap = nameMultiMap.get(key);
                if (valueMap != null) {
                    for (T v : valueMap.values()) {
                        final U u = clazz.cast(v);
                        if (predicate.test(u)) {
                            return u;
                        }
                    }
                }
            }
        }

        return null;
    }

    /** Sets the specified catalog into all CatalogInfo objects contained in this lookup */
    public CatalogInfoLookup<T> setCatalog(Catalog catalog) {
        for (Map<Name, T> valueMap : nameMultiMap.values()) {
            if (valueMap != null) {
                for (T v : valueMap.values()) {
                    if (v instanceof CatalogInfo) {
                        Method setter = OwsUtils.setter(v.getClass(), "catalog", Catalog.class);
                        if (setter != null) {
                            try {
                                setter.invoke(v, catalog);
                            } catch (Exception e) {
                                LOGGER.log(
                                        Level.FINE,
                                        "Failed to switch CatalogInfo to new catalog impl",
                                        e);
                            }
                        }
                    }
                }
            }
        }

        return this;
    }

    /**
     * CatalogInfoLookup specialization for {@code ResourceInfo} that encapsulates the logic to
     * update the name lookup for the linked {@code LayerInfo} given that {@code LayerInfo.getName()
     * == LayerInfo.getResource().getName()}
     */
    static final class ResouceInfoLookup extends CatalogInfoLookup<ResourceInfo> {
        private final LayerInfoLookup layers;

        public ResouceInfoLookup(LayerInfoLookup layers) {
            super(RESOURCE_NAME_MAPPER);
            this.layers = layers;
        }

        public @Override void update(ResourceInfo value) {
            Name oldName = getMapForValue(idToMameMultiMap, value).get(value.getId());
            Name newName = nameMapper.apply(value);
            super.update(value);
            if (!newName.equals(oldName)) {
                layers.updateName(oldName, newName);
            }
        }
    }

    static final class LayerInfoLookup extends CatalogInfoLookup<LayerInfo> {

        public LayerInfoLookup() {
            super(LAYER_NAME_MAPPER);
        }

        void updateName(Name oldName, Name newName) {
            ConcurrentMap<Name, LayerInfo> nameLookup =
                    getMapForType(nameMultiMap, LayerInfoImpl.class);
            LayerInfo layer = nameLookup.remove(oldName);
            if (layer != null) {
                nameLookup.put(newName, layer);
                getMapForType(idToMameMultiMap, LayerInfoImpl.class).put(layer.getId(), newName);
            }
        }

        @Override
        public LayerInfoLookup setCatalog(Catalog catalog) {
            super.setCatalog(catalog);
            return this;
        }
    }
}
