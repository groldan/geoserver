/* (c) 2014 Open Source Geospatial Foundation - all rights reserved
 * (c) 2001 - 2013 OpenPlans
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.cluster.impl.handlers.catalog;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import org.apache.commons.beanutils.BeanUtils;
import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.CatalogBuilder;
import org.geoserver.catalog.CoverageInfo;
import org.geoserver.catalog.CoverageStoreInfo;
import org.geoserver.catalog.DataStoreInfo;
import org.geoserver.catalog.FeatureTypeInfo;
import org.geoserver.catalog.LayerGroupInfo;
import org.geoserver.catalog.LayerInfo;
import org.geoserver.catalog.MapInfo;
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
import org.geotools.util.logging.Logging;

/** @author Carlo Cancellieri - carlo.cancellieri@geo-solutions.it */
public abstract class CatalogUtils {
    public static final java.util.logging.Logger LOGGER = Logging.getLogger(CatalogUtils.class);

    /** @return the local workspace if found or the passed one (localized) */
    public static WorkspaceInfo localizeWorkspace(
            final WorkspaceInfo info, final Catalog catalog, boolean createIfMissing) {
        if (info == null || catalog == null)
            throw new NullPointerException("Arguments may never be null");

        final WorkspaceInfo localObject = catalog.getWorkspace(info.getId());
        if (localObject != null || !createIfMissing) {
            return localObject;
        }

        final CatalogBuilder builder = new CatalogBuilder(catalog);
        builder.attach(info);

        return info;
    }

    public static NamespaceInfo localizeNamespace(
            final NamespaceInfo info, final Catalog catalog, boolean createIfMissing) {
        if (info == null || catalog == null)
            throw new NullPointerException("Arguments may never be null");

        final NamespaceInfo localObject = catalog.getNamespaceByURI(info.getURI());
        if (localObject != null || !createIfMissing) {
            return localObject;
        }

        final CatalogBuilder builder = new CatalogBuilder(catalog);
        builder.attach(info);
        return info;
    }

    /** @return the local style or the passed one (if not exists locally) */
    public static StyleInfo localizeStyle(
            final StyleInfo info, final Catalog catalog, boolean createIfMissing) {
        if (info == null || catalog == null)
            throw new NullPointerException("Arguments may never be null");

        final StyleInfo localObject = catalog.getStyle(info.getId());
        if (localObject != null || !createIfMissing) {
            return localObject;
        } else {
            if (LOGGER.isLoggable(java.util.logging.Level.INFO)) {
                LOGGER.info(
                        "No such style called \'"
                                + info.getName()
                                + "\' can be found: LOCALIZATION");
            }
            final CatalogBuilder builder = new CatalogBuilder(catalog);
            builder.attach(info);
            return info;
        }
    }

    private static List<StyleInfo> localizeStyles(
            final Collection<StyleInfo> stiles, final Catalog catalog, boolean createIfMissing) {
        if (stiles == null || catalog == null)
            throw new NullPointerException("Arguments may never be null");
        final List<StyleInfo> localStileSet = new ArrayList<>();
        final Iterator<StyleInfo> deserStyleSetIterator = stiles.iterator();
        while (deserStyleSetIterator.hasNext()) {
            final StyleInfo deserStyle = deserStyleSetIterator.next();
            final StyleInfo localStyle = localizeStyle(deserStyle, catalog, createIfMissing);
            if (localStyle != null) {
                localStileSet.add(localStyle);
            }
        }
        return localStileSet;
    }

    private static <T extends PublishedInfo> List<LayerInfo> localizeLayers(
            final List<T> info, final Catalog catalog, boolean createIfMissing)
            throws IllegalAccessException, InvocationTargetException {
        if (info == null || catalog == null)
            throw new NullPointerException("Arguments may never be null");
        final List<LayerInfo> localLayerList = new ArrayList<LayerInfo>(info.size());
        final Iterator<LayerInfo> it = localLayerList.iterator();
        while (it.hasNext()) {
            final LayerInfo layer = it.next();
            final LayerInfo localLayer = localizeLayer(layer, catalog, createIfMissing);
            if (localLayer != null) {
                localLayerList.add(localLayer);
            } else {
                if (LOGGER.isLoggable(java.util.logging.Level.WARNING)) {
                    LOGGER.warning(
                            "No such layer called \'"
                                    + layer.getName()
                                    + "\' can be found: SKIPPING");
                }
            }
        }
        return localLayerList;
    }

