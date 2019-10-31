package org.geoserver.catalog;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.geotools.util.logging.Logging;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;

import com.github.benmanes.caffeine.cache.Caffeine;

public class ResourcePoolCacheManager implements CacheManager, InitializingBean {

    private static final Logger LOGGER = Logging.getLogger(ResourcePoolCacheManager.class);

    private final ConcurrentMap<String, Cache> cacheMap = new ConcurrentHashMap<>();
    private final Map<String, Caffeine<Object, Object>> builders = new ConcurrentHashMap<>();

    private Map<String, String> cacheSpecs = new HashMap<>();

    public @Override void afterPropertiesSet() throws Exception {
        for (Map.Entry<String, String> cacheSpecEntry : cacheSpecs.entrySet()) {
            builders.put(cacheSpecEntry.getKey(), Caffeine.from(cacheSpecEntry.getValue()));
        }
    }

    @Override
    public Collection<String> getCacheNames() {
        return Collections.unmodifiableSet(this.cacheMap.keySet());
    }

    @Override
    @Nullable
    public Cache getCache(String name) {
        Cache cache = this.cacheMap.computeIfAbsent(name, this::createCache);
        return cache;
    }

    /**
     * Create a new CaffeineCache instance for the specified cache name.
     *
     * @param name the name of the cache
     * @return the Spring CaffeineCache adapter (or a decorator thereof)
     */
    private Cache createCache(String name) {
        boolean allowNullValues = false;
        return new CaffeineCache(name, createNativeCaffeineCache(name), allowNullValues);
    }

    /**
     * Create a native Caffeine Cache instance for the specified cache name.
     *
     * @param name the name of the cache
     * @return the native Caffeine Cache instance
     */
    protected com.github.benmanes.caffeine.cache.Cache<Object, Object> createNativeCaffeineCache(String name) {
        // TODO: provide a configurable default spec or disallow creating non configured
        // caches?
        Caffeine<Object, Object> builder = builders.computeIfAbsent(name, n -> Caffeine.newBuilder());
        return builder.build();
    }

    /** Per-cache name caffeine cache specification strings */
    public void setCacheSpecs(Map<String, String> cacheSpecs) {
        Objects.requireNonNull(cacheSpecs);
        this.cacheSpecs = cacheSpecs;
    }

    public Map<String, String> getCacheSpecs() {
        return cacheSpecs;
    }

    /** Clears all caches */
    public void clear() {
        this.cacheMap.entrySet().forEach(e -> {
            e.getValue().clear();
            LOGGER.warning("Explicitly cleared ResourcePool cache " + e.getKey());
        });
    }

    public void setCacheSize(String cacheName, long maximumSize) {
        Objects.requireNonNull(builders.get(cacheName), () -> String.format("Cache %s does not exist", cacheName));

        final String oldSpec = this.cacheSpecs.getOrDefault(cacheName, "");
        final String maxSizeParam = String.format("maximumSize=%d", maximumSize);
        List<String> params = Arrays.stream(oldSpec.split(",")).filter(v -> !v.startsWith("maximumSize"))
                .collect(Collectors.toList());
        params.add(maxSizeParam);
        String newSpec = params.stream().collect(Collectors.joining(","));

        Caffeine<Object, Object> newBuilder = Caffeine.from(newSpec);
        com.github.benmanes.caffeine.cache.@NonNull Cache<Object, Object> newNativeCache;
        newNativeCache = newBuilder.build();

        boolean allowNullValues = false;
        CaffeineCache newCache = new CaffeineCache(cacheName, newNativeCache, allowNullValues);

        this.builders.put(cacheName, newBuilder);
        this.cacheSpecs.put(cacheName, newSpec);
        Cache old = this.cacheMap.put(cacheName, newCache);
        if (old != null) {
            old.clear();
        }
    }
}
