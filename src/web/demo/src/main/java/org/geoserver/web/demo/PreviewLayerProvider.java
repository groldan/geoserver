/* (c) 2014 - 2016 Open Source Geospatial Foundation - all rights reserved
 * (c) 2001 - 2013 OpenPlans
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.web.demo;

import static org.geoserver.catalog.Predicates.*;

import com.google.common.base.Function;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Lists;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.apache.wicket.extensions.markup.html.repeater.util.SortParam;
import org.apache.wicket.model.IModel;
import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.LayerGroupInfo;
import org.geoserver.catalog.LayerInfo;
import org.geoserver.catalog.Predicates;
import org.geoserver.catalog.PublishedInfo;
import org.geoserver.catalog.util.CloseableIterator;
import org.geoserver.catalog.util.CloseableIteratorAdapter;
import org.geoserver.web.wicket.GeoServerDataProvider;
import org.opengis.filter.Filter;
import org.opengis.filter.sort.SortBy;

/**
 * Provides a filtered, sorted view over the catalog layers.
 *
 * @author Andrea Aime - OpenGeo
 */
@SuppressWarnings("serial")
public class PreviewLayerProvider extends GeoServerDataProvider<PreviewLayer> {

    public static final long DEFAULT_CACHE_TIME = 1;

    public static final String KEY_LAYERS_SIZE = "layers.size";
    public static final String KEY_LAYERS_FULL_SIZE = "layers.fullsize";

    public static final String KEY_LAYERGROUPS_SIZE = "groups.size";
    public static final String KEY_LAYERGROUPS_FULL_SIZE = "groups.fullsize";

    private final Cache<String, Integer> cache;

    public PreviewLayerProvider() {
        super();
        // Initialization of an inner cache in order to avoid to calculate two times
        // the size() method in a time minor than a second
        CacheBuilder<Object, Object> builder = CacheBuilder.newBuilder();

        cache = builder.expireAfterWrite(DEFAULT_CACHE_TIME, TimeUnit.SECONDS).build();
    }

    public static final Property<PreviewLayer> TYPE =
            new BeanProperty<PreviewLayer>("type", "type");

    public static final AbstractProperty<PreviewLayer> NAME =
            new AbstractProperty<PreviewLayer>("name") {
                @Override
                public Object getPropertyValue(PreviewLayer item) {
                    if (item.layerInfo != null) {
                        return item.layerInfo.prefixedName();
                    }
                    if (item.groupInfo != null) {
                        return item.groupInfo.prefixedName();
                    }
                    return null;
                }
            };

    public static final Property<PreviewLayer> TITLE =
            new BeanProperty<PreviewLayer>("title", "title");

    public static final Property<PreviewLayer> ABSTRACT =
            new BeanProperty<PreviewLayer>("abstract", "abstract", false);

    public static final Property<PreviewLayer> KEYWORDS =
            new BeanProperty<PreviewLayer>("keywords", "keywords", false);

    public static final Property<PreviewLayer> COMMON =
            new PropertyPlaceholder<PreviewLayer>("commonFormats");

    public static final Property<PreviewLayer> ALL =
            new PropertyPlaceholder<PreviewLayer>("allFormats");

    public static final List<Property<PreviewLayer>> PROPERTIES =
            Arrays.asList(TYPE, TITLE, NAME, ABSTRACT, KEYWORDS, COMMON, ALL);

    @Override
    protected List<PreviewLayer> getItems() {
        // forced to implement this method as its abstract in the super class
        throw new UnsupportedOperationException(
                "This method should not be being called! " + "We use the catalog streaming API");
    }

    @Override
    protected List<Property<PreviewLayer>> getProperties() {
        return PROPERTIES;
    }

    @Override
    protected IModel<PreviewLayer> newModel(PreviewLayer object) {
        return new PreviewLayerModel(object);
    }

