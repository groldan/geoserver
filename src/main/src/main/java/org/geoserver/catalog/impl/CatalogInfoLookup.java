/* (c) 2017 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.catalog.impl;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.CatalogInfo;
import org.geoserver.catalog.StoreInfo;
import org.geoserver.ows.util.OwsUtils;
import org.geotools.util.logging.Logging;
import org.opengis.feature.type.Name;

import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
import com.google.common.reflect.ClassPath.ResourceInfo;

/**
 * A support index for {@link DefaultCatalogFacade}, can perform fast lookups of
 * {@link CatalogInfo} objects by id or by "name", where the name is defined by
 * a a user provided mapping function.
 *
 * <p>
 * The lookups by predicate have been tested and optimized for performance, in
 * particular the current for loops turned out to be significantly faster than
 * building and returning streams
 *
 * @param <T>
 */
class CatalogInfoLookup<T extends CatalogInfo> {
    static final Logger LOGGER = Logging.getLogger(CatalogInfoLookup.class);

    static final Predicate<?> TRUE = x -> true;

    private final Map<String, Index<?, T>> indexes = new HashMap<>();

    private final Class<T> clazz;

    private final Index<String, T> idIndex;

    private final Index<Name, T> nameIndex;

    public CatalogInfoLookup(Class<T> clazz, Function<T, Name> nameMapper) {
        super();
        this.clazz = clazz;
        final boolean unique = true;
        final boolean hierarchical = isHierarchyRoot(clazz);
        this.idIndex = addIndex("id", String.class, unique, hierarchical, CatalogInfo::getId);
        this.nameIndex = addIndex("name", Name.class, unique, hierarchical, nameMapper);
    }

    private boolean isHierarchyRoot(Class<T> clazz) {
        return StoreInfo.class == clazz || ResourceInfo.class == clazz;
    }

    @SuppressWarnings("unchecked")
    public static <C> Predicate<C> alwaysTrue() {
        return (Predicate<C>) TRUE;
    }

    public <K> Index<K, T> addIndex(String propertyName, Class<K> propertyType, boolean unique, boolean hierarchical,
            Function<T, K> mapper) {

        Objects.requireNonNull(propertyName);
        Objects.requireNonNull(propertyType);
        Objects.requireNonNull(mapper);

        Index<K, T> index = Index.create(propertyName, propertyType, this.clazz, unique, hierarchical, mapper);
        indexes.put(propertyName, index);
        return index;
    }

    protected <K> Index<K, T> getIndex(String name) {
        return (Index<K, T>) indexes.get(name);
    }

    protected Index<String, T> getIds() {
        return idIndex;
    }

    protected Index<Name, T> getNames() {
        return nameIndex;
    }

    public List<T> values() {
        return getIds().stream(this.clazz, v -> true).collect(Collectors.toList());
    }

    public void add(T value) {
        final T resolvedValue = ModificationProxy.unwrap(value);
        this.indexes.values().forEach(index -> index.put(resolvedValue));
    }

    public void remove(T value) {
        final T resolvedValue = ModificationProxy.unwrap(value);
        this.indexes.values().forEach(index -> index.remove(resolvedValue));
    }

    /**
     * Updates the value in the name map. The new value must be a ModificationProxy
     */
    public void update(T proxiedValue) {
        final T actualValue = ModificationProxy.unwrap(proxiedValue);
        this.indexes.values().forEach(index -> index.replace(actualValue, proxiedValue));
    }

    public void clear() {
        this.indexes.clear();
    }

    /**
     * Looks up objects by class and matching predicate.
     *
     * <p>
     * This method is significantly faster than creating a stream and the applying
     * the predicate on it. Just using this approach instead of the stream makes the
     * overall startup of GeoServer with 20k layers go down from 50s to 44s (which
     * is a lot, considering there is a lot of other things going on)
     *
     * @param clazz
     * @param predicate
     * @return
     */
    public <U extends CatalogInfo> List<U> list(Class<U> clazz, Predicate<U> predicate) {
        Stopwatch sw = Stopwatch.createStarted();
        System.err.println("-------> listing " + clazz);
        ArrayList<U> result = new ArrayList<U>();
        getNames().forEach(clazz, predicate, u -> result.add(u));
        System.err.printf("-------> listing of %s built in %s, size: %,d%n", clazz, sw.stop(), result.size());
        return result;
    }

    public <U extends CatalogInfo> Stream<U> stream(Class<U> clazz, Predicate<U> predicate) {
        return getIds().stream(clazz, predicate);
    }