    public static LayerInfo localizeLayer(
            final LayerInfo info, final Catalog catalog, boolean createIfMissing)
            throws IllegalAccessException, InvocationTargetException {
        if (info == null || catalog == null)
            throw new NullPointerException("Arguments may never be null");

        // make sure we use the prefixed name to include the workspace
        LayerInfo localObject = catalog.getLayer(info.getId());
        if (localObject == null && !createIfMissing) {
            return localObject;
        } else if (localObject == null) {
            localObject = catalog.getFactory().createLayer();
        }

        // RESOURCE
        ResourceInfo resource = info.getResource();
        if (resource != null) {
            resource = localizeResource(resource, catalog, false);
        } else {
            throw new NullPointerException("No resource found !!!");
        }

        // we have to set the resource before [and after] calling copyProperties
        // it is needed to call setName(String)
        localObject.setResource(resource);

        // let's use the newly created object
        BeanUtils.copyProperties(localObject, info);

        // we have to set the resource before [and after] calling copyProperties
        // it is overwritten (set to null) by the copyProperties function
        localObject.setResource(resource);

        final StyleInfo deserDefaultStyle = info.getDefaultStyle();
        if (deserDefaultStyle != null) {
            final StyleInfo localDefaultStyle = localizeStyle(deserDefaultStyle, catalog, false);
            if (localDefaultStyle == null) {
                throw new NullPointerException(
                        "No matching style called \'"
                                + deserDefaultStyle.getName()
                                + "\'found locally.");
            }
            localObject.setDefaultStyle(localDefaultStyle);
        } else {

            // the default style is set by the builder

            // TODO: check: this happens when configuring a layer using GeoServer REST manager (see
            // ImageMosaicTest)
        }

        // STYLES
        localObject.getStyles().clear();
        localObject.getStyles().addAll(localizeStyles(info.getStyles(), catalog, false));

        final CatalogBuilder builder = new CatalogBuilder(catalog);
        builder.attach(localObject);
        return localObject;
    }

    public static MapInfo localizeMapInfo(
            final MapInfo info, final Catalog catalog, boolean createIfMissing)
            throws IllegalAccessException, InvocationTargetException {
        if (info == null || catalog == null)
            throw new NullPointerException("Arguments may never be null");

        final MapInfo localObject = catalog.getMap(info.getId());
        if (localObject != null || !createIfMissing) {
            return localObject;
            // else object is modified: continue with localization
        }

        info.getLayers().addAll(localizeLayers(info.getLayers(), catalog, createIfMissing));

        final CatalogBuilder builder = new CatalogBuilder(catalog);
        builder.attach(info);
        return info;
    }

    public static LayerGroupInfo localizeLayerGroup(
            final LayerGroupInfo info, final Catalog catalog, boolean createIfMissing)
            throws IllegalAccessException, InvocationTargetException {
        if (info == null || catalog == null)
            throw new NullPointerException("Arguments may never be null");

        LayerGroupInfo localObject = catalog.getLayerGroup(info.getId());

        if (localObject == null && createIfMissing) {
            localObject = catalog.getFactory().createLayerGroup();
            // let's use the newly created object
            BeanUtils.copyProperties(localObject, info);
        } else if (localObject == null) {
            return localObject;
        }

        try {
            List<PublishedInfo> publishedInfos = info.getLayers();
            List<PublishedInfo> layers = new ArrayList<>();
            for (PublishedInfo publishedInfo : publishedInfos) {
                PublishedInfo localizedChild;
                if (publishedInfo instanceof LayerGroupInfo) {
                    localizedChild =
                            localizeLayerGroup((LayerGroupInfo) publishedInfo, catalog, false);
                } else {
                    localizedChild = localizeLayer((LayerInfo) publishedInfo, catalog, false);
                }
                layers.add(localizedChild);
            }
            localObject.getLayers().clear();
            localObject.getLayers().addAll(layers);
        } catch (IllegalAccessException e) {
            if (LOGGER.isLoggable(java.util.logging.Level.SEVERE))
                LOGGER.severe(e.getLocalizedMessage());
            throw e;
        } catch (InvocationTargetException e) {
            if (LOGGER.isLoggable(java.util.logging.Level.SEVERE))
                LOGGER.severe(e.getLocalizedMessage());
            throw e;
        }

        // localize styles, order matters
        List<StyleInfo> styles = CatalogUtils.localizeStyles(info.getStyles(), catalog, false);
        localObject.getStyles().clear();
        localObject.getStyles().addAll(styles);

        if (info.getRootLayer() != null) {
            localObject.setRootLayer(
                    CatalogUtils.localizeLayer(info.getRootLayer(), catalog, false));
        }

        if (info.getRootLayerStyle() != null) {
            localObject.setRootLayerStyle(
                    CatalogUtils.localizeStyle(info.getRootLayerStyle(), catalog, false));
        }

        if (info.getWorkspace() != null) {
            localObject.setWorkspace(
                    CatalogUtils.localizeWorkspace(info.getWorkspace(), catalog, false));
        }

        final CatalogBuilder builder = new CatalogBuilder(catalog);
        builder.attach(localObject);

        return localObject;
    }