    @Override
    public long size() {
        Integer size;
        try {
            String lkey = KEY_LAYERS_SIZE;
            String gkey = KEY_LAYERGROUPS_SIZE;
            if (getKeywords() != null && getKeywords().length > 0) {
                // Use a unique key for different queries
                lkey += "." + String.join(",", getKeywords());
                lkey += "." + String.join(",", getKeywords());
            }
            Integer lsize = cache.get(lkey, () -> layersSize());
            Integer gsize = cache.get(gkey, () -> layerGroupsSize());
            size = lsize.intValue() + gsize.intValue();
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
        return size;
    }

    private int sizeInternal() {
        long s = System.currentTimeMillis();
        Filter filter = getFilter();
        int result = getCatalog().count(PublishedInfo.class, filter); // 1000022;//
        System.err.printf(
                "%s.sizeInternal(): %,dms%n",
                getClass().getSimpleName(), System.currentTimeMillis() - s);
        return result;
    }

    private int layersSize() {
        long s = System.currentTimeMillis();
        Filter filter = getLayerFilter();
        int result = getCatalog().count(LayerInfo.class, filter);
        System.err.printf(
                "%s.layersSize(): %,dms%n",
                getClass().getSimpleName(), System.currentTimeMillis() - s);
        return result;
    }

    private int layerGroupsSize() {
        long s = System.currentTimeMillis();
        Filter filter = getLayerGroupFilter();
        int result = getCatalog().count(LayerGroupInfo.class, filter);
        System.err.printf(
                "%s.layerGroupsSize(): %,dms%n",
                getClass().getSimpleName(), System.currentTimeMillis() - s);
        return result;
    }

    @Override
    public int fullSize() {
        long s = System.currentTimeMillis();
        Integer size;
        try {
            Integer layers = cache.get(KEY_LAYERS_FULL_SIZE, () -> layersFullSize());
            Integer groups = cache.get(KEY_LAYERGROUPS_FULL_SIZE, () -> layerGroupsFullSize());
            size = layers.intValue() + groups.intValue();
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
        System.err.printf(
                "%s.fullSize(): %,dms%n",
                getClass().getSimpleName(), System.currentTimeMillis() - s);
        return size;
    }

    private int fullSizeInternal() {
        long s = System.currentTimeMillis();
        Filter filter = Predicates.acceptAll();
        int count = getCatalog().count(PublishedInfo.class, filter);
        System.err.printf(
                "%s.fullSizeInternal(): %,dms%n",
                getClass().getSimpleName(), System.currentTimeMillis() - s);
        return count;
    }

    private int layersFullSize() {
        long s = System.currentTimeMillis();
        Filter filter = Predicates.acceptAll();
        int count = getCatalog().count(LayerInfo.class, filter);
        System.err.printf(
                "%s.layersFullSize(): %,dms%n",
                getClass().getSimpleName(), System.currentTimeMillis() - s);
        return count;
    }

    private int layerGroupsFullSize() {
        long s = System.currentTimeMillis();
        Filter filter = Predicates.acceptAll();
        int count = getCatalog().count(LayerGroupInfo.class, filter);
        System.err.printf(
                "%s.layerGroupsFullSize(): %,dms%n",
                getClass().getSimpleName(), System.currentTimeMillis() - s);
        return count;
    }

    @Override
    public Iterator<PreviewLayer> iterator(final long first, final long count) {
        long s = System.currentTimeMillis();
        Iterator<PreviewLayer> iterator = filteredItems(first, count);
        if (iterator instanceof CloseableIterator) {
            // don't know how to force wicket to close the iterator, lets return
            // a copy. Shouldn't be much overhead as we're paging
            try {
                iterator = Lists.newArrayList(iterator).iterator();
            } finally {
                CloseableIteratorAdapter.close(iterator);
            }
        }
        System.err.printf(
                "%s.iterator(): %,dms%n",
                getClass().getSimpleName(), System.currentTimeMillis() - s);
        return iterator;
    }

    /**
     * Returns the requested page of layer objects after applying any keyword filtering set on the
     * page
     */
    @SuppressWarnings("resource")
    private Iterator<PreviewLayer> filteredItems(long first, long count) {
        final Catalog catalog = getCatalog();

        // global sorting
        final SortParam sort = getSort();
        final Property<PreviewLayer> property = getProperty(sort);

        SortBy sortOrder = null;
        if (sort != null) {
            if (property instanceof BeanProperty) {
                final String sortProperty =
                        ((BeanProperty<PreviewLayer>) property).getPropertyPath();
                sortOrder = sortBy(sortProperty, sort.isAscending());
            } else if (property == NAME) {
                sortOrder = sortBy("prefixedName", sort.isAscending());
            }
        }

        Filter filter = getFilter();

        CloseableIterator<PublishedInfo> pi =
                catalog.list(PublishedInfo.class, filter, (int) first, (int) count, sortOrder);

        return CloseableIteratorAdapter.transform(
                pi,
                new Function<PublishedInfo, PreviewLayer>() {

                    @Override
                    public PreviewLayer apply(PublishedInfo input) {
                        if (input instanceof LayerInfo) {
                            return new PreviewLayer((LayerInfo) input);
                        } else if (input instanceof LayerGroupInfo) {
                            return new PreviewLayer((LayerGroupInfo) input);
                        }
                        return null;
                    }
                });
    }

    @Override
    protected Filter getFilter() {
        // need to get only advertised and enabled layers
        Filter isLayerInfo = Predicates.isInstanceOf(LayerInfo.class);
        Filter isLayerGroupInfo = Predicates.isInstanceOf(LayerGroupInfo.class);

        // Filter for the Layers
        Filter layerFilter = Predicates.and(isLayerInfo, getLayerFilter());
        // Filter for the LayerGroups
        Filter layerGroupFilter = Predicates.and(isLayerGroupInfo, getLayerGroupFilter());
        // Or filter for merging them
        Filter orFilter = Predicates.or(layerFilter, layerGroupFilter);
        // And between the new filter and the initial filter
        System.err.println(orFilter);
        return orFilter;
    }

    private Filter getLayerGroupFilter() {
        Filter fullTextSearchFilter = super.getFilter();
        Filter enabledLayerGroup = Predicates.equal("enabled", true);
        Filter advertisedLayerGroup = Predicates.equal("advertised", true);
        // return only layer groups that are not containers
        Filter nonContainerGroup =
                Predicates.or(
                        Predicates.equal("mode", LayerGroupInfo.Mode.EO),
                        Predicates.equal("mode", LayerGroupInfo.Mode.NAMED),
                        Predicates.equal("mode", LayerGroupInfo.Mode.OPAQUE_CONTAINER),
                        Predicates.equal("mode", LayerGroupInfo.Mode.SINGLE));
        // Filter for the LayerGroups
        Filter layerGroupFilter =
                Predicates.and(nonContainerGroup, enabledLayerGroup, advertisedLayerGroup);
        // And between the new filter and the initial filter
        return Predicates.and(layerGroupFilter, fullTextSearchFilter);
    }

    private Filter getLayerFilter() {
        Filter fullTextSearchFilter = super.getFilter();
        // need to get only advertised and enabled layers
        Filter enabledFilter = Predicates.equal("resource.enabled", true);
        Filter storeEnabledFilter = Predicates.equal("resource.store.enabled", true);
        Filter advertisedFilter = Predicates.equal("resource.advertised", true);
        // Filter for the Layers
        Filter layerFilter =
                Predicates.and(
                        enabledFilter, storeEnabledFilter, advertisedFilter, fullTextSearchFilter);
        // And between the new filter and the initial filter
        return layerFilter;
    }
}
