/* (c) 2014 Open Source Geospatial Foundation - all rights reserved
 * (c) 2001 - 2013 OpenPlans
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.cluster.impl.handlers.catalog;

import com.thoughtworks.xstream.XStream;
import java.io.ByteArrayInputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.CatalogException;
import org.geoserver.catalog.CatalogInfo;
import org.geoserver.catalog.CoverageInfo;
import org.geoserver.catalog.CoverageStoreInfo;
import org.geoserver.catalog.DataStoreInfo;
import org.geoserver.catalog.FeatureTypeInfo;
import org.geoserver.catalog.LayerGroupInfo;
import org.geoserver.catalog.LayerInfo;
import org.geoserver.catalog.MapInfo;
import org.geoserver.catalog.NamespaceInfo;
import org.geoserver.catalog.ResourceInfo;
import org.geoserver.catalog.StoreInfo;
import org.geoserver.catalog.StyleInfo;
import org.geoserver.catalog.WMSLayerInfo;
import org.geoserver.catalog.WMSStoreInfo;
import org.geoserver.catalog.WMTSLayerInfo;
import org.geoserver.catalog.WMTSStoreInfo;
import org.geoserver.catalog.WorkspaceInfo;
import org.geoserver.catalog.event.CatalogEvent;
import org.geoserver.catalog.event.CatalogModifyEvent;
import org.geoserver.cluster.events.ToggleSwitch;
import org.geoserver.cluster.impl.utils.BeanUtils;
import org.geoserver.cluster.server.events.StyleModifyEvent;

/**
 * Handle modify events synchronizing catalog with serialized objects
 *
 * @author Carlo Cancellieri - carlo.cancellieri@geo-solutions.it
 */
public class JMSCatalogModifyEventHandler extends JMSCatalogEventHandler {

    private final Catalog catalog;
    private final ToggleSwitch producer;

    /** */
    public JMSCatalogModifyEventHandler(
            Catalog catalog, XStream xstream, Class clazz, ToggleSwitch producer) {
        super(xstream, clazz);
        this.catalog = catalog;
        this.producer = producer;
    }

    @Override
    public boolean synchronize(CatalogEvent event) throws Exception {
        if (event == null) {
            throw new IllegalArgumentException("Incoming object is null");
        }
        try {
            if (event instanceof CatalogModifyEvent) {
                final CatalogModifyEvent modifyEv = ((CatalogModifyEvent) event);

                producer.disable();
                JMSCatalogModifyEventHandler.modify(catalog, modifyEv);

            } else {
                // incoming object not recognized
                if (LOGGER.isLoggable(java.util.logging.Level.SEVERE))
                    LOGGER.severe("Unrecognized event type");
                return false;
            }

        } catch (Exception e) {
            if (LOGGER.isLoggable(java.util.logging.Level.SEVERE))
                LOGGER.severe(
                        this.getClass() + " is unable to synchronize the incoming event: " + event);
            throw e;
        } finally {
            // re enable the producer
            producer.enable();
        }
        return true;
    }