    public <U extends CatalogInfo> Stream<U> stream(Class<U> clazz, Predicate<U> predicate, String indexName) {
        return getIndex(indexName).stream(clazz, predicate);
    }

    public <U extends CatalogInfo> int count(Class<U> clazz, Predicate<U> predicate) {
        AtomicInteger count = new AtomicInteger();
        getIds().forEach(clazz, predicate, o -> count.incrementAndGet());
        return count.get();
    }

    /**
     * Looks up a CatalogInfo by class and identifier
     *
     * @param id
     * @param clazz
     * @return
     */
    public <U extends CatalogInfo> U findById(String id, Class<U> clazz) {
        return (U) getIds().get(clazz, id);
    }

    /**
     * Looks up a CatalogInfo by class and name
     *
     * @param clazz
     * @param id
     * @return
     */
    public <U extends CatalogInfo> U findByName(Name name, Class<U> clazz) {
        return (U) getNames().get(clazz, name);
    }

    /**
     * Looks up objects by class and matching predicate.
     *
     * <p>
     * This method is significantly faster than creating a stream and the applying
     * the predicate on it. Just using this approach instead of the stream makes the
     * overall startup of GeoServer with 20k layers go down from 50s to 44s (which
     * is a lot, considering there is a lot of other things going on)
     *
     * @param clazz
     * @param predicate
     * @return
     */
    <U extends CatalogInfo> U findFirst(Class<U> clazz, Predicate<U> predicate) {
        return (U) getIds().findFirst(clazz, predicate);
    }

    /**
     * Sets the specified catalog into all CatalogInfo objects contained in this
     * lookup
     *
     * @param catalog
     */
    public CatalogInfoLookup<T> setCatalog(Catalog catalog) {
        getIds().forEach(i -> setCatalog(i, catalog));
        return this;
    }