    public static StoreInfo localizeStore(
            final StoreInfo info, final Catalog catalog, boolean createIfMissing)
            throws IllegalAccessException, InvocationTargetException {
        if (info == null || catalog == null)
            throw new NullPointerException("Arguments may never be null");

        if (info instanceof CoverageStoreInfo) {
            return localizeCoverageStore((CoverageStoreInfo) info, catalog, createIfMissing);
        } else if (info instanceof DataStoreInfo) {
            return localizeDataStore((DataStoreInfo) info, catalog, createIfMissing);
        } else if (info instanceof WMSStoreInfo) {
            return localizeWMSStore((WMSStoreInfo) info, catalog, createIfMissing);
        } else if (info instanceof WMTSStoreInfo) {
            return localizeWMTSStore((WMTSStoreInfo) info, catalog, createIfMissing);
        } else {
            throw new IllegalArgumentException(
                    "Unable to provide localization for the passed instance");
        }
    }

    private static DataStoreInfo localizeDataStore(
            final DataStoreInfo info, final Catalog catalog, boolean createIfMissing)
            throws IllegalAccessException, InvocationTargetException {
        if (info == null || catalog == null)
            throw new NullPointerException("Arguments may never be null");

        final DataStoreInfo localObject = catalog.getDataStore(info.getId());

        final CatalogBuilder builder = new CatalogBuilder(catalog);

        if (localObject != null || !createIfMissing) {
            return localObject;
        }

        final DataStoreInfo createdObject = catalog.getFactory().createDataStore();

        // let's using the created object (see getGridCoverageReader)
        BeanUtils.copyProperties(createdObject, info);

        createdObject.setWorkspace(localizeWorkspace(info.getWorkspace(), catalog, false));

        builder.attach(createdObject);
        return createdObject;
    }

    private static WMSStoreInfo localizeWMSStore(
            final WMSStoreInfo info, final Catalog catalog, boolean createIfMissing)
            throws IllegalAccessException, InvocationTargetException {
        if (info == null || catalog == null)
            throw new NullPointerException("Arguments may never be null");

        final WMSStoreInfo localObject = catalog.getStore(info.getId(), WMSStoreInfo.class);

        final CatalogBuilder builder = new CatalogBuilder(catalog);

        if (localObject != null || !createIfMissing) {
            return localObject;
        }

        final WMSStoreInfo createdObject = catalog.getFactory().createWebMapServer();

        // let's using the created object (see getGridCoverageReader)
        BeanUtils.copyProperties(createdObject, info);

        createdObject.setWorkspace(localizeWorkspace(info.getWorkspace(), catalog, false));

        builder.attach(createdObject);
        return createdObject;
    }

    private static WMTSStoreInfo localizeWMTSStore(
            final WMTSStoreInfo info, final Catalog catalog, boolean createIfMissing)
            throws IllegalAccessException, InvocationTargetException {
        if (info == null || catalog == null)
            throw new NullPointerException("Arguments may never be null");

        final WMTSStoreInfo localObject = catalog.getStore(info.getId(), WMTSStoreInfo.class);

        final CatalogBuilder builder = new CatalogBuilder(catalog);

        if (localObject != null || !createIfMissing) {
            return localObject;
        }

        final WMTSStoreInfo createdObject = catalog.getFactory().createWebMapTileServer();

        // let's using the created object (see getGridCoverageReader)
        BeanUtils.copyProperties(createdObject, info);

        createdObject.setWorkspace(localizeWorkspace(info.getWorkspace(), catalog, false));

        builder.attach(createdObject);
        return createdObject;
    }

    private static CoverageStoreInfo localizeCoverageStore(
            final CoverageStoreInfo info, final Catalog catalog, boolean createIfMissing)
            throws IllegalAccessException, InvocationTargetException {
        if (info == null || catalog == null)
            throw new NullPointerException("Arguments may never be null");

        final CoverageStoreInfo localObject = catalog.getCoverageStore(info.getId());

        final CatalogBuilder builder = new CatalogBuilder(catalog);

        if (localObject != null || !createIfMissing) {
            return localObject;
        }

        final CoverageStoreInfo createdObject = catalog.getFactory().createCoverageStore();

        // let's using the created object (see getGridCoverageReader)
        BeanUtils.copyProperties(createdObject, info);

        createdObject.setWorkspace(localizeWorkspace(info.getWorkspace(), catalog, false));

        builder.attach(createdObject);
        return createdObject;
    }