    /**
     * simulate a catalog.save() rebuilding the EventModify proxy object locally {@link
     * org.geoserver.catalog.impl.DefaultCatalogFacade#saved(CatalogInfo)}
     *
     * <p>TODO synchronization on catalog object
     */
    protected static void modify(final Catalog catalog, CatalogModifyEvent modifyEv)
            throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {

        final CatalogInfo info = modifyEv.getSource();

        if (info instanceof LayerGroupInfo) {
            final LayerGroupInfo localObject = catalog.getLayerGroup(info.getId());

            if (localObject == null) {
                throw new CatalogException("Unable to locate " + info + " locally.");
            }

            BeanUtils.smartUpdate(
                    localObject, modifyEv.getPropertyNames(), modifyEv.getNewValues());
            catalog.save(localObject);

        } else if (info instanceof LayerInfo) {
            final LayerInfo localObject = catalog.getLayer(info.getId());

            if (localObject == null) {
                throw new CatalogException("Unable to locate " + info + " locally.");
            }

            BeanUtils.smartUpdate(
                    localObject, modifyEv.getPropertyNames(), modifyEv.getNewValues());
            catalog.save(localObject);

        } else if (info instanceof MapInfo) {
            final MapInfo localObject = catalog.getMap(info.getId());

            if (localObject == null) {
                throw new CatalogException("Unable to locate " + info + " locally.");
            }

            BeanUtils.smartUpdate(
                    localObject, modifyEv.getPropertyNames(), modifyEv.getNewValues());
            catalog.save(localObject);

        } else if (info instanceof NamespaceInfo) {
            final NamespaceInfo localObject = catalog.getNamespace(info.getId());

            if (localObject == null) {
                throw new CatalogException("Unable to locate " + info + " locally.");
            }

            BeanUtils.smartUpdate(
                    localObject, modifyEv.getPropertyNames(), modifyEv.getNewValues());
            catalog.save(localObject);

        } else if (info instanceof StoreInfo) {
            final StoreInfo localObject;
            if (info instanceof CoverageStoreInfo) {
                localObject = catalog.getCoverageStore(info.getId());
            } else if (info instanceof DataStoreInfo) {
                localObject = catalog.getDataStore(info.getId());
            } else if (info instanceof WMSStoreInfo) {
                localObject = catalog.getStore(info.getId(), WMSStoreInfo.class);
            } else if (info instanceof WMTSStoreInfo) {
                localObject = catalog.getStore(info.getId(), WMTSStoreInfo.class);
            } else {
                throw new IllegalArgumentException(
                        "Unable to provide localization for the passed instance");
            }

            if (localObject == null) {
                throw new CatalogException("Unable to locate " + info + " locally.");
            }

            BeanUtils.smartUpdate(
                    localObject, modifyEv.getPropertyNames(), modifyEv.getNewValues());
            catalog.save(localObject);

        } else if (info instanceof ResourceInfo) {
            final ResourceInfo localObject;
            if (info instanceof CoverageInfo) {
                // coverage
                localObject = catalog.getCoverage(info.getId());
            } else if (info instanceof FeatureTypeInfo) {
                // feature
                localObject = catalog.getFeatureType(info.getId());
            } else if (info instanceof WMSLayerInfo) {
                // wmslayer
                localObject = catalog.getResource(info.getId(), WMSLayerInfo.class);
            } else if (info instanceof WMTSLayerInfo) {
                // wmtslayer
                localObject = catalog.getResource(info.getId(), WMTSLayerInfo.class);
            } else {
                throw new IllegalArgumentException(
                        "Unable to provide localization for the passed instance");
            }
            if (localObject == null) {
                throw new CatalogException("Unable to locate " + info + " locally.");
            }

            BeanUtils.smartUpdate(
                    localObject, modifyEv.getPropertyNames(), modifyEv.getNewValues());
            catalog.save(localObject);

        } else if (info instanceof StyleInfo) {
            final StyleInfo localObject = catalog.getStyle(info.getId());

            if (localObject == null) {
                throw new CatalogException("Unable to locate " + info + " locally.");
            }

            BeanUtils.smartUpdate(
                    localObject, modifyEv.getPropertyNames(), modifyEv.getNewValues());
            // update the style in the catalog
            catalog.save(localObject);

            // let's if the style file was provided
            if (modifyEv instanceof StyleModifyEvent) {
                StyleModifyEvent styleModifyEvent = (StyleModifyEvent) modifyEv;
                byte[] fileContent = styleModifyEvent.getFile();
                if (fileContent != null && fileContent.length != 0) {
                    // update the style file using the old style
                    StyleInfo oldStyle = catalog.getStyle(info.getId());
                    try {
                        catalog.getResourcePool()
                                .writeStyle(oldStyle, new ByteArrayInputStream(fileContent));
                    } catch (Exception exception) {
                        throw new RuntimeException(
                                String.format(
                                        "Error writing style '%s' file.", localObject.getName()),
                                exception);
                    }
                }
            }

        } else if (info instanceof WorkspaceInfo) {
            final WorkspaceInfo localObject = catalog.getWorkspace(info.getId());

            if (localObject == null) {
                throw new CatalogException("Unable to locate " + info + " locally.");
            }

            BeanUtils.smartUpdate(
                    localObject, modifyEv.getPropertyNames(), modifyEv.getNewValues());
            catalog.save(localObject);

        } else if (info instanceof CatalogInfo) {

            // change default workspace in the handled catalog
            /**
             * This piece of code was extracted from: {@link
             * org.geoserver.catalog.NamespaceWorkspaceConsistencyListener#handleModifyEvent(CatalogModifyEvent)}
             */
            final List<String> properties = modifyEv.getPropertyNames();
            if (properties.contains("defaultNamespace")) {
                final NamespaceInfo newDefault =
                        (NamespaceInfo)
                                modifyEv.getNewValues().get(properties.indexOf("defaultNamespace"));
                if (newDefault != null) {
                    final WorkspaceInfo ws = catalog.getWorkspaceByName(newDefault.getPrefix());
                    if (ws != null && !catalog.getDefaultWorkspace().equals(ws)) {
                        catalog.setDefaultWorkspace(ws);
                    }
                }
            } else if (properties.contains("defaultWorkspace")) {
                final WorkspaceInfo newDefault =
                        (WorkspaceInfo)
                                modifyEv.getNewValues().get(properties.indexOf("defaultWorkspace"));
                if (newDefault != null) {
                    final NamespaceInfo ns = catalog.getNamespaceByPrefix(newDefault.getName());
                    if (ns != null && !catalog.getDefaultNamespace().equals(ns)) {
                        catalog.setDefaultNamespace(ns);
                    }
                }
            }

        } else {
            if (LOGGER.isLoggable(java.util.logging.Level.WARNING)) {
                LOGGER.warning("info - ID: " + info.getId() + " toString: " + info.toString());
            }
            throw new IllegalArgumentException("Bad incoming object: " + info.toString());
        }
    }
}
