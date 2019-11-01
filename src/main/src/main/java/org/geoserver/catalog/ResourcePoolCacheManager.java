package org.geoserver.catalog;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import org.geotools.util.SoftValueHashMap;
import org.geotools.util.SoftValueHashMap.ValueCleaner;
import org.geotools.util.logging.Logging;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.support.AbstractValueAdaptingCache;
import org.springframework.cache.support.NoOpCache;

public class ResourcePoolCacheManager implements CacheManager, InitializingBean {

    private static final Logger LOGGER = Logging.getLogger(ResourcePoolCacheManager.class);

    private final ConcurrentMap<String, Cache> cacheMap = new ConcurrentHashMap<>();

    private Map<String, Integer> cacheSpecs = new HashMap<>();

    public @Override void afterPropertiesSet() throws Exception {
        //        for (Map.Entry<String, String> cacheSpecEntry : cacheSpecs.entrySet()) {
        //            builders.put(cacheSpecEntry.getKey(),
        // Caffeine.from(cacheSpecEntry.getValue()));
        //        }
    }

    public @Override Collection<String> getCacheNames() {
        return Collections.unmodifiableSet(this.cacheMap.keySet());
    }

    public @Override Cache getCache(String name) {
        return this.cacheMap.computeIfAbsent(name, this::createCache);
    }

    @SuppressWarnings("unchecked")
    public @Nullable <K, V> Map<K, V> asMap(String cacheName) {
        Cache cache = this.cacheMap.get(cacheName);
        if (!(cache instanceof SoftValueHashMapCache)) {
            return null;
        }
        return (Map<K, V>) ((SoftValueHashMapCache) cache).cache;
    }

    private Cache createCache(String name) {
        Integer maxSize = this.cacheSpecs.get(name);
        if (maxSize == null) {
            throw new IllegalArgumentException("Cache " + name + " not specified");
        }
        if (maxSize.intValue() == 0) {
            return new NoOpCache(name);
        }
        SoftValueHashMapCache cache;
        if (maxSize.intValue() < 0) {
            cache = new SoftValueHashMapCache(name);
        } else {
            cache = new SoftValueHashMapCache(name, maxSize.intValue());
        }
        BiConsumer<?, ?> listener = this.removalListeners.get(name);
        if (null != listener) {
            cache.setRemoalListener(listener);
        }
        return cache;
    }

    /**
     * Per-cache name cache size limits.
     *
     * <p>A negative value make an unbounded cache, a value of {@code 0} (zero) disables the cache,
     * a positive number sets the maximum number of hard references
     */
    public void setCacheSpecs(Map<String, Integer> cacheSpecs) {
        Objects.requireNonNull(cacheSpecs);
        this.cacheSpecs = cacheSpecs;
    }

    public Map<String, Integer> getCacheSpecs() {
        return cacheSpecs;
    }

    /** Clears all caches */
    public void clear() {
        this.cacheMap
                .entrySet()
                .forEach(
                        e -> {
                            e.getValue().clear();
                            LOGGER.warning("Explicitly cleared ResourcePool cache " + e.getKey());
                        });
    }

    public void setCacheSize(String cacheName, int maximumSize) {
        Objects.requireNonNull(
                this.cacheSpecs.get(cacheName), "Cache " + cacheName + " does not exist");
        this.cacheSpecs.put(cacheName, maximumSize);
        cacheMap.compute(
                cacheName,
                (name, old) -> {
                    Cache newCache = createCache(cacheName);
                    if (old instanceof SoftValueHashMapCache) {
                        // preserve as much entries as possible
                        int oldSize = ((SoftValueHashMapCache) old).cache.size();
                        int preserve = Math.min(maximumSize, oldSize);
                        Iterator<Entry<Object, Object>> entries =
                                ((SoftValueHashMapCache) old).cache.entrySet().iterator();
                        for (int i = 0; i < preserve && entries.hasNext(); i++) {
                            Entry<Object, Object> e = entries.next();
                            newCache.put(e.getKey(), e.getValue());
                            entries.remove();
                        }
                        // and dispose the rest
                        old.clear();
                    }
                    return newCache;
                });
    }

    private Map<String, BiConsumer<?, ?>> removalListeners = new HashMap<>();

    public <K, V> void setRemovalListener(String cacheName, BiConsumer<K, V> listener) {
        Objects.requireNonNull(
                this.cacheSpecs.get(cacheName), () -> "Cache " + cacheName + " does not exist");
        Objects.requireNonNull(listener);

        removalListeners.put(cacheName, listener);
        Cache cache = this.cacheMap.get(cacheName);
        if (cache instanceof SoftValueHashMapCache) {
            ((SoftValueHashMapCache) cache).setRemoalListener(listener);
        }
    }

    public static class SoftValueHashMapCache extends AbstractValueAdaptingCache implements Cache {

        private final SoftValueHashMap<Object, Object> cache;
        private final String name;

        private static final BiConsumer<Object, Object> NO_OP_LISTENER = (k, v) -> {};

        private BiConsumer<Object, Object> listener = NO_OP_LISTENER;

        private final ValueCleaner cleaner =
                new ValueCleaner() {
                    public @Override void clean(Object key, Object object) {
                        try {
                            listener.accept(key, object);
                        } catch (RuntimeException e) {
                            LOGGER.log(Level.WARNING, "Cache removal listener threw exception", e);
                        }
                    }
                };

        SoftValueHashMapCache(String name) {
            this(name, 100);
        }

        @SuppressWarnings("unchecked")
        public void setRemoalListener(@Nullable BiConsumer<?, ?> listener) {
            this.listener =
                    listener == null ? NO_OP_LISTENER : (BiConsumer<Object, Object>) listener;
        }

        public SoftValueHashMapCache(String cacheName, int maximumSize) {
            super(false);
            Objects.requireNonNull(cacheName, "cacheName");
            this.name = cacheName;
            this.cache = new SoftValueHashMap<>(maximumSize, cleaner);
        }

        public @Override String getName() {
            return this.name;
        }

        public @Override SoftValueHashMap<?, ?> getNativeCache() {
            return this.cache;
        }

        @SuppressWarnings("unchecked")
        public @Override <T> T get(Object key, Callable<T> valueLoader) {
            return (T)
                    this.cache.computeIfAbsent(
                            key,
                            k -> {
                                try {
                                    return valueLoader.call();
                                } catch (Exception e) {
                                    throw new ValueRetrievalException(key, valueLoader, e);
                                }
                            });
        }

        public @Override void put(Object key, Object value) {
            this.cache.put(key, value);
        }

        public @Override ValueWrapper putIfAbsent(Object key, Object value) {
            return super.toValueWrapper(this.cache.putIfAbsent(key, value));
        }

        public @Override void evict(Object key) {
            this.cache.remove(key);
        }

        public @Override void clear() {
            if (this.listener != NO_OP_LISTENER) {
                Iterator<Entry<Object, Object>> iterator = this.cache.entrySet().iterator();
                while (iterator.hasNext()) {
                    Entry<Object, Object> e = iterator.next();
                    this.listener.accept(e.getKey(), e.getValue());
                    iterator.remove();
                }
            }
            this.cache.clear();
        }

        //        @Override
        //        public V remove(Object key) {
        //            V object = super.remove(key);
        //            if (object != null) {
        //                dispose((K) key, object);
        //            }
        //            return object;
        //        }
        protected @Override Object lookup(Object key) {
            return this.cache.get(key);
        }
    }
}