    public static ResourceInfo localizeResource(
            final ResourceInfo info, final Catalog catalog, boolean createIfMissing)
            throws IllegalAccessException, InvocationTargetException {
        if (info == null || catalog == null)
            throw new NullPointerException("Arguments may never be null");

        if (info instanceof CoverageInfo) {
            // coverage
            return localizeCoverage((CoverageInfo) info, catalog, createIfMissing);

        } else if (info instanceof FeatureTypeInfo) {
            // feature
            return localizeFeatureType((FeatureTypeInfo) info, catalog, createIfMissing);

        } else if (info instanceof WMSLayerInfo) {
            // wmslayer
            return localizeWMSLayer((WMSLayerInfo) info, catalog, createIfMissing);
        } else if (info instanceof WMTSLayerInfo) {
            return localizeWMTSLayer((WMTSLayerInfo) info, catalog, createIfMissing);
        } else {
            throw new IllegalArgumentException(
                    "Unable to provide localization for the passed instance");
        }
    }

    private static WMSLayerInfo localizeWMSLayer(
            final WMSLayerInfo info, final Catalog catalog, boolean createIfMissing)
            throws IllegalAccessException, InvocationTargetException {
        if (info == null || catalog == null)
            throw new NullPointerException("Arguments may never be null");

        final WMSLayerInfo localObject = catalog.getResource(info.getId(), WMSLayerInfo.class);
        if (localObject != null || !createIfMissing) {
            return localObject;
        }

        final WMSLayerInfo createdObject = catalog.getFactory().createWMSLayer();

        // let's using the created object (see getGridCoverageReader)
        BeanUtils.copyProperties(createdObject, info);

        createdObject.setNamespace(localizeNamespace(info.getNamespace(), catalog, false));

        final StoreInfo store = localizeStore(info.getStore(), catalog, false);
        createdObject.setStore(store);

        // WMSLayerObject.setAttributes(localizeAttributes(...)); TODO(should be already serialized)

        final CatalogBuilder builder = new CatalogBuilder(catalog);
        builder.attach(createdObject);
        return createdObject;
    }

    private static WMTSLayerInfo localizeWMTSLayer(
            final WMTSLayerInfo info, final Catalog catalog, boolean createIfMissing)
            throws IllegalAccessException, InvocationTargetException {
        if (info == null || catalog == null)
            throw new NullPointerException("Arguments may never be null");

        final WMTSLayerInfo localObject = catalog.getResource(info.getId(), WMTSLayerInfo.class);
        if (localObject != null || !createIfMissing) {
            return localObject;
        }

        final WMTSLayerInfo createdObject = catalog.getFactory().createWMTSLayer();

        // let's using the created object (see getGridCoverageReader)
        BeanUtils.copyProperties(createdObject, info);

        createdObject.setNamespace(localizeNamespace(info.getNamespace(), catalog, false));

        final StoreInfo store = localizeStore(info.getStore(), catalog, false);
        createdObject.setStore(store);

        final CatalogBuilder builder = new CatalogBuilder(catalog);
        builder.attach(createdObject);
        return createdObject;
    }

    private static FeatureTypeInfo localizeFeatureType(
            final FeatureTypeInfo info, final Catalog catalog, boolean createIfMissing)
            throws IllegalAccessException, InvocationTargetException {
        if (info == null || catalog == null)
            throw new NullPointerException("Arguments may never be null");

        final FeatureTypeInfo localObject = catalog.getFeatureType(info.getId());

        if (localObject != null || !createIfMissing) {
            return localObject;
        }

        final FeatureTypeInfo createdObject = catalog.getFactory().createFeatureType();

        // let's using the created object (see getGridCoverageReader)
        BeanUtils.copyProperties(createdObject, info);

        createdObject.setNamespace(localizeNamespace(info.getNamespace(), catalog, false));

        final StoreInfo store = localizeStore(info.getStore(), catalog, false);
        createdObject.setStore(store);

        final CatalogBuilder builder = new CatalogBuilder(catalog);
        builder.attach(createdObject);
        return createdObject;
    }

    private static CoverageInfo localizeCoverage(
            final CoverageInfo info, final Catalog catalog, boolean createIfMissing)
            throws IllegalAccessException, InvocationTargetException {
        if (info == null || catalog == null)
            throw new NullPointerException("Arguments may never be null");

        final CoverageInfo localObject = catalog.getCoverage(info.getId());
        if (localObject != null || !createIfMissing) {
            return localObject;
        }

        final CoverageInfo createdObject = catalog.getFactory().createCoverage();

        // let's using the created object (see getGridCoverageReader)
        BeanUtils.copyProperties(createdObject, info);

        createdObject.setNamespace(localizeNamespace(info.getNamespace(), catalog, false));

        createdObject.setStore(localizeCoverageStore(info.getStore(), catalog, false));

        final CatalogBuilder builder = new CatalogBuilder(catalog);
        builder.attach(createdObject);
        return createdObject;
    }
}
