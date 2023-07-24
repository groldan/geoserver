/* (c) 2024 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.web.catalogstresstool;

import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.CatalogVisitor;
import org.geoserver.catalog.CoverageInfo;
import org.geoserver.catalog.CoverageStoreInfo;
import org.geoserver.catalog.DataStoreInfo;
import org.geoserver.catalog.FeatureTypeInfo;
import org.geoserver.catalog.ResourceInfo;
import org.geoserver.catalog.StoreInfo;
import org.geoserver.catalog.WMSLayerInfo;
import org.geoserver.catalog.WMSStoreInfo;
import org.geoserver.catalog.WMTSLayerInfo;
import org.geoserver.catalog.WMTSStoreInfo;

interface AbstractCatalogVisitor extends CatalogVisitor {

    @Override
    default void visit(Catalog catalog) {
        // no-op
    }

    void visit(StoreInfo store);

    void visit(ResourceInfo resource);

    @Override
    default void visit(DataStoreInfo store) {
        visit((StoreInfo) store);
    }

    @Override
    default void visit(CoverageStoreInfo store) {
        visit((StoreInfo) store);
    }

    @Override
    default void visit(WMSStoreInfo store) {
        visit((StoreInfo) store);
    }

    @Override
    default void visit(WMTSStoreInfo store) {
        visit((StoreInfo) store);
    }

    @Override
    default void visit(FeatureTypeInfo resource) {
        visit((ResourceInfo) resource);
    }

    @Override
    default void visit(CoverageInfo resource) {
        visit((ResourceInfo) resource);
    }

    @Override
    default void visit(WMSLayerInfo resource) {
        visit((ResourceInfo) resource);
    }

    @Override
    default void visit(WMTSLayerInfo resource) {
        visit((ResourceInfo) resource);
    }
}
