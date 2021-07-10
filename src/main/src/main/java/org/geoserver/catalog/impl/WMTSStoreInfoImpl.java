/* (c) 2017 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.catalog.impl;

import java.io.IOException;
import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.CatalogVisitor;
import org.geoserver.catalog.ResourcePool;
import org.geoserver.catalog.WMTSStoreInfo;
import org.geotools.ows.wmts.WebMapTileServer;
import org.opengis.util.ProgressListener;

@SuppressWarnings("serial")
public class WMTSStoreInfoImpl extends HTTPStoreInfoImpl implements WMTSStoreInfo {

    public static final int DEFAULT_MAX_CONNECTIONS = 6;

    public static final int DEFAULT_CONNECT_TIMEOUT = 30;

    public static final int DEFAULT_READ_TIMEOUT = 60;

    // Map<String, String> headers;
    private String headerName; // todo: replace with Map<String, String>
    private String headerValue; // todo: replace with Map<String, String>

    protected WMTSStoreInfoImpl() {}

    public WMTSStoreInfoImpl(Catalog catalog) {
        super(catalog);
    }

    @Override
    public String getHeaderName() {
        return headerName;
    }

    @Override
    public void setHeaderName(String headerName) {
        this.headerName = headerName;
    }

    @Override
    public String getHeaderValue() {
        return headerValue;
    }

    @Override
    public void setHeaderValue(String headerValue) {
        this.headerValue = headerValue;
    }

    @Override
    public void accept(CatalogVisitor visitor) {
        visitor.visit(this);
    }

    @Override
    public WebMapTileServer getWebMapTileServer(ProgressListener listener) throws IOException {
        Catalog catalog2 = getCatalog();
        ResourcePool resourcePool = catalog2.getResourcePool();
        WebMapTileServer webMapTileServer = resourcePool.getWebMapTileServer(this);
        return webMapTileServer;
    }

    @Override
    public int hashCode() {
        return WMTSStoreInfo.hashCode(this);
    }

    @Override
    public boolean equals(Object obj) {
        return WMTSStoreInfo.equals(this, obj);
    }
}