    private void setCatalog(Object v, Catalog catalog) {
        if (v instanceof CatalogInfo) {
            Method setter = OwsUtils.setter(v.getClass(), "catalog", Catalog.class);
            if (setter != null) {
                try {
                    setter.invoke(v, catalog);
                } catch (Exception e) {
                    LOGGER.log(Level.FINE, "Failed to switch CatalogInfo to new catalog impl", e);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    static <T extends CatalogInfo> CatalogInfoLookup<T> combineAsImmutable(Class<T> clazz,
            final CatalogInfoLookup<? extends CatalogInfo>... others) {
        Objects.requireNonNull(others);
        if (others.length == 1) {
            return (CatalogInfoLookup<T>) others[0];
        }
        return new CatalogInfoLookup<T>(clazz, null) {
            private final List<CatalogInfoLookup<? extends CatalogInfo>> delegates = Arrays.asList(others);

            public @Override List<T> values() {
                List<T> values = (List<T>) delegates.get(0).values();
                for (int i = 1; i < delegates.size(); i++) {
                    values.addAll((Collection<? extends T>) delegates.get(i).values());
                }
                return values;
            }

            public @Override <U extends CatalogInfo> List<U> list(Class<U> clazz, Predicate<U> predicate) {
                List<U> list = delegates.get(0).list(clazz, predicate);
                for (int i = 1; i < delegates.size(); i++) {
                    List<U> next = delegates.get(i).list(clazz, predicate);
                    list.addAll(next);
                }
                return list;
            }

            public @Override <U extends CatalogInfo> Stream<U> stream(Class<U> clazz, Predicate<U> predicate) {
                Stream<U> stream = delegates.get(0).stream(clazz, predicate);
                for (int i = 1; i < delegates.size(); i++) {
                    Stream<U> next = delegates.get(i).stream(clazz, predicate);
                    stream = Stream.concat(stream, next);
                }
                return stream;
            }

            public @Override <U extends CatalogInfo> Stream<U> stream(Class<U> clazz, Predicate<U> predicate,
                    String indexName) {
                Stream<U> stream = delegates.get(0).stream(clazz, predicate, indexName);
                for (int i = 1; i < delegates.size(); i++) {
                    Stream<U> next = delegates.get(i).stream(clazz, predicate, indexName);
                    stream = Stream.concat(stream, next);
                }
                return stream;
            }

            public @Override <U extends CatalogInfo> int count(Class<U> clazz, Predicate<U> predicate) {
                int count = 0;
                for (int i = 0; i < delegates.size(); i++) {
                    count += delegates.get(i).count(clazz, predicate);
                }
                return count;
            }

            public @Override <U extends CatalogInfo> U findById(String id, Class<U> clazz) {
                for (int i = 0; i < delegates.size(); i++) {
                    U found = delegates.get(i).findById(id, clazz);
                    if (null != found) {
                        return found;
                    }
                }
                return null;
            }

            public @Override <U extends CatalogInfo> U findByName(Name name, Class<U> clazz) {
                for (int i = 0; i < delegates.size(); i++) {
                    U found = delegates.get(i).findByName(name, clazz);
                    if (null != found) {
                        return found;
                    }
                }
                return null;
            }

            public @Override <U extends CatalogInfo> U findFirst(Class<U> clazz, Predicate<U> predicate) {
                for (int i = 0; i < delegates.size(); i++) {
                    U found = delegates.get(i).findFirst(clazz, predicate);
                    if (null != found) {
                        return found;
                    }
                }
                return null;
            }

            public @Override CatalogInfoLookup<T> setCatalog(Catalog catalog) {
                throw new UnsupportedOperationException();
            }

            public @Override void add(T value) {
                throw new UnsupportedOperationException();
            }

            public @Override void remove(T value) {
                throw new UnsupportedOperationException();
            }

            public @Override void update(T proxiedValue) {
                throw new UnsupportedOperationException();
            }

            public @Override void clear() {
                throw new UnsupportedOperationException();
            }
        };
    }

    protected static abstract class Index<K, T extends CatalogInfo> {
        final String name;
        final Function<T, K> mapper;
        final ValueProvider<K, T> valueProvider;
        final Class<T> clazz;

        private Index(String name, Class<K> propertyType, Class<T> clazz, Function<T, K> mapper,
                ValueProvider<K, T> valueProvider) {
            this.name = name;
            this.clazz = clazz;
            this.mapper = mapper;
            this.valueProvider = valueProvider;
        }

        protected static interface ValueProvider<K, V extends CatalogInfo> {

            void put(K key, V value);

            <T extends CatalogInfo> void forEeach(Class<T> clazz, Predicate<T> predicate, Consumer<T> action);

            V remove(K key);

            <U extends CatalogInfo> U get(Class<U> clazz, K key);

            <U extends CatalogInfo> U findFirst(Class<U> clazz, Predicate<U> predicate);

            <U extends CatalogInfo> Stream<U> stream(Class<U> clazz, Predicate<U> predicate);
        }

        protected static final class SingleClassValueProvider<K, V extends CatalogInfo> implements ValueProvider<K, V> {
            private ConcurrentHashMap<K, V> map = new ConcurrentHashMap<>();

            public @Override void put(K key, V value) {
                map.put(key, value);
            }

            @Override
            public <T extends CatalogInfo> void forEeach(Class<T> clazz, Predicate<T> predicate, Consumer<T> action) {
                Consumer<T> filterAndCall = v -> {
                    if (predicate.test(v))
                        action.accept(v);
                };
                map.forEachValue(1, (Consumer<? super V>) filterAndCall);
            }

            @Override
            public V remove(K key) {
                throw new UnsupportedOperationException("implement!");
            }

            @Override
            public <U extends CatalogInfo> U get(Class<U> clazz, K key) {
                return clazz.cast(map.get(key));
            }

            @Override
            public <U extends CatalogInfo> U findFirst(Class<U> clazz, Predicate<U> predicate) {
                Function<V, V> searchFunction = v -> predicate.test((U) v) ? v : null;
                V value = map.searchValues(1, searchFunction);
                return clazz.cast(value);
            }

            @Override
            public <U extends CatalogInfo> Stream<U> stream(Class<U> clazz, Predicate<U> predicate) {
                return map.values().stream().map(clazz::cast).filter(predicate);
            }
        }

        protected static final class SingleClassMultivalueProvider<K, V extends CatalogInfo>
                implements ValueProvider<K, V> {

            private ConcurrentHashMap<K, ConcurrentHashMap<String, V>> maps = new ConcurrentHashMap<>();

            @Override
            public void put(K key, V value) {
                ConcurrentMap<String, V> idMap = maps.computeIfAbsent(key, k -> new ConcurrentHashMap<>());
                idMap.put(value.getId(), value);
            }

            @SuppressWarnings("unchecked")
            @Override
            public <T extends CatalogInfo> void forEeach(Class<T> clazz, Predicate<T> predicate, Consumer<T> action) {
                maps.forEachValue(1, map -> {
                    map.forEachValue(1, v -> {
                        if (predicate.test((T) v))
                            action.accept((T) v);
                    });
                });
            }

            @Override
            public V remove(K key) {
                throw new UnsupportedOperationException("implement!");
            }

            @Override
            public <U extends CatalogInfo> U get(Class<U> clazz, K key) {
                throw new UnsupportedOperationException("implement!");
            }

            @Override
            public <U extends CatalogInfo> U findFirst(Class<U> clazz, Predicate<U> predicate) {
                final Function<V, V> searchFunction = v -> predicate.test((U) v) ? v : null;
                for (ConcurrentHashMap<String, V> idMap : maps.values()) {
                    V value = idMap.searchValues(1, searchFunction);
                    if (value != null) {
                        return (U) value;
                    }
                }
                return null;
            }

            @Override
            public <U extends CatalogInfo> Stream<U> stream(Class<U> clazz, Predicate<U> predicate) {
                Stream<U> stream = Stream.empty();
                for (ConcurrentHashMap<String, V> idMap : maps.values()) {
                    stream = Stream.concat(stream, idMap.values().stream().map(clazz::cast).filter(predicate));
                }
                return stream;
            }
        }

        protected static final class HierarchyValueProvider<K, V extends CatalogInfo> implements ValueProvider<K, V> {
            private ConcurrentMap<Class<?>, SingleClassValueProvider<K, V>> maps = new ConcurrentHashMap<>();

            public @Override void put(K key, V value) {
                Class<Object> intfc = ClassMappings.fromImpl(value.getClass()).getInterface();
                SingleClassValueProvider<K, V> typeValues;
                typeValues = maps.computeIfAbsent(intfc, type -> new SingleClassValueProvider<>());
                typeValues.put(key, value);
            }

            @Override
            public <T extends CatalogInfo> void forEeach(Class<T> clazz, Predicate<T> predicate, Consumer<T> action) {
                maps.forEach((type, values) -> {
                    if (clazz.isAssignableFrom(type))
                        values.forEeach(clazz, predicate, action);
                });
            }

            @Override
            public V remove(K key) {
                throw new UnsupportedOperationException("implement!");
            }

            @Override
            public <U extends CatalogInfo> U get(Class<U> clazz, K key) {
                for (Entry<Class<?>, SingleClassValueProvider<K, V>> e : maps.entrySet()) {
                    if (clazz.isAssignableFrom(e.getKey())) {
                        U found = e.getValue().get(clazz, key);
                        if (found != null) {
                            return found;
                        }
                    }
                }
                return null;
            }

            @Override
            public <U extends CatalogInfo> U findFirst(Class<U> clazz, Predicate<U> predicate) {
                for (Entry<Class<?>, SingleClassValueProvider<K, V>> e : maps.entrySet()) {
                    if (clazz.isAssignableFrom(e.getKey())) {
                        U found = e.getValue().findFirst(clazz, predicate);
                        if (found != null) {
                            return found;
                        }
                    }
                }
                return null;
            }

            @Override
            public <U extends CatalogInfo> Stream<U> stream(Class<U> clazz, Predicate<U> predicate) {
                Stream<U> stream = Stream.empty();
                for (Entry<Class<?>, SingleClassValueProvider<K, V>> e : maps.entrySet()) {
                    if (clazz.isAssignableFrom(e.getKey())) {
                        stream = Stream.concat(stream, e.getValue().stream(clazz, predicate));
                    }
                }
                return stream;
            }
        }

        protected static final class HierarchyMultivalueProvider<K, V extends CatalogInfo>
                implements ValueProvider<K, V> {

            private ConcurrentMap<Class<V>, SingleClassMultivalueProvider<K, V>> maps = new ConcurrentHashMap<>();

            @Override
            public void put(K key, V value) {
                Class<V> intfc = ClassMappings.fromImpl(value.getClass()).getInterface();
                SingleClassMultivalueProvider<K, V> typeMap = maps.computeIfAbsent(intfc,
                        type -> new SingleClassMultivalueProvider<>());
                typeMap.put(key, value);
            }

            @Override
            public <T extends CatalogInfo> void forEeach(Class<T> clazz, Predicate<T> predicate, Consumer<T> action) {
                maps.forEach((type, values) -> {
                    if (clazz.isAssignableFrom(type))
                        values.forEeach(clazz, predicate, action);
                });
            }

            @Override
            public V remove(K key) {
                throw new UnsupportedOperationException("implement!");
            }

            @Override
            public <U extends CatalogInfo> U get(Class<U> clazz, K key) {
                for (Entry<Class<V>, SingleClassMultivalueProvider<K, V>> e : maps.entrySet()) {
                    if (clazz.isAssignableFrom(e.getKey())) {
                        U found = e.getValue().get(clazz, key);
                        if (found != null) {
                            return found;
                        }
                    }
                }
                return null;
            }

            @Override
            public <U extends CatalogInfo> U findFirst(Class<U> clazz, Predicate<U> predicate) {
                for (Entry<Class<V>, SingleClassMultivalueProvider<K, V>> e : maps.entrySet()) {
                    if (clazz.isAssignableFrom(e.getKey())) {
                        U found = e.getValue().findFirst(clazz, predicate);
                        if (found != null) {
                            return found;
                        }
                    }
                }
                return null;
            }

            @Override
            public <U extends CatalogInfo> Stream<U> stream(Class<U> clazz, Predicate<U> predicate) {
                Stream<U> stream = Stream.empty();
                for (Entry<Class<V>, SingleClassMultivalueProvider<K, V>> e : maps.entrySet()) {
                    if (clazz.isAssignableFrom(e.getKey())) {
                        stream = Stream.concat(stream, e.getValue().stream(clazz, predicate));
                    }
                }
                return stream;
            }
        }

        /**
         * 
         * @param name         index name
         * @param propertyType the index property type
         * @param unique       whether the index is unique or not
         * @param hierarchical whether the index works across a class hierarchy (e.g.
         *                     ResourceInfo and its descendants) or a single class (e.g.
         *                     NamespaceInfo)
         * @param mapper       function to obtain the index key for a specific value
         */
        public static <K, V extends CatalogInfo> Index<K, V> create(String name, Class<K> propertyType,
                Class<V> valueType, boolean unique, boolean hierarchical, Function<V, K> mapper) {

            ValueProvider<K, V> valueProvider = hierarchical ? new HierarchyValueProvider<>()
                    : new SingleClassValueProvider<>();
            Index<K, V> index;
            if (unique) {
                index = new UniqueIndex<>(name, propertyType, valueType, mapper, valueProvider);
            } else {
                index = new NonUniqueIndex<>(name, propertyType, valueType, mapper, valueProvider);
            }
            return index;
        }

        public void replace(final T oldValue, final T newValue) {
            final K oldKey = mapper.apply(oldValue);
            final K newKey = mapper.apply(newValue);
            Preconditions.checkArgument(!(newValue instanceof Proxy));
            valueProvider.remove(oldKey);
            valueProvider.put(newKey, newValue);
        }

        public void put(T resolvedValue) {
            Preconditions.checkArgument(!(resolvedValue instanceof Proxy));
            K key = mapKey(resolvedValue);
            valueProvider.put(key, resolvedValue);
        }

        public void remove(T info) {
            K key = mapper.apply(info);
            valueProvider.remove(key);
        }

        public void forEach(Consumer<T> target) {
            valueProvider.forEeach(clazz, v -> true, target);
        }

        public <U extends CatalogInfo> void forEach(Class<U> clazz, Predicate<U> predicate, Consumer<U> action) {
            valueProvider.forEeach(clazz, predicate, action);
        }

        public <U extends CatalogInfo> U get(Class<U> clazz, K key) {
            return valueProvider.get(clazz, key);
        }

        <U extends CatalogInfo> U findFirst(@Nullable Class<U> clazz, Predicate<U> predicate) {
            return valueProvider.findFirst(clazz, predicate);
        }

        public <U extends CatalogInfo> Stream<U> stream(Class<U> clazz, Predicate<U> predicate) {
            return valueProvider.stream(clazz, predicate);
        }

        public K mapKey(T value) {
            return mapper.apply(value);
        }

        public void remap(K oldKey) {
            T value = valueProvider.remove(oldKey);
            if (value != null) {
                valueProvider.put(mapKey(value), value);
            }
        }
    }

    private static class UniqueIndex<K, T extends CatalogInfo> extends Index<K, T> {
        UniqueIndex(String name, Class<K> propType, Class<T> clazz, Function<T, K> mapper,
                ValueProvider<K, T> valueProvider) {
            super(name, propType, clazz, mapper, valueProvider);
        }
    }

    private static class NonUniqueIndex<K, T extends CatalogInfo> extends Index<K, T> {
        NonUniqueIndex(String name, Class<K> propType, Class<T> clazz, Function<T, K> mapper,
                ValueProvider<K, T> valueProvider) {
            super(name, propType, clazz, mapper, valueProvider);
        }
    }
}
